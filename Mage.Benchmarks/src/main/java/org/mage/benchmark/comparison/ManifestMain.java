package org.mage.benchmark.comparison;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ManifestMain {
    private ManifestMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 5) {
            err.println("Usage: <output.json> <ref> <commit> <dirty:true|false> <run-config>");
            return 2;
        }
        if (!"true".equals(args[3]) && !"false".equals(args[3])) {
            err.println("dirty must be exactly true or false");
            return 2;
        }
        try {
            Path output = Paths.get(args[0]);
            EnvironmentManifest manifest = EnvironmentManifest.capture(
                    args[1], args[2], Boolean.parseBoolean(args[3]), args[4]);
            manifest.writeNew(output);
            out.println(output);
            return 0;
        } catch (Exception e) {
            err.println("Unable to write environment manifest: " + e.getMessage());
            return 2;
        }
    }
}
