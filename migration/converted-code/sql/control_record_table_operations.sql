-- =========================================================================
-- control_record_table_operations.sql
-- Parameterized Oracle SQL extracted from
-- source/procobol/CONTROL-RECORD-TABLE-IO.pco and source/cobol/LABA05.cbl.
--
-- STATUS: Demo output pending SME review. Generated as part of the Guidehouse
-- JV COBOL/Pro*COBOL modernization walkthrough.
--
-- ASSUMPTIONS (see migration/ASSUMPTIONS-AND-PLACEHOLDERS.md)
--   A-2: CONTROL_RECORD_DATA is a CHAR(400) blob mirroring
--        JV-CONTROL-REC.cpy. The JV-NUMBER stretch is USAGE BINARY in legacy
--        COBOL — the modernized code reads/writes that stretch via the
--        binary↔display conversion in db_dispatcher.py.
--   A-9: The dynamic SQL construction in CONTROL-RECORD-TABLE-IO.pco
--        (PREPARE-STRING at lines 296-311, with WHERE-clause snippets at
--        lines 58-78 / SELECT chunks at 37-52) is replaced with two static
--        SELECT variants below. See RISKS-AND-GAPS.md Risk 4.
--
-- ORACLE-SPECIFIC CONSTRUCTS USED:
--   - ROWID column (Oracle pseudo-column) — preserved verbatim in SELECT 2
--     because legacy callers use it for in-place updates.
-- =========================================================================


-- -------------------------------------------------------------------------
-- 1) SELECT by composite primary key.
-- Source: derived from the static SELECT chunks at
--   CONTROL-RECORD-TABLE-IO.pco:37-52 + the WHERE-clause built from
--   W-DB-PRIMARY-KEY (lines 58-65).
-- Used by LABA05 FETCH-CTRL-REC (LABA05.cbl:152-174).
-- -------------------------------------------------------------------------
SELECT CONTROL_RECORD_DATA
  INTO :control_record_data
  FROM CONTROL_RECORD_TABLE
 WHERE CONTROL_RECORD_NAME   = :control_record_name
   AND CONTROL_RECORD_NUMBER = :control_record_number;


-- -------------------------------------------------------------------------
-- 2) SELECT by ROWID (used by CONTROL-RECORD-TABLE-IO when subsequent
--    UPDATE/DELETE wants to short-circuit the PK lookup).
-- Source: dynamic-SQL WHERE built from W-DB-ROWID-KEY at
--   CONTROL-RECORD-TABLE-IO.pco:70-78.
-- -------------------------------------------------------------------------
SELECT CONTROL_RECORD_DATA
  INTO :control_record_data
  FROM CONTROL_RECORD_TABLE
 WHERE ROWID = :control_record_rowid;


-- -------------------------------------------------------------------------
-- 3) INSERT a new control record.
-- Source: INSERT-DB-DATA section of CONTROL-RECORD-TABLE-IO.pco.
-- -------------------------------------------------------------------------
INSERT INTO CONTROL_RECORD_TABLE (
    CONTROL_RECORD_NAME,
    CONTROL_RECORD_NUMBER,
    CONTROL_RECORD_DATA
) VALUES (
    :control_record_name,
    :control_record_number,
    :control_record_data            -- CHAR(400) blob
);


-- -------------------------------------------------------------------------
-- 4) UPDATE the CONTROL_RECORD_DATA blob.
-- Source: UPDATE-DB-DATA / MODIFY-CTRL-REC. Called by LABA05 to reset
--   JV-NUMBER (LABA05.cbl:176-205). The 400-byte blob is rewritten in
--   place.
-- -------------------------------------------------------------------------
UPDATE CONTROL_RECORD_TABLE
   SET CONTROL_RECORD_DATA = :control_record_data
 WHERE CONTROL_RECORD_NAME   = :control_record_name
   AND CONTROL_RECORD_NUMBER = :control_record_number;


-- -------------------------------------------------------------------------
-- 5) DELETE.
-- Source: DELETE-DB-DATA section of CONTROL-RECORD-TABLE-IO.pco.
-- -------------------------------------------------------------------------
DELETE FROM CONTROL_RECORD_TABLE
 WHERE CONTROL_RECORD_NAME   = :control_record_name
   AND CONTROL_RECORD_NUMBER = :control_record_number;


-- -------------------------------------------------------------------------
-- Transaction control: matches DBIO.pco's CONNECT / COMMIT / ROLLBACK
-- behavior. LABA05.cbl issues an implicit COMMIT after MODIFY-CTRL-REC and
-- ROLLBACK on any DBIO error (LABA05.cbl: return-code 99).
-- -------------------------------------------------------------------------
COMMIT WORK;
ROLLBACK;
