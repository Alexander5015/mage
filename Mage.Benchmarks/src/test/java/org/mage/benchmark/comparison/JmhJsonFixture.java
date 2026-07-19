package org.mage.benchmark.comparison;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class JmhJsonFixture {
    private JmhJsonFixture() {
    }

    static Path write(
            Path path,
            String benchmark,
            String mode,
            String unit,
            double score,
            double lowerConfidence,
            double upperConfidence,
            Double allocation) throws IOException {
        return write(path, result(
                benchmark,
                mode,
                unit,
                score,
                lowerConfidence,
                upperConfidence,
                allocation));
    }

    static Path write(Path path, Result... results) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < results.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(results[i].json);
        }
        json.append(']');
        Files.write(path, json.toString().getBytes(StandardCharsets.UTF_8));
        return path;
    }

    static Result result(
            String benchmark,
            String mode,
            String unit,
            double score,
            double lowerConfidence,
            double upperConfidence,
            Double allocation) {
        String scoreValue = number(score);
        String lowerValue = number(lowerConfidence);
        String upperValue = number(upperConfidence);
        String secondaryMetrics = allocation == null
                ? "{}"
                : "{\"gc.alloc.rate.norm\":{\"score\":" + number(allocation)
                        + ",\"scoreError\":0.0,\"scoreConfidence\":[" + number(allocation)
                        + ',' + number(allocation) + "],\"scoreUnit\":\"B/op\"}}";
        return new Result("{"
                + "\"jmhVersion\":\"1.37\","
                + "\"benchmark\":\"" + benchmark + "\","
                + "\"mode\":\"" + mode + "\","
                + "\"threads\":1,\"forks\":3,\"jvm\":\"/usr/bin/java\","
                + "\"jvmArgs\":[\"-Dxmage.benchmark.fixture=/tmp/fixture.bin\"],"
                + "\"jdkVersion\":\"1.8.0\",\"vmName\":\"OpenJDK 64-Bit Server VM\","
                + "\"vmVersion\":\"25.0\",\"warmupIterations\":5,\"warmupTime\":\"1 s\","
                + "\"warmupBatchSize\":1,\"measurementIterations\":10,"
                + "\"measurementTime\":\"1 s\",\"measurementBatchSize\":1,\"params\":{},"
                + "\"primaryMetric\":{\"score\":" + scoreValue + ",\"scoreError\":2.0,"
                + "\"scoreConfidence\":[" + lowerValue + ',' + upperValue + "],"
                + "\"scorePercentiles\":{},\"scoreUnit\":\"" + unit + "\","
                + "\"rawData\":[[" + scoreValue + "]]},"
                + "\"secondaryMetrics\":" + secondaryMetrics
                + '}');
    }

    private static String number(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "Infinity";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-Infinity";
        }
        return Double.toString(value);
    }

    static final class Result {
        private final String json;

        private Result(String json) {
            this.json = json;
        }
    }
}
