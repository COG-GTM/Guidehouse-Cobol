# LABA05 & LABD20 Business Requirements (With Source Citations)

> Demo / prep output — refined from `business-requirements/initial-requirements.md`.
> Each requirement cites the original COBOL/Pro\*COBOL source path and line range.
> Source files were not modified.

## Scope

This document refines the initial derived requirements for the two SBA Journal-Voucher
(JV) comment-processing programs supplied by Guidehouse:

- `LABA05` — fiscal-year reset of the JV control number (`source/cobol/LABA05.cbl`)
- `LABD20` — daily JV comment-file ingestion into Oracle tables
  (`source/procobol/LABD20.pco`)

The requirements cover input handling, validation, duplicate detection, inserts,
count updates, commit / rollback behavior, and end-of-job reporting.

A confidence/risk section at the end calls out the ~~**missing `DATECONV-WS` and
`DATECONV-PD` copybooks**, which limit the fidelity of legacy date-validation
behavior that the modernized system must preserve.~~

> **Resolved 2026-05-21 (customer follow-up shipment):** `DATECONV-WS`, `DATECONV-PD`, `DATECONV.cbl`, and the 4 JDN helpers were supplied. Risk 1 CLOSED, Assumption A-1 RETIRED, `BR-LABD20-006` confidence LOW → HIGH. Faithful Python port at `migration/converted-code/python/dateconv.py`; byte-for-byte GnuCOBOL parity at `migration/test-results/cobol-parity-report.html`.

## Citation Convention

Each row in the requirement tables includes a `Source` column with one or more
references of the form `path:line-start–line-end`. Line numbers are 1-indexed and
match the supplied source files at the time this document was generated.

---

## 1. LABA05 — Fiscal-Year JV Control-Number Reset

`LABA05` is a once-per-fiscal-year batch job that resets `JV-NUMBER` on the JV
control record to `1`. The legacy implementation accesses the control record via
the generic `DBIO` dispatcher, which maps to the Oracle-backed
`CONTROL_RECORD_TABLE`.

### 1.1 Functional Requirements

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABA05-001 | The system shall connect to the JV Oracle database before performing any control-record maintenance, using the standard `DBIO` `CONNECT` dispatch. | `source/cobol/LABA05.cbl:69–85` |
| BR-LABA05-002 | The system shall fetch the `JV-CONTROL-REC` row from `CONTROL_RECORD_TABLE` via `DBIO` with `DB-FUNCTION='SELECT'` / `DB-FUNCTION-TYPE='FETCH'` and `DB-DMSREC-NAME='JV-CONTROL-REC'`. | `source/cobol/LABA05.cbl:150–166`; `source/copybooks/JV-CONTROL-REC.cpy:1–9` |
| BR-LABA05-003 | If the fetch of the JV control record fails (any non-zero `ERROR-NUM`), the system shall display `Unable FETCH JV-CONTROL-REC`, set `RETURN-CODE = 99`, and `STOP RUN` without applying any update. | `source/cobol/LABA05.cbl:87–101` |
| BR-LABA05-004 | The system shall set `JV-NUMBER` on `JV-CONTROL-REC` to `1` as the fiscal-year reset action. | `source/cobol/LABA05.cbl:176–180`; `source/copybooks/JV-CONTROL-REC.cpy:6` |
| BR-LABA05-005 | The system shall persist the updated control record by calling `DBIO` with `DB-FUNCTION='UPDATE'` / `DB-FUNCTION-TYPE='MODIFY'` for `JV-CONTROL-REC`. | `source/cobol/LABA05.cbl:181–195` |
| BR-LABA05-006 | The system shall display the control record before and after modification (via the `9999-DISPLAY-REC` paragraph) for run-time audit visibility. | `source/cobol/LABA05.cbl:176–205` |
| BR-LABA05-007 | If the modify fails (any non-zero `ERROR-NUM`), the system shall display `ERROR ON MODIFY CONTROL REC`, set `RETURN-CODE = 99`, and `STOP RUN` (no claim of success on failure). | `source/cobol/LABA05.cbl:196–205` |
| BR-LABA05-008 | The system shall close/commit the database session via `DBIO` `COMMIT` / `DEPART` at end of job. | `source/cobol/LABA05.cbl:120–140` |
| BR-LABA05-009 | The `JV-NUMBER` field is stored as `PIC 9(006) USAGE BINARY` (COMP) in `JV-CONTROL-REC`; modernized code must preserve unsigned 6-digit semantics when reading/writing this value. | `source/copybooks/JV-CONTROL-REC.cpy:2–9` |

### 1.2 Out-of-Scope / Non-Behaviors For LABA05

- LABA05 does **not** validate the existing `JV-NUMBER`; the reset is unconditional
  once the fetch succeeds (`source/cobol/LABA05.cbl:176–180`).
- LABA05 does **not** read external files or take command-line parameters; the
  Perl wrapper (`source/perl/LABA05.pl`) only invokes the COBOL binary and
  captures `DISPLAY UPON PRINTER` output to a log.

---

## 2. LABD20 — Daily JV Comment-File Submission Load

`LABD20` is a daily Pro\*COBOL batch program that ingests a fixed-width
comment file (`TST123-COMMENT-REC`), validates each record, deduplicates against
`JC_SUBMITTED_COMMENT_TBL`, inserts accepted comments, updates the per-section
count in `JC_COUNT_TBL`, commits, and emits an end-of-job stats report. The
program ABENDs and rolls back on any unexpected SQL error.

### 2.1 Inputs

| Input | Description | Source |
| --- | --- | --- |
| `CARDFILE` (external) | One-record control file containing the process date in `MM/DD/CCYY` form (whitespace-padded). | `source/procobol/LABD20.pco:35–36, 57–60, 224–234`; `test-data/DAILY.MM-DD-CCYY.ctl` |
| `COMMENT` (external) | Line-sequential file of fixed-width JV comment records (`TST123-COMMENT-REC`, 330 bytes). | `source/procobol/LABD20.pco:32–33, 40–55, 239` |

### 2.2 `TST123-COMMENT-REC` Fixed-Width Layout

The comment-file record layout is defined in `source/procobol/LABD20.pco:43–55`.
Offsets are 1-indexed within a 330-byte record:

| Bytes | Field | PIC | Notes |
| --- | --- | --- | --- |
| 1–26 | `TST123-LOAN-DT-NR` (the 26-char submitted key) | `PIC X(026)` | Used as `WS-TST123-LOAN-DT-NR` host variable and matched against `JC_SUBMITTED` primary key. |
| 1–8 | `TST123-COMMENT-DT` (redefines bytes 1–8 of the key) | `PIC 9(008)` | CCYYMMDD process date for the comment. |
| 9–14 | `TST123-JV-NUMBER` (redefines bytes 9–14) | `PIC 9(006)` | JV number; must be numeric and `> 0`. |
| 15–16 | `TST123-SECTION-ID` (redefines bytes 15–16) | `PIC 9(002)` | Section id; must be numeric. |
| 17–26 | `TST123-LOAN-NUMBER` (redefines bytes 17–26) | `PIC 9(010)` | Loan number; must be numeric. |
| 27–36 | `TST123-SCHEDULE-DOC-NO` | `PIC X(010)` | Schedule doc number; not validated. |
| 37–266 | `TST123-COMMENT-TEXT` | `PIC X(230)` | Comment body; must not be blank. |
| 267–286 | `TST123-COMMENT-REQUESTOR` | `PIC X(020)` | Requestor identifier; must not be blank. |
| 287–300 | `TST123-COMMENT-APPROVER` | `PIC X(014)` | Approver identifier; must not be blank. (Note: the in-record field is 14 bytes; an earlier commented-out definition shows the original 20-byte width, and host variable `WS-TST123-COMMENT-APPROVER` is `PIC X(020)`.) |

### 2.3 Functional Requirements

#### 2.3.1 Startup / Housekeeping

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-001 | The system shall connect to the Oracle database via `DBIO` `CONNECT` before any comment processing, and shall abort with `RETURN-CODE=99` on connect failure. | `source/procobol/LABD20.pco:186–206` |
| BR-LABD20-002 | The system shall read the daily process date from `CARDFILE` in `MM/DD/CCYY` form and reorder its components into `WS-PROCESS-DATE` in `CCYYMMDD` form for use in SQL bind variables. | `source/procobol/LABD20.pco:35–36, 57–60, 223–234`; `test-data/DAILY.MM-DD-CCYY.ctl` |
| BR-LABD20-003 | The system shall initialize `WS-COUNTERS` (record / duplicate / error / table-stats counters) to zero before processing. | `source/procobol/LABD20.pco:146–161, 237` |
| BR-LABD20-004 | The system shall open the `COMMENT` external file as `LINE SEQUENTIAL` input before reading records. | `source/procobol/LABD20.pco:32–33, 239` |

#### 2.3.2 Read Loop & Per-Record Validation

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-005 | The system shall iterate over the comment file until end-of-file (`WS-EOF-COMMENTS-FLAG='*'`), incrementing `WS-JV-COMMENTS-CNT` for each record read and routing each non-EOF record to `DETERMINE-COMMENT-DISPOSITION`. | `source/procobol/LABD20.pco:242–254` |
| BR-LABD20-006 | The system shall reject a record (set `WS-TST123-RECORD-FLAG=1` → `TST123-RECORD-IN-ERROR`) when the entire `TST123-COMMENT-REC` is blank. | `source/procobol/LABD20.pco:259–263` |
| BR-LABD20-007 | The system shall validate that `TST123-COMMENT-DT` is numeric **and** represents a valid calendar date via `CHECK-CYMD-DT` against `FROM-CYMD-DT`. Records failing either check shall be rejected. | `source/procobol/LABD20.pco:265–274` |
| BR-LABD20-008 | The system shall validate that `TST123-JV-NUMBER` is numeric and strictly greater than zero. | `source/procobol/LABD20.pco:276–281` |
| BR-LABD20-009 | The system shall validate that `TST123-SECTION-ID` is numeric. | `source/procobol/LABD20.pco:283–287` |
| BR-LABD20-010 | The system shall validate that `TST123-LOAN-NUMBER` is numeric. | `source/procobol/LABD20.pco:289–293` |
| BR-LABD20-011 | The system shall reject records where `TST123-COMMENT-TEXT` is all spaces. | `source/procobol/LABD20.pco:297–299` |
| BR-LABD20-012 | The system shall reject records where `TST123-COMMENT-REQUESTOR` is all spaces. | `source/procobol/LABD20.pco:301–303` |
| BR-LABD20-013 | The system shall reject records where `TST123-COMMENT-APPROVER` is all spaces. | `source/procobol/LABD20.pco:305–307` |
| BR-LABD20-014 | `TST123-SCHEDULE-DOC-NO` is intentionally **not** validated in legacy code; modernized code must preserve this behavior unless a new business rule is approved. | `source/procobol/LABD20.pco:294–296` |
| BR-LABD20-015 | When any validation fails, the system shall increment `WS-TST123-RECS-ERR-CNT` and `WS-ERRORS-CNT`, and shall **not** attempt the duplicate check or insert for that record. | `source/procobol/LABD20.pco:309–314` |

#### 2.3.3 Duplicate Detection

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-016 | For every record that passes validation, the system shall move the file-section record into working-storage host variables and then look up the 26-character submitted key in `JC_SUBMITTED_COMMENT_TBL`. | `source/procobol/LABD20.pco:317–330` |
| BR-LABD20-017 | The duplicate-check `SELECT` shall be `SELECT JC_SUBMITTED_NUMBER INTO :WS-CHECK-NUMBER FROM JC_SUBMITTED_COMMENT_TBL WHERE JC_SUBMITTED = :WS-TST123-LOAN-DT-NR`. | `source/procobol/LABD20.pco:325–330` |
| BR-LABD20-018 | If `SQLCODE = 0` (row found), the system shall treat the record as a duplicate, log `'DUPLICATE ENTRY '` followed by the 26-byte key to PRINTER, and **not** insert the record. | `source/procobol/LABD20.pco:334–337` |
| BR-LABD20-019 | If `SQLCODE = 100` (not found), the system shall proceed to `CREATE-COMMENT-RECORD`. | `source/procobol/LABD20.pco:337–339` |
| BR-LABD20-020 | If the duplicate-check `SELECT` returns any other `SQLCODE`, the system shall branch to `9999-ROLL-BACK` (abort + rollback path). | `source/procobol/LABD20.pco:331–333` |

#### 2.3.4 Insert

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-021 | The system shall increment `WS-JV-COUNTER` (the per-row submitted number) before each insert. | `source/procobol/LABD20.pco:343–345` |
| BR-LABD20-022 | The system shall insert non-duplicate records into `JC_SUBMITTED_COMMENT_TBL` with the following columns: `JC_SUBMITTED`, `JC_SUBMITTED_NUMBER`, `JC_SUBMITTED_SCHED_DOC_NO`, `JC_SUBMITTED_COMMENT_HIST`, `JC_SUBMITTED_COMMENT_REQUESTOR`, `JC_SUBMITTED_COMMENT_APPROVER`, `JC_SUBMITTED_CONTROL_NUM`, `JC_SUBMITTED_UPDT_PROG_ID`, `JC_SUBMITTED_UPDT_PROG_DT`. | `source/procobol/LABD20.pco:352–372`; `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt` |
| BR-LABD20-023 | The literal `'LABD20'` shall be written as `JC_SUBMITTED_UPDT_PROG_ID`, and `WS-PROCESS-DATE` (from the `CARDFILE`) shall be written as `JC_SUBMITTED_UPDT_PROG_DT` via `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')`. | `source/procobol/LABD20.pco:370–371` |
| BR-LABD20-024 | On any non-zero `SQLCODE` from the insert, the system shall branch to `9999-ROLL-BACK`. | `source/procobol/LABD20.pco:373–375` |
| BR-LABD20-025 | After a successful insert, the system shall `INITIALIZE` the FD and working-storage record fields used for the comment (`TST123-COMMENT-DT`, `TST123-JV-NUMBER`, …, `WS-TST123-COMMENT-APPROVER`) so they cannot bleed into the next record. | `source/procobol/LABD20.pco:376–389` |

#### 2.3.5 Section-Count Update

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-026 | After all comment records have been processed, the system shall update `JC_COUNT_TBL` only if `WS-JV-COUNTER > WS-JV-COUNTERS` (the prior persisted count). When the condition holds, the system shall `UPDATE JC_COUNT_TBL SET JC_SECTION_COUNT = :WS-JV-COUNTER WHERE JC_SECTION = 'MA'`. | `source/procobol/LABD20.pco:392–405`; `database/descriptions/describe JC_COUNT_TBL.txt` |
| BR-LABD20-027 | On any non-zero `SQLCODE` from the `JC_COUNT_TBL` update, the system shall branch to `9999-ROLL-BACK`. | `source/procobol/LABD20.pco:402–404` |

#### 2.3.6 Commit, End-of-Job Stats, and Reporting

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-028 | After successful processing, the system shall `COMMIT WORK` to make all inserts/updates durable. Any non-zero `SQLCODE` on the commit shall route to `9999-ROLL-BACK`. | `source/procobol/LABD20.pco:408–416` |
| BR-LABD20-029 | After the commit, the system shall capture end-of-job counts via three `SELECT COUNT(*)` queries against `JC_SUBMITTED_COMMENT_TBL`, `JC_REJECTED_COMMENT_TBL`, and `JC_APPLIED_COMMENT_TBL` into `WS-TOTAL-SUBMIT-END-CNT`, `WS-TOTAL-REJECT-END-CNT`, and `WS-TOTAL-APPLIED-END-CNT`. Any non-zero `SQLCODE` shall route to `9999-ROLL-BACK`. | `source/procobol/LABD20.pco:417–446` |
| BR-LABD20-030 | The system shall emit an end-of-job report (`DISPLAY ... UPON PRINTER`) containing: prior + this-job ending counters, total TST123 comments processed, JV-NET comments, total error count, and prior + EOJ counts for the submitted, rejected, and applied tables. | `source/procobol/LABD20.pco:448–486` |
| BR-LABD20-031 | The system shall close the `COMMENT-FILE`, briefly reopen it in `OUTPUT` mode (truncating it), and close it again before terminating the run. Modernization must preserve the truncation semantic if downstream tooling relies on it. | `source/procobol/LABD20.pco:213–220` |

#### 2.3.7 Error Handling & Rollback

| ID | Requirement | Source |
| --- | --- | --- |
| BR-LABD20-032 | The system shall maintain SQL diagnostic state (`WS-PARA-NAME`, `WS-TABLE-NAME`, `WS-COMMAND-NAME`, `WS-PROCESSING-SW`) updated immediately before each SQL operation so error reporting can identify the failing context. | `source/procobol/LABD20.pco:124–135, 321–324, 347–350, 394–397, 409–440` |
| BR-LABD20-033 | On any SQL error (any `SQLCODE` other than `0` or `100` for the duplicate-check `SELECT`, or any non-zero `SQLCODE` for inserts/updates/commit/stats), the system shall execute the `9999-ROLL-BACK` paragraph. | `source/procobol/LABD20.pco:331–333, 373–375, 402–404, 414–416, 424–426, 434–436, 444–446` |
| BR-LABD20-034 | `9999-ROLL-BACK` shall display `'LABD20 ABORTED'`, `'ROLL BACK'`, the failing paragraph name, the table name, the command, and `SQLCODE` to PRINTER; invoke `RDMS-ERR-RTN` (if SQL) or display the DMS error context (if DMS); call `DBIO` with `DB-FUNCTION='ROLLBACK'` / `DB-FUNCTION-TYPE='DEPART'`; set `RETURN-CODE=99`; and `STOP RUN`. | `source/procobol/LABD20.pco:489–529` |
| BR-LABD20-035 | The system shall preserve the legacy invariant that a rollback occurs in lieu of a partial commit; modernized code must use a single Oracle transaction spanning all per-batch inserts and the count update so the rollback semantic is equivalent. | `source/procobol/LABD20.pco:408–416, 489–529` |

### 2.4 Out-of-Scope / Non-Behaviors For LABD20

- LABD20 does **not** insert into `JC_REJECTED_COMMENT_TBL` for failed records; it
  only increments local counters. Rejected-table population is performed by a
  different program/process; LABD20 only `SELECT COUNT(*)`s it for the EOJ report
  (`source/procobol/LABD20.pco:431–433`).
- LABD20 does **not** insert into `JC_APPLIED_COMMENT_TBL`; per the program
  header, that table is populated by `LABD21` (`source/procobol/LABD20.pco:14–17`).
- LABD20 does **not** edit `TST123-SCHEDULE-DOC-NO`
  (`source/procobol/LABD20.pco:294–296`).

---

## 3. Confidence & Risk Items

### 3.1 ~~Missing~~ Copybooks: `DATECONV-WS` and `DATECONV-PD`

> **Resolved 2026-05-21 (customer follow-up shipment):** All copybooks listed below were supplied and ingested. The legacy date-validation behavior is now reproduced precisely via a faithful Python port (`migration/converted-code/python/dateconv.py`) with byte-for-byte GnuCOBOL runtime parity. Risk 1 CLOSED, Assumption A-1 RETIRED, `BR-LABD20-006` confidence LOW → HIGH. The pre-shipment claims below are preserved for the audit trail.

~~`LABD20.pco` references the date-conversion working-storage and procedure
copybooks that are **not present in the supplied zip**:~~

- ~~`COPY DATECONV-WS.` — `source/procobol/LABD20.pco:182`~~ — supplied 2026-05-21 (`source/copybooks/DATECONV-WS.cpy`)
- ~~`COPY DATECONV-PD.` — `source/procobol/LABD20.pco:531`~~ — supplied 2026-05-21 (`source/copybooks/DATECONV-PD.cpy`)

These copybooks define:

- `FROM-CYMD-DT` — the host variable that `TST123-COMMENT-DT` is moved into
  before validation (`source/procobol/LABD20.pco:266`).
- `CHECK-CYMD-DT` — the procedure that performs the calendar-date validation
  (`source/procobol/LABD20.pco:267`).
- `DATE-IS-VALID` — the level-88 / condition that the validation paragraph sets
  (`source/procobol/LABD20.pco:268`).

~~**Risk:** Without these copybooks, the exact legacy date-validation behavior
cannot be reproduced precisely. In particular:~~

- ~~Leap-year rules, valid month/day ranges, and any century-window assumptions
  used by `CHECK-CYMD-DT` are unknown.~~
- ~~Whether the legacy routine rejects `00000000`, `99999999`, or sentinel dates
  is unknown.~~
- ~~Whether the legacy routine treats years outside a specific range (e.g.
  `1900–2099`) as invalid is unknown.~~

> **Resolved 2026-05-21:** Leap-year rules, valid month/day ranges, century-window assumptions, sentinel-date handling, and year bounds are now fully specified in `source/copybooks/DATECONV-PD.cpy` and reproduced faithfully in `migration/converted-code/python/dateconv.py`. GnuCOBOL parity verifies byte-for-byte agreement across ~50 vectors.

~~**Confidence rating:** All other requirements above are **High confidence** —
they are derived directly from the supplied source. Date-validation behavior
(BR-LABD20-007) is **Medium confidence** until either the missing copybooks are
provided or a substitute calendar-date validator (e.g. `EXTRACT` /
`TO_DATE` round-trip in Oracle, or `datetime.date` in Python) is approved by a
Guidehouse / SBA SME.~~

> **Resolved 2026-05-21:** All requirements are now **High confidence**. The pre-shipment Medium-confidence rating on date validation no longer applies.

~~**Recommended next steps:**~~

1. ~~Request `DATECONV-WS.cpy` and `DATECONV-PD.cpy` from Guidehouse, or any
   equivalent date-validation source from the legacy environment.~~
2. ~~If unavailable, codify the validator behavior with the SME and document it
   alongside the modernized implementation as a "Reconstructed Legacy
   Behavior" appendix in the test plan.~~
3. ~~Until then, treat any per-record reject reason of "invalid date" as
   needing manual review during equivalence testing.~~

> **Resolved 2026-05-21:** All three next-steps items closed by the customer follow-up shipment.

### 3.2 Other Confidence Notes

- **`TST123-COMMENT-APPROVER` width discrepancy.** The active definition is 14
  bytes (`PIC X(014)`), while the commented-out original was 20 bytes
  (`source/procobol/LABD20.pco:54–55`), and the working-storage host variable
  `WS-TST123-COMMENT-APPROVER` is 20 bytes
  (`source/procobol/LABD20.pco:141`). Modernized field-width policy must be
  confirmed; the current behavior reads 14 bytes from the file and writes 20
  bytes (padded) into Oracle.
- **`JC_COUNT_TBL` primary key.** The supplied description shows
  `primary key uses JC_SUBMITTED`
  (`database/descriptions/describe JC_COUNT_TBL.txt`), but the only columns in
  the description are `JC_SECTION` and `JC_SECTION_COUNT`. This looks like a
  documentation copy/paste discrepancy from `JC_SUBMITTED_COMMENT_TBL`; the
  actual primary key is almost certainly `JC_SECTION`. Confirm with the DBA
  before modernizing.
- **`9999-ROLL-BACK` `END-IF` placement.** The legacy paragraph contains
  control flow (`source/procobol/LABD20.pco:489–529`) where the indentation of
  the final `END-IF` and the post-`END-IF` `INITIALIZE` / `MOVE 'ROLLBACK'`
  block suggests the rollback CALL may execute even on the SQL branch.
  Modernized code should treat rollback + abort as the universal terminal
  action regardless of which branch (`SQL-PROCESSING` vs `DMS-PROCESSING`)
  surfaced the error.
- **`DETERMINE-IF-DUPLICATE` else-without-end-if.** The legacy paragraph ends
  with `IF SQLCODE = 100 PERFORM CREATE-COMMENT-RECORD.`
  (`source/procobol/LABD20.pco:338–339`) without an explicit `END-IF`. Behavior
  is correct (period-terminated), but modernized refactors should make the
  nesting explicit to avoid regression.
- **`DBIO` connection credentials.** `DBIO.pco` reads Oracle username/password
  from external files `USRID` and `PASSWD`
  (`source/procobol/DBIO.pco:40–80`). Modernized code must replace this with a
  managed-secret / configuration mechanism (e.g., AWS Secrets Manager,
  HashiCorp Vault) and use parameterized connections.

---

## 4. Traceability To Original Requirements

The original `business-requirements/initial-requirements.md` BR-IDs are
preserved where they map 1:1; LABD20 has been expanded from 13 to 35 IDs to
cover validation per-field, duplicate vs. SQL-error paths, insert columns
specifically, count-update guard, EOJ stats, file truncation, and rollback
diagnostics. The original BR-LABD20-001 (process-date parse) becomes
BR-LABD20-002 here; the original BR-LABD20-013 (rollback) is now covered by
BR-LABD20-032 through BR-LABD20-035.
