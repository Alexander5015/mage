package org.mage.benchmark.comparison;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnvironmentManifestTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void ignoresRefCommitAndTimestampForCompatibility() throws Exception {
        EnvironmentManifest first = readManifest("first.json", manifest(
                "baseline", "aaa", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-01T00:00:00Z"));
        EnvironmentManifest second = readManifest("second.json", manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-02-01T00:00:00Z"));

        assertTrue(first.compatibilityProblems(second).isEmpty());
    }

    @Test
    public void namesEveryMaterialCompatibilityMismatch() throws Exception {
        EnvironmentManifest expected = readManifest("expected.json", manifest(
                "baseline", "aaa", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-01T00:00:00Z"));

        assertMismatch(expected, "dirty", manifest(
                "candidate", "bbb", true, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "runConfig", manifest(
                "candidate", "bbb", false, "smoke", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "javaVendor", manifest(
                "candidate", "bbb", false, "full", "OtherVendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "javaVersion", manifest(
                "candidate", "bbb", false, "full", "Vendor", "21", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "vmName", manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "OtherVM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "osName", manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "OtherOS", "x86_64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "osArch", manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "TestOS", "aarch64", "CPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "cpuModel", manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "OtherCPU", 8,
                "2026-01-02T00:00:00Z"));
        assertMismatch(expected, "processors", manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 16,
                "2026-01-02T00:00:00Z"));
    }

    @Test
    public void manifestMainCreatesNewFileAndRejectsBadInputs() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("manifest.json");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int created = ManifestMain.run(
                new String[]{output.toString(), "baseline", "abc", "false", "smoke"},
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(0, created);
        assertTrue(Files.exists(output));
        assertEquals("baseline", EnvironmentManifest.read(output).getRef());

        int existing = ManifestMain.run(
                new String[]{output.toString(), "baseline", "abc", "false", "smoke"},
                new PrintStream(stdout),
                new PrintStream(stderr));
        int malformedBoolean = ManifestMain.run(
                new String[]{temporaryFolder.getRoot().toPath().resolve("bad.json").toString(),
                        "baseline", "abc", "not-a-boolean", "smoke"},
                new PrintStream(stdout),
                new PrintStream(stderr));

        assertEquals(2, existing);
        assertEquals(2, malformedBoolean);
        assertFalse(stderr.toString("UTF-8").isEmpty());
    }

    private void assertMismatch(EnvironmentManifest expected, String field, String json) throws Exception {
        EnvironmentManifest actual = readManifest(field + ".json", json);
        List<String> problems = expected.compatibilityProblems(actual);
        assertFalse(problems.isEmpty());
        assertTrue(problems.toString(), problems.toString().contains(field));
    }

    private EnvironmentManifest readManifest(String name, String json) throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve(name);
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        return EnvironmentManifest.read(path);
    }

    static String manifest(
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
        return "{"
                + "\"ref\":\"" + ref + "\",\"commit\":\"" + commit + "\","
                + "\"dirty\":" + dirty + ",\"runConfig\":\"" + runConfig + "\","
                + "\"javaVendor\":\"" + javaVendor + "\",\"javaVersion\":\"" + javaVersion + "\","
                + "\"vmName\":\"" + vmName + "\",\"osName\":\"" + osName + "\","
                + "\"osArch\":\"" + osArch + "\",\"cpuModel\":\"" + cpuModel + "\","
                + "\"processors\":" + processors + ",\"timestamp\":\"" + timestamp + "\"}";
    }
}
