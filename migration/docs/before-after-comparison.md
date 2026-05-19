# Before / after comparison

> **Status:** Demo output, pending SME review.
> Side-by-side mapping of each business requirement to (1) the COBOL/Pro\*COBOL
> source, (2) the modernized Python, and (3) the test that validates it.

For the full requirement definitions, see
[`business-requirements/requirements-with-citations.md`](../business-requirements/requirements-with-citations.md).

---

## LABA05 — fiscal-year reset

| ID | COBOL source (before) | Python analog (after) | Validating test |
|----|------------------------|------------------------|-----------------|
| BR-LABA05-001 | `source/cobol/LABA05.cbl:69-85` (CONNECT-RTN via DBIO) | `db_dispatcher.DBDispatcher.from_env` + `laba05_reset.run` (dispatcher injection) | `test_laba05_reset.TestReset.test_end_to_end_success` (happy connect path implicit) |
| BR-LABA05-002 | `source/cobol/LABA05.cbl:152-174` (FETCH-CTRL-REC) | `laba05_reset.run` — `dispatcher.fetch_one(SELECT_CONTROL_RECORD_SQL, …)` | `test_laba05_reset.TestReset.test_end_to_end_success` |
| BR-LABA05-003 | `source/cobol/LABA05.cbl:176-189` (DISPLAY of JV-NUMBER OF JV-CONTROL-REC) | `laba05_reset.run` — `ResetOutcome.before_jv_number` (returned for caller display) | `test_laba05_reset.TestReset.test_end_to_end_success` (verifies before=42, after=1) |
| BR-LABA05-004 | `source/cobol/LABA05.cbl:184-205` (MOVE 1 + UPDATE) | `laba05_reset._replace_jv_number` + `dispatcher.update(UPDATE_CONTROL_RECORD_SQL, …)` | `test_laba05_reset.TestJVNumberByteLayout.test_extract_replace_round_trip`, `TestReset.test_end_to_end_success` |
| BR-LABA05-005 | `source/cobol/LABA05.cbl` PROGRAM-EXIT + `source/procobol/DBIO.pco:374-398` (DMS translation) | `laba05_reset.run` — ROLLBACK + return code 99 path | `test_laba05_reset.TestReset.test_failure_returns_99` |
| BR-LABA05-006 | `source/copybooks/JV-CONTROL-REC.cpy` + `source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28, 257-266` | `laba05_reset._extract_jv_number` / `_replace_jv_number` (with `# PLACEHOLDER` for binary form) | `test_laba05_reset.TestBinaryDisplayConversion.test_round_trip_via_str` |

---

## LABD20 — daily comment loader

### Input + housekeeping

| ID | COBOL source | Python analog | Validating test |
|----|--------------|----------------|-----------------|
| BR-LABD20-001 | `source/procobol/LABD20.pco:224-234` (CARDFILE → WS-PROCESS-DATE) | `labd20_loader.read_process_date` | `test_labd20_loader.TestReadProcessDate.test_reshuffles_mmddccyy_to_yyyymmdd`, `test_synthetic_card_file_parses` |
| BR-LABD20-002 | `source/procobol/LABD20.pco:43-55` (FD layout) | `labd20_loader` byte slices + `parse_comment_record` | `test_labd20_loader.TestRecordLayout.test_record_length_is_300`, `test_byte_slices_match_pic_widths`, `test_approver_is_14_not_20` |
| BR-LABD20-003 | `source/procobol/LABD20.pco:44-49` (REDEFINES) + line 329 | `CommentRecord.loan_dt_nr` / `.submitted_key` properties | `test_labd20_loader.TestParseCommentRecord.test_submitted_key_is_first_26_bytes` |

### Validation rules

| ID | COBOL source | Python analog | Validating test |
|----|--------------|----------------|-----------------|
| BR-LABD20-004 | LABD20.pco:261-263 (blank record) | `labd20_loader.determine_disposition` (blank check) | `test_labd20_loader.TestValidationRules.test_blank_record_rejected` |
| BR-LABD20-005 | LABD20.pco:265, 272-274 (non-numeric date) | `determine_disposition` (`isdigit`) | `TestValidationRules.test_non_numeric_date_rejected` |
| BR-LABD20-006 | LABD20.pco:266-274 (PERFORM CHECK-CYMD-DT, **uses missing DATECONV-PD**) | `labd20_loader.check_cymd_dt` (**# PLACEHOLDER for DATECONV-PD**) | `TestCheckCYMD.test_valid_date`, `test_invalid_month`, `test_invalid_day`, `test_leap_day_valid`, `test_non_leap_feb29_invalid`, `TestValidationRules.test_invalid_calendar_date_rejected` |
| BR-LABD20-007 | LABD20.pco:276-281 (JV non-numeric or zero) | `determine_disposition` (`isdigit` + `int > 0`) | `TestValidationRules.test_jv_number_zero_rejected`, `test_jv_number_non_numeric_rejected` |
| BR-LABD20-008 | LABD20.pco:283-287 (section non-numeric) | `determine_disposition` | `TestValidationRules.test_non_numeric_section_rejected` |
| BR-LABD20-009 | LABD20.pco:289-293 (loan non-numeric) | `determine_disposition` | `TestValidationRules.test_non_numeric_loan_rejected` |
| BR-LABD20-010 | LABD20.pco:294-299 (comment text blank) | `determine_disposition` | `TestValidationRules.test_blank_comment_rejected` |
| BR-LABD20-011 | LABD20.pco:301-303 (requestor blank) | `determine_disposition` | `TestValidationRules.test_blank_requestor_rejected` |
| BR-LABD20-012 | LABD20.pco:305-307 (approver blank) | `determine_disposition` | `TestValidationRules.test_blank_approver_rejected` |

### Persistence + transaction

| ID | COBOL source | Python analog | Validating test |
|----|--------------|----------------|-----------------|
| BR-LABD20-013 | LABD20.pco:317-339 (DETERMINE-IF-DUPLICATE) | `LABD20Loader._handle_record` (SELECT-then-INSERT pattern) | `TestLoaderEndToEnd.test_duplicate_record_does_not_insert_twice` |
| BR-LABD20-014 | LABD20.pco:342-372 (CREATE-COMMENT-RECORD, 9 columns) | `LABD20Loader._insert` + `INSERT_COMMENT_SQL` | `TestLoaderEndToEnd.test_insert_parameter_mapping_uses_all_nine_columns` |
| BR-LABD20-015 | LABD20.pco:345 (WS-JV-COUNTER increment) | `LABD20Loader._handle_record` (`self._counter += 1`) | `TestLoaderEndToEnd.test_runs_synthetic_dataset` (counter implicit in 7 inserts) |
| BR-LABD20-016 | LABD20.pco:392-405 (POST-PROCESS UPDATE) | `LABD20Loader._post_process` | `TestLoaderEndToEnd.test_runs_synthetic_dataset` (verifies UPDATE issued) |
| BR-LABD20-017 | LABD20.pco:408-446 (CLOSE-SQL-ENVIRONMENT) | `LABD20Loader._commit_environment` + `_emit_stats` | `TestLoaderEndToEnd.test_runs_synthetic_dataset` (asserts stats values) |
| BR-LABD20-018 | LABD20.pco:215-218 (OPEN OUTPUT / CLOSE truncate) | `labd20_loader.truncate_file` | `test_truncate_file_zeros_it` |
| BR-LABD20-019 | LABD20.pco fall-through to 9999-ROLL-BACK | `LABD20Loader._fatal_rollback` | `TestLoaderEndToEnd.test_rollback_on_insert_failure` |

### Cross-cutting

| ID | COBOL source | Python analog | Validating test |
|----|--------------|----------------|-----------------|
| BR-LABD20-021 | LABD20.pco:160-165 + 359, 369 (WS-CONTROL-NUM redefine) | `CommentRecord.control_num` | `TestParseCommentRecord.test_control_num_concatenates_jv_and_section` |
| BR-LABD20-022 | LABD20.pco:50-52 + 366 (COMMENT-HIST composite) | `CommentRecord.comment_hist` | `TestParseCommentRecord.test_parses_each_field_at_correct_offset` |

---

## Cross-cutting (both programs) — error envelope

| ID | COBOL source | Python analog | Validating test |
|----|--------------|----------------|-----------------|
| SQLCODE→DMS translation | `source/procobol/DBIO.pco:374-398` | `db_dispatcher.translate_sqlcode` (5 cases + default) | `test_labd20_loader.TestSQLCodeTranslation` (6 cases) |
| Any SQL error → ROLLBACK + RC=99 | All `GO TO 9999-ROLL-BACK` paths + `RETURN-CODE 99` | `LABD20Loader._fatal_rollback`, `laba05_reset.run` return-99 branch | `TestLoaderEndToEnd.test_rollback_on_insert_failure`, `test_laba05_reset.TestReset.test_failure_returns_99` |

---

## Behavioral gaps (intentional differences)

| Item | Legacy behavior | Modernized behavior | Reason |
|------|------------------|----------------------|--------|
| Date validation precision | `CHECK-CYMD-DT` from `DATECONV-PD` (missing) — may include business-calendar gates beyond pure Gregorian. | Pure Gregorian-calendar check. | `DATECONV-PD` not supplied. Marked `# PLACEHOLDER` + Risk 1. |
| JV-NUMBER storage form | `USAGE BINARY` (6-byte binary) in production. | Display digits in demo. | Demo uses an in-memory mock; binary form re-introducible via `struct.unpack('>I', …)`. Marked `# PLACEHOLDER` + Risk 2. |
| Credentials | `/tst/.oralogin` + `/tst/.orapasswd` files. | `JV_DB_*` env vars resolved via managed secrets. | Risk 3 / federal security baseline. |
| DBIO dispatch | Runtime string concatenation. | Typed `DBDispatcher` class with named methods. | Risk 4 / static safety. |
| Approver field width | FD active line says 14 bytes; commented line says 20. | 14 bytes consistent with active FD line; column itself remains `CHAR(20)` in Oracle (right-padded). | Risk 5 — flag for SME confirmation. |
| Stats display | COBOL DISPLAY statements. | Python `LoaderStats.format_report` (text) + JSON via `demo_app --json`. | Same information, additional structured form. |

---

## Traceability matrix (compact)

| Layer | Count |
|-------|------:|
| BR-LABA05 requirements covered by Python | 6 / 6 |
| BR-LABA05 requirements covered by tests | 6 / 6 |
| BR-LABD20 requirements covered by Python | 19 / 22 (3 inferred/cross-cutting — see RISKS) |
| BR-LABD20 requirements covered by tests | 17 / 22 (4 are non-functional or environment-only) |
| Unresolvable BRs (missing artifacts) | 4 (all explicitly flagged) |
| Total passing tests | 52 |
