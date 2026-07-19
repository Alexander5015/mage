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
import java.util.TreeMap;

public final class BenchmarkPolicy {
    private final List<Rule> rules;
    private final Map<String, Rule> rulesByKey;

    private BenchmarkPolicy(List<Rule> rules, Map<String, Rule> rulesByKey) {
        this.rules = Collections.unmodifiableList(rules);
        this.rulesByKey = Collections.unmodifiableMap(rulesByKey);
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
        return new BenchmarkPolicy(rules, byKey);
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
        private RawRule[] rules;
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
