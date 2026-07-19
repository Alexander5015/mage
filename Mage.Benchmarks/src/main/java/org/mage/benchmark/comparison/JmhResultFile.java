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

public final class JmhResultFile {
    private final List<Result> results;
    private final Map<String, Result> resultsByKey;

    private JmhResultFile(List<Result> results, Map<String, Result> resultsByKey) {
        this.results = Collections.unmodifiableList(results);
        this.resultsByKey = Collections.unmodifiableMap(resultsByKey);
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
        for (int i = 0; i < rawResults.length; i++) {
            if (rawResults[i] == null) {
                throw new IllegalArgumentException("JMH result at index " + i + " is null");
            }
            Result result = new Result(rawResults[i], i);
            Result previous = byKey.put(result.key(), result);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate JMH result key: " + result.key());
            }
            results.add(result);
        }
        return new JmhResultFile(results, byKey);
    }

    public List<Result> getResults() {
        return results;
    }

    public Map<String, Result> byKey() {
        return resultsByKey;
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

    public static final class Result {
        private final String benchmark;
        private final String mode;
        private final Map<String, String> params;
        private final Metric primaryMetric;
        private final Map<String, Metric> secondaryMetrics;

        private Result(RawResult raw, int index) {
            String prefix = "JMH result at index " + index;
            benchmark = requireText(raw.benchmark, prefix + " benchmark");
            mode = requireText(raw.mode, prefix + " mode");
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
        private String benchmark;
        private String mode;
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
