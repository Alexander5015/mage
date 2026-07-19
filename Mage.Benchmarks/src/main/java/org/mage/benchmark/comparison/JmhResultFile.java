package org.mage.benchmark.comparison;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class JmhResultFile {
    private final List<Result> results;
    private final Map<String, Result> resultsByKey;
    private final RunConfiguration runConfiguration;

    private JmhResultFile(
            List<Result> results,
            Map<String, Result> resultsByKey,
            RunConfiguration runConfiguration) {
        this.results = Collections.unmodifiableList(results);
        this.resultsByKey = Collections.unmodifiableMap(resultsByKey);
        this.runConfiguration = runConfiguration;
    }

    public static JmhResultFile read(Path path) throws IOException {
        RawResult[] rawResults;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            rawResults = new Gson().fromJson(reader, RawResult[].class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid JMH JSON: " + path, e);
        }
        if (rawResults == null) {
            throw new IllegalArgumentException("JMH result file must contain a JSON array");
        }

        List<Result> results = new ArrayList<>();
        Map<String, Result> byKey = new LinkedHashMap<>();
        RunConfiguration runConfiguration = null;
        for (int i = 0; i < rawResults.length; i++) {
            if (rawResults[i] == null) {
                throw new IllegalArgumentException("JMH result at index " + i + " is null");
            }
            Result result = new Result(rawResults[i], i);
            if (runConfiguration == null) {
                runConfiguration = result.getRunConfiguration();
            } else if (!runConfiguration.equals(result.getRunConfiguration())) {
                throw new IllegalArgumentException(
                        "JMH results contain inconsistent run configurations at index " + i);
            }
            Result previous = byKey.put(result.key(), result);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate JMH result key: " + result.key());
            }
            results.add(result);
        }
        return new JmhResultFile(results, byKey, runConfiguration);
    }

    public List<Result> getResults() {
        return results;
    }

    public Map<String, Result> byKey() {
        return resultsByKey;
    }

    public RunConfiguration getRunConfiguration() {
        return runConfiguration;
    }

    static String key(String benchmark, String mode, String scoreUnit, Map<String, String> params) {
        return benchmark + '|' + mode + '|' + scoreUnit + '|' + new TreeMap<>(params);
    }

    private static String requireText(String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " is missing");
        }
        return value;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int requireNonNegative(Integer value, String description) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(description + " must be a non-negative integer");
        }
        return value;
    }

    private static int requirePositive(Integer value, String description) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(description + " must be a positive integer");
        }
        return value;
    }

    public static final class Result {
        private final String benchmark;
        private final String mode;
        private final RunConfiguration runConfiguration;
        private final Map<String, String> params;
        private final Metric primaryMetric;
        private final Map<String, Metric> secondaryMetrics;

        private Result(RawResult raw, int index) {
            String prefix = "JMH result at index " + index;
            benchmark = requireText(raw.benchmark, prefix + " benchmark");
            mode = requireText(raw.mode, prefix + " mode");
            runConfiguration = new RunConfiguration(raw, prefix);
            TreeMap<String, String> sortedParams = new TreeMap<>();
            if (raw.params != null) {
                for (Map.Entry<String, String> entry : raw.params.entrySet()) {
                    requireText(entry.getKey(), prefix + " parameter name");
                    if (entry.getValue() == null) {
                        throw new IllegalArgumentException(prefix + " parameter value is null: " + entry.getKey());
                    }
                    sortedParams.put(entry.getKey(), entry.getValue());
                }
            }
            params = Collections.unmodifiableMap(sortedParams);
            primaryMetric = new Metric(raw.primaryMetric, prefix + " primary metric");
            if (raw.secondaryMetrics == null) {
                throw new IllegalArgumentException(prefix + " secondary metrics are missing");
            }
            Map<String, Metric> metrics = new LinkedHashMap<>();
            for (Map.Entry<String, RawMetric> entry : raw.secondaryMetrics.entrySet()) {
                String name = requireText(entry.getKey(), prefix + " secondary metric name");
                metrics.put(name, new Metric(entry.getValue(), prefix + " secondary metric " + name));
            }
            secondaryMetrics = Collections.unmodifiableMap(metrics);
        }

        public String getBenchmark() {
            return benchmark;
        }

        public String getMode() {
            return mode;
        }

        public RunConfiguration getRunConfiguration() {
            return runConfiguration;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public Metric getPrimaryMetric() {
            return primaryMetric;
        }

        public Metric secondaryMetric(String name) {
            return secondaryMetrics.get(name);
        }

        public String key() {
            return JmhResultFile.key(benchmark, mode, primaryMetric.getScoreUnit(), params);
        }
    }

    public static final class RunConfiguration {
        private final String jmhVersion;
        private final int threads;
        private final int forks;
        private final String jvm;
        private final List<String> jvmArgs;
        private final String jdkVersion;
        private final String vmName;
        private final String vmVersion;
        private final int warmupIterations;
        private final String warmupTime;
        private final int warmupBatchSize;
        private final int measurementIterations;
        private final String measurementTime;
        private final int measurementBatchSize;

        private RunConfiguration(RawResult raw, String prefix) {
            jmhVersion = requireText(raw.jmhVersion, prefix + " JMH version");
            threads = requirePositive(raw.threads, prefix + " threads");
            forks = requirePositive(raw.forks, prefix + " forks");
            jvm = requireText(raw.jvm, prefix + " JVM executable");
            if (raw.jvmArgs == null) {
                throw new IllegalArgumentException(prefix + " JVM arguments are missing");
            }
            List<String> arguments = new ArrayList<>();
            for (int i = 0; i < raw.jvmArgs.size(); i++) {
                arguments.add(requireText(raw.jvmArgs.get(i), prefix + " JVM argument " + i));
            }
            jvmArgs = Collections.unmodifiableList(arguments);
            jdkVersion = requireText(raw.jdkVersion, prefix + " JDK version");
            vmName = requireText(raw.vmName, prefix + " VM name");
            vmVersion = requireText(raw.vmVersion, prefix + " VM version");
            warmupIterations = requireNonNegative(raw.warmupIterations, prefix + " warmup iterations");
            warmupTime = requireText(raw.warmupTime, prefix + " warmup time");
            warmupBatchSize = requirePositive(raw.warmupBatchSize, prefix + " warmup batch size");
            measurementIterations = requirePositive(
                    raw.measurementIterations, prefix + " measurement iterations");
            measurementTime = requireText(raw.measurementTime, prefix + " measurement time");
            measurementBatchSize = requirePositive(
                    raw.measurementBatchSize, prefix + " measurement batch size");
        }

        public List<String> compatibilityProblems(RunConfiguration other) {
            List<String> problems = new ArrayList<>();
            if (other == null) {
                problems.add("one result file has no run configuration");
                return problems;
            }
            compare("jmhVersion", jmhVersion, other.jmhVersion, problems);
            compare("threads", threads, other.threads, problems);
            compare("forks", forks, other.forks, problems);
            compare("jvm", jvm, other.jvm, problems);
            compare("jvmArgs", jvmArgs, other.jvmArgs, problems);
            compare("jdkVersion", jdkVersion, other.jdkVersion, problems);
            compare("vmName", vmName, other.vmName, problems);
            compare("vmVersion", vmVersion, other.vmVersion, problems);
            compare("warmupIterations", warmupIterations, other.warmupIterations, problems);
            compare("warmupTime", warmupTime, other.warmupTime, problems);
            compare("warmupBatchSize", warmupBatchSize, other.warmupBatchSize, problems);
            compare("measurementIterations", measurementIterations, other.measurementIterations, problems);
            compare("measurementTime", measurementTime, other.measurementTime, problems);
            compare("measurementBatchSize", measurementBatchSize, other.measurementBatchSize, problems);
            return problems;
        }

        public int getThreads() {
            return threads;
        }

        public int getForks() {
            return forks;
        }

        public List<String> getJvmArgs() {
            return jvmArgs;
        }

        public int getWarmupIterations() {
            return warmupIterations;
        }

        public String getWarmupTime() {
            return warmupTime;
        }

        public int getWarmupBatchSize() {
            return warmupBatchSize;
        }

        public int getMeasurementIterations() {
            return measurementIterations;
        }

        public String getMeasurementTime() {
            return measurementTime;
        }

        public int getMeasurementBatchSize() {
            return measurementBatchSize;
        }

        private static void compare(String field, Object left, Object right, List<String> problems) {
            if (!Objects.equals(left, right)) {
                problems.add(field + " mismatch: " + left + " != " + right);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RunConfiguration)) {
                return false;
            }
            RunConfiguration that = (RunConfiguration) other;
            return threads == that.threads
                    && forks == that.forks
                    && warmupIterations == that.warmupIterations
                    && warmupBatchSize == that.warmupBatchSize
                    && measurementIterations == that.measurementIterations
                    && measurementBatchSize == that.measurementBatchSize
                    && Objects.equals(jmhVersion, that.jmhVersion)
                    && Objects.equals(jvm, that.jvm)
                    && Objects.equals(jvmArgs, that.jvmArgs)
                    && Objects.equals(jdkVersion, that.jdkVersion)
                    && Objects.equals(vmName, that.vmName)
                    && Objects.equals(vmVersion, that.vmVersion)
                    && Objects.equals(warmupTime, that.warmupTime)
                    && Objects.equals(measurementTime, that.measurementTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    jmhVersion,
                    threads,
                    forks,
                    jvm,
                    jvmArgs,
                    jdkVersion,
                    vmName,
                    vmVersion,
                    warmupIterations,
                    warmupTime,
                    warmupBatchSize,
                    measurementIterations,
                    measurementTime,
                    measurementBatchSize);
        }
    }

    public static final class Metric {
        private final double score;
        private final double scoreError;
        private final double[] scoreConfidence;
        private final String scoreUnit;

        private Metric(RawMetric raw, String description) {
            if (raw == null) {
                throw new IllegalArgumentException(description + " is missing");
            }
            if (raw.score == null || !isFinite(raw.score)) {
                throw new IllegalArgumentException(description + " score must be finite");
            }
            if (raw.scoreError == null || !isFinite(raw.scoreError)) {
                throw new IllegalArgumentException(description + " score error must be finite");
            }
            if (raw.scoreConfidence == null || raw.scoreConfidence.length != 2) {
                throw new IllegalArgumentException(description + " must have a two-value confidence pair");
            }
            if (!isFinite(raw.scoreConfidence[0]) || !isFinite(raw.scoreConfidence[1])) {
                throw new IllegalArgumentException(description + " confidence values must be finite");
            }
            if (raw.scoreConfidence[0] > raw.scoreConfidence[1]) {
                throw new IllegalArgumentException(description + " confidence pair is reversed");
            }
            score = raw.score;
            scoreError = raw.scoreError;
            scoreConfidence = raw.scoreConfidence.clone();
            scoreUnit = requireText(raw.scoreUnit, description + " unit");
        }

        public double getScore() {
            return score;
        }

        public double lowerConfidence() {
            return scoreConfidence[0];
        }

        public double upperConfidence() {
            return scoreConfidence[1];
        }

        public String getScoreUnit() {
            return scoreUnit;
        }
    }

    private static final class RawResult {
        private String jmhVersion;
        private String benchmark;
        private String mode;
        private Integer threads;
        private Integer forks;
        private String jvm;
        private List<String> jvmArgs;
        private String jdkVersion;
        private String vmName;
        private String vmVersion;
        private Integer warmupIterations;
        private String warmupTime;
        private Integer warmupBatchSize;
        private Integer measurementIterations;
        private String measurementTime;
        private Integer measurementBatchSize;
        private Map<String, String> params;
        private RawMetric primaryMetric;
        private Map<String, RawMetric> secondaryMetrics;
    }

    private static final class RawMetric {
        private Double score;
        private Double scoreError;
        private double[] scoreConfidence;
        private String scoreUnit;
    }
}
