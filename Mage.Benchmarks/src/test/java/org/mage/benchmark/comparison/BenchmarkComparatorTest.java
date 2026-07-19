package org.mage.benchmark.comparison;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BenchmarkComparatorTest {

    private static final String BENCHMARK = "org.mage.benchmark.GameCopyBenchmark.copyGame";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void passesAllGates() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0),
                policy("avgt", "us/op"));

        assertTrue(report.isPassed());
        assertTrue(report.getErrors().isEmpty());
        assertEquals(10.0, report.getBenchmarks().get(0).getImprovementPercent(), 0.0);
        assertEquals(0.0, report.getBenchmarks().get(0).getAllocationRegressionPercent(), 0.0);
    }

    @Test
    public void failsMinimumImprovementGate() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 96.0, 94.0, 98.0, 1000.0),
                policy("avgt", "us/op"));

        assertEntryFailure(report, "minimum improvement");
    }

    @Test
    public void failsConfidenceGateWhenIntervalsOverlap() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 91.0, 99.0, 1000.0),
                policy("avgt", "us/op"));

        assertEntryFailure(report, "confidence");
    }

    @Test
    public void failsAllocationRegressionGate() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1030.0),
                policy("avgt", "us/op"));

        assertEntryFailure(report, "allocation regression");
    }

    @Test
    public void failsWhenCandidateResultIsMissing() throws Exception {
        Path emptyCandidate = JmhJsonFixture.write(path("candidate.json"));

        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                emptyCandidate,
                policy("avgt", "us/op"));

        assertGlobalFailure(report, "metadata set mismatch");
    }

    @Test
    public void failsWhenPrimaryUnitsDiffer() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "ns/op", 90000.0, 88000.0, 92000.0, 1000.0),
                policy("avgt", "us/op"));

        assertGlobalFailure(report, "metadata set mismatch");
    }

    @Test
    public void failsWhenProtectedAllocationMetricIsMissing() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, null),
                policy("avgt", "us/op"));

        assertEntryFailure(report, "missing protected metric");
    }

    @Test
    public void rejectsInvalidNumericInputBeforeComparison() throws Exception {
        Path invalid = result(
                "invalid.json", "avgt", "us/op", Double.POSITIVE_INFINITY, 98.0, 102.0, 1000.0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JmhResultFile.read(invalid));

        assertTrue(error.getMessage().contains("finite"));
    }

    @Test
    public void failsWhenAResultHasNoPolicyRule() throws Exception {
        JmhJsonFixture.Result tracked = JmhJsonFixture.result(
                BENCHMARK, "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0);
        JmhJsonFixture.Result extraBaseline = JmhJsonFixture.result(
                "example.UnlistedBenchmark.operation", "avgt", "us/op", 50.0, 48.0, 52.0, 100.0);
        JmhJsonFixture.Result trackedCandidate = JmhJsonFixture.result(
                BENCHMARK, "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0);
        JmhJsonFixture.Result extraCandidate = JmhJsonFixture.result(
                "example.UnlistedBenchmark.operation", "avgt", "us/op", 40.0, 38.0, 42.0, 100.0);

        ComparisonReport report = compare(
                JmhJsonFixture.write(path("baseline.json"), tracked, extraBaseline),
                JmhJsonFixture.write(path("candidate.json"), trackedCandidate, extraCandidate),
                policy("avgt", "us/op"));

        assertGlobalFailure(report, "uncovered benchmark");
    }

    @Test
    public void supportsThroughputModeWithReversedGates() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "thrpt", "ops/s", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "thrpt", "ops/s", 110.0, 108.0, 112.0, 1000.0),
                policy("thrpt", "ops/s"));

        assertTrue(report.isPassed());
        assertEquals(10.0, report.getBenchmarks().get(0).getImprovementPercent(), 0.0);
    }

    private ComparisonReport compare(Path baseline, Path candidate, Path policy) throws Exception {
        return BenchmarkComparator.compare(
                "test-pairing",
                JmhResultFile.read(baseline),
                JmhResultFile.read(candidate),
                BenchmarkPolicy.load(policy));
    }

    private Path result(
            String name,
            String mode,
            String unit,
            double score,
            double lowerConfidence,
            double upperConfidence,
            Double allocation) throws Exception {
        return JmhJsonFixture.write(
                path(name), BENCHMARK, mode, unit, score, lowerConfidence, upperConfidence, allocation);
    }

    private Path policy(String mode, String unit) throws Exception {
        String json = "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + "\"rules\":[{\"benchmark\":\"" + BENCHMARK + "\","
                + "\"mode\":\"" + mode + "\",\"scoreUnit\":\"" + unit + "\",\"params\":{}}]}";
        Path path = path("policy-" + mode + ".json");
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private Path path(String name) throws Exception {
        return temporaryFolder.newFile(name).toPath();
    }

    private static void assertEntryFailure(ComparisonReport report, String reason) {
        assertFalse(report.isPassed());
        assertTrue(report.getBenchmarks().get(0).getReasons().toString().contains(reason));
    }

    private static void assertGlobalFailure(ComparisonReport report, String reason) {
        assertFalse(report.isPassed());
        assertTrue(report.getErrors().toString().contains(reason));
    }
}
