package org.mage.benchmark.comparison;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class JmhResultFileTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void parsesStableKeyConfidenceAndAllocation() throws Exception {
        Path sample = new File(getClass().getResource("/comparison/sample-jmh.json").toURI()).toPath();

        JmhResultFile resultFile = JmhResultFile.read(sample);
        JmhResultFile.Result result = resultFile.getResults().get(0);

        assertEquals("org.mage.benchmark.GameCopyBenchmark.copyGame|avgt|us/op|{}", result.key());
        assertEquals(98.0, result.getPrimaryMetric().lowerConfidence(), 0.0);
        assertEquals(102.0, result.getPrimaryMetric().upperConfidence(), 0.0);
        assertEquals(1000.0, result.secondaryMetric("gc.alloc.rate.norm").getScore(), 0.0);
        assertEquals("B/op", result.secondaryMetric("gc.alloc.rate.norm").getScoreUnit());
        assertEquals(result, resultFile.byKey().get(result.key()));
    }

    @Test
    public void treatsJmhOmittedParamsAsEmpty() throws Exception {
        String result = validResult("example.Benchmark.operation", "100.0", "[98.0, 102.0]")
                .replace("\"params\":{},", "");
        Path path = write("no-params.json", "[" + result + "]");

        JmhResultFile.Result parsed = JmhResultFile.read(path).getResults().get(0);

        assertEquals("example.Benchmark.operation|avgt|us/op|{}", parsed.key());
        assertEquals(0, parsed.getParams().size());
    }

    @Test
    public void rejectsDuplicateResultKeys() throws Exception {
        String result = validResult("example.Benchmark.operation", "100.0", "[98.0, 102.0]");
        Path path = write("duplicates.json", "[" + result + "," + result + "]");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JmhResultFile.read(path));

        assertEquals(true, error.getMessage().contains("Duplicate JMH result key"));
    }

    @Test
    public void rejectsInconsistentRunConfigurationWithinFile() throws Exception {
        String first = validResult("example.Benchmark.first", "100.0", "[98.0, 102.0]");
        String second = validResult("example.Benchmark.second", "100.0", "[98.0, 102.0]")
                .replace("\"threads\":1", "\"threads\":2");
        Path path = write("configuration.json", "[" + first + "," + second + "]");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JmhResultFile.read(path));

        assertEquals(true, error.getMessage().contains("inconsistent run configurations"));
    }

    @Test
    public void rejectsMissingConfidencePair() throws Exception {
        Path path = write("confidence.json",
                "[" + validResult("example.Benchmark.operation", "100.0", "[98.0]") + "]");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JmhResultFile.read(path));

        assertEquals(true, error.getMessage().contains("confidence pair"));
    }

    @Test
    public void rejectsNonFiniteScore() throws Exception {
        Path path = write("non-finite.json",
                "[" + validResult("example.Benchmark.operation", "1e999", "[98.0, 102.0]") + "]");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> JmhResultFile.read(path));

        assertEquals(true, error.getMessage().contains("finite"));
    }

    private Path write(String name, String value) throws Exception {
        Path path = temporaryFolder.newFile(name).toPath();
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static String validResult(String benchmark, String score, String confidence) {
        return "{\"benchmark\":\"" + benchmark + "\","
                + "\"jmhVersion\":\"1.37\",\"mode\":\"avgt\","
                + "\"threads\":1,\"forks\":3,\"jvm\":\"/usr/bin/java\",\"jvmArgs\":[],"
                + "\"jdkVersion\":\"1.8.0\",\"vmName\":\"OpenJDK 64-Bit Server VM\","
                + "\"vmVersion\":\"25.0\",\"warmupIterations\":5,\"warmupTime\":\"1 s\","
                + "\"warmupBatchSize\":1,\"measurementIterations\":10,"
                + "\"measurementTime\":\"1 s\",\"measurementBatchSize\":1,\"params\":{},"
                + "\"primaryMetric\":{\"score\":" + score + ",\"scoreError\":2.0,"
                + "\"scoreConfidence\":" + confidence + ",\"scoreUnit\":\"us/op\"},"
                + "\"secondaryMetrics\":{}}";
    }
}
