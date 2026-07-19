package org.mage.benchmark.comparison;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class EnvironmentManifest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String ref;
    private final String commit;
    private final boolean dirty;
    private final String runConfig;
    private final String javaVendor;
    private final String javaVersion;
    private final String vmName;
    private final String osName;
    private final String osArch;
    private final String cpuModel;
    private final int processors;
    private final String timestamp;

    private EnvironmentManifest(
            String ref,
            String commit,
            boolean dirty,
            String runConfig,
            String javaVendor,
            String javaVersion,
            String vmName,
            String osName,
            String osArch,
            String cpuModel,
            int processors,
            String timestamp) {
        this.ref = requireText(ref, "ref");
        this.commit = requireText(commit, "commit");
        this.dirty = dirty;
        this.runConfig = requireText(runConfig, "runConfig");
        this.javaVendor = requireText(javaVendor, "javaVendor");
        this.javaVersion = requireText(javaVersion, "javaVersion");
        this.vmName = requireText(vmName, "vmName");
        this.osName = requireText(osName, "osName");
        this.osArch = requireText(osArch, "osArch");
        this.cpuModel = requireText(cpuModel, "cpuModel");
        if (processors <= 0) {
            throw new IllegalArgumentException("processors must be positive");
        }
        this.processors = processors;
        this.timestamp = requireText(timestamp, "timestamp");
        try {
            Instant.parse(timestamp);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("timestamp must be an ISO-8601 instant", e);
        }
    }

    public static EnvironmentManifest capture(
            String ref,
            String commit,
            boolean dirty,
            String runConfig) {
        String osName = requireSystemProperty("os.name");
        String osArch = requireSystemProperty("os.arch");
        return new EnvironmentManifest(
                ref,
                commit,
                dirty,
                runConfig,
                requireSystemProperty("java.vendor"),
                requireSystemProperty("java.version"),
                requireSystemProperty("java.vm.name"),
                osName,
                osArch,
                detectCpuModel(osName, osArch),
                Runtime.getRuntime().availableProcessors(),
                Instant.now().toString());
    }

    public static EnvironmentManifest read(Path path) throws IOException {
        RawManifest raw;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            raw = GSON.fromJson(reader, RawManifest.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid environment manifest JSON: " + path, e);
        }
        if (raw == null) {
            throw new IllegalArgumentException("Environment manifest must contain a JSON object");
        }
        if (raw.dirty == null) {
            throw new IllegalArgumentException("dirty is missing");
        }
        if (raw.processors == null) {
            throw new IllegalArgumentException("processors is missing");
        }
        return new EnvironmentManifest(
                raw.ref,
                raw.commit,
                raw.dirty,
                raw.runConfig,
                raw.javaVendor,
                raw.javaVersion,
                raw.vmName,
                raw.osName,
                raw.osArch,
                raw.cpuModel,
                raw.processors,
                raw.timestamp);
    }

    public void writeNew(Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            GSON.toJson(this, writer);
        }
    }

    public List<String> compatibilityProblems(EnvironmentManifest other) {
        if (other == null) {
            throw new IllegalArgumentException("Other environment manifest must not be null");
        }
        List<String> problems = new ArrayList<>();
        if (dirty || other.dirty) {
            problems.add("dirty: benchmark worktrees must both be clean");
        }
        compare("runConfig", runConfig, other.runConfig, problems);
        compare("javaVendor", javaVendor, other.javaVendor, problems);
        compare("javaVersion", javaVersion, other.javaVersion, problems);
        compare("vmName", vmName, other.vmName, problems);
        compare("osName", osName, other.osName, problems);
        compare("osArch", osArch, other.osArch, problems);
        compare("cpuModel", cpuModel, other.cpuModel, problems);
        if (processors != other.processors) {
            problems.add("processors mismatch: " + processors + " != " + other.processors);
        }
        return problems;
    }

    public String getRef() {
        return ref;
    }

    private static void compare(String field, String left, String right, List<String> problems) {
        if (!Objects.equals(left, right)) {
            problems.add(field + " mismatch: " + left + " != " + right);
        }
    }

    private static String requireSystemProperty(String name) {
        return requireText(System.getProperty(name), name);
    }

    private static String requireText(String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " is missing or empty");
        }
        return value.trim();
    }

    private static String detectCpuModel(String osName, String osArch) {
        String normalizedOs = osName.toLowerCase(Locale.ROOT);
        String model = null;
        if (normalizedOs.contains("mac")) {
            model = readMacCpuModel();
        } else if (normalizedOs.contains("linux")) {
            model = readLinuxCpuModel();
        } else if (normalizedOs.contains("windows")) {
            model = System.getenv("PROCESSOR_IDENTIFIER");
        }
        if (model == null || model.trim().isEmpty()) {
            model = osArch;
        }
        return requireText(model, "cpuModel");
    }

    private static String readMacCpuModel() {
        Process process = null;
        try {
            process = new ProcessBuilder("/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string")
                    .redirectErrorStream(true)
                    .start();
            String line;
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }
            return process.waitFor() == 0 ? line : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readLinuxCpuModel() {
        Path cpuInfo = java.nio.file.Paths.get("/proc/cpuinfo");
        try (BufferedReader reader = Files.newBufferedReader(cpuInfo, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator >= 0 && "model name".equalsIgnoreCase(line.substring(0, separator).trim())) {
                    return line.substring(separator + 1).trim();
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static final class RawManifest {
        private String ref;
        private String commit;
        private Boolean dirty;
        private String runConfig;
        private String javaVendor;
        private String javaVersion;
        private String vmName;
        private String osName;
        private String osArch;
        private String cpuModel;
        private Integer processors;
        private String timestamp;
    }
}
