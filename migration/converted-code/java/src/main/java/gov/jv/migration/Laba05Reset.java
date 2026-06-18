// STATUS: Demo output — pending SME review
package gov.jv.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modernized analog of source/cobol/LABA05.cbl (285 lines).
 * <p>
 * LABA05 is a fiscal-year reset utility. It resets JV-NUMBER on JV-CONTROL-REC
 * to 1 at the start of each fiscal year.
 * <p>
 * Source: source/cobol/LABA05.cbl:1-285
 * <p>
 * Flow (mirrors COBOL paragraphs):
 * <ol>
 *   <li>CONNECT to DB — LABA05.cbl:69-85 (handled externally; connection passed in)</li>
 *   <li>FETCH JV-CONTROL-REC — LABA05.cbl:150-174 (FETCH-CTRL-REC)</li>
 *   <li>DISPLAY before-state — LABA05.cbl:274-283 (9999-DISPLAY-REC)</li>
 *   <li>MOVE 1 TO JV-NUMBER — LABA05.cbl:179</li>
 *   <li>UPDATE the row — LABA05.cbl:176-205 (MODIFY-CTRL-REC)</li>
 *   <li>COMMIT on success / ROLLBACK on error — LABA05.cbl:120-140 / 207-228</li>
 *   <li>Return code: 0 = success, 99 = DB error</li>
 * </ol>
 * <p>
 * // ASSUMPTION A-6: Return codes (0/99) match LABA05's RETURN-CODE conventions.
 */
public class Laba05Reset {

    private static final Logger LOG = LoggerFactory.getLogger(Laba05Reset.class);

    /** Target value for JV-NUMBER. Source: LABA05.cbl:179 — MOVE 1 TO JV-NUMBER */
    private static final int TARGET_JV_NUMBER = 1;

    /** Control record name key. Source: LABA05.cbl:152 */
    private static final String CONTROL_RECORD_NAME = "JV-CONTROL-REC";

    /** Control record number key. Source: LABA05.cbl:152 */
    private static final int CONTROL_RECORD_NUMBER = 1;

    /** Success return code. Source: LABA05.cbl — default RETURN-CODE */
    public static final int RC_OK = 0;

    /** Error return code. Source: LABA05.cbl:83, 97, 199 — MOVE 99 TO RETURN-CODE */
    public static final int RC_DB_ERROR = 99;

    private Laba05Reset() {}

    /**
     * Structured result from the LABA05 reset operation.
     * Carries return code and before/after JV-NUMBER values for test verification.
     */
    public record ResetOutcome(
            int returnCode,
            Integer beforeJvNumber,
            Integer afterJvNumber,
            String message
    ) {
        public boolean ok() {
            return returnCode == RC_OK;
        }
    }

    /**
     * Executes the LABA05 fiscal-year reset.
     * <p>
     * Source: LABA05.cbl:87-105 (0000-MAIN-LINE) orchestrating FETCH → MODIFY → COMMIT/ROLLBACK.
     *
     * @param dispatcher the DbDispatcher providing DB access
     * @return ResetOutcome with returnCode matching LABA05 conventions (0 or 99)
     */
    public static ResetOutcome run(DbDispatcher dispatcher) {
        // Step 1: FETCH the control record
        // Source: LABA05.cbl:150-174 (FETCH-CTRL-REC paragraph)
        DispatcherResult fetch = fetchControlRecord(dispatcher);
        if (!fetch.ok() || fetch.rows() == null || fetch.rows().isEmpty()) {
            LOG.error("LABA05: fetch failed dms={} msg={}", fetch.rtncodesDms(), fetch.message());
            return new ResetOutcome(RC_DB_ERROR, null, null,
                    "fetch failed: dms=" + fetch.rtncodesDms() + " sqlcode=" + fetch.sqlcode());
        }

        // Step 2: Parse the 400-byte blob
        // Source: LABA05.cbl:168 — MOVE DB-DATA TO JV-CONTROL-REC
        String data = (String) fetch.rows().get(0)[0];
        JvControlRecord record = JvControlRecord.fromControlRecordData(data);
        int before = record.jvNumber();

        // Step 3: Display before-state
        // Source: LABA05.cbl:177-178 + 274-283 (9999-DISPLAY-REC)
        displayRecord("Before modification", record);

        // Step 4: MOVE 1 TO JV-NUMBER and UPDATE
        // Source: LABA05.cbl:179 — MOVE 1 TO JV-NUMBER
        JvControlRecord updated = record.withJvNumber(TARGET_JV_NUMBER);
        String newData = updated.toControlRecordData();

        // Step 5: UPDATE the row
        // Source: LABA05.cbl:176-205 (MODIFY-CTRL-REC paragraph)
        DispatcherResult updateResult = modifyControlRecord(dispatcher, newData);
        if (!updateResult.ok()) {
            // Source: LABA05.cbl:196-200 — error path, MOVE 99 TO RETURN-CODE
            dispatcher.rollback();  // Source: LABA05.cbl:207-228 (DML-ROLLBACK-PARA)
            LOG.error("LABA05: update failed dms={} msg={}", updateResult.rtncodesDms(), updateResult.message());
            return new ResetOutcome(RC_DB_ERROR, before, null,
                    "update failed: dms=" + updateResult.rtncodesDms() + " sqlcode=" + updateResult.sqlcode());
        }

        // Step 6: COMMIT
        // Source: LABA05.cbl:120-140 (CLOSE-DEPART-DB paragraph)
        dispatcher.commit();

        // Step 7: Display after-state
        // Source: LABA05.cbl:202-204
        int after = updated.jvNumber();
        displayRecord("After modification", updated);

        LOG.info("LABA05: JV-NUMBER reset from {} to {}", before, after);
        return new ResetOutcome(RC_OK, before, after, "JV-NUMBER reset to 1");
    }

    /**
     * Fetches JV-CONTROL-REC from CONTROL_RECORD_TABLE.
     * Source: LABA05.cbl:150-174 (FETCH-CTRL-REC paragraph)
     */
    private static DispatcherResult fetchControlRecord(DbDispatcher dispatcher) {
        return dispatcher.selectOne(
                """
                SELECT CONTROL_RECORD_DATA
                  FROM CONTROL_RECORD_TABLE
                 WHERE CONTROL_RECORD_NAME = ?
                   AND CONTROL_RECORD_NUMBER = ?
                """,
                CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER);
    }

    /**
     * Updates the CONTROL_RECORD_DATA blob for JV-CONTROL-REC.
     * Source: LABA05.cbl:176-205 (MODIFY-CTRL-REC paragraph)
     */
    private static DispatcherResult modifyControlRecord(DbDispatcher dispatcher, String newData) {
        return dispatcher.update(
                """
                UPDATE CONTROL_RECORD_TABLE
                   SET CONTROL_RECORD_DATA = ?
                 WHERE CONTROL_RECORD_NAME = ?
                   AND CONTROL_RECORD_NUMBER = ?
                """,
                newData, CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER);
    }

    /**
     * Logs the current state of all JV-CONTROL-REC fields.
     * Source: LABA05.cbl:274-283 (9999-DISPLAY-REC paragraph)
     */
    private static void displayRecord(String label, JvControlRecord rec) {
        LOG.info("LABA05: {}", label);
        LOG.info("  JV-CONTROL-1 = {}", String.format("%06d", rec.jvControl1()));
        LOG.info("  JV-CONTROL-2 = {}", String.format("%06d", rec.jvControl2()));
        LOG.info("  JV-CONTROL-3 = {}", String.format("%06d", rec.jvControl3()));
        LOG.info("  JV-CONTROL-4 = {}", String.format("%06d", rec.jvControl4()));
        LOG.info("  JV-CONTROL-5 = {}", String.format("%06d", rec.jvControl5()));
        LOG.info("  JV-NUMBER    = {}", String.format("%06d", rec.jvNumber()));
    }

    /**
     * CLI entrypoint. Mirrors LABA05's "return 0 or 99" contract.
     * Source: LABA05.cbl:105 — STOP RUN (with RETURN-CODE set)
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        LOG.info("LABA05 JV-NUMBER Fiscal Year Reset — Java 21 Migration");
        LOG.info("STATUS: Demo output — pending SME review");
        // In production, connection would be established from env vars (Risk 3).
        // For demo, this serves as entrypoint documentation only.
        LOG.error("No database connection configured. Use DbDispatcher with a live Connection.");
        System.exit(RC_DB_ERROR);
    }
}
