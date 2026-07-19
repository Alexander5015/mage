package org.mage.benchmark.fixture;

import java.nio.file.Paths;

public final class FixtureMain {
    private FixtureMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: <fixture-output.bin>");
            System.exit(2);
        }
        DeterministicGameFixture.writeFresh(Paths.get(args[0]));
    }
}
