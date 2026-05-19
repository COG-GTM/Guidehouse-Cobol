# Requirements with Citations — LABA05 + LABD20

> **Status:** demo / prep — pending SME review.
> Phase 1A output of the JV COBOL/Pro\*COBOL modernization walkthrough. Supersedes the baseline at `business-requirements/initial-requirements.md`.
> Every requirement below carries: (a) a source citation `file:line-range`, (b) a tag of **CONFIRMED FROM SOURCE**, **INFERRED**, or **UNRESOLVABLE WITHOUT MISSING COPYBOOKS**, and (c) a confidence rating **HIGH / MEDIUM / LOW**. Cross-links point to entries in [`../RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md) and [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).
>
> Read [`../MIGRATION-PLAN.md`](../MIGRATION-PLAN.md), [`../RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md), and [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md) first — they are the Phase 0 foundation this document is built on.

---

## Table of contents

- [Conventions](#conventions)
- [Section 1 — LABA05: Fiscal-year JV control-number reset](#section-1--laba05-fiscal-year-jv-control-number-reset)
- [Section 2 — LABD20: Daily JV comment-file ingestion](#section-2--labd20-daily-jv-comment-file-ingestion)
- [Section 3 — Cross-cutting infrastructure requirements](#section-3--cross-cutting-infrastructure-requirements)
- [Section 4 — Traceability summary](#section-4--traceability-summary)
- [Section 5 — Confidence calibration](#section-5--confidence-calibration)

---

## Conventions

### Tag definitions

| Tag | Meaning |
| --- | --- |
| **CONFIRMED FROM SOURCE** | The requirement is directly readable from the cited COBOL/Pro\*COBOL lines without inference. |
| **INFERRED** | The requirement is a reasonable interpretation of the source but combines multiple paragraphs, depends on legacy COBOL semantics that are not literally in the cited lines, or fills in an unstated default. |
| **UNRESOLVABLE WITHOUT MISSING COPYBOOKS** | The exact behavior cannot be confirmed without source artifacts that were NOT supplied with this repository — primarily `DATECONV-WS` / `DATECONV-PD` (see [Risk 1](../RISKS-AND-GAPS.md#risk-1--missing-dateconv-ws-and-dateconv-pd-copybooks)). |

### Confidence ratings

| Confidence | Meaning |
| --- | --- |
| **HIGH** | Direct citation, no missing dependencies, no ambiguity in COBOL semantics. |
| **MEDIUM** | Source clearly supports the requirement but legacy data-format, dispatch, or dead-code considerations introduce some ambiguity. |
| **LOW** | Source is ambiguous, conflicting (e.g. live code vs. commented-out code), or depends on missing copybooks. |

### Citation style

`source/procobol/LABD20.pco:43-55` references lines 43 through 55 (inclusive) of that file in the repository at the commit this branch was created from. Each requirement may carry multiple citations.

---

## Section 1 — LABA05: Fiscal-year JV control-number reset

LABA05 is a once-per-fiscal-year batch program (`source/cobol/LABA05.cbl`, 285 lines, AUTHOR: Daniel Lee, DATE-WRITTEN: Jan 4 1995). It resets the in-memory and persisted `JV-NUMBER` field of the externally-shared `JV-CONTROL-REC` to `1`. It does **not** read, validate, or insert comment data — that is LABD20's responsibility. LABA05 is dispatch-driven through `DBIO`, which transparently routes the `JV-CONTROL-REC` traffic through `CONTROL-RECORD-TABLE-IO`.

### BR-LABA05-001 — Connect to the Oracle database before any control-record work

**Statement:** The program shall establish an Oracle database connection through the `DBIO` dispatcher with `DB-FUNCTION = 'CONNECT'` as its first action in the procedure division. If the connect call returns `NOT DB-OK`, the program shall display `'*** PROGRAM ABORTED DUE TO CONNECT ERROR***'` and stop the run with `RETURN-CODE = 99`.

| Aspect | Value |
| --- | --- |
| Citation | `source/cobol/LABA05.cbl:69-85` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 3 — Credential files `/tst/.oralogin`, `/tst/.orapasswd`](../RISKS-AND-GAPS.md#risk-3--credential-files-tstoralogin-tstorapasswd) (via `DBIO.pco:33-38, 41-45, 161-175`), [Risk 4 — Dynamic dispatch in DBIO](../RISKS-AND-GAPS.md#risk-4--dynamic-subroutine-dispatch-in-dbio) |

**Notes.** The `'CONNECT'` command short-circuits the DBIO dispatch logic (`source/procobol/DBIO.pco:182-187`) — it does NOT walk the IO-module lookup. The connection itself is established earlier in `0000-MAIN` of DBIO at `source/procobol/DBIO.pco:161-175` using credential files `/tst/.oralogin` and `/tst/.orapasswd`. The modernized client must replace these credential files with a managed-secrets equivalent (see Risk 3).

### BR-LABA05-002 — Fetch the `JV-CONTROL-REC` control record via DBIO

**Statement:** The program shall fetch the `JV-CONTROL-REC` control record from `CONTROL_RECORD_TABLE` via the `DBIO` dispatcher using `DB-FUNCTION = 'SELECT'`, `DB-FUNCTION-TYPE = 'FETCH'`, and `DB-DMSREC-NAME = 'JV-CONTROL-REC'`. On success (`DB-OK`), the dispatcher's `DB-DATA` and `DB-ROWID` outputs are copied into the in-memory `JV-CONTROL-REC` and `287-ROWID` respectively.

| Aspect | Value |
| --- | --- |
| Citation | `source/cobol/LABA05.cbl:150-174`; `source/copybooks/JV-CONTROL-REC.cpy:1-12`; `source/procobol/DBIO.pco:203, 267-276`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:189-228` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 8 — EXTERNAL shared data items](../RISKS-AND-GAPS.md#risk-8--external-shared-data-items); [A-2 — Display-only DB representation of `JV-NUMBER`](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-2--display-only-representation-of-jv-number-at-the-db-boundary); [BR-XCUT-002](#br-xcut-002--binarydisplay-conversion-for-jv-number) |

**Notes.** The DBIO override at `source/procobol/DBIO.pco:267-276` (`1100-CHECK-CONTROL-RECORD`) is what makes `'JV-CONTROL-REC'` actually resolve to `CONTROL-RECORD-TABLE-IO` rather than the would-be `JV-CONTROL-REC-IO` that the generic name-builder at `DBIO.pco:228-260` would otherwise produce. This is the **only** statically-resolvable dispatch path through DBIO for the LABA05/LABD20 scope (see [A-3](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-3--static-dispatch-path-resolution-for-dbio)).

### BR-LABA05-003 — Abort on fetch failure

**Statement:** If the FETCH of `JV-CONTROL-REC` does not return a DMS-style `ERROR-NUM` of `'0000'`, the program shall (a) display `'Unable FETCH JV-CONTROL-REC'`, (b) set `RETURN-CODE` to `99`, and (c) `STOP RUN` without attempting any MODIFY.

| Aspect | Value |
| --- | --- |
| Citation | `source/cobol/LABA05.cbl:91-99`; `source/cobol/LABA05.cbl:171-174` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 6 — SQLCODE → DMS translation](../RISKS-AND-GAPS.md#risk-6--sqlcode--dms-style-return-code-translation); [BR-XCUT-001](#br-xcut-001--sqlcode--dms-style-return-code-translation) |

**Notes.** Callers of DBIO check the DMS-style 4-character `ERROR-NUM`, not the raw Oracle `SQLCODE`. The translation table at `source/procobol/DBIO.pco:374-398` is what maps `SQLCODE` to `DB-RTNCODE-DMS` (and thence to `ERROR-NUM` via the `DMCA-ERRFLDS` macro performed at `source/cobol/LABA05.cbl:80, 166, 195`). Any modernized client must preserve the **DMS-style** error-code surface its callers expect — see BR-XCUT-001.

### BR-LABA05-004 — Set `JV-NUMBER` to `1`

**Statement:** After a successful fetch, the program shall set the in-memory `JV-CONTROL-REC.JV-NUMBER` field to the numeric value `1` before issuing the persistence update. The `JV-NUMBER` field is declared `PIC 9(006) USAGE BINARY` inside the EXTERNAL copybook `JV-CONTROL-REC`.

| Aspect | Value |
| --- | --- |
| Citation | `source/cobol/LABA05.cbl:176-179`; `source/copybooks/JV-CONTROL-REC.cpy:6` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 2 — Binary↔display `JV-NUMBER` conversion](../RISKS-AND-GAPS.md#risk-2--binarydisplay-jv-number-conversion); [A-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-2--display-only-representation-of-jv-number-at-the-db-boundary); [BR-XCUT-002](#br-xcut-002--binarydisplay-conversion-for-jv-number) |

**Notes.** The in-memory representation of `JV-NUMBER` is `USAGE BINARY` (`JV-CONTROL-REC.cpy:6`), but the persisted value sits inside a `CHAR(400)` column (`database/descriptions/describe CONTROL_RECORD_TABLE.txt:5`). The binary-to-display shuttle is handled inside `CONTROL-RECORD-TABLE-IO` (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28, 257-266`); LABA05 itself only deals with the binary in-memory view. **Modernization choice (A-2):** the Python equivalent operates on the *display* layout of `CONTROL_RECORD_DATA` to avoid carrying a binary representation across the DB boundary.

### BR-LABA05-005 — Persist the reset via DBIO `'UPDATE' / 'MODIFY'`

**Statement:** After setting `JV-NUMBER = 1`, the program shall update the persisted control record by calling `DBIO` with `DB-FUNCTION = 'UPDATE'`, `DB-FUNCTION-TYPE = 'MODIFY'`, `DB-DMSREC-NAME = 'JV-CONTROL-REC'`, and `DB-DATA = JV-CONTROL-REC`. The dispatcher routes this call through `CONTROL-RECORD-TABLE-IO`'s `UPDATE-DB` path (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:232-234`).

| Aspect | Value |
| --- | --- |
| Citation | `source/cobol/LABA05.cbl:176-195`; `source/procobol/DBIO.pco:211-212` (`WHEN DB-UPDATE`); `source/procobol/CONTROL-RECORD-TABLE-IO.pco:232-234` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 4](../RISKS-AND-GAPS.md#risk-4--dynamic-subroutine-dispatch-in-dbio); [A-3](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-3--static-dispatch-path-resolution-for-dbio) |

**Notes.** Around the update, `LABA05` displays the control record before and after via `9999-DISPLAY-REC` (`source/cobol/LABA05.cbl:177, 178, 204`). The display routine is a debug breadcrumb, not a transactional requirement — the modernized loader should preserve it as a structured-log line (see [P-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#p-2--error-printer-output)).

### BR-LABA05-006 — Abort on update failure

**Statement:** If the persistence UPDATE returns a non-`'0000'` DMS-style `ERROR-NUM`, the program shall display `'ERROR ON MODIFY CONTROL REC'`, set `RETURN-CODE` to `99`, and `STOP RUN` without reporting success. On success, it shall display the post-update control record and then exit normally.

| Aspect | Value |
| --- | --- |
| Citation | `source/cobol/LABA05.cbl:196-205` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 7 — Rollback paths](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms); [BR-XCUT-001](#br-xcut-001--sqlcode--dms-style-return-code-translation) |

**Notes.** LABA05 does NOT issue an explicit `EXEC SQL ROLLBACK`. Its rollback path is **the absence of any subsequent action and an exit with `RETURN-CODE = 99`**; the implicit Oracle behavior of "uncommitted work is rolled back when the session disconnects" is what undoes the failed update. This is in contrast to LABD20, which explicitly funnels SQL errors through `9999-ROLL-BACK` (see [BR-LABD20-018](#br-labd20-018--explicit-rollback-on-sql-or-dms-error-9999-roll-back)).

---

## Section 2 — LABD20: Daily JV comment-file ingestion

LABD20 (`source/procobol/LABD20.pco`, 535 lines, AUTHOR: DIGICON, DATE-WRITTEN: Nov 8 1989) ingests a daily fixed-width comment file into `JC_SUBMITTED_COMMENT_TBL`. It runs through validation, duplicate detection, INSERT, count-table maintenance, COMMIT, end-of-job statistics, and explicit rollback on any SQL error. The control flow is: `LABD20-HOUSE-KEEPING` (lines 223-239) → `TST123-COMMENT-PROCESS` (lines 242-256) → `POST-PROCESS` (lines 392-405) → `CLOSE-SQL-ENVIRONMENT` (lines 408-446) → reporting block (lines 448-486) → file truncation (lines 215-218) → `STOP RUN`. Any SQL non-zero `SQLCODE` short-circuits to `9999-ROLL-BACK` (lines 489-529).

### BR-LABD20-001 — Connect to Oracle before any record processing

**Statement:** The program shall establish an Oracle connection through `DBIO` with `DB-FUNCTION = 'CONNECT'` as its first action. If the connect returns `NOT DB-OK`, the program shall display `'!!! ORACLE DATABASE ERROR!!! ' SQLERRMC` followed by `'*** PROGRAM ABORTED DUE TO CONNECT ERROR***'`, set `RETURN-CODE` to `99`, and stop the run.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:189-206` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 3](../RISKS-AND-GAPS.md#risk-3--credential-files-tstoralogin-tstorapasswd) |

### BR-LABD20-002 — Read process date from `CARDFILE` as `MM/DD/CCYY` and recompose as `CCYYMMDD`

**Statement:** During `LABD20-HOUSE-KEEPING`, the program shall open `CARDFILE` for input, read exactly one record into `CARD-FILE-REC.CARD-DATE` (PIC X(10), `source/procobol/LABD20.pco:58-60`), close the file, and recompose the date through the redefinition layout `WS-PARM-DATE` (`MM/DD/CCYY`, `source/procobol/LABD20.pco:83-90`) into the binary process date `WS-PROCESS-DATE` (`CCYYMMDD`, `source/procobol/LABD20.pco:92-97`). The process date is then displayed for the operator log.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:224-234`; layout context at `LABD20.pco:58-60, 83-97` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [A-7 — Process date is read once from CARDFILE](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-7--process-date-is-read-once-from-the-cardfile-in-mmddccyy-and-stored-as-ccyymmdd); used in BR-LABD20-014 |

**Notes.** Note the asymmetric layout: the input slot is `MM/DD/CCYY` but the working-storage canonical is `CCYYMMDD`. The recomposition order at `LABD20.pco:229-232` is `MM → DD → CC → YY` into `MM → DD → CC → YY` slots of `WS-PROCESS-DATE` — i.e. it puts the *century* before the *year* but after the *day*. The 8 digits land as `CCYYMMDD` only because of the field offsets in the redefinition at lines 92-97; if the redefinition is changed during modernization the move order has to change with it.

### BR-LABD20-003 — Read comment file as `LINE SEQUENTIAL` with fixed-width record `TST123-COMMENT-REC`

**Statement:** The program shall open `COMMENT-FILE` (`SELECT COMMENT-FILE ASSIGN TO EXTERNAL COMMENT ORGANIZATION IS LINE SEQUENTIAL`) for input and read records into `TST123-COMMENT-REC`. The on-disk record layout is:

| Field (FD) | Bytes | PIC | Offset (1-based) |
| --- | --- | --- | --- |
| `TST123-LOAN-DT-NR` | 26 | `X(026)` | 1-26 |
| ↳ `TST123-COMMENT-DT` (redef) | 8 | `9(008)` | 1-8 |
| ↳ `TST123-JV-NUMBER` (redef) | 6 | `9(006)` | 9-14 |
| ↳ `TST123-SECTION-ID` (redef) | 2 | `9(002)` | 15-16 |
| ↳ `TST123-LOAN-NUMBER` (redef) | 10 | `9(010)` | 17-26 |
| `TST123-SCHEDULE-DOC-NO` | 10 | `X(010)` | 27-36 |
| `TST123-COMMENT-TEXT` | 230 | `X(230)` | 37-266 |
| `TST123-COMMENT-REQUESTOR` | 20 | `X(020)` | 267-286 |
| `TST123-COMMENT-APPROVER` | 14 | `X(014)` | 287-300 |

Total on-disk record length = **300 bytes**.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:32-33` (SELECT), `LABD20.pco:40-55` (FD) |
| Tag | CONFIRMED FROM SOURCE (record layout); INFERRED (canonical approver width — see Quirk) |
| Confidence | HIGH for offsets 1-286; MEDIUM for the 287-300 approver slice (see Quirk) |
| Cross-links | [Risk 5 — Fixed-width record layout byte precision](../RISKS-AND-GAPS.md#risk-5--fixed-width-record-layout-byte-precision); [A-4 — Canonical approver width](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-4--canonical-tst123-comment-approver-width-is-14-bytes-per-fd) |

**Quirk — the 14 vs. 20 approver width.** At `source/procobol/LABD20.pco:54-55`, the FD has two consecutive lines for the approver field:
```
05  TST123-COMMENT-APPROVER        PIC X(020).               CHG-015   *commented out*
05  TST123-COMMENT-APPROVER        PIC X(014).               CHG-015   *active*
```
The *active* on-disk definition is **14 bytes**. The working-storage scratch view `WS-TST123-COMMENT-APPROVER` at `source/procobol/LABD20.pco:141` is *20 bytes*, and the target Oracle column `JC_SUBMITTED_COMMENT_APPROVER` is `CHAR(20)` (`database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt`). The modernized parser must use the **14-byte FD width on the input side** and right-pad to 20 bytes for the INSERT (see [A-4](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-4--canonical-tst123-comment-approver-width-is-14-bytes-per-fd)). SME confirmation requested on whether the comment-out at line 54 was an intentional shrink or a regression.

### BR-LABD20-004 — Reject blank-record rows

**Statement:** While iterating records, the program shall set the in-flight error flag `WS-TST123-RECORD-FLAG = 1` (i.e. `TST123-RECORD-IN-ERROR`) when the entire `TST123-COMMENT-REC` is `EQUAL SPACES`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:261-263`; flag definition at `LABD20.pco:178-179, 260` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

### BR-LABD20-005 — Validate `TST123-COMMENT-DT` is numeric **AND** a valid calendar date

**Statement:** For each record, the program shall test that `TST123-COMMENT-DT` is `NUMERIC` and additionally passes the `CHECK-CYMD-DT` procedure. The procedure is invoked by moving `TST123-COMMENT-DT` into the working-storage field `FROM-CYMD-DT` (defined in the missing copybook `DATECONV-WS`) and then performing `CHECK-CYMD-DT` (defined in the missing copybook `DATECONV-PD`). The post-condition flag `DATE-IS-VALID` controls the branch. If either `NUMERIC` or the calendar check fails, set `WS-TST123-RECORD-FLAG = 1`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:265-274`; missing copybook references at `LABD20.pco:182, 531` |
| Tag | UNRESOLVABLE WITHOUT MISSING COPYBOOKS (exact calendar semantics); CONFIRMED FROM SOURCE (the call-out itself) |
| Confidence | LOW (for the *exact* calendar semantics); HIGH (for "there is some calendar check") |
| Cross-links | [Risk 1 — Missing DATECONV-WS / DATECONV-PD](../RISKS-AND-GAPS.md#risk-1--missing-dateconv-ws-and-dateconv-pd-copybooks); [A-1](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-1--calendar-date-validation-stub-for-the-missing-dateconv-ws--dateconv-pd-copybooks); [U-1](../ASSUMPTIONS-AND-PLACEHOLDERS.md#c-items-that-remain-unresolvable-without-input-from-customer) |

**Notes.** The exact behavior of `CHECK-CYMD-DT` (leap-year window, lower-bound year, handling of `00000000`, etc.) cannot be reproduced byte-for-byte without the copybook. The modernized stub in `labd20_loader.py` is the *standard* `YYYYMMDD` calendar check (year ≥ 1900 and ≤ 2100, month 1-12, day 1-days-in-month with Feb-29 leap-year), explicitly marked `# PLACEHOLDER (Risk 1):` and `# SME-REVIEW:`.

### BR-LABD20-006 — Validate `TST123-JV-NUMBER` is numeric **AND** strictly greater than zero

**Statement:** For each record, the program shall test that `TST123-JV-NUMBER` is both `NUMERIC` and `> 0`. If either condition fails, set `WS-TST123-RECORD-FLAG = 1`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:276-281` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

**Notes.** This is the only field in the validation block that has both a type test and a value test. All other numeric fields are only tested for `NUMERIC`.

### BR-LABD20-007 — Validate `TST123-SECTION-ID` is numeric

**Statement:** For each record, the program shall test that `TST123-SECTION-ID` is `NUMERIC`. If not, set `WS-TST123-RECORD-FLAG = 1`. There is **no** value-range check on the section id at this layer.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:283-287` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

### BR-LABD20-008 — Validate `TST123-LOAN-NUMBER` is numeric

**Statement:** For each record, the program shall test that `TST123-LOAN-NUMBER` is `NUMERIC`. If not, set `WS-TST123-RECORD-FLAG = 1`. There is **no** check digit or modulus check on the loan number at this layer.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:289-293` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

### BR-LABD20-009 — Validate `TST123-COMMENT-TEXT` is non-blank

**Statement:** For each record, the program shall test that `TST123-COMMENT-TEXT` is **not** `EQUAL SPACES`. If it is all spaces, set `WS-TST123-RECORD-FLAG = 1`. `TST123-SCHEDULE-DOC-NO` is **NOT** edited at this layer (comment at `LABD20.pco:294-296`).

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:294-299` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

### BR-LABD20-010 — Validate `TST123-COMMENT-REQUESTOR` is non-blank

**Statement:** For each record, the program shall test that `TST123-COMMENT-REQUESTOR` is **not** `EQUAL SPACES`. If it is all spaces, set `WS-TST123-RECORD-FLAG = 1`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:301-303` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

### BR-LABD20-011 — Validate `TST123-COMMENT-APPROVER` is non-blank

**Statement:** For each record, the program shall test that `TST123-COMMENT-APPROVER` is **not** `EQUAL SPACES`. If it is all spaces, set `WS-TST123-RECORD-FLAG = 1`. Reminder: per the FD this field is 14 bytes (see BR-LABD20-003).

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:305-307` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH (test); MEDIUM (which byte range is being tested — see BR-LABD20-003) |

### BR-LABD20-012 — Validation-failure rollup

**Statement:** After the per-field validations, if `WS-TST123-RECORD-FLAG = 1` (`TST123-RECORD-IN-ERROR`), the program shall (a) add `1` to `WS-TST123-RECS-ERR-CNT`, (b) add `1` to `WS-ERRORS-CNT`, and (c) skip the duplicate-detection / insert paragraph entirely. Records that pass all validations proceed to `DETERMINE-IF-DUPLICATE`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:309-314` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |

**Notes.** A single record can fail multiple validations; the design only records that *some* validation failed, not which. This is preserved in the modernized loader's structured error report — see [P-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#p-2--error-printer-output) for the error-printer mapping.

### BR-LABD20-013 — Duplicate detection via SELECT against `JC_SUBMITTED_COMMENT_TBL`

**Statement:** For each validated record, the program shall test for an existing key in `JC_SUBMITTED_COMMENT_TBL` by selecting `JC_SUBMITTED_NUMBER` into `WS-CHECK-NUMBER` where `JC_SUBMITTED = :WS-TST123-LOAN-DT-NR`. The duplicate-detection key is the 26-byte composite `CCYYMMDD + JV-NUM(6) + SECTION(2) + LOAN(10)` — see [A-9](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-9--jc_submitted-is-the-26-byte-composite-of-ccyymmdd--jv-num6--section2--loan10). The post-select control flow is:

| SQLCODE | Branch |
| --- | --- |
| `0` (row found) | Display `'DUPLICATE ENTRY ' TST123-LOAN-DT-NR UPON PRINTER`; do **not** insert (`LABD20.pco:334-336`). |
| `100` (no row) | Perform `CREATE-COMMENT-RECORD` (`LABD20.pco:338-339`). |
| Any other value | `GO TO 9999-ROLL-BACK` (`LABD20.pco:331-333`). |

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:317-339`; SQL at lines 325-330; composite-key context at `LABD20.pco:44-49, 329` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [A-9](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-9--jc_submitted-is-the-26-byte-composite-of-ccyymmdd--jv-num6--section2--loan10); [BR-LABD20-018](#br-labd20-018--explicit-rollback-on-sql-or-dms-error-9999-roll-back) |

**Notes.** The `IF SQLCODE NOT = 0 AND 100` at `LABD20.pco:331` is COBOL "abbreviated comparison" syntax — it means `IF SQLCODE NOT = 0 AND SQLCODE NOT = 100`, NOT a bitwise/logical combination of literals. Translation must reproduce that semantic (modernized Python: `if sqlcode not in (0, 100):`).

### BR-LABD20-014 — Insert accepted comment into `JC_SUBMITTED_COMMENT_TBL`

**Statement:** For each non-duplicate validated record, the program shall (a) move `TST123-JV-NUMBER` into `WS-TST123-JV-NUMBER` and `TST123-SECTION-ID` into `WS-TST123-SECTION-ID` to compose `WS-CONTROL-NUM`, (b) add `1` to `WS-JV-COUNTER`, and (c) INSERT the row into `JC_SUBMITTED_COMMENT_TBL` with these nine columns and bind variables:

| Column | Bind / literal | Notes |
| --- | --- | --- |
| `JC_SUBMITTED` | `:WS-TST123-LOAN-DT-NR` | 26-byte composite — see [A-9](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-9--jc_submitted-is-the-26-byte-composite-of-ccyymmdd--jv-num6--section2--loan10) |
| `JC_SUBMITTED_NUMBER` | `:WS-JV-COUNTER` | Strictly-incrementing run counter |
| `JC_SUBMITTED_SCHED_DOC_NO` | `:WS-TST123-SCHEDULE-DOC-NO` | 10 bytes |
| `JC_SUBMITTED_COMMENT_HIST` | `:WS-TST123-COMMENT-HIST` | 240 bytes |
| `JC_SUBMITTED_COMMENT_REQUESTOR` | `:WS-TST123-COMMENT-REQUESTOR` | 20 bytes |
| `JC_SUBMITTED_COMMENT_APPROVER` | `:WS-TST123-COMMENT-APPROVER` | 20 bytes (right-padded from 14 — see [A-4](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-4--canonical-tst123-comment-approver-width-is-14-bytes-per-fd)) |
| `JC_SUBMITTED_CONTROL_NUM` | `:WS-CONTROL-NUM` | 8 bytes = `WS-TST123-JV-NUMBER` (6) + `WS-TST123-SECTION-ID` (2) |
| `JC_SUBMITTED_UPDT_PROG_ID` | literal `'LABD20'` | Program-name stamp |
| `JC_SUBMITTED_UPDT_PROG_DT` | `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` | Process date from `CARDFILE` |

After the INSERT the program initializes all the FD and working-storage scratch fields for the next iteration.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:342-389`; column types confirmed against `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms) (error branch); [BR-LABD20-018](#br-labd20-018--explicit-rollback-on-sql-or-dms-error-9999-roll-back) |

**Notes.** If `SQLCODE NOT = 0` the program does `GO TO 9999-ROLL-BACK` (`LABD20.pco:373-375`) — even on `SQLCODE = 100`, since 100 from an INSERT typically means a constraint conflict variant. Modernization must keep the **single rollback policy**: any SQL error funnels into one rollback paragraph.

### BR-LABD20-015 — Update `JC_COUNT_TBL` for section `'MA'`

**Statement:** During `POST-PROCESS`, if `WS-JV-COUNTER > WS-JV-COUNTERS` (i.e. this run inserted at least one record), the program shall update `JC_COUNT_TBL.JC_SECTION_COUNT = :WS-JV-COUNTER WHERE JC_SECTION = 'MA'`. Only section `'MA'` is updated.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:392-405` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [A-5 — Only section 'MA' is updated](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-5--section-ma-is-the-only-section-updated-by-post-process); [A-6 — `JC_COUNT_TBL` PK column](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-6--jc_count_tbl-primary-key-is-jc_section-not-jc_submitted-as-the-describe-file-claims) |

**Notes.** The `database/descriptions/describe JC_COUNT_TBL.txt` file at line 10 claims the primary key is `JC_SUBMITTED`, but no such column exists on `JC_COUNT_TBL` — the only NOT NULL column is `JC_SECTION`. Treat the describe file as having a typo (see [A-6](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-6--jc_count_tbl-primary-key-is-jc_section-not-jc_submitted-as-the-describe-file-claims)); SME action required to correct the describe artifact upstream.

### BR-LABD20-016 — Commit and capture end-of-job table counts

**Statement:** In `CLOSE-SQL-ENVIRONMENT`, the program shall (a) `EXEC SQL COMMIT WORK`, (b) `SELECT COUNT(*) INTO :WS-TOTAL-SUBMIT-END-CNT FROM JC_SUBMITTED_COMMENT_TBL`, (c) `SELECT COUNT(*) INTO :WS-TOTAL-REJECT-END-CNT FROM JC_REJECTED_COMMENT_TBL`, and (d) `SELECT COUNT(*) INTO :WS-TOTAL-APPLIED-END-CNT FROM JC_APPLIED_COMMENT_TBL`. Any non-zero `SQLCODE` on any of the four statements funnels to `9999-ROLL-BACK`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:408-446` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [BR-LABD20-018](#br-labd20-018--explicit-rollback-on-sql-or-dms-error-9999-roll-back) |

**Notes.** Routing a count-failure into a ROLLBACK is technically defensible (signals an environment issue), but it does mean that a perfectly valid load with a transient `SELECT COUNT(*)` failure would be rolled back after the COMMIT — i.e. the rollback could not actually undo the inserts, it would only signal failure. The modernized client should preserve the error-funneling but document that the inserts have already been committed by this point.

### BR-LABD20-017 — End-of-job reporting block

**Statement:** After the stats SELECTs, the program shall display the end-of-job report `' *** COMMENT PROCESSING ***'` followed by the prior and current counters and table stats. Output lines are routed `UPON PRINTER` and include:

- `PRIOR          ENDING COUNT: ` `WS-JV-COUNTERS`
- `THIS JOB       ENDING COUNT: ` `WS-JV-COUNTER`
- `TST123 COMMENTS       COUNT: ` `WS-JV-COMMENTS-CNT`
- `JV NET COMMENTS       COUNT: ` `WS-JVNET-COMMENTS-CNT`
- ` TOTAL COMMENTS ERROR COUNT: ` `WS-TST123-RECS-ERR-CNT`
- (blank line)
- ` ***  TABLE STATS       ***  `
- `PRIOR SUBMITTED TABLE COUNT: ` `WS-TOTAL-SUBMIT-BEG-CNT`
- `PRIOR  REJECTED TABLE COUNT: ` `WS-TOTAL-REJECT-BEG-CNT`
- `PRIOR  APPLIED  TABLE COUNT: ` `WS-TOTAL-APPLIED-BEG-CNT`
- `EOJ   SUBMITTED TABLE COUNT: ` `WS-TOTAL-SUBMIT-END-CNT`
- `EOJ    REJECTED TABLE COUNT: ` `WS-TOTAL-REJECT-END-CNT`
- `EOJ    APPLIED  TABLE COUNT: ` `WS-TOTAL-APPLIED-END-CNT`

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:448-486` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [P-2 — Error-printer output mapping](../ASSUMPTIONS-AND-PLACEHOLDERS.md#p-2--error-printer-output) |

**Notes.** `WS-DISP-NUM` is `PIC ZZZ,ZZZ,ZZZ,ZZZ,ZZ9-` (`LABD20.pco:175`) — i.e. up to 14 digits with thousands separators and a trailing minus-sign indicator. The modernized loader should reproduce the formatting in its log line so audit comparison stays simple.

**Gap noticed but not in scope of this phase.** The `*-BEG-CNT` counters (`WS-TOTAL-SUBMIT-BEG-CNT`, `WS-TOTAL-REJECT-BEG-CNT`, `WS-TOTAL-APPLIED-BEG-CNT`) are displayed but never populated by an `EXEC SQL SELECT COUNT(*)` in this program — they are always `0` unless populated elsewhere. SME action: confirm whether a prior CHG-version populated these or whether the report has always shown zeros. Flag this as a Phase 1B follow-up.

### BR-LABD20-018 — Explicit rollback on SQL or DMS error (`9999-ROLL-BACK`)

**Statement:** Any non-zero `SQLCODE` from the duplicate-check SELECT (BR-LABD20-013), the INSERT (BR-LABD20-014), the JC_COUNT_TBL UPDATE (BR-LABD20-015), the COMMIT or the three stats SELECTs (BR-LABD20-016) shall transfer control via `GO TO 9999-ROLL-BACK`. The paragraph at `LABD20.pco:489-529` shall (a) display `'LABD20 ABORTED'` and `'ROLL BACK'`, (b) display the failing paragraph name `WS-PARA-NAME`, (c) branch on `SQL-PROCESSING` vs. `DMS-PROCESSING` and display either SQL context (table, command, `SQLCODE`) or DMS context (record name, command, `ERROR-NUM`), (d) call `RDMS-ERR-RTN` (from copybook `RDMS-ERR-RTN`) on the SQL branch, (e) call `DBIO` with `DB-FUNCTION = 'ROLLBACK'`, `DB-FUNCTION-TYPE = 'DEPART'` to perform the actual rollback, and (f) set `RETURN-CODE = 99` and `STOP RUN`.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:331-333, 373-375, 402-404, 414-416, 424-426, 434-436, 444-446` (error checks); `LABD20.pco:489-529` (paragraph body); `LABD20.pco:193-197` (DBIO ROLLBACK path); copybook references at `LABD20.pco:531-532` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms); [BR-XCUT-001](#br-xcut-001--sqlcode--dms-style-return-code-translation) |

**Notes.** This is the **single rollback policy** referenced from every SQL error site. There is no per-record skip-and-continue path — *any* SQL error aborts the entire run.

### BR-LABD20-019 — Truncate the comment file after successful processing

**Statement:** After `CLOSE-SQL-ENVIRONMENT` returns and the reporting block has printed, the program shall (a) `CLOSE COMMENT-FILE`, then (b) `OPEN OUTPUT COMMENT-FILE` immediately followed by `CLOSE COMMENT-FILE`. This open-output-then-close pattern is the legacy idiom for truncating the input file to zero length so the same file cannot be reprocessed by a subsequent run.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:215-218` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [A-8 — Truncate pattern](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-8--comment-file-is-opened-output-and-immediately-closed-after-the-read-loop-to-truncate-it) |

**Notes.** This pattern only runs on the success path — if any SQL error has fired, control has already transferred to `9999-ROLL-BACK` (BR-LABD20-018) and `STOP RUN` is executed without truncation. So the legacy contract is "the input file is preserved on failure for re-run after correction; zeroed on success."

### BR-LABD20-020 — Typo: `PERFORM PERFORM CLOSE-SQL-ENVIRONMENT`

**Statement:** At `source/procobol/LABD20.pco:213`, the source contains the literal text `PERFORM PERFORM CLOSE-SQL-ENVIRONMENT.`. COBOL compilers typically treat this as `PERFORM CLOSE-SQL-ENVIRONMENT` (the leading `PERFORM` is consumed; the second is the actual verb), but the syntax is non-standard. Flag for SME confirmation that this is a known typo and is being silently accepted by the compiler in use.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/LABD20.pco:213` |
| Tag | INFERRED (semantic interpretation); CONFIRMED FROM SOURCE (typo presence) |
| Confidence | LOW (for the semantic interpretation) |
| Cross-links | [Risk 11 — `PERFORM PERFORM` typo](../RISKS-AND-GAPS.md#risk-11--perform-perform-typo) |

**Notes.** The modernized loader does not need to reproduce this; it is documentation-only.

---

## Section 3 — Cross-cutting infrastructure requirements

These requirements are referenced by both LABA05 and LABD20 and live in the shared infrastructure modules `DBIO.pco` and `CONTROL-RECORD-TABLE-IO.pco`.

### BR-XCUT-001 — SQLCODE → DMS-style return-code translation

**Statement:** All Oracle `SQLCODE` values returned to DMS-style callers (LABA05 is one) shall be translated to a 4-character DMS return code through the `5300-TRANSLATE-SQLCODE` paragraph:

| `SQLCODE` | `DB-RTNCODE-DMS` | Notes |
| --- | --- | --- |
| `0` | `'0000'` | Success |
| `100` | `'0013'` | "Not found"; **but** if `DB-SET-NAME > SPACES` AND `DB-FUNCTION-TYPE ≠ 'FETCH OWNER'`, override to `'0007'` |
| `-1` | `'0005'` | "Duplicate key" |
| `-8103` | `'0000'` (with audit display) | Oracle "object no longer exists"; treated as continue + log |
| OTHER | `'9999'` | Catch-all |

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/DBIO.pco:374-398` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 6](../RISKS-AND-GAPS.md#risk-6--sqlcode--dms-style-return-code-translation) |

**Notes.** The modernized `db_dispatcher.py` ports this table verbatim and includes unit tests for each branch. Modernization must preserve the DMS-style surface for any caller that does not directly check `SQLCODE`.

### BR-XCUT-002 — Binary↔display conversion for `JV-NUMBER`

**Statement:** `JV-CONTROL-REC.JV-NUMBER` is held in memory as `PIC 9(006) USAGE BINARY` (`source/copybooks/JV-CONTROL-REC.cpy:6`) but persisted as a slice of a `CHAR(400)` column. The `CONTROL-RECORD-TABLE-IO` module shuttles the field between two parallel record layouts:

- **`JV-CONTROL-REC-B`** (`CONTROL-RECORD-TABLE-IO.pco:21-24`) — binary view with `JV-NUMBER-B PIC 9(006) USAGE BINARY`.
- **`JV-CONTROL-REC-D`** (`CONTROL-RECORD-TABLE-IO.pco:25-28`) — display view with `JV-NUMBER-D PIC 9(006)`.

On INSERT/UPDATE the binary view is moved into the display view (`CONTROL-RECORD-TABLE-IO.pco:257-262`); on FETCH the display view is moved back into the binary view (`CONTROL-RECORD-TABLE-IO.pco:275-280`). Modernized clients must round-trip identically — operating on the display layout at the database boundary and the binary layout in any caller that depended on the binary form.

| Aspect | Value |
| --- | --- |
| Citation | `source/copybooks/JV-CONTROL-REC.cpy:6`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28, 252-288` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH (the shuttle is observable); MEDIUM (whether any non-CONTROL-RECORD-TABLE-IO caller reads the binary view is unknown — see [U-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#c-items-that-remain-unresolvable-without-input-from-customer)) |
| Cross-links | [Risk 2](../RISKS-AND-GAPS.md#risk-2--binarydisplay-jv-number-conversion); [A-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-2--display-only-representation-of-jv-number-at-the-db-boundary); BR-LABA05-004 |

**Notes.** The `JV-NUMBER-B` field declared as `PIC 9(006) USAGE BINARY` does **not** literally take 6 bytes on the wire — on most COBOL compilers `PIC 9(1..9) USAGE BINARY` occupies 4 bytes (32-bit). The `400-byte` `CONTROL_RECORD_DATA` slot is large enough either way; the modernization correctness condition is round-trip fidelity, not byte-count identity.

### BR-XCUT-003 — EXTERNAL shared-memory model

**Statement:** `JV-CONTROL-REC` (`source/copybooks/JV-CONTROL-REC.cpy:1`) and `CONTROL-RECORD-TABLE` (`source/copybooks/CONTROL-RECORD-TABLE.cpy:24`) are both declared `EXTERNAL`. They are shared memory between the calling program (LABA05) and the IO module (`CONTROL-RECORD-TABLE-IO`). Modernization must model this as a single shared in-memory object, not as an ad-hoc copy across a call boundary.

| Aspect | Value |
| --- | --- |
| Citation | `source/copybooks/JV-CONTROL-REC.cpy:1`; `source/copybooks/CONTROL-RECORD-TABLE.cpy:24` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 8](../RISKS-AND-GAPS.md#risk-8--external-shared-data-items) |

**Notes.** The Python conversion uses dataclasses passed by reference between the loader/reset functions and `db_dispatcher.py` to mirror these EXTERNAL semantics. SQL/Python conversion details are deferred to Phase 1C.

### BR-XCUT-004 — Dynamic IO-module dispatch in DBIO

**Statement:** Generic record names that are NOT the `'JV-CONTROL-REC'` override target shall be dispatched through the runtime-constructed `IO-SUBROUTINE` name, computed by walking the first 26 characters of `DB-DMSREC-NAME` for the substring `-REC` and then `STRING`ing either `-CYMD-IO` (when `DB-MISC = 'USE GREGORIAN'`) or `-IO` onto the prefix. `DB-DMSREC-NAME = 'JV-CONTROL-REC'` is special-cased earlier at `1100-CHECK-CONTROL-RECORD` to bypass this lookup entirely.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/DBIO.pco:228-260` (name-builder); `source/procobol/DBIO.pco:267-276` (`JV-CONTROL-REC` override) |
| Tag | CONFIRMED FROM SOURCE (mechanism); INFERRED (which IO modules actually exist) |
| Confidence | MEDIUM |
| Cross-links | [Risk 4](../RISKS-AND-GAPS.md#risk-4--dynamic-subroutine-dispatch-in-dbio); [A-3](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-3--static-dispatch-path-resolution-for-dbio); [U-3](../ASSUMPTIONS-AND-PLACEHOLDERS.md#c-items-that-remain-unresolvable-without-input-from-customer) |

**Notes.** For the LABA05/LABD20 scope, only one dispatch path actually flows through this lookup: the `'JV-CONTROL-REC' → CONTROL-RECORD-TABLE-IO` override. LABD20 does NOT use the IO-module dispatch — it uses embedded SQL directly (BR-LABD20-013 through BR-LABD20-016) and only goes through DBIO for `'CONNECT'` (BR-LABD20-001) and `'ROLLBACK'` (BR-LABD20-018). SME action: provide the production inventory of `*-IO` modules so any path beyond LABA05/LABD20 can be statically mapped.

### BR-XCUT-005 — Credential-file pattern for Oracle connect

**Statement:** The legacy `DBIO` module reads Oracle credentials from `/tst/.oralogin` and `/tst/.orapasswd`, concatenates `USERID/PASSWORD@SRVR` (where `SRVR` is read from environment variable `ORACLE_SID`), and issues `EXEC SQL CONNECT :USR-PWD-DBNAME`. Modernized clients shall NOT reproduce this pattern; they shall read credentials from a managed-secrets store (environment variables or a vault) and the modernized `db_dispatcher.py` accepts credentials at construction time only.

| Aspect | Value |
| --- | --- |
| Citation | `source/procobol/DBIO.pco:33-38, 41-45, 161-175` |
| Tag | CONFIRMED FROM SOURCE |
| Confidence | HIGH |
| Cross-links | [Risk 3](../RISKS-AND-GAPS.md#risk-3--credential-files-tstoralogin-tstorapasswd) |

---

## Section 4 — Traceability summary

### LABA05 traceability matrix

| Requirement | File:line(s) | Tag | Confidence |
| --- | --- | --- | --- |
| BR-LABA05-001 — Connect via DBIO | `source/cobol/LABA05.cbl:69-85` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABA05-002 — Fetch JV-CONTROL-REC | `LABA05.cbl:150-174`; `DBIO.pco:267-276`; `CONTROL-RECORD-TABLE-IO.pco:189-228` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABA05-003 — Abort on fetch fail | `LABA05.cbl:91-99, 171-174` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABA05-004 — Set JV-NUMBER = 1 | `LABA05.cbl:176-179`; `JV-CONTROL-REC.cpy:6` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABA05-005 — Persist via DBIO UPDATE/MODIFY | `LABA05.cbl:176-195`; `DBIO.pco:211-212`; `CONTROL-RECORD-TABLE-IO.pco:232-234` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABA05-006 — Abort on modify fail | `LABA05.cbl:196-205` | CONFIRMED FROM SOURCE | HIGH |

### LABD20 traceability matrix

| Requirement | File:line(s) | Tag | Confidence |
| --- | --- | --- | --- |
| BR-LABD20-001 — Connect via DBIO | `LABD20.pco:189-206` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-002 — Process date `MM/DD/CCYY` → `YYYYMMDD` | `LABD20.pco:224-234` (with layouts at 58-60, 83-97) | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-003 — Fixed-width parse TST123-COMMENT-REC | `LABD20.pco:32-33, 40-55` | CONFIRMED FROM SOURCE | HIGH/MEDIUM |
| BR-LABD20-004 — Reject blank record | `LABD20.pco:261-263` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-005 — Validate comment date numeric + CHECK-CYMD-DT | `LABD20.pco:265-274, 182, 531` | UNRESOLVABLE WITHOUT MISSING COPYBOOKS | LOW |
| BR-LABD20-006 — Validate JV-NUMBER numeric > 0 | `LABD20.pco:276-281` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-007 — Validate SECTION-ID numeric | `LABD20.pco:283-287` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-008 — Validate LOAN-NUMBER numeric | `LABD20.pco:289-293` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-009 — Reject blank comment text | `LABD20.pco:294-299` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-010 — Reject blank requestor | `LABD20.pco:301-303` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-011 — Reject blank approver | `LABD20.pco:305-307` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-012 — Validation rollup | `LABD20.pco:309-314` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-013 — Dupe-detect via SELECT JC_SUBMITTED | `LABD20.pco:317-339` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-014 — INSERT 9 columns into JC_SUBMITTED_COMMENT_TBL | `LABD20.pco:342-389` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-015 — UPDATE JC_COUNT_TBL section 'MA' | `LABD20.pco:392-405` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-016 — COMMIT + EOJ stats SELECTs | `LABD20.pco:408-446` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-017 — Reporting block | `LABD20.pco:448-486` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-018 — Rollback path 9999-ROLL-BACK | `LABD20.pco:489-529, 331, 373, 402, 414, 424, 434, 444` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-019 — Truncate comment file (OPEN OUTPUT / CLOSE) | `LABD20.pco:215-218` | CONFIRMED FROM SOURCE | HIGH |
| BR-LABD20-020 — `PERFORM PERFORM` typo | `LABD20.pco:213` | INFERRED | LOW |

### Cross-cutting traceability matrix

| Requirement | File:line(s) | Tag | Confidence |
| --- | --- | --- | --- |
| BR-XCUT-001 — SQLCODE → DMS translation | `DBIO.pco:374-398` | CONFIRMED FROM SOURCE | HIGH |
| BR-XCUT-002 — Binary↔display JV-NUMBER | `JV-CONTROL-REC.cpy:6`; `CONTROL-RECORD-TABLE-IO.pco:21-28, 252-288` | CONFIRMED FROM SOURCE | HIGH/MEDIUM |
| BR-XCUT-003 — EXTERNAL shared-memory model | `JV-CONTROL-REC.cpy:1`; `CONTROL-RECORD-TABLE.cpy:24` | CONFIRMED FROM SOURCE | HIGH |
| BR-XCUT-004 — Dynamic IO-module dispatch | `DBIO.pco:228-260, 267-276` | CONFIRMED FROM SOURCE (mechanism); INFERRED (production module set) | MEDIUM |
| BR-XCUT-005 — Credential-file pattern | `DBIO.pco:33-38, 41-45, 161-175` | CONFIRMED FROM SOURCE | HIGH |

---

## Section 5 — Confidence calibration

| Tier | Count | Examples |
| --- | --- | --- |
| **CONFIRMED FROM SOURCE, HIGH** | 23 | All LABA05 requirements; LABD20 validations, INSERT, COMMIT, reporting, rollback; XCUT-001/003/005 |
| **CONFIRMED FROM SOURCE, MEDIUM** | 3 | BR-LABD20-011 (approver byte-range), BR-XCUT-002 (binary↔display, unknown non-IO callers), BR-XCUT-004 (production IO-module inventory unknown) |
| **INFERRED, LOW** | 1 | BR-LABD20-020 (semantic interpretation of `PERFORM PERFORM`) |
| **UNRESOLVABLE WITHOUT MISSING COPYBOOKS, LOW** | 1 | BR-LABD20-005 (exact `CHECK-CYMD-DT` semantics — see [U-1](../ASSUMPTIONS-AND-PLACEHOLDERS.md#c-items-that-remain-unresolvable-without-input-from-customer)) |

**Overall confidence statement.** 23 of 28 requirements are HIGH-confidence CONFIRMED-FROM-SOURCE. The five non-HIGH entries are tracked against existing items in the risk register and assumptions log: every LOW/MEDIUM entry has an SME-action item already filed in [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md). No requirement is fabricated — every requirement carries a file:line citation against the customer-supplied source.

---

## Document footer

- **Phase:** 1A — Requirements extraction with citations and confidence levels.
- **Status:** demo / prep — pending SME review.
- **Supersedes:** `business-requirements/initial-requirements.md` (baseline, 30 lines, no citations).
- **Next phase (1B):** field-level lineage from `TST123-COMMENT-REC` → working-storage → `JC_*` columns, and dependency map covering the dynamic-dispatch and copybook graph.
- **Companion phase-0 docs:** [`../MIGRATION-PLAN.md`](../MIGRATION-PLAN.md), [`../RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md), [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).
