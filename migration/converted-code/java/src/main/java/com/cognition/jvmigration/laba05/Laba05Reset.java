package com.cognition.jvmigration.laba05;

import com.cognition.jvmigration.db.DbDispatcher;
import com.cognition.jvmigration.db.DispatcherResult;

import java.util.logging.Logger;

/**
 * Modernized analog of {@code source/cobol/LABA05.cbl} (fiscal-year reset
 * utility). Java port of {@code migration/converted-code/python/laba05_reset.py}.
 *
 * <p>STATUS: Demo output pending SME review.
 *
 * <p>Legacy behavior (LABA05.cbl): CONNECT (69-85), FETCH-CTRL-REC (152-174)
 * keyed by CONTROL_RECORD_NAME='JV-CONTROL-REC' AND CONTROL_RECORD_NUMBER=1,
 * display prior JV-NUMBER, MOVE 1 TO JV-NUMBER, UPDATE (MODIFY-CTRL-REC,
 * 176-205), return 0 on success / 99 on any DBIO error.
 *
 * <p>ASSUMPTION A-2: the JV-NUMBER stretch lives at bytes 25-30 (0-based 24-30)
 * of the 400-byte CONTROL_RECORD_DATA. Production stores it USAGE BINARY; the
 * demo uses a 6-digit zoned-display form (RISKS-AND-GAPS.md Risk 2).
 */
public final class Laba05Reset {

    private static final Logger LOGGER = Logger.getLogger(Laba05Reset.class.getName());

    public static final String CONTROL_RECORD_NAME = "JV-CONTROL-REC";
    public static final int CONTROL_RECORD_NUMBER = 1;
    public static final int TARGET_JV_NUMBER = 1; // LABA05.cbl:184-189

    // Byte offsets inside CONTROL_RECORD_DATA (400 bytes), 0-based, end-exclusive.
    static final int JV_NUMBER_START = 24;
    static final int JV_NUMBER_END = 30; // 6 bytes
    static final int JV_CONTROL_DATA_LENGTH = 400;

    public static final int RC_OK = 0;
    public static final int RC_DB_ERROR = 99;

    private Laba05Reset() {
    }

    /**
     * Decode the JV-NUMBER stretch out of CONTROL_RECORD_DATA. Demo parses 6
     * ASCII digits (display form). PLACEHOLDER for production: replace with a
     * binary decode of the legacy COMP field (CONTROL-RECORD-TABLE-IO.pco:21-28).
     */
    static int extractJvNumber(String data) {
        String chunk = data.substring(JV_NUMBER_START, JV_NUMBER_END);
        return Integer.parseInt(chunk.trim());
    }

    /**
     * Write a new JV-NUMBER back into the 400-byte CONTROL_RECORD_DATA,
     * preserving all surrounding bytes (no padding drift).
     */
    static String replaceJvNumber(String data, int newValue) {
        if (data.length() != JV_CONTROL_DATA_LENGTH) {
            throw new IllegalArgumentException(
                "CONTROL_RECORD_DATA must be " + JV_CONTROL_DATA_LENGTH
                    + " bytes; got " + data.length());
        }
        String encoded = String.format("%06d", newValue);
        if (encoded.length() != 6) {
            throw new IllegalArgumentException("JV_NUMBER must fit in 6 zoned-display digits");
        }
        return data.substring(0, JV_NUMBER_START) + encoded + data.substring(JV_NUMBER_END);
    }

    /** Execute the LABA05 fiscal-year reset against a caller-supplied dispatcher. */
    public static ResetOutcome run(DbDispatcher dispatcher) {
        // Step 1: FETCH the control record (LABA05.cbl:152-174).
        DispatcherResult fetch = dispatcher.selectOne(
            "SELECT CONTROL_RECORD_DATA"
                + " FROM CONTROL_RECORD_TABLE"
                + " WHERE CONTROL_RECORD_NAME = ?"
                + " AND CONTROL_RECORD_NUMBER = ?",
            CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER);
        if (!fetch.ok() || fetch.rows.isEmpty()) {
            LOGGER.severe("LABA05: fetch failed dms=" + fetch.rtncodeDms + " msg=" + fetch.message);
            return new ResetOutcome(RC_DB_ERROR, null, null,
                "fetch failed: dms=" + fetch.rtncodeDms + " sqlcode=" + fetch.sqlcode);
        }

        String data = String.valueOf(fetch.rows.get(0)[0]);
        int before = extractJvNumber(data);
        LOGGER.info(String.format("LABA05: PRIOR JV NUMBER WAS %06d", before));

        // Step 2: MOVE 1 TO JV-NUMBER and UPDATE (LABA05.cbl:184-205).
        String newData = replaceJvNumber(data, TARGET_JV_NUMBER);
        DispatcherResult update = dispatcher.update(
            "UPDATE CONTROL_RECORD_TABLE"
                + " SET CONTROL_RECORD_DATA = ?"
                + " WHERE CONTROL_RECORD_NAME = ?"
                + " AND CONTROL_RECORD_NUMBER = ?",
            newData, CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER);
        if (!update.ok()) {
            dispatcher.rollback();
            LOGGER.severe("LABA05: update failed dms=" + update.rtncodeDms + " msg=" + update.message);
            return new ResetOutcome(RC_DB_ERROR, before, null,
                "update failed: dms=" + update.rtncodeDms + " sqlcode=" + update.sqlcode);
        }

        dispatcher.commit();
        int after = extractJvNumber(newData);
        LOGGER.info(String.format("LABA05: JV NUMBER IS NOW %06d", after));
        return new ResetOutcome(RC_OK, before, after, "JV-NUMBER reset to 1");
    }

    /** Convenience overload mirroring {@code run()} that builds a dispatcher from env. */
    public static ResetOutcome run() {
        try (DbDispatcher dispatcher = DbDispatcher.fromEnv()) {
            return run(dispatcher);
        }
    }
}
