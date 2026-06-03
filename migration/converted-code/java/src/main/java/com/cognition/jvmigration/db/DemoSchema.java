package com.cognition.jvmigration.db;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Demo/test schema helper — Java port of the {@code build_demo_schema} /
 * {@code seed_control_record} helpers in
 * {@code migration/converted-code/python/db_dispatcher.py}.
 *
 * <p>STATUS: Demo output pending SME review.
 *
 * <p>ASSUMPTION A-7: the table shapes below reflect
 * {@code database/descriptions/*.txt} (JC_SUBMITTED_COMMENT_TBL, JC_COUNT_TBL,
 * CONTROL_RECORD_TABLE). Oracle DDL types are simplified to H2-compatible types
 * for the demo runtime. The {@code JC_COUNT_TBL} primary-key uses JC_SECTION to
 * reproduce the legacy describe-file PK (see RISKS-AND-GAPS.md).
 */
public final class DemoSchema {

    private DemoSchema() {
    }

    public static final String[] DEMO_SCHEMA_DDL = {
        "CREATE TABLE IF NOT EXISTS JC_SUBMITTED_COMMENT_TBL ("
            + " JC_SUBMITTED                   VARCHAR(64) PRIMARY KEY,"
            + " JC_SUBMITTED_SCHED_DOC_NO      VARCHAR(64),"
            + " JC_SUBMITTED_COMMENT_HIST      VARCHAR(4000),"
            + " JC_SUBMITTED_COMMENT_REQUESTOR VARCHAR(64),"
            + " JC_SUBMITTED_COMMENT_APPROVER  VARCHAR(64),"
            + " JC_SUBMITTED_CONTROL_NUM       VARCHAR(64),"
            + " JC_SUBMITTED_UPDT_PROG_ID      VARCHAR(64),"
            + " JC_SUBMITTED_UPDT_PROG_DT      VARCHAR(32),"
            + " JC_SUBMITTED_NUMBER            INTEGER)",
        "CREATE TABLE IF NOT EXISTS JC_COUNT_TBL ("
            + " JC_SECTION   VARCHAR(8) PRIMARY KEY,"
            + " JC_COUNT_NUM INTEGER)",
        "CREATE TABLE IF NOT EXISTS JC_REJECTED_COMMENT_TBL ("
            + " JC_REJECTED        VARCHAR(64) PRIMARY KEY,"
            + " JC_REJECTED_REASON VARCHAR(4000))",
        "CREATE TABLE IF NOT EXISTS JC_APPLIED_COMMENT_TBL ("
            + " JC_APPLIED VARCHAR(64) PRIMARY KEY)",
        "CREATE TABLE IF NOT EXISTS CONTROL_RECORD_TABLE ("
            + " CONTROL_RECORD_NAME   VARCHAR(64),"
            + " CONTROL_RECORD_NUMBER INTEGER,"
            + " CONTROL_RECORD_DATA   VARCHAR(4000),"
            + " PRIMARY KEY (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER))"
    };

    /** Create the demo tables. Idempotent. */
    public static void buildDemoSchema(DbDispatcher dispatcher) {
        try (Statement st = dispatcher.connection().createStatement()) {
            for (String ddl : DEMO_SCHEMA_DDL) {
                st.execute(ddl);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to build demo schema: " + e.getMessage(), e);
        }
        dispatcher.commit();
    }

    /**
     * Insert a JV-CONTROL-REC row so LABA05 has something to reset. The 400-byte
     * CONTROL_RECORD_DATA layout reflects JV-CONTROL-REC.cpy (JV-NUMBER at bytes
     * 25-30, here a 6-digit display form per ASSUMPTION A-2 — production must
     * reproduce the legacy USAGE BINARY layout, RISKS-AND-GAPS.md Risk 2).
     */
    public static void seedControlRecord(DbDispatcher dispatcher) {
        seedControlRecord(dispatcher, 42);
    }

    public static void seedControlRecord(DbDispatcher dispatcher, int jvNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append("000001");                       // JV-CONTROL-1
        sb.append("000002");                       // JV-CONTROL-2
        sb.append("000003");                       // JV-CONTROL-3
        sb.append("000004");                       // JV-CONTROL-4
        sb.append(String.format("%06d", jvNumber)); // JV-NUMBER (display)
        sb.append(repeat(' ', 9));                  // filler
        sb.append("000005");                       // JV-CONTROL-5
        sb.append(repeat(' ', 355));               // remaining filler up to 400 bytes
        String data = sb.toString();
        if (data.length() != 400) {
            throw new IllegalStateException(
                "CONTROL_RECORD_DATA must be 400 bytes, got " + data.length());
        }
        dispatcher.insert(
            "INSERT INTO CONTROL_RECORD_TABLE"
                + " (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER, CONTROL_RECORD_DATA)"
                + " VALUES (?, ?, ?)",
            "JV-CONTROL-REC", 1, data);
        dispatcher.commit();
    }

    private static String repeat(char c, int n) {
        char[] arr = new char[n];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }
}
