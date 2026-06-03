package com.cognition.jvmigration.laba05;

import com.cognition.jvmigration.laba05.Laba05Reset;

/**
 * Structured result from {@link Laba05Reset#run}. Java analog of the
 * {@code ResetOutcome} dataclass in
 * {@code migration/converted-code/python/laba05_reset.py}.
 */
public class ResetOutcome {
    public final int returnCode;
    public final Integer beforeJvNumber; // null when fetch failed
    public final Integer afterJvNumber;  // null when update failed
    public final String message;

    public ResetOutcome(int returnCode, Integer beforeJvNumber,
                        Integer afterJvNumber, String message) {
        this.returnCode = returnCode;
        this.beforeJvNumber = beforeJvNumber;
        this.afterJvNumber = afterJvNumber;
        this.message = message != null ? message : "";
    }

    public boolean ok() {
        return returnCode == Laba05Reset.RC_OK;
    }
}
