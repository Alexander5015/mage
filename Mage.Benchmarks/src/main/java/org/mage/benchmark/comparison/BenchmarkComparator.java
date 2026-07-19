package org.mage.benchmark.comparison;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class BenchmarkComparator {
    private BenchmarkComparator() {
    }

    public static ComparisonReport compare(
            String pairing,
            JmhResultFile baseline,
            JmhResultFile candidate,
            BenchmarkPolicy policy) {
        if (pairing == null || pairing.trim().isEmpty()) {
            throw new IllegalArgumentException("Comparison pairing is missing");
        }
        if (baseline == null || candidate == null || policy == null) {
            throw new IllegalArgumentException("Comparison inputs must not be null");
        }

        Set<String> errors = new LinkedHashSet<>();
        Map<String, JmhResultFile.Result> baselineByKey = baseline.byKey();
        Map<String, JmhResultFile.Result> candidateByKey = candidate.byKey();
        Set<String> baselineKeys = new TreeSet<>(baselineByKey.keySet());
        Set<String> candidateKeys = new TreeSet<>(candidateByKey.keySet());
        if (!baselineKeys.equals(candidateKeys)) {
            Set<String> missingFromCandidate = difference(baselineKeys, candidateKeys);
            Set<String> missingFromBaseline = difference(candidateKeys, baselineKeys);
            errors.add("metadata set mismatch: missing from candidate=" + missingFromCandidate
                    + ", missing from baseline=" + missingFromBaseline);
        }

        Set<String> policyKeys = new TreeSet<>();
        for (BenchmarkPolicy.Rule rule : policy.getRules()) {
            policyKeys.add(rule.key());
        }
        if (policyKeys.isEmpty()) {
            errors.add("benchmark policy contains no rules");
        }

        Set<String> allResultKeys = new TreeSet<>(baselineKeys);
        allResultKeys.addAll(candidateKeys);
        for (String key : allResultKeys) {
            if (!policyKeys.contains(key)) {
                errors.add("uncovered benchmark result: " + key);
            }
        }
        for (String key : policyKeys) {
            if (!baselineKeys.contains(key)) {
                errors.add("missing baseline result for policy rule: " + key);
            }
            if (!candidateKeys.contains(key)) {
                errors.add("missing candidate result for policy rule: " + key);
            }
        }

        List<ComparisonReport.BenchmarkResult> benchmarkReports = new ArrayList<>();
        Set<String> comparableKeys = new TreeSet<>(baselineKeys);
        comparableKeys.retainAll(candidateKeys);
        comparableKeys.retainAll(policyKeys);
        for (String key : comparableKeys) {
            JmhResultFile.Result baselineResult = baselineByKey.get(key);
            JmhResultFile.Result candidateResult = candidateByKey.get(key);
            BenchmarkPolicy.Rule rule = policy.ruleFor(baselineResult);
            benchmarkReports.add(compareResult(key, baselineResult, candidateResult, rule));
        }

        return new ComparisonReport(pairing, new ArrayList<>(errors), benchmarkReports);
    }

    private static ComparisonReport.BenchmarkResult compareResult(
            String key,
            JmhResultFile.Result baseline,
            JmhResultFile.Result candidate,
            BenchmarkPolicy.Rule rule) {
        List<String> reasons = new ArrayList<>();
        JmhResultFile.Metric baselineMetric = baseline.getPrimaryMetric();
        JmhResultFile.Metric candidateMetric = candidate.getPrimaryMetric();
        Double improvementPercent = null;

        if (!isPositiveFinite(baselineMetric.getScore()) || !isPositiveFinite(candidateMetric.getScore())) {
            reasons.add("invalid numeric data: primary scores must be positive finite values");
        } else if ("avgt".equals(baseline.getMode())) {
            improvementPercent = ((baselineMetric.getScore() - candidateMetric.getScore())
                    / baselineMetric.getScore()) * 100.0;
            if (improvementPercent < rule.getMinimumImprovementPercent()) {
                reasons.add("minimum improvement gate failed");
            }
            if (!(candidateMetric.upperConfidence() < baselineMetric.lowerConfidence())) {
                reasons.add("confidence gate failed: intervals overlap");
            }
        } else if ("thrpt".equals(baseline.getMode())) {
            improvementPercent = ((candidateMetric.getScore() - baselineMetric.getScore())
                    / baselineMetric.getScore()) * 100.0;
            if (improvementPercent < rule.getMinimumImprovementPercent()) {
                reasons.add("minimum improvement gate failed");
            }
            if (!(candidateMetric.lowerConfidence() > baselineMetric.upperConfidence())) {
                reasons.add("confidence gate failed: intervals overlap");
            }
        } else {
            reasons.add("unsupported benchmark mode: " + baseline.getMode());
        }

        JmhResultFile.Metric baselineAllocationMetric = baseline.secondaryMetric(rule.getAllocationMetric());
        JmhResultFile.Metric candidateAllocationMetric = candidate.secondaryMetric(rule.getAllocationMetric());
        Double baselineAllocation = baselineAllocationMetric == null ? null : baselineAllocationMetric.getScore();
        Double candidateAllocation = candidateAllocationMetric == null ? null : candidateAllocationMetric.getScore();
        Double allocationRegressionPercent = null;
        if (baselineAllocationMetric == null || candidateAllocationMetric == null) {
            reasons.add("missing protected metric: " + rule.getAllocationMetric());
        } else if (!baselineAllocationMetric.getScoreUnit().equals(candidateAllocationMetric.getScoreUnit())) {
            reasons.add("protected metric unit mismatch: " + baselineAllocationMetric.getScoreUnit()
                    + " != " + candidateAllocationMetric.getScoreUnit());
        } else if (!isNonNegativeFinite(baselineAllocation) || !isNonNegativeFinite(candidateAllocation)) {
            reasons.add("invalid numeric data: allocation scores must be non-negative finite values");
        } else {
            allocationRegressionPercent = baselineAllocation == 0.0
                    ? (candidateAllocation == 0.0 ? 0.0 : Double.POSITIVE_INFINITY)
                    : ((candidateAllocation - baselineAllocation) / baselineAllocation) * 100.0;
            if (allocationRegressionPercent > rule.getMaximumAllocationRegressionPercent()) {
                reasons.add("allocation regression gate failed");
            }
        }

        return new ComparisonReport.BenchmarkResult(
                key,
                baselineMetric,
                candidateMetric,
                improvementPercent,
                baselineAllocation,
                candidateAllocation,
                allocationRegressionPercent,
                rule,
                reasons);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static boolean isPositiveFinite(double value) {
        return value > 0.0 && !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isNonNegativeFinite(double value) {
        return value >= 0.0 && !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
