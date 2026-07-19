package org.mage.benchmark.comparison;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompareMainTest {

    private static final String BENCHMARK = "org.mage.benchmark.GameCopyBenchmark.copyGame";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void returnsZeroAndWritesCombinedReportWhenBothPairingsPass() throws Exception {
        Invocation invocation = invocation(90.0, 90.0, false);

        int exit = invocation.run();

        assertEquals(0, exit);
        assertTrue(Files.exists(invocation.report));
        String report = new String(Files.readAllBytes(invocation.report), StandardCharsets.UTF_8);
        assertTrue(report.contains("\"pairing\": \"AB\""));
        assertTrue(report.contains("\"pairing\": \"BA\""));
        assertTrue(report.contains("\"passed\": true"));
        assertTrue(invocation.stdout().contains("overall PASS"));
    }

    @Test
    public void returnsOneAndWritesBothPairingsWhenEitherFails() throws Exception {
        Invocation invocation = invocation(90.0, 96.0, false);

        int exit = invocation.run();

        assertEquals(1, exit);
        String report = new String(Files.readAllBytes(invocation.report), StandardCharsets.UTF_8);
        assertTrue(report.contains("\"pairing\": \"AB\""));
        assertTrue(report.contains("\"pairing\": \"BA\""));
        assertTrue(report.contains("minimum improvement gate failed"));
        assertTrue(invocation.stdout().contains("overall FAIL"));
    }

    @Test
    public void returnsTwoForMissingArgumentsUnreadableInputAndIncompatibleEnvironment() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        assertEquals(2, CompareMain.run(new String[0], new PrintStream(stdout), new PrintStream(stderr)));

        Invocation missing = invocation(90.0, 90.0, false);
        Files.delete(missing.abBaselineResults);
        assertEquals(2, missing.run());

        Invocation incompatible = invocation(90.0, 90.0, true);
        assertEquals(2, incompatible.run());
        assertFalse(Files.exists(incompatible.report));
        assertTrue(incompatible.stderr().contains("cpuModel"));
    }

    @Test
    public void neverOverwritesExistingReport() throws Exception {
        Invocation invocation = invocation(90.0, 90.0, false);
        Files.write(invocation.report, "sentinel".getBytes(StandardCharsets.UTF_8));

        int exit = invocation.run();

        assertEquals(2, exit);
        assertEquals("sentinel", new String(Files.readAllBytes(invocation.report), StandardCharsets.UTF_8));
    }

    private Invocation invocation(double abCandidateScore, double baCandidateScore, boolean incompatible)
            throws Exception {
        Path policy = write("policy.json", "{"
                + "\"minimumImprovementPercent\":5.0,"
                + "\"maximumAllocationRegressionPercent\":2.0,"
                + "\"allocationMetric\":\"gc.alloc.rate.norm\","
                + "\"rules\":[{\"benchmark\":\"" + BENCHMARK + "\","
                + "\"mode\":\"avgt\",\"scoreUnit\":\"us/op\",\"params\":{}}]}");
        Path report = temporaryFolder.getRoot().toPath().resolve("report.json");
        Path abBaselineResults = result("ab-baseline.json", 100.0, 98.0, 102.0);
        Path abCandidateResults = result(
                "ab-candidate.json", abCandidateScore, abCandidateScore - 2.0, abCandidateScore + 2.0);
        Path baBaselineResults = result("ba-baseline.json", 100.0, 98.0, 102.0);
        Path baCandidateResults = result(
                "ba-candidate.json", baCandidateScore, baCandidateScore - 2.0, baCandidateScore + 2.0);

        String baseManifest = EnvironmentManifestTest.manifest(
                "baseline", "aaa", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-01T00:00:00Z");
        String candidateManifest = EnvironmentManifestTest.manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64", "CPU", 8,
                "2026-01-01T00:01:00Z");
        String maybeIncompatible = EnvironmentManifestTest.manifest(
                "candidate", "bbb", false, "full", "Vendor", "17", "VM", "TestOS", "x86_64",
                incompatible ? "OtherCPU" : "CPU", 8, "2026-01-01T00:02:00Z");

        Path abBaselineManifest = write("ab-baseline-manifest.json", baseManifest);
        Path abCandidateManifest = write("ab-candidate-manifest.json", candidateManifest);
        Path baBaselineManifest = write("ba-baseline-manifest.json", baseManifest);
        Path baCandidateManifest = write("ba-candidate-manifest.json", maybeIncompatible);

        return new Invocation(
                policy,
                report,
                abBaselineResults,
                abBaselineManifest,
                abCandidateResults,
                abCandidateManifest,
                baBaselineResults,
                baBaselineManifest,
                baCandidateResults,
                baCandidateManifest);
    }

    private Path result(String name, double score, double lower, double upper) throws Exception {
        return JmhJsonFixture.write(
                temporaryFolder.getRoot().toPath().resolve(name),
                BENCHMARK,
                "avgt",
                "us/op",
                score,
                lower,
                upper,
                1000.0);
    }

    private Path write(String name, String value) throws Exception {
        Path path = temporaryFolder.getRoot().toPath().resolve(name);
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static final class Invocation {
        private final Path policy;
        private final Path report;
        private final Path abBaselineResults;
        private final Path abBaselineManifest;
        private final Path abCandidateResults;
        private final Path abCandidateManifest;
        private final Path baBaselineResults;
        private final Path baBaselineManifest;
        private final Path baCandidateResults;
        private final Path baCandidateManifest;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private Invocation(
                Path policy,
                Path report,
                Path abBaselineResults,
                Path abBaselineManifest,
                Path abCandidateResults,
                Path abCandidateManifest,
                Path baBaselineResults,
                Path baBaselineManifest,
                Path baCandidateResults,
                Path baCandidateManifest) {
            this.policy = policy;
            this.report = report;
            this.abBaselineResults = abBaselineResults;
            this.abBaselineManifest = abBaselineManifest;
            this.abCandidateResults = abCandidateResults;
            this.abCandidateManifest = abCandidateManifest;
            this.baBaselineResults = baBaselineResults;
            this.baBaselineManifest = baBaselineManifest;
            this.baCandidateResults = baCandidateResults;
            this.baCandidateManifest = baCandidateManifest;
        }

        private int run() {
            return CompareMain.run(new String[]{
                    policy.toString(), report.toString(),
                    abBaselineResults.toString(), abBaselineManifest.toString(),
                    abCandidateResults.toString(), abCandidateManifest.toString(),
                    baBaselineResults.toString(), baBaselineManifest.toString(),
                    baCandidateResults.toString(), baCandidateManifest.toString()
            }, new PrintStream(stdout), new PrintStream(stderr));
        }

        private String stdout() throws Exception {
            return stdout.toString("UTF-8");
        }

        private String stderr() throws Exception {
            return stderr.toString("UTF-8");
        }
    }
}
