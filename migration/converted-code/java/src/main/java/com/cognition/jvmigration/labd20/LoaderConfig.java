package com.cognition.jvmigration.labd20;

import java.nio.file.Path;

/**
 * Configuration for a {@link Labd20Loader} run. Java analog of the
 * {@code LoaderConfig} dataclass in
 * {@code migration/converted-code/python/labd20_loader.py}.
 */
public class LoaderConfig {
    public final Path cardPath;
    public final Path commentPath;
    public final boolean truncateAfterProcessing;
    public final String sectionForCount; // LABD20.pco:400

    public LoaderConfig(Path cardPath, Path commentPath,
                        boolean truncateAfterProcessing, String sectionForCount) {
        this.cardPath = cardPath;
        this.commentPath = commentPath;
        this.truncateAfterProcessing = truncateAfterProcessing;
        this.sectionForCount = sectionForCount;
    }

    public LoaderConfig(Path cardPath, Path commentPath) {
        this(cardPath, commentPath, true, "MA");
    }

    public LoaderConfig(Path cardPath, Path commentPath, boolean truncateAfterProcessing) {
        this(cardPath, commentPath, truncateAfterProcessing, "MA");
    }
}
