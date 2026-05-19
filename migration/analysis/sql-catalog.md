# Embedded SQL catalog

> **Status:** Demo output, pending SME review.
> See [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).

This document catalogs every `EXEC SQL` statement extracted from the supplied
Pro\*COBOL sources, with file + line citations, bind variables, transaction
boundaries, and Oracle-specific construct flags.

A parameterized, demo-grade extraction of these statements lives at
[`migration/converted-code/sql/`](../converted-code/sql/).

---

## 1. Catalog table

| # | Source file | Lines | Statement | Tables touched | Bind variables / literals | Tx role | Oracle-specific |
|---|-------------|-------|-----------|----------------|----------------------------|---------|------------------|
| 1 | `source/procobol/LABD20.pco` | 325-330 | `SELECT JC_SUBMITTED_NUMBER INTO :WS-CHECK-NUMBER FROM JC_SUBMITTED_COMMENT_TBL WHERE JC_SUBMITTED = :WS-TST123-LOAN-DT-NR` | JC_SUBMITTED_COMMENT_TBL | bind `:WS-TST123-LOAN-DT-NR` (CHAR(26)) | read (no tx side effect) | — |
| 2 | `source/procobol/LABD20.pco` | 352-372 | `INSERT INTO JC_SUBMITTED_COMMENT_TBL (...) VALUES (...)` (9 columns) | JC_SUBMITTED_COMMENT_TBL | binds `:WS-TST123-LOAN-DT-NR`, `:WS-JV-COUNTER`, `:WS-TST123-SCHEDULE-DOC-NO`, `:WS-TST123-COMMENT-HIST`, `:WS-TST123-COMMENT-REQUESTOR`, `:WS-TST123-COMMENT-APPROVER`, `:WS-CONTROL-NUM`, literal `'LABD20'`, `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` | write, in tx | **TO_DATE** |
| 3 | `source/procobol/LABD20.pco` | 398-401 | `UPDATE JC_COUNT_TBL SET JC_SECTION_COUNT = :WS-JV-COUNTER WHERE JC_SECTION = 'MA'` | JC_COUNT_TBL | bind `:WS-JV-COUNTER`; literal `'MA'` | write, in tx | — (note column-name discrepancy with describe file — see RISKS Risk 8) |
| 4 | `source/procobol/LABD20.pco` | 413 | `COMMIT WORK` | — | — | tx commit | — (ANSI) |
| 5 | `source/procobol/LABD20.pco` | 421-423 | `SELECT COUNT(*) INTO :WS-TOTAL-SUBMIT-END-CNT FROM JC_SUBMITTED_COMMENT_TBL` | JC_SUBMITTED_COMMENT_TBL | — | read | — |
| 6 | `source/procobol/LABD20.pco` | 431-433 | `SELECT COUNT(*) INTO :WS-TOTAL-REJECT-END-CNT FROM JC_REJECTED_COMMENT_TBL` | JC_REJECTED_COMMENT_TBL | — | read | — |
| 7 | `source/procobol/LABD20.pco` | 441-443 | `SELECT COUNT(*) INTO :WS-TOTAL-APPLIED-END-CNT FROM JC_APPLIED_COMMENT_TBL` | JC_APPLIED_COMMENT_TBL | — | read | — |
| 8 | `source/procobol/LABD20.pco` | 489+ | `EXEC SQL ROLLBACK` (inside `9999-ROLL-BACK`) | — | — | tx rollback | — |
| 9 | `source/procobol/CONTROL-RECORD-TABLE-IO.pco` | 37-78 (static + dynamic WHERE) + 296-311 (PREPARE) | `SELECT CONTROL_RECORD_DATA FROM CONTROL_RECORD_TABLE WHERE …` (dynamic WHERE on PK or ROWID) | CONTROL_RECORD_TABLE | binds via PREPARE-STRING; resolves to `:CONTROL_RECORD_NAME` + `:CONTROL_RECORD_NUMBER` (PK) or `:CONTROL_RECORD_ROWID` | read | **ROWID** |
| 10 | `source/procobol/CONTROL-RECORD-TABLE-IO.pco` | INSERT-DB-DATA section | `INSERT INTO CONTROL_RECORD_TABLE (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER, CONTROL_RECORD_DATA) VALUES (...)` | CONTROL_RECORD_TABLE | bind on three columns; CONTROL_RECORD_DATA is CHAR(400) | write, in tx | — |
| 11 | `source/procobol/CONTROL-RECORD-TABLE-IO.pco` | UPDATE-DB-DATA section | `UPDATE CONTROL_RECORD_TABLE SET CONTROL_RECORD_DATA = :data WHERE CONTROL_RECORD_NAME = :name AND CONTROL_RECORD_NUMBER = :number` | CONTROL_RECORD_TABLE | binds on 3 cols | write, in tx | — |
| 12 | `source/procobol/CONTROL-RECORD-TABLE-IO.pco` | DELETE-DB-DATA section | `DELETE FROM CONTROL_RECORD_TABLE WHERE …` | CONTROL_RECORD_TABLE | binds on PK | write, in tx | — |
| 13 | `source/procobol/DBIO.pco` | CONNECT-RTN (188-215) | `EXEC SQL CONNECT :user IDENTIFIED BY :pwd` | (Oracle session) | binds via `GET-FILE-NAMES` reading `/tst/.oralogin` and `/tst/.orapasswd` (RISKS Risk 3) | session | — |
| 14 | `source/procobol/DBIO.pco` | implicit | `EXEC SQL COMMIT WORK` / `EXEC SQL ROLLBACK` | — | — | tx | — |

---

## 2. Dynamic SQL construction

The only dynamic-SQL site in the supplied source is in
`source/procobol/CONTROL-RECORD-TABLE-IO.pco`. The WHERE clause is built at
runtime from one of two key buffers (`W-DB-PRIMARY-KEY` at lines 58-65, or
`W-DB-ROWID-KEY` at lines 70-78) via a `PREPARE-STRING` paragraph
(CONTROL-RECORD-TABLE-IO.pco:296-311). The structural pattern is:

```
EXEC SQL PREPARE …
EXEC SQL EXECUTE … USING :HOST-VAR-LIST
```

**Modernization stance (RISKS Risk 4 + ASSUMPTIONS A-9):**
Both variants are replaced by two **static, parameterized** statements in
[`migration/converted-code/sql/control_record_table_operations.sql`](../converted-code/sql/control_record_table_operations.sql)
(SELECT-by-PK and SELECT-by-ROWID). The PREPARE/EXECUTE machinery itself is
not preserved — modern callers choose which static statement to issue.

---

## 3. Transaction boundaries

LABD20 transaction profile (per execution):

```
BEGIN [implicit on first DML]
  ┌─ for each accepted record:
  │    SELECT (dupe-check)               # 1
  │    INSERT JC_SUBMITTED_COMMENT_TBL    # 2
  ├─ POST-PROCESS:
  │    UPDATE JC_COUNT_TBL               # 3  (only if WS-JV-COUNTER > WS-JV-COUNTERS)
  ├─ CLOSE-SQL-ENVIRONMENT:
  │    COMMIT WORK                       # 4
  │    SELECT COUNT(*) ×3                # 5,6,7  (stats; logically outside tx)
  └─ on ANY non-zero SQLCODE → GO TO 9999-ROLL-BACK → ROLLBACK + RETURN-CODE=99
```

LABA05 transaction profile:

```
CONNECT                                  # 13
SELECT (FETCH-CTRL-REC)                  # 9 (SELECT variant)
UPDATE (MODIFY-CTRL-REC)                 # 11
COMMIT WORK                              # 14
   (or ROLLBACK + RETURN-CODE=99 on any DBIO non-zero)
```

---

## 4. Oracle-specific constructs in scope

| Construct | Where | Modernization stance |
|-----------|-------|----------------------|
| `TO_DATE(:string,'YYYYMMDD')` | LABD20.pco:371 | Preserved in `labd20_operations.sql`; the modernized Python stores ISO `YYYY-MM-DD` for the sqlite demo but **must** restore `TO_DATE` against real Oracle. See ASSUMPTIONS A-7. |
| `ROWID` pseudo-column | CONTROL-RECORD-TABLE-IO.pco:70-78 | Preserved in `control_record_table_operations.sql` as a separate static SELECT variant. |
| `SYSDATE`, `RETURNING INTO` | **Not present** in supplied source. | n/a |
| `SQLCA` / `SQLCODE` global state | All files | Replaced by per-call `DispatcherResult` objects with explicit `sqlcode` + `rtncode_dms` fields. |
| `EXEC SQL CONNECT :user IDENTIFIED BY :pwd` reading credential files | DBIO.pco:188-215 + 33-38 | **Forbidden** in modernized code (RISKS Risk 3). Replace with managed secrets injection. |
| `EXEC SQL INCLUDE SQLCA END-EXEC` | All files | n/a — Python DB-API drivers raise exceptions instead. |

---

## 5. SQL extracts (parameterized) — generated artifacts

- [`migration/converted-code/sql/labd20_operations.sql`](../converted-code/sql/labd20_operations.sql) — statements 1-8.
- [`migration/converted-code/sql/control_record_table_operations.sql`](../converted-code/sql/control_record_table_operations.sql) — statements 9-12.

Each generated SQL file leads with an "ASSUMPTIONS" comment block listing the
source citation, parameterization choices, and Oracle-specific constructs.
