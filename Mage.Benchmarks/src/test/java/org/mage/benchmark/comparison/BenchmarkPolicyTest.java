package org.mage.benchmark.comparison;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BenchmarkPolicyTest {

    private static final String CLAIM_CONFIGURATION = "\"claimConfiguration\":{"
            + "\"threads\":1,\"minimumForks\":3,"
            + "\"minimumWarmupIterations\":5,\"minimumWarmupTime\":\"1 s\","
            + "\"warmupBatchSize\":1,\"minimumMeasurementIterations\":10,"
            + "\"minimumMeasurementTime\":\"1 s\",\"measurementBatchSize\":1,"
            + "\"requiredJvmArgumentPrefixes\":[\"-Dxmage.benchmark.fixture=\"]},";

    private static final String[] BENCHMARKS = {
            "org.mage.benchmark.GameCopyBenchmark.copyGame",
            "org.mage.benchmark.GameCopyBenchmark.copyGameState",
            "org.mage.benchmark.GameCopyBenchmark.copyBattlefield",
            "org.mage.benchmark.PayloadCompressionBenchmark.compressGameView",
            "org.mage.benchmark.PayloadCompressionBenchmark.decompressGameView",
            "org.mage.benchmark.PayloadCompressionBenchmark.compressControlPayload",
            "org.mage.benchmark.PayloadCompressionBenchmark.decompressControlPayload",
            "org.mage.benchmark.PayloadSerializationBenchmark.serializeGameView",
            "org.mage.benchmark.PayloadSerializationBenchmark.deserializeGameView",
            "org.mage.benchmark.PayloadSerializationBenchmark.serializeControlPayload",
            "org.mage.benchmark.PayloadSerializationBenchmark.deserializeControlPayload"
    };

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesEveryTrackedBenchmarkAndInheritedDefaults() throws Exception {
        BenchmarkPolicy policy = BenchmarkPolicy.load(
                Paths.get(System.getProperty("basedir"), "benchmark-policy.json"));
        JmhResultFile results = JmhResultFile.read(writeResults(BENCHMARKS));

        assertEquals(11, policy.getRules().size());
        int guardRules = 0;
        for (JmhResultFile.Result result : results.getResults()) {
            BenchmarkPolicy.Rule rule = policy.ruleFor(result);
            assertEquals(5.0, rule.getMinimumImprovementPercent(), 0.0);
            assertEquals(BenchmarkPolicy.Expectation.GUARD, rule.getExpectation());
            assertEquals(2.0, rule.getMaximumTimeRegressionPercent(), 0.0);
            assertEquals(2.0, rule.getMaximumAllocationRegressionPercent(), 0.0);
            assertEquals("gc.alloc.rate.norm", rule.getAllocationMetric());
            if (rule.getExpectation() == BenchmarkPolicy.Expectation.GUARD) {
                guardRules++;
            }
        }
        assertEquals(11, guardRules);
        assertEquals(0, policy.claimConfigurationProblems(results.getRunConfiguration()).size());
    }

    @Test
    public void rejectsUnsupportedExpectation() throws Exception {
        Path policyPath = write("invalid-expectation-policy.json", "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumTimeRegressionPercent\":2.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + CLAIM_CONFIGURATION
                + "\"rules\":[{\"benchmark\":\"example.Benchmark.operation\","
                + "\"mode\":\"avgt\",\"scoreUnit\":\"us/op\",\"params\":{},"
                + "\"expectation\":\"unknown\"}]}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkPolicy.load(policyPath));

        assertEquals(true, error.getMessage().contains("expectation"));
    }

    @Test
    public void appliesRuleMaximumTimeRegressionOverrideAndLegacyExpectationDefault() throws Exception {
        Path policyPath = write("time-regression-override-policy.json", "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumTimeRegressionPercent\":2.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + CLAIM_CONFIGURATION
                + "\"rules\":[{\"benchmark\":\"example.Benchmark.operation\","
                + "\"mode\":\"avgt\",\"scoreUnit\":\"us/op\",\"params\":{},"
                + "\"maximumTimeRegressionPercent\":1.0}]}");

        BenchmarkPolicy policy = BenchmarkPolicy.load(policyPath);
        BenchmarkPolicy.Rule rule = policy.getRules().get(0);

        assertEquals(BenchmarkPolicy.Expectation.IMPROVEMENT, rule.getExpectation());
        assertEquals(1.0, rule.getMaximumTimeRegressionPercent(), 0.0);
    }

    @Test
    public void rejectsUnlistedBenchmark() throws Exception {
        BenchmarkPolicy policy = BenchmarkPolicy.load(
                Paths.get(System.getProperty("basedir"), "benchmark-policy.json"));
        JmhResultFile.Result result = JmhResultFile.read(
                writeResults(new String[]{"example.UnlistedBenchmark.operation"}))
                .getResults().get(0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> policy.ruleFor(result));

        assertEquals(true, error.getMessage().contains("No policy rule"));
    }

    @Test
    public void rejectsDuplicateRules() throws Exception {
        String rule = "{\"benchmark\":\"example.Benchmark.operation\",\"mode\":\"avgt\","
                + "\"scoreUnit\":\"us/op\",\"params\":{}}";
        Path policyPath = write("duplicate-policy.json", "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumTimeRegressionPercent\":2.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + CLAIM_CONFIGURATION
                + "\"rules\":[" + rule + "," + rule + "]}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkPolicy.load(policyPath));

        assertEquals(true, error.getMessage().contains("Duplicate policy rule"));
    }

    @Test
    public void rejectsNonPositiveDefaultThreshold() throws Exception {
        Path policyPath = write("invalid-policy.json", "{"
                + "\"minimumImprovementPercent\":0.0,"
                + "\"maximumTimeRegressionPercent\":2.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + "\"rules\":[]}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkPolicy.load(policyPath));

        assertEquals(true, error.getMessage().contains("positive finite"));
    }

    @Test
    public void rejectsNonPositiveMaximumTimeRegressionThreshold() throws Exception {
        Path policyPath = write("invalid-time-regression-policy.json", "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumTimeRegressionPercent\":0.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + CLAIM_CONFIGURATION
                + "\"rules\":[]}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkPolicy.load(policyPath));

        assertEquals(true, error.getMessage().contains("maximumTimeRegressionPercent"));
    }

    @Test
    public void rejectsMissingClaimConfiguration() throws Exception {
        Path policyPath = write("missing-claim-policy.json", "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumTimeRegressionPercent\":2.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + "\"rules\":[]}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BenchmarkPolicy.load(policyPath));

        assertEquals(true, error.getMessage().contains("claimConfiguration"));
    }

    private Path writeResults(String[] benchmarks) throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < benchmarks.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"benchmark\":\"").append(benchmarks[i]).append("\","
                    + "\"jmhVersion\":\"1.37\",\"mode\":\"avgt\","
                    + "\"threads\":1,\"forks\":3,\"jvm\":\"/usr/bin/java\","
                    + "\"jvmArgs\":[\"-Dxmage.benchmark.fixture=/tmp/fixture.bin\"],"
                    + "\"jdkVersion\":\"1.8.0\",\"vmName\":\"OpenJDK 64-Bit Server VM\","
                    + "\"vmVersion\":\"25.0\",\"warmupIterations\":5,\"warmupTime\":\"1 s\","
                    + "\"warmupBatchSize\":1,\"measurementIterations\":10,"
                    + "\"measurementTime\":\"1 s\",\"measurementBatchSize\":1,\"params\":{},"
                    + "\"primaryMetric\":{\"score\":100.0,\"scoreError\":2.0,"
                    + "\"scoreConfidence\":[98.0,102.0],\"scoreUnit\":\"us/op\"},"
                    + "\"secondaryMetrics\":{}}");
        }
        return write("results-" + System.nanoTime() + ".json", json.append(']').toString());
    }

    private Path write(String name, String value) throws Exception {
        Path path = temporaryFolder.newFile(name).toPath();
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        return path;
    }
}
