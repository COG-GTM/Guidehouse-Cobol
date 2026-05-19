# Business requirements with source citations

> **Status:** Demo output, pending SME review.
> Generated as part of the Guidehouse JV COBOL/Pro*COBOL modernization walkthrough.
> See [`migration/MIGRATION-PLAN.md`](../MIGRATION-PLAN.md) and
> [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).

This document expands the baseline at `business-requirements/initial-requirements.md`
with explicit source citations and confidence levels. Each requirement is keyed
`BR-<program>-<nnn>` and tagged with one of:

- **Confirmed (HIGH)** — directly visible in source.
- **Inferred (MEDIUM)** — strongly implied by source + database descriptions.
- **Unresolvable without supplied artifact (LOW)** — depends on missing copybooks (DATECONV-WS/DATECONV-PD) or runtime/environment artifacts that were not supplied.

---

## Program 1: LABA05 (`source/cobol/LABA05.cbl`) — fiscal-year reset

| ID | Requirement | Source citation | Classification | Confidence |
|----|-------------|-----------------|----------------|------------|
| BR-LABA05-001 | Connect to Oracle via DBIO; abort with `RETURN-CODE=99` on connect failure. | `source/cobol/LABA05.cbl:69-85`; `source/procobol/DBIO.pco:188-215` | Confirmed | HIGH |
| BR-LABA05-002 | SELECT the JV-CONTROL-REC row from `CONTROL_RECORD_TABLE` keyed by `CONTROL_RECORD_NAME='JV-CONTROL-REC'` AND `CONTROL_RECORD_NUMBER=1`. | `source/cobol/LABA05.cbl:152-174` (FETCH-CTRL-REC); table shape from `database/descriptions/describe CONTROL_RECORD_TABLE.txt` | Confirmed | HIGH |
| BR-LABA05-003 | Display the JV-NUMBER read from CONTROL_RECORD_DATA prior to mutation. | `source/cobol/LABA05.cbl` MODIFY-CTRL-REC, lines `176-205` (DISPLAY of JV-NUMBER OF JV-CONTROL-REC) | Confirmed | HIGH |
| BR-LABA05-004 | Set `JV-NUMBER = 1` and UPDATE the row in place. | `source/cobol/LABA05.cbl:184-189` (MOVE 1 TO JV-NUMBER), `:190-205` (UPDATE via DBIO) | Confirmed | HIGH |
| BR-LABA05-005 | COMMIT on success, ROLLBACK + return code 99 on any DBIO non-zero. | `source/cobol/LABA05.cbl` PROGRAM-EXIT / error fall-throughs; `source/procobol/DBIO.pco:374-398` (DMS translation) | Confirmed | HIGH |
| BR-LABA05-006 | The JV-NUMBER value lives at a fixed byte offset inside the 400-byte CONTROL_RECORD_DATA blob; legacy stores it as `USAGE BINARY` and converts to display before printing. | `source/copybooks/JV-CONTROL-REC.cpy:1-12`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28`, `:257-266` (binary/display conversion) | Confirmed | HIGH (layout); MEDIUM (exact byte offset depends on how callers redefine the blob) |

**Modernized analog:** [`migration/converted-code/python/laba05_reset.py`](../converted-code/python/laba05_reset.py)
**Tests:** [`migration/converted-code/python/tests/test_laba05_reset.py`](../converted-code/python/tests/test_laba05_reset.py)

---

## Program 2: LABD20 (`source/procobol/LABD20.pco`) — daily JV comment ingestion

### Input layout requirements

| ID | Requirement | Source citation | Classification | Confidence |
|----|-------------|-----------------|----------------|------------|
| BR-LABD20-001 | Process date is read from the `CARDFILE` external file as `MM/DD/CCYY` and reshuffled to `YYYYMMDD` for subsequent SQL bind. | `source/procobol/LABD20.pco:224-234` (LABD20-HOUSE-KEEPING, MOVE WS-PARM-MM/-DD/-CC/-YY to WS-PROCESS-DATE-…) | Confirmed | HIGH |
| BR-LABD20-002 | Each comment record is fixed-width 300 bytes (`TST123-COMMENT-REC`) with the field layout: comment-date (8) + JV-number (6) + section-id (2) + loan-number (10) + schedule-doc-no (10) + comment-text (230) + requestor (20) + approver (14). | `source/procobol/LABD20.pco:43-55` (FD layout). **Active** line 55 sets APPROVER to 14 bytes; commented line 54 (20 bytes) is **not** used. | Confirmed | HIGH |
| BR-LABD20-003 | The first 26 bytes form a composite identifier (`TST123-LOAN-DT-NR`) that doubles as the JC_SUBMITTED primary key. | `source/procobol/LABD20.pco:44-49` (REDEFINES); LABD20.pco:329 (`WHERE JC_SUBMITTED = :WS-TST123-LOAN-DT-NR`) | Confirmed | HIGH |

### Validation requirements (DETERMINE-COMMENT-DISPOSITION)

| ID | Requirement | Source citation | Classification | Confidence |
|----|-------------|-----------------|----------------|------------|
| BR-LABD20-004 | Reject any record that is all spaces. | LABD20.pco:261-263 | Confirmed | HIGH |
| BR-LABD20-005 | Reject any record where `TST123-COMMENT-DT` is not numeric. | LABD20.pco:265, 272-274 | Confirmed | HIGH |
| BR-LABD20-006 | Reject any record where `TST123-COMMENT-DT` is numeric but `CHECK-CYMD-DT` (from `DATECONV-PD`) flags it as invalid. | LABD20.pco:266-274 — references `DATECONV-PD` which was **not** supplied. | Inferred / partly unresolvable | LOW (we use a Gregorian-calendar stub — see ASSUMPTIONS A-5) |
| BR-LABD20-007 | Reject any record where `TST123-JV-NUMBER` is non-numeric or ≤ 0. | LABD20.pco:276-281 | Confirmed | HIGH |
| BR-LABD20-008 | Reject any record where `TST123-SECTION-ID` is non-numeric. | LABD20.pco:283-287 | Confirmed | HIGH |
| BR-LABD20-009 | Reject any record where `TST123-LOAN-NUMBER` is non-numeric. | LABD20.pco:289-293 | Confirmed | HIGH |
| BR-LABD20-010 | Comment text (`TST123-COMMENT-TEXT`) must not be blank. Note the schedule-doc-no field is explicitly NOT validated (LABD20.pco:294-296 commentary). | LABD20.pco:297-299 | Confirmed | HIGH |
| BR-LABD20-011 | Requestor (`TST123-COMMENT-REQUESTOR`) must not be blank. | LABD20.pco:301-303 | Confirmed | HIGH |
| BR-LABD20-012 | Approver (`TST123-COMMENT-APPROVER`) must not be blank. | LABD20.pco:305-307 | Confirmed | HIGH |

### Persistence requirements

| ID | Requirement | Source citation | Classification | Confidence |
|----|-------------|-----------------|----------------|------------|
| BR-LABD20-013 | Before insert, probe `JC_SUBMITTED_COMMENT_TBL` for an existing row with the same 26-byte JC_SUBMITTED key. `SQLCODE=0` → duplicate; `SQLCODE=100` → safe to insert; any other code → roll back. | LABD20.pco:317-339 (DETERMINE-IF-DUPLICATE) | Confirmed | HIGH |
| BR-LABD20-014 | INSERT into `JC_SUBMITTED_COMMENT_TBL` with 9 columns: `JC_SUBMITTED`, `JC_SUBMITTED_NUMBER`, `JC_SUBMITTED_SCHED_DOC_NO`, `JC_SUBMITTED_COMMENT_HIST`, `JC_SUBMITTED_COMMENT_REQUESTOR`, `JC_SUBMITTED_COMMENT_APPROVER`, `JC_SUBMITTED_CONTROL_NUM`, `JC_SUBMITTED_UPDT_PROG_ID` (literal `'LABD20'`), `JC_SUBMITTED_UPDT_PROG_DT` (Oracle `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')`). | LABD20.pco:342-372 (CREATE-COMMENT-RECORD). Column types from `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt`. | Confirmed | HIGH |
| BR-LABD20-015 | Maintain a per-batch in-memory counter `WS-JV-COUNTER` (incremented on each successful insert). | LABD20.pco:345 | Confirmed | HIGH |
| BR-LABD20-016 | After processing all records, if `WS-JV-COUNTER > WS-JV-COUNTERS` then `UPDATE JC_COUNT_TBL SET JC_SECTION_COUNT = :WS-JV-COUNTER WHERE JC_SECTION = 'MA'`. | LABD20.pco:392-405 (POST-PROCESS) | Confirmed (literals + control flow) | MEDIUM — origin of `WS-JV-COUNTERS` not visible in supplied source; see ASSUMPTIONS A-10. The hardcoded section `'MA'` is recorded but its design intent (hardcoded vs. table-driven) is open (RISKS Risk 8). |
| BR-LABD20-017 | COMMIT after post-process; emit COUNT(*) stats for `JC_SUBMITTED_COMMENT_TBL`, `JC_REJECTED_COMMENT_TBL`, `JC_APPLIED_COMMENT_TBL`. | LABD20.pco:408-446 (CLOSE-SQL-ENVIRONMENT) | Confirmed | HIGH |
| BR-LABD20-018 | Truncate the COMMENT-FILE on the way out (OPEN OUTPUT + immediate CLOSE) so the next run does not re-process the same records. | LABD20.pco:215-218 | Confirmed | HIGH |
| BR-LABD20-019 | On any `SQLCODE NOT = 0` (with the SELECT-100 exception in BR-LABD20-013) `GO TO 9999-ROLL-BACK`, ROLLBACK, set `RETURN-CODE=99`. | LABD20.pco fall-throughs at 332, 374, 403, 414, 424, 434, 444; section `9999-ROLL-BACK` near LABD20.pco:489+. | Confirmed | HIGH |
| BR-LABD20-020 | Rejected records are counted via `WS-TST123-RECS-ERR-CNT` and `WS-ERRORS-CNT`, but the supplied source does NOT show a populated `JC_REJECTED_COMMENT_TBL` insert path — the table is referenced only by COUNT(*) at LABD20.pco:431-433. The legacy site likely populates that table elsewhere (different program / DML). | LABD20.pco:309-314 (counters); LABD20.pco:431-433 (read-only here) | Inferred (gap) | LOW — flag for SME confirmation |

### Cross-cutting requirements

| ID | Requirement | Source citation | Classification | Confidence |
|----|-------------|-----------------|----------------|------------|
| BR-LABD20-021 | Construct an 8-character `WS-CONTROL-NUM` = JV-NUMBER (6) + SECTION-ID (2) and write it into `JC_SUBMITTED_CONTROL_NUM`. | LABD20.pco:160-165 (WS-CONTROL-NUM redefine) + LABD20.pco:359, 369 | Confirmed | HIGH |
| BR-LABD20-022 | All accepted comment records preserve a 240-byte `COMMENT-HIST` = schedule-doc-no (10) + comment-text (230) which is written verbatim to `JC_SUBMITTED_COMMENT_HIST`. | LABD20.pco:50-52 + LABD20.pco:366 | Confirmed | HIGH |

---

## Unresolvable items (require missing artifacts to fully validate)

| ID | Item | Why unresolvable | Mitigation |
|----|------|------------------|------------|
| BR-LABD20-UR-001 | Exact behavior of `CHECK-CYMD-DT` (incl. any business-calendar gates such as fiscal-year cutoffs). | `DATECONV-PD` copybook not supplied. | Gregorian-calendar stub in `labd20_loader.check_cymd_dt`; explicit `# PLACEHOLDER` marker; SME confirmation required. |
| BR-LABD20-UR-002 | Legacy date-conversion working-storage layout that may be required for FROM-CYMD-DT / WS-PROCESS-DATE-* fields. | `DATECONV-WS` copybook not supplied. | Modernized code uses Python `datetime` objects; no internal layout reproduction. |
| BR-LABD20-UR-003 | Source of `WS-JV-COUNTERS` threshold variable. | Not declared in the supplied LABD20 source; may live in a copybook not provided. | Modernized code reads current `JC_COUNT_NUM` for section 'MA' as the inferred threshold. |
| BR-LABD20-UR-004 | Origin of records in `JC_REJECTED_COMMENT_TBL` / `JC_APPLIED_COMMENT_TBL`. | Only `COUNT(*)` references appear in supplied source. | Flag for SME confirmation; ensure the upstream/downstream programs are part of the modernization scope. |

---

## Traceability — requirements to artifacts

| Requirement | Modernized analog | Test |
|-------------|-------------------|------|
| BR-LABA05-001..005 | `laba05_reset.run` | `test_laba05_reset.TestReset` |
| BR-LABA05-006 | `_extract_jv_number` / `_replace_jv_number` | `TestJVNumberByteLayout`, `TestBinaryDisplayConversion` |
| BR-LABD20-001 | `read_process_date` | `TestReadProcessDate` |
| BR-LABD20-002, 003, 021, 022 | `parse_comment_record`, `CommentRecord` | `TestRecordLayout`, `TestParseCommentRecord` |
| BR-LABD20-004..012 | `determine_disposition` + `check_cymd_dt` | `TestValidationRules`, `TestCheckCYMD` |
| BR-LABD20-013 | `LABD20Loader._handle_record` (dupe-check branch) | `TestLoaderEndToEnd.test_duplicate_record_does_not_insert_twice` |
| BR-LABD20-014 | `LABD20Loader._insert` | `TestLoaderEndToEnd.test_insert_parameter_mapping_uses_all_nine_columns` |
| BR-LABD20-015..016 | `LABD20Loader._post_process` | `TestLoaderEndToEnd.test_runs_synthetic_dataset` |
| BR-LABD20-017 | `LABD20Loader._commit_environment` | `TestLoaderEndToEnd.test_runs_synthetic_dataset` |
| BR-LABD20-018 | `truncate_file` | `test_truncate_file_zeros_it` |
| BR-LABD20-019 | `LABD20Loader._fatal_rollback` | `TestLoaderEndToEnd.test_rollback_on_insert_failure` |
