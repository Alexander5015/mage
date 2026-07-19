package org.mage.benchmark.comparison;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComparisonReport {
    private final String pairing;
    private final boolean passed;
    private final List<String> errors;
    private final List<BenchmarkResult> benchmarks;

    ComparisonReport(String pairing, List<String> errors, List<BenchmarkResult> benchmarks) {
        this.pairing = pairing;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.benchmarks = Collections.unmodifiableList(new ArrayList<>(benchmarks));
        boolean entriesPassed = true;
        for (BenchmarkResult benchmark : benchmarks) {
            entriesPassed &= benchmark.isPassed();
        }
        this.passed = errors.isEmpty() && entriesPassed;
    }

    public String getPairing() {
        return pairing;
    }

    public boolean isPassed() {
        return passed;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<BenchmarkResult> getBenchmarks() {
        return benchmarks;
    }

    public static final class BenchmarkResult {
        private final String key;
        private final double baselineScore;
        private final double baselineLowerConfidence;
        private final double baselineUpperConfidence;
        private final double candidateScore;
        private final double candidateLowerConfidence;
        private final double candidateUpperConfidence;
        private final Double improvementPercent;
        private final Double baselineAllocation;
        private final Double candidateAllocation;
        private final Double allocationRegressionPercent;
        private final double minimumImprovementPercent;
        private final double maximumTimeRegressionPercent;
        private final double maximumAllocationRegressionPercent;
        private final BenchmarkPolicy.Expectation expectation;
        private final boolean passed;
        private final List<String> reasons;

        BenchmarkResult(
                String key,
                JmhResultFile.Metric baseline,
                JmhResultFile.Metric candidate,
                Double improvementPercent,
                Double baselineAllocation,
                Double candidateAllocation,
                Double allocationRegressionPercent,
                BenchmarkPolicy.Rule rule,
                List<String> reasons) {
            this.key = key;
            this.baselineScore = baseline.getScore();
            this.baselineLowerConfidence = baseline.lowerConfidence();
            this.baselineUpperConfidence = baseline.upperConfidence();
            this.candidateScore = candidate.getScore();
            this.candidateLowerConfidence = candidate.lowerConfidence();
            this.candidateUpperConfidence = candidate.upperConfidence();
            this.improvementPercent = improvementPercent;
            this.baselineAllocation = baselineAllocation;
            this.candidateAllocation = candidateAllocation;
            this.allocationRegressionPercent = allocationRegressionPercent;
            this.minimumImprovementPercent = rule.getMinimumImprovementPercent();
            this.maximumTimeRegressionPercent = rule.getMaximumTimeRegressionPercent();
            this.maximumAllocationRegressionPercent = rule.getMaximumAllocationRegressionPercent();
            this.expectation = rule.getExpectation();
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.passed = reasons.isEmpty();
        }

        public String getKey() {
            return key;
        }

        public double getBaselineScore() {
            return baselineScore;
        }

        public double getBaselineLowerConfidence() {
            return baselineLowerConfidence;
        }

        public double getBaselineUpperConfidence() {
            return baselineUpperConfidence;
        }

        public double getCandidateScore() {
            return candidateScore;
        }

        public double getCandidateLowerConfidence() {
            return candidateLowerConfidence;
        }

        public double getCandidateUpperConfidence() {
            return candidateUpperConfidence;
        }

        public Double getImprovementPercent() {
            return improvementPercent;
        }

        public Double getBaselineAllocation() {
            return baselineAllocation;
        }

        public Double getCandidateAllocation() {
            return candidateAllocation;
        }

        public Double getAllocationRegressionPercent() {
            return allocationRegressionPercent;
        }

        public double getMinimumImprovementPercent() {
            return minimumImprovementPercent;
        }

        public double getMaximumTimeRegressionPercent() {
            return maximumTimeRegressionPercent;
        }

        public double getMaximumAllocationRegressionPercent() {
            return maximumAllocationRegressionPercent;
        }

        public BenchmarkPolicy.Expectation getExpectation() {
            return expectation;
        }

        public boolean isPassed() {
            return passed;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }
}
