package com.cognition.jvmigration.labd20;

import com.cognition.jvmigration.dateconv.DateConv;
import com.cognition.jvmigration.db.DbDispatcher;
import com.cognition.jvmigration.db.DispatcherResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Modernized analog of {@code source/procobol/LABD20.pco} (daily JV comment
 * ingestion). Java port of
 * {@code migration/converted-code/python/labd20_loader.py}.
 *
 * <p>STATUS: Demo output pending SME review.
 *
 * <p>Construct with a {@link DbDispatcher}; call {@link #run(LoaderConfig)} to
 * execute the full job. On any DB error the loader rolls back and raises a
 * {@link RuntimeException}, mirroring the 9999-ROLL-BACK fall-through
 * (LABD20.pco:489+).
 *
 * <p>ASSUMPTIONS (migration/ASSUMPTIONS-AND-PLACEHOLDERS.md): A-4 (300-byte
 * record, APPROVER=14), A-1 RESOLVED (DATECONV faithful port), A-7 (column
 * shapes), A-8 (JC_SECTION='MA'), A-10 (WS-JV-COUNTERS = current count).
 */
public class Labd20Loader {

    private static final Logger LOGGER = Logger.getLogger(Labd20Loader.class.getName());

    // ---- Fixed-width record layout — TST123-COMMENT-REC (LABD20.pco:43-55) --
    public static final int TST123_RECORD_LENGTH = 300;

    // Byte offsets (0-based, end-exclusive), matching LABD20.pco:43-55.
    static final int COMMENT_DT_S = 0, COMMENT_DT_E = 8;        // PIC 9(008)
    static final int JV_NUMBER_S = 8, JV_NUMBER_E = 14;        // PIC 9(006)
    static final int SECTION_ID_S = 14, SECTION_ID_E = 16;     // PIC 9(002)
    static final int LOAN_NUMBER_S = 16, LOAN_NUMBER_E = 26;   // PIC 9(010)
    static final int LOAN_DT_NR_S = 0, LOAN_DT_NR_E = 26;      // composite redefine
    static final int SCHEDULE_DOC_NO_S = 26, SCHEDULE_DOC_NO_E = 36; // PIC X(010)
    static final int COMMENT_TEXT_S = 36, COMMENT_TEXT_E = 266;      // PIC X(230)
    static final int COMMENT_HIST_S = 26, COMMENT_HIST_E = 266;      // composite
    static final int REQUESTOR_S = 266, REQUESTOR_E = 286;          // PIC X(020)
    static final int APPROVER_S = 286, APPROVER_E = 300;           // PIC X(014)

    // ---- SQL (parameterized; replaces embedded EXEC SQL) -------------------
    // INSERT replaces LABD20.pco:352-372. Legacy uses Oracle
    // TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD'); the demo stores YYYY-MM-DD text.
    static final String INSERT_SQL =
        "INSERT INTO JC_SUBMITTED_COMMENT_TBL ("
            + " JC_SUBMITTED, JC_SUBMITTED_NUMBER, JC_SUBMITTED_SCHED_DOC_NO,"
            + " JC_SUBMITTED_COMMENT_HIST, JC_SUBMITTED_COMMENT_REQUESTOR,"
            + " JC_SUBMITTED_COMMENT_APPROVER, JC_SUBMITTED_CONTROL_NUM,"
            + " JC_SUBMITTED_UPDT_PROG_ID, JC_SUBMITTED_UPDT_PROG_DT"
            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    // Duplicate check replaces LABD20.pco:325-330.
    static final String SELECT_DUPE_SQL =
        "SELECT JC_SUBMITTED_NUMBER FROM JC_SUBMITTED_COMMENT_TBL WHERE JC_SUBMITTED = ?";

    // Post-process count update replaces LABD20.pco:398-401.
    static final String UPDATE_COUNT_SQL =
        "UPDATE JC_COUNT_TBL SET JC_COUNT_NUM = ? WHERE JC_SECTION = ?";

    static final String SELECT_COUNT_FOR_SECTION_SQL =
        "SELECT JC_COUNT_NUM FROM JC_COUNT_TBL WHERE JC_SECTION = ?";

    private final DbDispatcher dispatcher;
    private final LoaderStats stats = new LoaderStats();

    public Labd20Loader(DbDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public LoaderStats getStats() {
        return stats;
    }

    /** Result of validation — (valid?, reasons). */
    public static final class Disposition {
        public final boolean valid;
        public final List<String> reasons;
        Disposition(boolean valid, List<String> reasons) {
            this.valid = valid;
            this.reasons = reasons;
        }
    }

    // ---- parsing -----------------------------------------------------------
    /** Parse a single 300-byte raw record. Layout: LABD20.pco:43-55. */
    public static CommentRecord parseCommentRecord(String raw) {
        if (raw.length() != TST123_RECORD_LENGTH) {
            throw new IllegalArgumentException(
                "TST123-COMMENT-REC must be " + TST123_RECORD_LENGTH
                    + " bytes; got " + raw.length());
        }
        return new CommentRecord(
            raw,
            raw.substring(COMMENT_DT_S, COMMENT_DT_E),
            raw.substring(JV_NUMBER_S, JV_NUMBER_E),
            raw.substring(SECTION_ID_S, SECTION_ID_E),
            raw.substring(LOAN_NUMBER_S, LOAN_NUMBER_E),
            raw.substring(LOAN_DT_NR_S, LOAN_DT_NR_E),
            raw.substring(SCHEDULE_DOC_NO_S, SCHEDULE_DOC_NO_E),
            raw.substring(COMMENT_TEXT_S, COMMENT_TEXT_E),
            raw.substring(COMMENT_HIST_S, COMMENT_HIST_E),
            raw.substring(REQUESTOR_S, REQUESTOR_E),
            raw.substring(APPROVER_S, APPROVER_E));
    }

    /** CHECK-CYMD-DT (PERFORM at LABD20.pco:267) -> DATECONV func 1. */
    public static boolean checkCymdDt(String yyyymmdd) {
        return DateConv.checkCymdDt(yyyymmdd).valid;
    }

    /** Apply the validation rules from LABD20.pco:261-307. */
    public static Disposition determineDisposition(CommentRecord record) {
        List<String> reasons = new ArrayList<>();

        if (record.raw.trim().isEmpty()) {
            reasons.add("blank record");
        }

        if (!isDigits(record.commentDt)) {
            reasons.add("comment date is non-numeric");
        } else if (!checkCymdDt(record.commentDt)) {
            reasons.add("comment date is not a valid YYYYMMDD calendar date");
        }

        if (!(isDigits(record.jvNumber) && Integer.parseInt(record.jvNumber) > 0)) {
            reasons.add("JV number is non-numeric or zero");
        }

        if (!isDigits(record.sectionId)) {
            reasons.add("section id is non-numeric");
        }

        if (!isDigits(record.loanNumber)) {
            reasons.add("loan number is non-numeric");
        }

        if (record.commentText.trim().isEmpty()) {
            reasons.add("comment text is blank");
        }

        if (record.requestor.trim().isEmpty()) {
            reasons.add("requestor is blank");
        }

        if (record.approver.trim().isEmpty()) {
            reasons.add("approver is blank");
        }

        return new Disposition(reasons.isEmpty(), reasons);
    }

    // ---- card file (process date) -----------------------------------------
    /**
     * Read the MM/DD/CCYY date from CARDFILE and reshuffle to YYYYMMDD
     * (LABD20.pco:224-232).
     */
    public static String readProcessDate(Path cardPath) {
        String line;
        try {
            List<String> all = Files.readAllLines(cardPath, StandardCharsets.UTF_8);
            String joined = String.join("\n", all).trim();
            if (joined.isEmpty()) {
                throw new IllegalArgumentException("CARDFILE is empty");
            }
            line = joined.split("\n", -1)[0];
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String mmDdCcyy = line.length() >= 10 ? line.substring(0, 10) : line;
        String[] parts = mmDdCcyy.split("/");
        if (parts.length != 3) {
            throw new IllegalArgumentException("CARDFILE date must be MM/DD/CCYY; got " + mmDdCcyy);
        }
        String mm = parts[0], dd = parts[1], ccyy = parts[2];
        if (!(isDigits(mm) && isDigits(dd) && isDigits(ccyy))) {
            throw new IllegalArgumentException("CARDFILE date components must be numeric");
        }
        return ccyy + mm + dd;
    }

    // ---- comment-file iteration -------------------------------------------
    /**
     * Read each 300-byte fixed-width record in COMMENT-FILE (LABD20.pco:239,
     * 247-253). Tolerates both newline-terminated and no-newline records and
     * pads/truncates to the fixed width, matching the Python reference.
     */
    public static List<String> readRecords(Path commentPath) {
        List<String> records = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(commentPath, StandardCharsets.UTF_8)) {
            String raw;
            while ((raw = br.readLine()) != null) {
                // readLine() already strips the trailing LF/CRLF.
                if (raw.isEmpty()) {
                    continue;
                }
                if (raw.length() < TST123_RECORD_LENGTH) {
                    StringBuilder sb = new StringBuilder(raw);
                    while (sb.length() < TST123_RECORD_LENGTH) {
                        sb.append(' ');
                    }
                    raw = sb.toString();
                } else if (raw.length() > TST123_RECORD_LENGTH) {
                    raw = raw.substring(0, TST123_RECORD_LENGTH);
                }
                records.add(raw);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return records;
    }

    /** Mirror the OPEN OUTPUT / CLOSE truncate at LABD20.pco:215-218. */
    public static void truncateFile(Path commentPath) {
        try {
            Files.write(commentPath, new byte[0]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- mainline ----------------------------------------------------------
    /** Execute the full LABD20 mainline (LABD20.pco:208-220). */
    public LoaderStats run(LoaderConfig config) {
        String processDate = readProcessDate(config.cardPath);
        stats.processDate = processDate;
        LOGGER.info("PROCESS DATE = " + processDate);

        try {
            for (String raw : readRecords(config.commentPath)) {
                handleRecord(raw, processDate);
            }
            postProcess(config.sectionForCount);
            commitEnvironment();
            if (config.truncateAfterProcessing) {
                truncateFile(config.commentPath);
            }
        } catch (RuntimeException exc) {
            // _fatal_rollback already rolled back; rethrow as-is.
            throw exc;
        }
        return stats;
    }

    // ---- per-record processing --------------------------------------------
    private void handleRecord(String raw, String processDate) {
        stats.totalRead += 1;
        CommentRecord record;
        try {
            record = parseCommentRecord(raw);
        } catch (IllegalArgumentException exc) {
            stats.rejected += 1;
            stats.rejectedReasons.add("parse error: " + exc.getMessage());
            return;
        }

        Disposition disp = determineDisposition(record);
        if (!disp.valid) {
            stats.rejected += 1;
            stats.rejectedReasons.addAll(disp.reasons);
            LOGGER.info("REJECTED " + record.submittedKey() + " reasons=" + disp.reasons);
            return;
        }

        // Duplicate check (LABD20.pco:317-339).
        DispatcherResult dupe = dispatcher.selectOne(SELECT_DUPE_SQL, record.submittedKey());
        if (!dupe.rtncodeDms.equals(DbDispatcher.DMS_OK)
                && !dupe.rtncodeDms.equals(DbDispatcher.DMS_NOT_FOUND)) {
            fatalRollback("duplicate-check failed dms=" + dupe.rtncodeDms + " sqlcode=" + dupe.sqlcode);
        }
        if (dupe.ok()) {
            stats.duplicates += 1;
            LOGGER.info("DUPLICATE ENTRY " + record.submittedKey());
            return;
        }

        insert(record, processDate);
    }

    private void insert(CommentRecord record, String processDate) {
        // CREATE-COMMENT-RECORD path (LABD20.pco:342-389).
        stats.accepted += 1;
        int jcNumber = stats.accepted; // mirrors WS-JV-COUNTER (LABD20.pco:345)
        DispatcherResult result = dispatcher.insert(INSERT_SQL,
            record.submittedKey(),
            jcNumber,
            record.scheduleDocNo,
            record.commentHist,
            record.requestor,
            record.approver,
            record.controlNum(),
            "LABD20",
            formatProcessDate(processDate));
        if (!result.ok()) {
            fatalRollback("INSERT failed dms=" + result.rtncodeDms + " sqlcode=" + result.sqlcode);
        }
        stats.inserted += 1;
    }

    // ---- post-process ------------------------------------------------------
    private void postProcess(String section) {
        // POST-PROCESS UPDATE block (LABD20.pco:392-405).
        DispatcherResult existing = dispatcher.selectOne(SELECT_COUNT_FOR_SECTION_SQL, section);
        int prior = (existing.ok() && !existing.rows.isEmpty())
            ? ((Number) existing.rows.get(0)[0]).intValue() : 0;
        if (stats.accepted <= prior) {
            LOGGER.info("POST-PROCESS: counter " + stats.accepted
                + " not greater than prior " + prior + " — skip update");
            return;
        }
        DispatcherResult result = dispatcher.update(UPDATE_COUNT_SQL, stats.accepted, section);
        if (!result.ok()) {
            fatalRollback("JC_COUNT_TBL UPDATE failed dms=" + result.rtncodeDms
                + " sqlcode=" + result.sqlcode);
        }
    }

    // ---- close-sql-environment + stats ------------------------------------
    private void commitEnvironment() {
        // CLOSE-SQL-ENVIRONMENT (LABD20.pco:408-446). COMMIT + stats.
        DispatcherResult commit = dispatcher.commit();
        if (!commit.ok()) {
            fatalRollback("COMMIT failed dms=" + commit.rtncodeDms + " sqlcode=" + commit.sqlcode);
        }
        stats.submittedTotal = dispatcher.countRows("JC_SUBMITTED_COMMENT_TBL");
        stats.rejectedTotal = dispatcher.countRows("JC_REJECTED_COMMENT_TBL");
        stats.appliedTotal = dispatcher.countRows("JC_APPLIED_COMMENT_TBL");
        LOGGER.info("\n" + stats.formatReport());
    }

    // ---- helpers -----------------------------------------------------------
    private void fatalRollback(String message) {
        // Mirror the 9999-ROLL-BACK section (LABD20.pco:489+).
        LOGGER.severe("LABD20: " + message + " — rolling back");
        dispatcher.rollback();
        throw new RuntimeException(message);
    }

    /**
     * LABD20.pco:371 uses TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD'); the demo stores
     * ISO YYYY-MM-DD text. Production must restore TO_DATE.
     */
    static String formatProcessDate(String yyyymmdd) {
        if (yyyymmdd.length() != 8 || !isDigits(yyyymmdd)) {
            return yyyymmdd;
        }
        return yyyymmdd.substring(0, 4) + "-" + yyyymmdd.substring(4, 6) + "-"
            + yyyymmdd.substring(6, 8);
    }

    /** Equivalent of Python {@code str.isdigit()} for ASCII: non-empty, all digits. */
    static boolean isDigits(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
