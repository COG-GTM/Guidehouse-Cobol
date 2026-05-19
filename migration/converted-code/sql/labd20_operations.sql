-- =========================================================================
-- labd20_operations.sql
-- Parameterized Oracle SQL extracted from source/procobol/LABD20.pco.
--
-- STATUS: Demo output pending SME review. Generated as part of the Guidehouse
-- JV COBOL/Pro*COBOL modernization walkthrough.
--
-- ASSUMPTIONS (see migration/ASSUMPTIONS-AND-PLACEHOLDERS.md)
--   A-3: All host variables are converted to bind parameters (:name).
--        Legacy Pro*COBOL uses the same `:NAME` host-variable syntax, so the
--        statements below are very close to the original — they have just
--        been disentangled from EXEC SQL ... END-EXEC wrappers and re-indented.
--   A-7: Column names + types reflect database/descriptions/describe
--        JC_SUBMITTED_COMMENT_TBL.txt and JC_COUNT_TBL.txt verbatim.
--   A-8: The 'MA' literal in the post-process UPDATE is taken from
--        LABD20.pco:400. See RISKS-AND-GAPS.md Risk 8 for the broader
--        question of whether this section ID is hardcoded by design.
--
-- TRANSACTION BOUNDARIES:
--   Statements 1-4 below participate in a single Oracle transaction. The
--   legacy program executes COMMIT WORK (statement 5) only after all per-
--   record inserts succeed; any non-zero SQLCODE triggers a jump to
--   9999-ROLL-BACK (see LABD20.pco:489+).
--
-- ORACLE-SPECIFIC CONSTRUCTS USED:
--   - TO_DATE(:string,'YYYYMMDD') in statement 2 — Oracle-specific.
--   - COMMIT WORK / ROLLBACK statements — ANSI but Oracle-flavored.
-- =========================================================================


-- -------------------------------------------------------------------------
-- 1) Duplicate-key probe.
-- Source: source/procobol/LABD20.pco:325-330 (DETERMINE-IF-DUPLICATE).
-- Caller checks SQLCODE: 0 → duplicate, 100 → safe to insert, anything else
-- → ROLLBACK (LABD20.pco:331-339).
-- -------------------------------------------------------------------------
SELECT JC_SUBMITTED_NUMBER
  INTO :ws_check_number
  FROM JC_SUBMITTED_COMMENT_TBL
 WHERE JC_SUBMITTED = :ws_tst123_loan_dt_nr;   -- 26-char composite key


-- -------------------------------------------------------------------------
-- 2) Insert one accepted comment row.
-- Source: source/procobol/LABD20.pco:352-372 (CREATE-COMMENT-RECORD).
-- Caller increments WS-JV-COUNTER before each call (LABD20.pco:345) and
-- passes 'LABD20' as the literal update-program identifier.
-- -------------------------------------------------------------------------
INSERT INTO JC_SUBMITTED_COMMENT_TBL (
    JC_SUBMITTED,
    JC_SUBMITTED_NUMBER,
    JC_SUBMITTED_SCHED_DOC_NO,
    JC_SUBMITTED_COMMENT_HIST,
    JC_SUBMITTED_COMMENT_REQUESTOR,
    JC_SUBMITTED_COMMENT_APPROVER,
    JC_SUBMITTED_CONTROL_NUM,
    JC_SUBMITTED_UPDT_PROG_ID,
    JC_SUBMITTED_UPDT_PROG_DT
) VALUES (
    :ws_tst123_loan_dt_nr,         -- 26 chars (date+jv+section+loan)
    :ws_jv_counter,                -- in-batch counter
    :ws_tst123_schedule_doc_no,    -- 10 chars
    :ws_tst123_comment_hist,       -- 240 chars (schedule-doc-no + text)
    :ws_tst123_comment_requestor,  -- 20 chars
    :ws_tst123_comment_approver,   -- 14 chars (per LABD20.pco line 55)
    :ws_control_num,               -- 8 chars (JV-NUMBER + SECTION-ID)
    'LABD20',                      -- literal program id, LABD20.pco:370
    TO_DATE(:ws_process_date, 'YYYYMMDD')  -- LABD20.pco:371 — Oracle-specific
);


-- -------------------------------------------------------------------------
-- 3) Post-process count update — only runs if the in-batch counter is
-- strictly greater than the persisted counter (LABD20.pco:393).
-- Source: source/procobol/LABD20.pco:398-401.
-- ASSUMPTION A-8: section literal 'MA' from LABD20.pco:400 preserved.
--   The legacy column name in LABD20 is JC_SECTION_COUNT (line 399);
--   the supplied database/descriptions/describe JC_COUNT_TBL.txt also lists
--   JC_COUNT_NUM. Production deployments must confirm which name applies
--   (SME review required — see RISKS-AND-GAPS.md Risk 8).
-- -------------------------------------------------------------------------
UPDATE JC_COUNT_TBL
   SET JC_SECTION_COUNT = :ws_jv_counter
 WHERE JC_SECTION = 'MA';


-- -------------------------------------------------------------------------
-- 4) Commit the transaction.
-- Source: source/procobol/LABD20.pco:413.
-- -------------------------------------------------------------------------
COMMIT WORK;


-- -------------------------------------------------------------------------
-- 5) Stats SELECTs reported at end of run.
-- Source: source/procobol/LABD20.pco:421-446.
-- -------------------------------------------------------------------------
SELECT COUNT(*) INTO :ws_total_submit_end_cnt
  FROM JC_SUBMITTED_COMMENT_TBL;          -- LABD20.pco:421-423

SELECT COUNT(*) INTO :ws_total_reject_end_cnt
  FROM JC_REJECTED_COMMENT_TBL;           -- LABD20.pco:431-433

SELECT COUNT(*) INTO :ws_total_applied_end_cnt
  FROM JC_APPLIED_COMMENT_TBL;            -- LABD20.pco:441-443


-- -------------------------------------------------------------------------
-- 6) Roll back on any error.
-- Source: source/procobol/LABD20.pco:9999-ROLL-BACK (lines 489+).
-- All failure paths in LABD20 fall through to ROLLBACK + STOP RUN with
-- RETURN-CODE = 99.
-- -------------------------------------------------------------------------
ROLLBACK;
