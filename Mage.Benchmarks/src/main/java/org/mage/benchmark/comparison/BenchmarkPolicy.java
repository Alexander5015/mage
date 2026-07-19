package org.mage.benchmark.comparison;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BenchmarkPolicy {
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*(ns|us|µs|ms|s|min|hr)$");

    private final List<Rule> rules;
    private final Map<String, Rule> rulesByKey;
    private final ClaimConfiguration claimConfiguration;

    private BenchmarkPolicy(
            List<Rule> rules,
            Map<String, Rule> rulesByKey,
            ClaimConfiguration claimConfiguration) {
        this.rules = Collections.unmodifiableList(rules);
        this.rulesByKey = Collections.unmodifiableMap(rulesByKey);
        this.claimConfiguration = claimConfiguration;
    }

    public static BenchmarkPolicy load(Path path) throws IOException {
        RawPolicy raw;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            raw = new Gson().fromJson(reader, RawPolicy.class);
        } catch (JsonParseException e) {
            throw new IOException("Invalid benchmark policy JSON: " + path, e);
        }
        if (raw == null) {
            throw new IllegalArgumentException("Benchmark policy must contain a JSON object");
        }
        double defaultImprovement = requirePositiveFinite(
                raw.minimumImprovementPercent, "minimumImprovementPercent");
        double defaultAllocation = requirePositiveFinite(
                raw.maximumAllocationRegressionPercent, "maximumAllocationRegressionPercent");
        String defaultAllocationMetric = requireText(raw.allocationMetric, "allocationMetric");
        ClaimConfiguration claimConfiguration = new ClaimConfiguration(raw.claimConfiguration);
        if (raw.rules == null) {
            throw new IllegalArgumentException("Benchmark policy rules are missing");
        }

        List<Rule> rules = new ArrayList<>();
        Map<String, Rule> byKey = new LinkedHashMap<>();
        for (int i = 0; i < raw.rules.length; i++) {
            if (raw.rules[i] == null) {
                throw new IllegalArgumentException("Benchmark policy rule at index " + i + " is null");
            }
            Rule rule = new Rule(
                    raw.rules[i],
                    i,
                    defaultImprovement,
                    defaultAllocation,
                    defaultAllocationMetric);
            Rule previous = byKey.put(rule.key(), rule);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate policy rule: " + rule.key());
            }
            rules.add(rule);
        }
        return new BenchmarkPolicy(rules, byKey, claimConfiguration);
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Rule ruleFor(JmhResultFile.Result result) {
        Rule rule = rulesByKey.get(result.key());
        if (rule == null) {
            throw new IllegalArgumentException("No policy rule for JMH result: " + result.key());
        }
        return rule;
    }

    public List<String> claimConfigurationProblems(JmhResultFile.RunConfiguration actual) {
        return claimConfiguration.compatibilityProblems(actual);
    }

    private static double requirePositiveFinite(Double value, String description) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(description + " must be positive finite");
        }
        return value;
    }

    private static String requireText(String value, String description) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " is missing");
        }
        return value;
    }

    private static int requirePositive(Integer value, String description) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(description + " must be a positive integer");
        }
        return value;
    }

    private static BigDecimal parseDurationNanos(String value, String description) {
        String text = requireText(value, description).trim();
        Matcher matcher = DURATION_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(description + " has unsupported duration: " + text);
        }
        BigDecimal multiplier;
        switch (matcher.group(2)) {
            case "ns":
                multiplier = BigDecimal.ONE;
                break;
            case "us":
            case "µs":
                multiplier = new BigDecimal("1000");
                break;
            case "ms":
                multiplier = new BigDecimal("1000000");
                break;
            case "s":
                multiplier = new BigDecimal("1000000000");
                break;
            case "min":
                multiplier = new BigDecimal("60000000000");
                break;
            case "hr":
                multiplier = new BigDecimal("3600000000000");
                break;
            default:
                throw new IllegalArgumentException(description + " has unsupported duration: " + text);
        }
        return new BigDecimal(matcher.group(1)).multiply(multiplier);
    }

    private static final class ClaimConfiguration {
        private final int threads;
        private final int minimumForks;
        private final int minimumWarmupIterations;
        private final BigDecimal minimumWarmupTimeNanos;
        private final int warmupBatchSize;
        private final int minimumMeasurementIterations;
        private final BigDecimal minimumMeasurementTimeNanos;
        private final int measurementBatchSize;
        private final List<String> requiredJvmArgumentPrefixes;

        private ClaimConfiguration(RawClaimConfiguration raw) {
            if (raw == null) {
                throw new IllegalArgumentException("claimConfiguration is missing");
            }
            threads = requirePositive(raw.threads, "claimConfiguration threads");
            minimumForks = requirePositive(raw.minimumForks, "claimConfiguration minimumForks");
            minimumWarmupIterations = requirePositive(
                    raw.minimumWarmupIterations, "claimConfiguration minimumWarmupIterations");
            minimumWarmupTimeNanos = requirePositiveDuration(
                    raw.minimumWarmupTime, "claimConfiguration minimumWarmupTime");
            warmupBatchSize = requirePositive(
                    raw.warmupBatchSize, "claimConfiguration warmupBatchSize");
            minimumMeasurementIterations = requirePositive(
                    raw.minimumMeasurementIterations, "claimConfiguration minimumMeasurementIterations");
            minimumMeasurementTimeNanos = requirePositiveDuration(
                    raw.minimumMeasurementTime, "claimConfiguration minimumMeasurementTime");
            measurementBatchSize = requirePositive(
                    raw.measurementBatchSize, "claimConfiguration measurementBatchSize");
            if (raw.requiredJvmArgumentPrefixes == null || raw.requiredJvmArgumentPrefixes.isEmpty()) {
                throw new IllegalArgumentException(
                        "claimConfiguration requiredJvmArgumentPrefixes are missing");
            }
            List<String> prefixes = new ArrayList<>();
            for (int i = 0; i < raw.requiredJvmArgumentPrefixes.size(); i++) {
                prefixes.add(requireText(
                        raw.requiredJvmArgumentPrefixes.get(i),
                        "claimConfiguration requiredJvmArgumentPrefixes[" + i + "]"));
            }
            requiredJvmArgumentPrefixes = Collections.unmodifiableList(prefixes);
        }

        private static BigDecimal requirePositiveDuration(String value, String description) {
            BigDecimal duration = parseDurationNanos(value, description);
            if (duration.signum() <= 0) {
                throw new IllegalArgumentException(description + " must be positive");
            }
            return duration;
        }

        private List<String> compatibilityProblems(JmhResultFile.RunConfiguration actual) {
            List<String> problems = new ArrayList<>();
            if (actual == null) {
                problems.add("result file contains no benchmarks");
                return problems;
            }
            if (actual.getThreads() != threads) {
                problems.add("threads must equal " + threads + ": " + actual.getThreads());
            }
            if (actual.getForks() < minimumForks) {
                problems.add("forks must be at least " + minimumForks + ": " + actual.getForks());
            }
            if (actual.getWarmupIterations() < minimumWarmupIterations) {
                problems.add("warmup iterations must be at least " + minimumWarmupIterations
                        + ": " + actual.getWarmupIterations());
            }
            requireMinimumDuration(
                    actual.getWarmupTime(),
                    minimumWarmupTimeNanos,
                    "warmup time",
                    problems);
            if (actual.getWarmupBatchSize() != warmupBatchSize) {
                problems.add("warmup batch size must equal " + warmupBatchSize
                        + ": " + actual.getWarmupBatchSize());
            }
            if (actual.getMeasurementIterations() < minimumMeasurementIterations) {
                problems.add("measurement iterations must be at least " + minimumMeasurementIterations
                        + ": " + actual.getMeasurementIterations());
            }
            requireMinimumDuration(
                    actual.getMeasurementTime(),
                    minimumMeasurementTimeNanos,
                    "measurement time",
                    problems);
            if (actual.getMeasurementBatchSize() != measurementBatchSize) {
                problems.add("measurement batch size must equal " + measurementBatchSize
                        + ": " + actual.getMeasurementBatchSize());
            }
            for (String prefix : requiredJvmArgumentPrefixes) {
                boolean found = false;
                for (String argument : actual.getJvmArgs()) {
                    if (argument.startsWith(prefix)
                            && !argument.substring(prefix.length()).trim().isEmpty()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    problems.add("missing required JVM argument prefix: " + prefix);
                }
            }
            return problems;
        }

        private static void requireMinimumDuration(
                String actual,
                BigDecimal minimumNanos,
                String description,
                List<String> problems) {
            try {
                if (parseDurationNanos(actual, "JMH " + description).compareTo(minimumNanos) < 0) {
                    problems.add(description + " is below the policy minimum: " + actual);
                }
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
    }

    public static final class Rule {
        private final String benchmark;
        private final String mode;
        private final String scoreUnit;
        private final Map<String, String> params;
        private final double minimumImprovementPercent;
        private final double maximumAllocationRegressionPercent;
        private final String allocationMetric;

        private Rule(
                RawRule raw,
                int index,
                double defaultImprovement,
                double defaultAllocation,
                String defaultAllocationMetric) {
            String prefix = "Benchmark policy rule at index " + index;
            benchmark = requireText(raw.benchmark, prefix + " benchmark");
            mode = requireText(raw.mode, prefix + " mode");
            scoreUnit = requireText(raw.scoreUnit, prefix + " scoreUnit");
            if (raw.params == null) {
                throw new IllegalArgumentException(prefix + " params are missing");
            }
            TreeMap<String, String> sortedParams = new TreeMap<>();
            for (Map.Entry<String, String> entry : raw.params.entrySet()) {
                requireText(entry.getKey(), prefix + " parameter name");
                if (entry.getValue() == null) {
                    throw new IllegalArgumentException(prefix + " parameter value is null: " + entry.getKey());
                }
                sortedParams.put(entry.getKey(), entry.getValue());
            }
            params = Collections.unmodifiableMap(sortedParams);
            minimumImprovementPercent = raw.minimumImprovementPercent == null
                    ? defaultImprovement
                    : requirePositiveFinite(raw.minimumImprovementPercent, prefix + " minimumImprovementPercent");
            maximumAllocationRegressionPercent = raw.maximumAllocationRegressionPercent == null
                    ? defaultAllocation
                    : requirePositiveFinite(
                            raw.maximumAllocationRegressionPercent,
                            prefix + " maximumAllocationRegressionPercent");
            allocationMetric = raw.allocationMetric == null
                    ? defaultAllocationMetric
                    : requireText(raw.allocationMetric, prefix + " allocationMetric");
        }

        public String key() {
            return JmhResultFile.key(benchmark, mode, scoreUnit, params);
        }

        public double getMinimumImprovementPercent() {
            return minimumImprovementPercent;
        }

        public double getMaximumAllocationRegressionPercent() {
            return maximumAllocationRegressionPercent;
        }

        public String getAllocationMetric() {
            return allocationMetric;
        }
    }

    private static final class RawPolicy {
        private Double minimumImprovementPercent;
        private Double maximumAllocationRegressionPercent;
        private String allocationMetric;
        private RawClaimConfiguration claimConfiguration;
        private RawRule[] rules;
    }

    private static final class RawClaimConfiguration {
        private Integer threads;
        private Integer minimumForks;
        private Integer minimumWarmupIterations;
        private String minimumWarmupTime;
        private Integer warmupBatchSize;
        private Integer minimumMeasurementIterations;
        private String minimumMeasurementTime;
        private Integer measurementBatchSize;
        private List<String> requiredJvmArgumentPrefixes;
    }

    private static final class RawRule {
        private String benchmark;
        private String mode;
        private String scoreUnit;
        private Map<String, String> params;
        private Double minimumImprovementPercent;
        private Double maximumAllocationRegressionPercent;
        private String allocationMetric;
    }
}
