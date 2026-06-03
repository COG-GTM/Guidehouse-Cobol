package com.cognition.jvmigration.demo;

import com.cognition.jvmigration.db.DbDispatcher;
import com.cognition.jvmigration.db.DemoSchema;
import com.cognition.jvmigration.laba05.Laba05Reset;
import com.cognition.jvmigration.laba05.ResetOutcome;
import com.cognition.jvmigration.labd20.Labd20Loader;
import com.cognition.jvmigration.labd20.LoaderConfig;
import com.cognition.jvmigration.labd20.LoaderStats;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Runnable demo entrypoint for the modernized JV comment loader and FY-reset
 * utilities. Java analog of the {@code run} subcommand in
 * {@code migration/converted-code/python/demo_app.py}.
 *
 * <p>STATUS: Demo output pending SME review.
 *
 * <p>Zero-setup demo (bundled synthetic data + an in-memory H2 stand-in for
 * Oracle): {@code mvn -q exec:java} or, after packaging,
 * {@code java -cp ... com.cognition.jvmigration.demo.DemoApp run}.
 *
 * <p>The {@code serve} HTML dashboard from the Python reference is intentionally
 * NOT ported — Python remains canonical for the live dashboard (see
 * JAVA-MIGRATION-PLAN.md "Out of scope").
 */
public final class DemoApp {

    private static final int SEED_JV = 99;

    private DemoApp() {
    }

    public static void main(String[] args) {
        String cmd = (args.length == 0) ? "run" : args[0];
        if (!"run".equals(cmd)) {
            System.err.println("Usage: DemoApp run   (the Java port implements the 'run' subcommand only)");
            System.exit(2);
        }
        System.exit(cmdRun());
    }

    static int cmdRun() {
        Path testData = locateTestData();
        Path syntheticComments = testData.resolve("synthetic_comments.dat");
        Path syntheticCard = testData.resolve("synthetic_card.ctl");
        Path workDir = locateRepoRoot()
            .resolve("migration").resolve("test-results").resolve("demo-run-java");
        Path comments = copyComments(syntheticComments, workDir);

        try (DbDispatcher dispatcher = buildDispatcherWithSeed(SEED_JV)) {
            // Phase 1: FY reset (LABA05).
            ResetOutcome reset = Laba05Reset.run(dispatcher);

            // Phase 2: comment load (LABD20).
            Labd20Loader loader = new Labd20Loader(dispatcher);
            LoaderStats stats = loader.run(
                new LoaderConfig(syntheticCard, comments, true));

            printReport(reset, stats);
        }
        return 0;
    }

    private static DbDispatcher buildDispatcherWithSeed(int seedJv) {
        DbDispatcher dispatcher = DbDispatcher.inMemory();
        DemoSchema.buildDemoSchema(dispatcher);
        DemoSchema.seedControlRecord(dispatcher, seedJv);
        // Seed JC_COUNT_TBL row for section 'MA' so the post-process update path
        // has a baseline (per ASSUMPTIONS A-10).
        dispatcher.insert(
            "INSERT INTO JC_COUNT_TBL (JC_SECTION, JC_COUNT_NUM) VALUES (?, ?)",
            "MA", 0);
        dispatcher.commit();
        return dispatcher;
    }

    private static Path copyComments(Path synthetic, Path workDir) {
        try {
            Files.createDirectories(workDir);
            Path target = workDir.resolve("comments.dat");
            Files.copy(synthetic, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void printReport(ResetOutcome reset, LoaderStats stats) {
        String bar = repeat('=', 72);
        System.out.println(bar);
        System.out.println("Cognition x Guidehouse — JV COBOL modernization demo (Java)");
        System.out.println(bar);
        System.out.println("Process date           : " + stats.processDate);
        System.out.println();
        System.out.println("LABA05 fiscal-year reset");
        System.out.println("  return code          : " + reset.returnCode);
        System.out.println("  JV-NUMBER before     : " + reset.beforeJvNumber);
        System.out.println("  JV-NUMBER after      : " + reset.afterJvNumber);
        System.out.println("  message              : " + reset.message);
        System.out.println();
        System.out.println("LABD20 comment loader");
        System.out.println("  records read         : " + stats.totalRead);
        System.out.println("  inserted             : " + stats.inserted);
        System.out.println("  duplicates           : " + stats.duplicates);
        System.out.println("  rejected             : " + stats.rejected);
        System.out.println("  submitted total      : " + stats.submittedTotal);
        System.out.println();
        System.out.println("Rejection reasons (first 12):");
        List<String> reasons = stats.rejectedReasons;
        for (int i = 0; i < Math.min(12, reasons.size()); i++) {
            System.out.println("  - " + reasons.get(i));
        }
        System.out.println();
        System.out.println(stats.formatReport());
    }

    // ---- path resolution ---------------------------------------------------
    /** Walk upward from the working dir to find the repo root (contains migration/test-data). */
    static Path locateRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("migration").resolve("test-data"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "Could not locate repo root containing migration/test-data from "
                + Paths.get("").toAbsolutePath());
    }

    static Path locateTestData() {
        return locateRepoRoot().resolve("migration").resolve("test-data");
    }

    private static String repeat(char c, int n) {
        char[] arr = new char[n];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }
}
