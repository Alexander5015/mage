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
    private static final String CLAIM_CONFIGURATION = "\"claimConfiguration\":{"
            + "\"threads\":1,\"minimumForks\":3,"
            + "\"minimumWarmupIterations\":5,\"minimumWarmupTime\":\"1 s\","
            + "\"warmupBatchSize\":1,\"minimumMeasurementIterations\":10,"
            + "\"minimumMeasurementTime\":\"1 s\",\"measurementBatchSize\":1,"
            + "\"requiredJvmArgumentPrefixes\":[\"-Dxmage.benchmark.fixture=\"]},";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void passesAllGates() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0),
                policy("avgt", "us/op", "improvement"));

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
                policy("avgt", "us/op", "improvement"));

        assertEntryFailure(report, "minimum improvement");
    }

    @Test
    public void failsConfidenceGateWhenIntervalsOverlap() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 91.0, 99.0, 1000.0),
                policy("avgt", "us/op", "improvement"));

        assertEntryFailure(report, "confidence");
    }

    @Test
    public void failsAllocationRegressionGate() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1030.0),
                policy("avgt", "us/op", "improvement"));

        assertEntryFailure(report, "allocation regression");
    }

    @Test
    public void failsFiniteJsonAllocationGateWhenBaselineIsZero() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 0.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1.0),
                policy("avgt", "us/op", "improvement"));

        assertEntryFailure(report, "baseline is zero");
        assertEquals(null, report.getBenchmarks().get(0).getAllocationRegressionPercent());
    }

    @Test
    public void failsWhenCandidateResultIsMissing() throws Exception {
        Path emptyCandidate = JmhJsonFixture.write(path("candidate.json"));

        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                emptyCandidate,
                policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "metadata set mismatch");
    }

    @Test
    public void failsWhenPrimaryUnitsDiffer() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "ns/op", 90000.0, 88000.0, 92000.0, 1000.0),
                policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "metadata set mismatch");
    }

    @Test
    public void failsWhenActualJmhConfigurationsDiffer() throws Exception {
        Path candidate = result(
                "candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0);
        String json = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8)
                .replace("\"measurementIterations\":10", "\"measurementIterations\":9");
        Files.write(candidate, json.getBytes(StandardCharsets.UTF_8));

        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                candidate,
                policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "measurementIterations mismatch");
    }

    @Test
    public void failsWhenMatchingRunsAreBelowClaimMinimums() throws Exception {
        Path baseline = result(
                "baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0);
        Path candidate = result(
                "candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0);
        replace(baseline, "\"forks\":3", "\"forks\":1");
        replace(candidate, "\"forks\":3", "\"forks\":1");

        ComparisonReport report = compare(baseline, candidate, policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "forks must be at least 3");
    }

    @Test
    public void acceptsMatchingRunsStrongerThanClaimMinimums() throws Exception {
        Path baseline = result(
                "baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0);
        Path candidate = result(
                "candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0);
        replace(baseline, "\"forks\":3", "\"forks\":4");
        replace(candidate, "\"forks\":3", "\"forks\":4");
        replace(baseline, "\"measurementTime\":\"1 s\"", "\"measurementTime\":\"1500 ms\"");
        replace(candidate, "\"measurementTime\":\"1 s\"", "\"measurementTime\":\"1500 ms\"");

        ComparisonReport report = compare(baseline, candidate, policy("avgt", "us/op", "improvement"));

        assertTrue(report.isPassed());
    }

    @Test
    public void failsWhenSharedFixtureArgumentIsMissing() throws Exception {
        Path baseline = result(
                "baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0);
        Path candidate = result(
                "candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0);
        String fixtureArgument = "[\"-Dxmage.benchmark.fixture=/tmp/fixture.bin\"]";
        replace(baseline, fixtureArgument, "[]");
        replace(candidate, fixtureArgument, "[]");

        ComparisonReport report = compare(baseline, candidate, policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "missing required JVM argument prefix");
    }

    @Test
    public void failsWhenSharedFixtureArgumentIsBlank() throws Exception {
        Path baseline = result(
                "baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0);
        Path candidate = result(
                "candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, 1000.0);
        String fixtureArgument = "-Dxmage.benchmark.fixture=/tmp/fixture.bin";
        replace(baseline, fixtureArgument, "-Dxmage.benchmark.fixture=   ");
        replace(candidate, fixtureArgument, "-Dxmage.benchmark.fixture=   ");

        ComparisonReport report = compare(baseline, candidate, policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "missing required JVM argument prefix");
    }

    @Test
    public void failsWhenProtectedAllocationMetricIsMissing() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 90.0, 88.0, 92.0, null),
                policy("avgt", "us/op", "improvement"));

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
                policy("avgt", "us/op", "improvement"));

        assertGlobalFailure(report, "uncovered benchmark");
    }

    @Test
    public void supportsThroughputModeWithReversedGates() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "thrpt", "ops/s", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "thrpt", "ops/s", 110.0, 108.0, 112.0, 1000.0),
                policy("thrpt", "ops/s", "improvement"));

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

    @Test
    public void passesGuardWhenAverageTimeIsUnchangedDespiteOverlappingConfidenceIntervals() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                policy("avgt", "us/op", "guard"));

        assertTrue(report.isPassed());
        assertEquals(BenchmarkPolicy.Expectation.GUARD, report.getBenchmarks().get(0).getExpectation());
        assertEquals(2.0, report.getBenchmarks().get(0).getMaximumTimeRegressionPercent(), 0.0);
    }

    @Test
    public void failsGuardWhenAverageTimeRegressionExceedsMaximum() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 102.1, 100.1, 104.1, 1000.0),
                policy("avgt", "us/op", "guard"));

        assertEntryFailure(report, "time regression gate failed");
    }

    @Test
    public void passesGuardWhenAverageTimeImproves() throws Exception {
        ComparisonReport report = compare(
                result("baseline.json", "avgt", "us/op", 100.0, 98.0, 102.0, 1000.0),
                result("candidate.json", "avgt", "us/op", 97.0, 95.0, 99.0, 1000.0),
                policy("avgt", "us/op", "guard"));

        assertTrue(report.isPassed());
    }

    private Path policy(String mode, String unit, String expectation) throws Exception {
        String json = "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumTimeRegressionPercent\":2.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + CLAIM_CONFIGURATION
                + "\"rules\":[{\"benchmark\":\"" + BENCHMARK + "\","
                + "\"mode\":\"" + mode + "\",\"scoreUnit\":\"" + unit + "\",\"params\":{},"
                + "\"expectation\":\"" + expectation + "\"}]}";
        Path path = path("policy-" + mode + ".json");
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static void replace(Path path, String original, String replacement) throws Exception {
        String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace(original, replacement);
        Files.write(path, json.getBytes(StandardCharsets.UTF_8));
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
