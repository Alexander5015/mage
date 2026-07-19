package org.mage.benchmark.comparison;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public final class CompareMain {
    private CompareMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 10) {
            err.println("Usage: <policy.json> <report.json> "
                    + "<ab-baseline-results.json> <ab-baseline-manifest.json> "
                    + "<ab-candidate-results.json> <ab-candidate-manifest.json> "
                    + "<ba-baseline-results.json> <ba-baseline-manifest.json> "
                    + "<ba-candidate-results.json> <ba-candidate-manifest.json>");
            return 2;
        }

        Path reportPath = Paths.get(args[1]);
        if (Files.exists(reportPath)) {
            err.println("Report path already exists: " + reportPath);
            return 2;
        }

        try {
            BenchmarkPolicy policy = BenchmarkPolicy.load(Paths.get(args[0]));
            JmhResultFile abBaselineResults = JmhResultFile.read(Paths.get(args[2]));
            EnvironmentManifest abBaselineManifest = EnvironmentManifest.read(Paths.get(args[3]));
            JmhResultFile abCandidateResults = JmhResultFile.read(Paths.get(args[4]));
            EnvironmentManifest abCandidateManifest = EnvironmentManifest.read(Paths.get(args[5]));
            JmhResultFile baBaselineResults = JmhResultFile.read(Paths.get(args[6]));
            EnvironmentManifest baBaselineManifest = EnvironmentManifest.read(Paths.get(args[7]));
            JmhResultFile baCandidateResults = JmhResultFile.read(Paths.get(args[8]));
            EnvironmentManifest baCandidateManifest = EnvironmentManifest.read(Paths.get(args[9]));

            boolean environmentsCompatible = true;
            environmentsCompatible &= compatible("AB", abBaselineManifest, abCandidateManifest, err);
            environmentsCompatible &= compatible("BA", baBaselineManifest, baCandidateManifest, err);
            environmentsCompatible &= compatible("AB/BA baseline", abBaselineManifest, baBaselineManifest, err);
            environmentsCompatible &= compatible("AB/BA candidate", abCandidateManifest, baCandidateManifest, err);
            if (!environmentsCompatible) {
                return 2;
            }

            ComparisonReport ab = BenchmarkComparator.compare(
                    "AB", abBaselineResults, abCandidateResults, policy);
            ComparisonReport ba = BenchmarkComparator.compare(
                    "BA", baBaselineResults, baCandidateResults, policy);
            CombinedReport combined = new CombinedReport(ab, ba);
            writeNew(reportPath, combined);
            printReport(ab, out);
            printReport(ba, out);
            out.println("overall " + (combined.passed ? "PASS" : "FAIL"));
            return combined.passed ? 0 : 1;
        } catch (Exception e) {
            err.println("Unable to compare benchmark results: " + e.getMessage());
            return 2;
        }
    }

    private static boolean compatible(
            String description,
            EnvironmentManifest baseline,
            EnvironmentManifest candidate,
            PrintStream err) {
        List<String> problems = baseline.compatibilityProblems(candidate);
        for (String problem : problems) {
            err.println(description + " environment mismatch: " + problem);
        }
        return problems.isEmpty();
    }

    private static void writeNew(Path path, CombinedReport report) throws Exception {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        try (Writer writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            gson.toJson(report, writer);
        }
    }

    private static void printReport(ComparisonReport report, PrintStream out) {
        for (String error : report.getErrors()) {
            out.println(report.getPairing() + " ERROR " + error);
        }
        for (ComparisonReport.BenchmarkResult benchmark : report.getBenchmarks()) {
            out.println(report.getPairing()
                    + ' ' + benchmarkName(benchmark.getKey())
                    + " baseline=" + benchmark.getBaselineScore()
                    + " candidate=" + benchmark.getCandidateScore()
                    + " improvement=" + display(benchmark.getImprovementPercent()) + "%"
                    + " allocation=" + display(benchmark.getAllocationRegressionPercent()) + "%"
                    + ' ' + (benchmark.isPassed() ? "PASS" : "FAIL"));
        }
    }

    private static String benchmarkName(String key) {
        int separator = key.indexOf('|');
        return separator < 0 ? key : key.substring(0, separator);
    }

    private static String display(Double value) {
        return value == null ? "n/a" : value.toString();
    }

    private static final class CombinedReport {
        private final boolean passed;
        private final List<ComparisonReport> pairings;

        private CombinedReport(ComparisonReport ab, ComparisonReport ba) {
            this.passed = ab.isPassed() && ba.isPassed();
            this.pairings = Arrays.asList(ab, ba);
        }
    }
}
