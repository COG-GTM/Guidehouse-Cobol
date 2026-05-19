# Field-level data lineage — JV comment ingestion (LABD20) and JV control record (LABA05)

> **Label:** demo / prep — pending SME review.
> Derived artifact. Does not modify any file under `source/`.
> Companion to [`../MIGRATION-PLAN.md`](../MIGRATION-PLAN.md), [`../RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md), and [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).
> Every claim below is cited with `file:line` against the customer-supplied source under `source/` and table descriptions under `database/descriptions/`.
> Risk and assumption references: [Risk 2 — binary↔display JV-NUMBER conversion](../RISKS-AND-GAPS.md#risk-2--binarydisplay-jv-number-conversion), [Risk 5 — fixed-width record layout byte precision](../RISKS-AND-GAPS.md#risk-5--fixed-width-record-layout-byte-precision), [A-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-2--display-only-representation-of-jv-number-at-the-db-boundary), [A-4](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-4--canonical-tst123-comment-approver-width-is-14-bytes-per-fd).

---

## Scope

Two field-level lineage traces are produced:

- **Flow A — LABD20 daily ingest:** Fixed-width record `TST123-COMMENT-REC` (from external file `COMMENT`) is parsed, validated, deduplicated, and INSERTed into `JC_SUBMITTED_COMMENT_TBL`. A post-process step updates `JC_COUNT_TBL` for section `'MA'`.
- **Flow B — LABA05 fiscal-year reset + CONTROL-RECORD-TABLE-IO CRUD:** `JV-CONTROL-REC` (EXTERNAL shared data with `JV-NUMBER PIC 9(006) USAGE BINARY`) is shuttled between an in-memory binary layout and a display-string layout for persistence to `CONTROL_RECORD_TABLE.CONTROL_RECORD_DATA` (CHAR(400)).

Out of scope here: SQL→DMS error-code translation, error-table writes to `JC_REJECTED_COMMENT_TBL` (these live in the dependency map / requirements artifacts).

---

## Flow A — `TST123-COMMENT-REC` → `JC_SUBMITTED_COMMENT_TBL`

### A.0 Source record layout (FD; line-sequential external file `COMMENT`)

File selection — `source/procobol/LABD20.pco:32-33`:

```
SELECT COMMENT-FILE ASSIGN TO EXTERNAL COMMENT
              ORGANIZATION IS LINE SEQUENTIAL.
```

Record layout — `source/procobol/LABD20.pco:43-55`:

| Offset (bytes) | Length | COBOL field | PIC clause | Source citation |
| ---: | ---: | --- | --- | --- |
|   0 |  26 | `TST123-LOAN-DT-NR`             (group; redefined below) | `PIC X(026)` | `source/procobol/LABD20.pco:44` |
|   0 |   8 | `TST123-COMMENT-DT`             (under redefine) | `PIC 9(008)` | `source/procobol/LABD20.pco:46` |
|   8 |   6 | `TST123-JV-NUMBER`              (under redefine) | `PIC 9(006)` | `source/procobol/LABD20.pco:47` |
|  14 |   2 | `TST123-SECTION-ID`             (under redefine) | `PIC 9(002)` | `source/procobol/LABD20.pco:48` |
|  16 |  10 | `TST123-LOAN-NUMBER`            (under redefine) | `PIC 9(010)` | `source/procobol/LABD20.pco:49` |
|  26 | 240 | `TST123-COMMENT-HIST`           (group)            | — | `source/procobol/LABD20.pco:50` |
|  26 |  10 | `TST123-SCHEDULE-DOC-NO`        (under `COMMENT-HIST`) | `PIC X(010)` | `source/procobol/LABD20.pco:51` |
|  36 | 230 | `TST123-COMMENT-TEXT`           (under `COMMENT-HIST`) | `PIC X(230)` | `source/procobol/LABD20.pco:52` |
| 266 |  20 | `TST123-COMMENT-REQUESTOR`                            | `PIC X(020)` | `source/procobol/LABD20.pco:53` |
| 286 |  14 | `TST123-COMMENT-APPROVER`       **(active FD; was 20)** | `PIC X(014)` | `source/procobol/LABD20.pco:55` (active); `source/procobol/LABD20.pco:54` (commented-out 20-byte predecessor) |

**Total active FD record length = 8 + 6 + 2 + 10 + 10 + 230 + 20 + 14 = 300 bytes.**

> **Width quirk (Risk 5 / A-4).** The active FD declares `TST123-COMMENT-APPROVER` at 14 bytes (`source/procobol/LABD20.pco:55`); the immediately preceding (commented) line declared 20 bytes (`source/procobol/LABD20.pco:54`); the working-storage scratch view at `source/procobol/LABD20.pco:141` defines `WS-TST123-COMMENT-APPROVER PIC X(020)`. Because `WS-TST123-COMMENT-REC` is a flat 300-byte image that allocates 20 bytes for approver, a `MOVE TST123-COMMENT-REC TO WS-TST123-COMMENT-REC` (`source/procobol/LABD20.pco:319`) copies only 14 bytes of approver from the FD area and leaves the remaining 6 bytes of `WS-TST123-COMMENT-APPROVER` as whatever was previously in working storage. The Oracle target column `JC_SUBMITTED_COMMENT_APPROVER` is `CHAR(20)` (`database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:10`), and `:WS-TST123-COMMENT-APPROVER` is the bind variable on INSERT (`source/procobol/LABD20.pco:368`), so the persisted column receives all 20 bytes of the working-storage view — including the trailing 6-byte slice. This is recorded as [Risk 5](../RISKS-AND-GAPS.md#risk-5--fixed-width-record-layout-byte-precision) and as Assumption [A-4](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-4--canonical-tst123-comment-approver-width-is-14-bytes-per-fd); requires SME confirmation of canonical width and downstream truncation/pad expectation.

### A.1 Working-storage and host-variable staging

`WS-TST123-COMMENT-REC` is the host-variable image populated by `MOVE TST123-COMMENT-REC TO WS-TST123-COMMENT-REC` (`source/procobol/LABD20.pco:319`).

| Working-storage / host variable | PIC | Definition citation |
| --- | --- | --- |
| `WS-TST123-COMMENT-REC` (group, 300 bytes) | — | `source/procobol/LABD20.pco:137` |
| `WS-TST123-LOAN-DT-NR`        | `PIC X(026)` | `source/procobol/LABD20.pco:138` |
| `WS-TST123-COMMENT-HIST`      | `PIC X(240)` | `source/procobol/LABD20.pco:139` |
| `WS-TST123-COMMENT-REQUESTOR` | `PIC X(020)` | `source/procobol/LABD20.pco:140` |
| `WS-TST123-COMMENT-APPROVER`  | `PIC X(020)` | `source/procobol/LABD20.pco:141` |
| `WS-TST123-SCHEDULE-DOC-NO`   | `PIC X(010)` | `source/procobol/LABD20.pco:142` (populated separately by `MOVE TST123-SCHEDULE-DOC-NO TO WS-TST123-SCHEDULE-DOC-NO`, `source/procobol/LABD20.pco:320`) |
| `WS-CONTROL-NUM`              | `PIC X(8)`   | `source/procobol/LABD20.pco:162` |
| `WS-TST123-JV-NUMBER` (under `WS-CONTROL-NUMREDEF`) | `PIC 9(006)` | `source/procobol/LABD20.pco:164` (loaded at `source/procobol/LABD20.pco:343`) |
| `WS-TST123-SECTION-ID` (under `WS-CONTROL-NUMREDEF`) | `PIC 9(002)` | `source/procobol/LABD20.pco:165` (loaded at `source/procobol/LABD20.pco:344`) |
| `WS-CHECK-NUMBER`             | `PIC 9(12)`  | `source/procobol/LABD20.pco:147` (target of duplicate-check SELECT INTO; `source/procobol/LABD20.pco:327`) |
| `WS-JV-COUNTER`               | `PIC 9(12)`  | `source/procobol/LABD20.pco:149` (incremented at `source/procobol/LABD20.pco:345`) |
| `WS-PROCESS-DATE`             | `PIC 9(08)`  | `source/procobol/LABD20.pco:92` (recomposed at `source/procobol/LABD20.pco:229-232` from `WS-PARM-DATE`) |
| `WS-PARM-DATE`                | `PIC X(10)`  | `source/procobol/LABD20.pco:83` (loaded from `CARD-DATE`, `source/procobol/LABD20.pco:228`) |

Embedded SQL declaration boundaries: `EXEC SQL BEGIN DECLARE SECTION` at `source/procobol/LABD20.pco:124`, `EXEC SQL END DECLARE SECTION` at `source/procobol/LABD20.pco:144`. `WS-TST123-COMMENT-REC` and its sub-fields sit inside that declare section (`source/procobol/LABD20.pco:137-142`) and are therefore valid Pro*COBOL bind variables.

### A.2 Validations applied before INSERT

All validations live in `DETERMINE-COMMENT-DISPOSITION` (`source/procobol/LABD20.pco:259-314`). Any failure sets `WS-TST123-RECORD-FLAG = 1` (88-level `TST123-RECORD-IN-ERROR`, `source/procobol/LABD20.pco:179`) and the record is counted into `WS-TST123-RECS-ERR-CNT` / `WS-ERRORS-CNT` instead of inserted (`source/procobol/LABD20.pco:309-314`).

| Check | Rule | Citation |
| --- | --- | --- |
| Record-not-blank | `IF TST123-COMMENT-REC EQUAL SPACES` → error flag | `source/procobol/LABD20.pco:261-263` |
| Date numeric + valid calendar | `IF TST123-COMMENT-DT NUMERIC` then `MOVE TST123-COMMENT-DT TO FROM-CYMD-DT` / `PERFORM CHECK-CYMD-DT`; check `DATE-IS-VALID` | `source/procobol/LABD20.pco:265-274` (uses `FROM-CYMD-DT`, `CHECK-CYMD-DT`, `DATE-IS-VALID` from `COPY DATECONV-WS` at `source/procobol/LABD20.pco:182` — **copybook not supplied**, Risk 1 / A-1) |
| JV number numeric and > 0 | `IF (TST123-JV-NUMBER NUMERIC) AND (TST123-JV-NUMBER > 0)` | `source/procobol/LABD20.pco:276-281` |
| Section id numeric | `IF (TST123-SECTION-ID NUMERIC)` | `source/procobol/LABD20.pco:283-287` |
| Loan number numeric | `IF TST123-LOAN-NUMBER NUMERIC` | `source/procobol/LABD20.pco:289-293` |
| Schedule doc no | **Not edited** | `source/procobol/LABD20.pco:295` (`TST123-SCHEDULE-DOC-NO NOT CURRENTLY EDITED`) |
| Comment text not blank | `IF TST123-COMMENT-TEXT EQUAL SPACES` → error | `source/procobol/LABD20.pco:297-299` |
| Requestor not blank | `IF TST123-COMMENT-REQUESTOR EQUAL SPACES` → error | `source/procobol/LABD20.pco:301-303` |
| Approver not blank | `IF TST123-COMMENT-APPROVER EQUAL SPACES` → error | `source/procobol/LABD20.pco:305-307` |

Records that pass validation are then run through `DETERMINE-IF-DUPLICATE` (`source/procobol/LABD20.pco:317-339`), which SELECTs by composite key:

```sql
SELECT JC_SUBMITTED_NUMBER
  INTO :WS-CHECK-NUMBER
  FROM JC_SUBMITTED_COMMENT_TBL
 WHERE JC_SUBMITTED = :WS-TST123-LOAN-DT-NR
```

— `source/procobol/LABD20.pco:325-330`. `SQLCODE = 0` is logged as `'DUPLICATE ENTRY ' TST123-LOAN-DT-NR` (`source/procobol/LABD20.pco:335-336`); `SQLCODE = 100` triggers `PERFORM CREATE-COMMENT-RECORD` (`source/procobol/LABD20.pco:338-339`); any other SQLCODE jumps to `9999-ROLL-BACK` (`source/procobol/LABD20.pco:331-333`).

### A.3 INSERT host-variable → Oracle column lineage

The `INSERT INTO JC_SUBMITTED_COMMENT_TBL` statement spans `source/procobol/LABD20.pco:342-389`. Column list at `source/procobol/LABD20.pco:352-361`; VALUES list at `source/procobol/LABD20.pco:362-371`. Oracle types from `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:3-13` (PK note at line 17).

| # | Bind variable in VALUES | Source field(s) it carries | Bind citation | Target Oracle column | Oracle type | Column citation | Transformation |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 1 | `:WS-TST123-LOAN-DT-NR` | 26-byte composite `CCYYMMDD(8) + JV-NUMBER(6) + SECTION-ID(2) + LOAN-NUMBER(10)` from FD record | `source/procobol/LABD20.pco:363` | `JC_SUBMITTED` | `NOT NULL CHAR(26)` (PK) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:5,17` | Direct copy from FD `TST123-LOAN-DT-NR` (`source/procobol/LABD20.pco:44`) via `MOVE TST123-COMMENT-REC TO WS-TST123-COMMENT-REC` (`source/procobol/LABD20.pco:319`). The same value drives the duplicate-check `WHERE JC_SUBMITTED = :WS-TST123-LOAN-DT-NR` (`source/procobol/LABD20.pco:329`). Treated as A-9 (`ASSUMPTIONS-AND-PLACEHOLDERS.md`: 26-byte composite is the canonical PK). |
| 2 | `:WS-JV-COUNTER` | Running counter — number of accepted comments in this run | `source/procobol/LABD20.pco:364` | `JC_SUBMITTED_NUMBER` | `NOT NULL NUMBER` | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:6` | Set to 0 by `INITIALIZE WS-COUNTERS` (`source/procobol/LABD20.pco:237`); incremented `ADD 1 TO WS-JV-COUNTER` at `source/procobol/LABD20.pco:345` once duplicate-free. |
| 3 | `:WS-TST123-SCHEDULE-DOC-NO` | `TST123-SCHEDULE-DOC-NO` (10 bytes) | `source/procobol/LABD20.pco:365` | `JC_SUBMITTED_SCHED_DOC_NO` | `CHAR(10)` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:7` | Loaded via `MOVE TST123-SCHEDULE-DOC-NO TO WS-TST123-SCHEDULE-DOC-NO` (`source/procobol/LABD20.pco:320`). Source field defined at `source/procobol/LABD20.pco:51`. |
| 4 | `:WS-TST123-COMMENT-HIST` | 240-byte `COMMENT-HIST` group = `SCHEDULE-DOC-NO(10) + COMMENT-TEXT(230)` | `source/procobol/LABD20.pco:366` | `JC_SUBMITTED_COMMENT_HIST` | `CHAR(240)` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:8` | Direct copy from FD `TST123-COMMENT-HIST` (`source/procobol/LABD20.pco:50-52`) via working-storage image (`source/procobol/LABD20.pco:139`, `319`). Note the schedule-doc-number is therefore persisted twice — once in column 3 (`SCHED_DOC_NO`) and once as the leading 10 bytes of `COMMENT_HIST`. |
| 5 | `:WS-TST123-COMMENT-REQUESTOR` | 20-byte `TST123-COMMENT-REQUESTOR` | `source/procobol/LABD20.pco:367` | `JC_SUBMITTED_COMMENT_REQUESTOR` | `CHAR(20)` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:9` | Direct copy (`source/procobol/LABD20.pco:53`, `140`, `319`). |
| 6 | `:WS-TST123-COMMENT-APPROVER` | 20-byte working-storage view; underlying FD field is 14 bytes | `source/procobol/LABD20.pco:368` | `JC_SUBMITTED_COMMENT_APPROVER` | `CHAR(20)` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:10` | **Width quirk — see Risk 5 / A-4.** Active FD is 14 bytes (`source/procobol/LABD20.pco:55`); WS host var is 20 bytes (`source/procobol/LABD20.pco:141`). The trailing 6 bytes of the bound 20-byte field are residual working-storage content rather than data sourced from the input record. Acts as an implicit right-pad with whatever previously occupied that slot — typically spaces after `INITIALIZE`, but not guaranteed across iterations. |
| 7 | `:WS-CONTROL-NUM` | 8-byte concatenation `JV-NUMBER(6) + SECTION-ID(2)` | `source/procobol/LABD20.pco:369` | `JC_SUBMITTED_CONTROL_NUM` | `CHAR(8)` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:11` | Built by `MOVE TST123-JV-NUMBER TO WS-TST123-JV-NUMBER` (`source/procobol/LABD20.pco:343`) and `MOVE TST123-SECTION-ID TO WS-TST123-SECTION-ID` (`source/procobol/LABD20.pco:344`), where `WS-TST123-JV-NUMBER` (6 digits) and `WS-TST123-SECTION-ID` (2 digits) are the sub-fields of `WS-CONTROL-NUMREDEF` over the 8-byte `WS-CONTROL-NUM` (`source/procobol/LABD20.pco:162-165`). |
| 8 | Literal `'LABD20'` | Program name | `source/procobol/LABD20.pco:370` | `JC_SUBMITTED_UPDT_PROG_ID` | `CHAR(6)` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:12` | Hard-coded literal in the INSERT. |
| 9 | `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` | 8-digit process date built from CARDFILE | `source/procobol/LABD20.pco:371` | `JC_SUBMITTED_UPDT_PROG_DT` | `DATE` (nullable) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:13` | `WS-PARM-DATE` read from CARDFILE (`source/procobol/LABD20.pco:225,228`), sliced via `FILLER REDEFINES WS-PARM-DATE` (`source/procobol/LABD20.pco:84-90`) into MM/DD/CC/YY components, recomposed as `CCYYMMDD` into `WS-PROCESS-DATE` (`source/procobol/LABD20.pco:92, 229-232`) via the `FILLER REDEFINES WS-PROCESS-DATE` sub-field layout (`source/procobol/LABD20.pco:93-97`), then converted by Oracle `TO_DATE(...,'YYYYMMDD')` at bind time. Assumption A-7. |

`CREATE_TIMESTAMP` and `LAST_UPDATE_TIMESTAMP` (`database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:14-15`) are not set by LABD20 — they default at the database side.

### A.4 Post-INSERT working-storage reset

After a successful INSERT, LABD20 zero-initializes all FD sub-fields and the working-storage staging view (`source/procobol/LABD20.pco:377-389`). Notably, `WS-CONTROL-NUM` is **not** in this initialize list — its value persists into the next record's INSERT — but its sub-fields are overwritten by the next iteration's `MOVE`s at `source/procobol/LABD20.pco:343-344`.

### A.5 Post-process counter update

`POST-PROCESS` (`source/procobol/LABD20.pco:392-405`) runs if `WS-JV-COUNTER > WS-JV-COUNTERS`:

```sql
UPDATE JC_COUNT_TBL
   SET JC_SECTION_COUNT = :WS-JV-COUNTER
 WHERE JC_SECTION = 'MA'
```

— `source/procobol/LABD20.pco:398-400`. The `'MA'` section is the only target; see Assumption [A-5](../ASSUMPTIONS-AND-PLACEHOLDERS.md). Note Assumption [A-6](../ASSUMPTIONS-AND-PLACEHOLDERS.md) — the `JC_COUNT_TBL` describe file lists the PK as `JC_SUBMITTED`, but that column does not exist on the table; the correct PK is `JC_SECTION`.

---

## Flow B — `JV-CONTROL-REC` → `CONTROL_RECORD_TABLE`

### B.0 In-memory `JV-CONTROL-REC` layout (EXTERNAL shared data)

`source/copybooks/JV-CONTROL-REC.cpy:1-9`:

| Offset | Length (display bytes) | Field | PIC | Citation |
| ---: | ---: | --- | --- | --- |
|  0 |  6 | `JV-CONTROL-1` | `PIC 9(006)` | `source/copybooks/JV-CONTROL-REC.cpy:2` |
|  6 |  6 | `JV-CONTROL-2` | `PIC 9(006)` | `source/copybooks/JV-CONTROL-REC.cpy:3` |
| 12 |  6 | `JV-CONTROL-3` | `PIC 9(006)` | `source/copybooks/JV-CONTROL-REC.cpy:4` |
| 18 |  6 | `JV-CONTROL-4` | `PIC 9(006)` | `source/copybooks/JV-CONTROL-REC.cpy:5` |
| 24 |  6 | `JV-NUMBER`    | `PIC 9(006) USAGE BINARY` | `source/copybooks/JV-CONTROL-REC.cpy:6` |
| 30 |  9 | `FILLER`       | `PIC X(009)` | `source/copybooks/JV-CONTROL-REC.cpy:7` |
| 39 |  6 | `JV-CONTROL-5` | `PIC 9(006)` | `source/copybooks/JV-CONTROL-REC.cpy:8` |
| 45 | 10 | `FILLER`       | `PIC X(010)` | `source/copybooks/JV-CONTROL-REC.cpy:9` |

Total declared "display" footprint = 6+6+6+6+6+9+6+10 = **55 bytes**, which matches the documented `Data Length` of `JV-CONTROL-REC` in the inline `CONTROL-RECORD-TABLE.cpy` table at `source/copybooks/CONTROL-RECORD-TABLE.cpy:10` (`*JV-CONTROL-REC            1           55        1`). The container row `CONTROL_RECORD_DATA` is `CHAR(400)` (`database/descriptions/describe CONTROL_RECORD_TABLE.txt:7`, `source/copybooks/CONTROL-RECORD-TABLE.cpy:27`), so the legacy on-disk image is "first 55 meaningful bytes + 345 trailing bytes of the CHAR(400) column". This is consistent with the sample data on `database/descriptions/describe CONTROL_RECORD_TABLE.txt:7` (`  000105                105376` — two spaces + 6 digits + 16 spaces + 6 digits).

> **Binary/display quirk (Risk 2 / A-2).** `JV-NUMBER` is declared `USAGE BINARY` (`source/copybooks/JV-CONTROL-REC.cpy:6`). In a 6-digit BINARY declaration, COBOL allocates a binary-integer storage (typically 4 bytes; the exact width depends on the compiler runtime). However, the persisted `CONTROL_RECORD_DATA` column is a plain CHAR(400) display string, so a binary-to-display conversion must happen on read and a display-to-binary conversion must happen on write. That conversion is the entire purpose of the `JV-CONTROL-REC-B` / `JV-CONTROL-REC-D` parallel layouts in `CONTROL-RECORD-TABLE-IO.pco` (next section). See [Risk 2](../RISKS-AND-GAPS.md#risk-2--binarydisplay-jv-number-conversion) and Assumption [A-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-2--display-only-representation-of-jv-number-at-the-db-boundary).

### B.1 Binary↔display conversion inside `CONTROL-RECORD-TABLE-IO`

`source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28`:

```
01  JV-CONTROL-REC-B.
    05  JV-PART-1-B               PIC X(024).
    05  JV-NUMBER-B               PIC 9(006) USAGE BINARY.
    05  JV-PART-2-B               PIC X(025).
01  JV-CONTROL-REC-D.
    05  JV-PART-1-D               PIC X(024).
    05  JV-NUMBER-D               PIC 9(006).
    05  JV-PART-2-D               PIC X(025).
```

Both layouts span the same 55-byte logical record split as `24 + (JV-NUMBER) + 25`, where `JV-PART-1` covers `JV-CONTROL-1..4` (4 × 6 = 24 bytes) and `JV-PART-2` covers `FILLER(9) + JV-CONTROL-5(6) + FILLER(10)` (= 25 bytes). The host-variable image of `CONTROL_RECORD_DATA` is `W-CONTROL-RECORD-DATA PIC X(400)` (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:114`), bound on every read/write (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:319-323, 339-347, 358-369, 377-381`).

**On write (`'INSERT'` / `'UPDATE'` → `1000-MOVE-DATA`, `source/procobol/CONTROL-RECORD-TABLE-IO.pco:252-267`):** when the caller's `CONTROL-RECORD-NAME = 'JV-CONTROL-REC'`, the module copies the caller's binary-form `CONTROL-RECORD-DATA` into `JV-CONTROL-REC-B`, then field-by-field moves `JV-NUMBER-B` (binary) to `JV-NUMBER-D` (display) — this is the **binary→display** conversion — then writes the resulting `JV-CONTROL-REC-D` into the bound host variable `W-CONTROL-RECORD-DATA` (lines 258-262). Any other record name does a plain `MOVE CONTROL-RECORD-DATA TO W-CONTROL-RECORD-DATA` (lines 263-265).

**On read (`'SELECT'` → `2000-MOVE-DATA-BACK`, `source/procobol/CONTROL-RECORD-TABLE-IO.pco:270-288`):** the FETCHed `W-CONTROL-RECORD-DATA` (display) is copied into `JV-CONTROL-REC-D`, then field-by-field moves to `JV-CONTROL-REC-B` — the **display→binary** conversion — and the binary image is written into the caller's EXTERNAL `CONTROL-RECORD-TABLE.CONTROL-RECORD-DATA` (lines 275-280).

Net effect for `JV-NUMBER`:

```
caller's in-memory binary  ──INSERT/UPDATE──▶  JV-NUMBER-B → JV-NUMBER-D ──▶ W-CONTROL-RECORD-DATA ──▶ Oracle CHAR(400) (display)
Oracle CHAR(400) (display) ──SELECT─────────▶ W-CONTROL-RECORD-DATA ──▶ JV-NUMBER-D → JV-NUMBER-B ──▶ caller's in-memory binary
```

This is the round trip [Risk 2](../RISKS-AND-GAPS.md#risk-2--binarydisplay-jv-number-conversion) flags. Modernized Python operates on the display layout only (Assumption [A-2](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-2--display-only-representation-of-jv-number-at-the-db-boundary)).

### B.2 Bind variables and Oracle columns

| Caller-side field (EXTERNAL `CONTROL-RECORD-TABLE`) | Host variable bound to SQL | Target column | Oracle type | Citation |
| --- | --- | --- | --- | --- |
| `CONTROL-RECORD-NAME` (`PIC X(30)`)   | `:W-CONTROL-RECORD-NAME` (`PIC X(030)`)   | `CONTROL_RECORD_NAME`   | `NOT NULL CHAR(30)` | `source/copybooks/CONTROL-RECORD-TABLE.cpy:25`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:112`; `database/descriptions/describe CONTROL_RECORD_TABLE.txt:5` |
| `CONTROL-RECORD-NUMBER` (`PIC 9(04)`) | `:W-CONTROL-RECORD-NUMBER` (`PIC 9(004)`) | `CONTROL_RECORD_NUMBER` | `NOT NULL NUMBER`   | `source/copybooks/CONTROL-RECORD-TABLE.cpy:26`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:113`; `database/descriptions/describe CONTROL_RECORD_TABLE.txt:6` |
| `CONTROL-RECORD-DATA` (`PIC X(400)`)  | `:W-CONTROL-RECORD-DATA` (`PIC X(400)`)   | `CONTROL_RECORD_DATA`   | `NOT NULL CHAR(400)` | `source/copybooks/CONTROL-RECORD-TABLE.cpy:27`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:114`; `database/descriptions/describe CONTROL_RECORD_TABLE.txt:7` |
| (ROWID, returned)                     | `:W-ROWID` (`PIC X(018)`)                 | Oracle pseudo `ROWID`   | `ROWID`              | `source/procobol/CONTROL-RECORD-TABLE-IO.pco:115`; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:346` (`RETURNING ROWID INTO :W-ROWID`) |

SQL statements where these binds appear:

- **INSERT** (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:336-351`): `INSERT INTO CONTROL_RECORD_TABLE (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER, CONTROL_RECORD_DATA) VALUES (:W-CONTROL-RECORD-NAME, :W-CONTROL-RECORD-NUMBER, :W-CONTROL-RECORD-DATA) RETURNING ROWID INTO :W-ROWID`. Display-form `JV-NUMBER` already populated by `1000-MOVE-DATA`.
- **UPDATE** (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:357-372`): `UPDATE CONTROL_RECORD_TABLE SET CONTROL_RECORD_NAME = :W-CONTROL-RECORD-NAME, CONTROL_RECORD_NUMBER = :W-CONTROL-RECORD-NUMBER, CONTROL_RECORD_DATA = :W-CONTROL-RECORD-DATA, LAST_UPDATE_TIMESTAMP = SYSDATE WHERE ROWID = :W-ROWID`. `LAST_UPDATE_TIMESTAMP` is set to `SYSDATE` by the database, not bound from COBOL.
- **DELETE** (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:377-384`): `DELETE FROM CONTROL_RECORD_TABLE WHERE ROWID = :W-ROWID`.
- **SELECT** (dynamic, built at runtime; `source/procobol/CONTROL-RECORD-TABLE-IO.pco:37-78, 297-308`): `SELECT CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER, CONTROL_RECORD_DATA, ROWID FROM CONTROL_RECORD_TABLE WHERE ...`; the `WHERE` clause is built from either ROWID (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:58-65`) or `CONTROL_RECORD_NAME = ... AND CONTROL_RECORD_NUMBER = ...` (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:66-78`) and FETCHed into `:W-CONTROL-RECORD-NAME, :W-CONTROL-RECORD-NUMBER, :W-CONTROL-RECORD-DATA, :W-ROWID` (`source/procobol/CONTROL-RECORD-TABLE-IO.pco:319-323`).

Primary key on the Oracle side: `CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER` (`database/descriptions/describe CONTROL_RECORD_TABLE.txt:11`).

### B.3 LABA05 caller-side usage of `JV-CONTROL-REC`

LABA05 (`source/cobol/LABA05.cbl`) copies in `JV-CONTROL-REC` (`source/cobol/LABA05.cbl:61`), CONNECTs through DBIO (`source/cobol/LABA05.cbl:69-85`), then `PERFORM FETCH-CTRL-REC` (`source/cobol/LABA05.cbl:91`) → DBIO → CONTROL-RECORD-TABLE-IO `SELECT`, after which the binary `JV-NUMBER` field is reset to 1 (`MOVE 1 TO JV-NUMBER` in the modify paragraph, see LABA05 procedure division and Risk 2) and re-persisted via `MODIFY-CTRL-REC` (`source/cobol/LABA05.cbl:93`) → DBIO → CONTROL-RECORD-TABLE-IO `UPDATE`. The binary↔display dance described in B.1 is therefore exercised on every fiscal-year reset.

---

## Catalog of REDEFINES used in this lineage

> Each entry: name, file:line, purpose, and which Flow it touches.

| # | REDEFINES | Citation | Purpose | Flow |
| ---: | --- | --- | --- | --- |
|  1 | `TST123-LOAN-DT-NR-REDEF REDEFINES TST123-LOAN-DT-NR` | `source/procobol/LABD20.pco:45-49` | Cuts the 26-byte FD field into typed sub-fields: `TST123-COMMENT-DT(8)`, `TST123-JV-NUMBER(6)`, `TST123-SECTION-ID(2)`, `TST123-LOAN-NUMBER(10)` — used for numeric/calendar validation; the 26-byte original is what binds to `JC_SUBMITTED`. | A |
|  2 | `FILLER REDEFINES WS-PARM-DATE` | `source/procobol/LABD20.pco:84-90` | Slices the 10-byte `MM/DD/CCYY` CARDFILE date into `WS-PARM-MM`, `WS-PARM-FILLER1`, `WS-PARM-DD`, `WS-PARM-FILLER2`, `WS-PARM-CC`, `WS-PARM-YY` for component-level access. | A |
|  3 | `FILLER REDEFINES WS-PROCESS-DATE` | `source/procobol/LABD20.pco:93-97` | Slices the 8-byte process date into `WS-PROCESS-DATE-CC`, `-YY`, `-MM`, `-DD` so the recomposition at `source/procobol/LABD20.pco:229-232` lands the right CCYYMMDD digits. | A |
|  4 | `WS-AMT-CONVR REDEFINES WS-AMT-CONV` | `source/procobol/LABD20.pco:107-109` | Strips the leading-sign byte of `WS-AMT-CONV` (a signed-separate amount) so `WS-AMT` exposes the unsigned digits and `WS-AMTS` exposes the sign byte. Reporting only; not on the INSERT path. | A (peripheral) |
|  5 | `WS-CNT-CONVR REDEFINES WS-CNT-CONV` | `source/procobol/LABD20.pco:112-114` | Same pattern as #4 for the 5-digit count `WS-CNT-CONV`; `WS-CNTS` is the sign byte, `WS-CNT` the digits. Reporting only. | A (peripheral) |
|  6 | `WS-CT-CNTX REDEFINES WS-CT-CNT` | `source/procobol/LABD20.pco:71-72` | Aliases the 5-digit `WS-CT-CNT` as a 5-byte alphanumeric so it can be edited / displayed without numeric formatting. | A (peripheral) |
|  7 | `WS-CONTROL-NUMREDEF REDEFINES WS-CONTROL-NUM` | `source/procobol/LABD20.pco:163-165` | Exposes the 8-byte `WS-CONTROL-NUM` (bound to `JC_SUBMITTED_CONTROL_NUM`) as `WS-TST123-JV-NUMBER(6) + WS-TST123-SECTION-ID(2)` so the two sources can be written independently (`MOVE`s at `source/procobol/LABD20.pco:343-344`) and the concatenated 8 bytes are bound at INSERT. | A |
|  8 | `WS-DOC-NUMERICX REDEFINES WS-DOC-NUMERIC` | `source/procobol/LABD20.pco:167-170` | Slices the 10-digit `WS-DOC-NUMERIC` into `WS-DOC-LEAD-DIGITS(2)`, `WS-DOC-JV-NUM(6)`, `WS-DOC-SEC-ID(2)`. Helper structure for document-number decomposition; not on the INSERT path. | A (peripheral) |
|  9 | `WS-SQL-LOAN-NBRREDEF REDEFINES WS-SQL-LOAN-NBR` | `source/procobol/LABD20.pco:172-174` | Splits a 10-digit `WS-SQL-LOAN-NBR` into `WS-SQL-LNR(8) + WS-SQL-CHKDIG(2)` (loan base + check digit). Not bound to a column directly. | A (peripheral) |
| 10 | `FILLER REDEFINES TBLKNR-EXIST-MES` | — **not present** in the supplied `LABD20.pco`. `TBLKNR-SAVED` (`source/procobol/LABD20.pco:78-81`) is defined but no `TBLKNR-EXIST-MES` field appears in the file. Flagged here only because the Phase 1B spec listed it as conditional. | n/a | n/a |

---

## Confidence and SME questions raised by this trace

- **Approver column width** — see Risk 5 / A-4. SME must confirm canonical width (14 vs 20) and whether downstream consumers depend on bytes beyond the 14-byte FD payload. Live in `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:10` (column is CHAR(20)).
- **`JV-NUMBER` binary/display conversion** — see Risk 2 / A-2. SME must confirm there are no other in-memory consumers of the binary form of `JV-CONTROL-REC` outside `CONTROL-RECORD-TABLE-IO`.
- **`DATECONV-WS` / `DATECONV-PD` copybooks** — referenced at `source/procobol/LABD20.pco:182` and `source/procobol/LABD20.pco:266-267` but not supplied. Exact `CHECK-CYMD-DT` behavior cannot be verified (Risk 1 / A-1).
- **`JC_COUNT_TBL` primary key discrepancy** — describe file claims `JC_SUBMITTED` is the PK, but that column does not exist on the table; PK is `JC_SECTION` (A-6).
- **`PERFORM PERFORM` typo** — `source/procobol/LABD20.pco:213` reads `PERFORM PERFORM CLOSE-SQL-ENVIRONMENT.`; flagged as Risk 11. Does not affect lineage but should be documented for SME.
