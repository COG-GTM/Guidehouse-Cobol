# Field-level data lineage

> **Status:** Demo output, pending SME review.
> See [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).

This document traces every meaningful field from input file → working storage
→ Oracle column, for the two programs in scope (`LABD20`, `LABA05`).

---

## 1. Input file → working storage → Oracle column (LABD20)

Input file: `COMMENT-FILE` (FD `TST123-COMMENT-REC` — `source/procobol/LABD20.pco:43-55`).
Record length: **300 bytes** fixed (no record separator written by COBOL —
the legacy file is line-sequential, so each record is delimited by a newline
in modern Unix views).

### TST123-COMMENT-REC layout (LABD20.pco:43-55)

| Bytes (1-based) | Bytes (0-based slice) | Field | PIC clause | Notes |
|-----------------|------------------------|-------|------------|-------|
| 1-26 | `[0:26]` | `TST123-LOAN-DT-NR` (composite — REDEFINES) | `PIC X(026)` | Concatenated key (date + JV + section + loan). Becomes `JC_SUBMITTED`. |
| 1-8 | `[0:8]`  | └ `TST123-COMMENT-DT` | `PIC 9(008)` | YYYYMMDD; validated by DATECONV (missing copybook). |
| 9-14 | `[8:14]` | └ `TST123-JV-NUMBER` | `PIC 9(006)` | Must be numeric AND > 0. |
| 15-16 | `[14:16]` | └ `TST123-SECTION-ID` | `PIC 9(002)` | Must be numeric. |
| 17-26 | `[16:26]` | └ `TST123-LOAN-NUMBER` | `PIC 9(010)` | Must be numeric. |
| 27-266 | `[26:266]` | `TST123-COMMENT-HIST` (composite) | — | 240 bytes. Becomes `JC_SUBMITTED_COMMENT_HIST`. |
| 27-36 | `[26:36]` | └ `TST123-SCHEDULE-DOC-NO` | `PIC X(010)` | Not edited (per LABD20.pco:294-296 comment). |
| 37-266 | `[36:266]` | └ `TST123-COMMENT-TEXT` | `PIC X(230)` | Must not be blank. |
| 267-286 | `[266:286]` | `TST123-COMMENT-REQUESTOR` | `PIC X(020)` | Must not be blank. |
| 287-300 | `[286:300]` | `TST123-COMMENT-APPROVER` | `PIC X(014)` | **14 bytes** per active LABD20.pco line 55. The commented line 54 (20 bytes) is **not** used. See RISKS Risk 5. |

### Lineage: input → working storage → bind variable → JC_SUBMITTED_COMMENT_TBL

| Input field (FD) | Working-storage host var | Bind into INSERT | Oracle column | Column type (per describe file) |
|------------------|--------------------------|-------------------|---------------|---------------------------------|
| `TST123-LOAN-DT-NR` (26) | `WS-TST123-LOAN-DT-NR` (LABD20.pco:319) | `:WS-TST123-LOAN-DT-NR` (line 363) | `JC_SUBMITTED` | CHAR(26) — PK |
| (derived in-batch counter) | `WS-JV-COUNTER` (line 345, 100-105) | `:WS-JV-COUNTER` (line 364) | `JC_SUBMITTED_NUMBER` | NUMBER |
| `TST123-SCHEDULE-DOC-NO` (10) | `WS-TST123-SCHEDULE-DOC-NO` (line 320) | `:WS-TST123-SCHEDULE-DOC-NO` (line 365) | `JC_SUBMITTED_SCHED_DOC_NO` | CHAR(10) |
| `TST123-COMMENT-HIST` (240) | `WS-TST123-COMMENT-HIST` (implied — same MOVE pattern as line 319) | `:WS-TST123-COMMENT-HIST` (line 366) | `JC_SUBMITTED_COMMENT_HIST` | CHAR(240) |
| `TST123-COMMENT-REQUESTOR` (20) | `WS-TST123-COMMENT-REQUESTOR` | `:WS-TST123-COMMENT-REQUESTOR` (line 367) | `JC_SUBMITTED_COMMENT_REQUESTOR` | CHAR(20) |
| `TST123-COMMENT-APPROVER` (14) | `WS-TST123-COMMENT-APPROVER` | `:WS-TST123-COMMENT-APPROVER` (line 368) | `JC_SUBMITTED_COMMENT_APPROVER` | CHAR(20 in describe file — see note below) |
| `TST123-JV-NUMBER` + `TST123-SECTION-ID` | `WS-CONTROL-NUM` (LABD20.pco:160-165 redefine) | `:WS-CONTROL-NUM` (line 369) | `JC_SUBMITTED_CONTROL_NUM` | CHAR(8) |
| literal `'LABD20'` | — | literal (line 370) | `JC_SUBMITTED_UPDT_PROG_ID` | CHAR(6) |
| `CARDFILE` MM/DD/CCYY | `WS-PROCESS-DATE` (YYYYMMDD) | `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` (line 371) | `JC_SUBMITTED_UPDT_PROG_DT` | DATE |

> **Note on APPROVER width mismatch:** The FD declares 14 bytes (active line 55),
> but the Oracle column `JC_SUBMITTED_COMMENT_APPROVER` is `CHAR(20)` per the
> describe file. Oracle right-pads with spaces; behavior preserved. Flag for SME
> review whether the deprecated 20-byte FD line (line 54) was intentionally
> shortened or whether downstream code expects 20.

### Lineage: card file → bind variable

| Card file slice (CARDFILE) | Working-storage field | Bind into INSERT |
|----------------------------|------------------------|-------------------|
| MM (bytes 1-2) | `WS-PARM-MM` → `WS-PROCESS-DATE-MM` | part of `WS-PROCESS-DATE` (YYYYMMDD) |
| DD (bytes 4-5) | `WS-PARM-DD` → `WS-PROCESS-DATE-DD` | part of `WS-PROCESS-DATE` |
| CCYY (bytes 7-10) | `WS-PARM-CC` + `WS-PARM-YY` → `WS-PROCESS-DATE-CC` + `WS-PROCESS-DATE-YY` | part of `WS-PROCESS-DATE` |

Sources: `LABD20.pco:224-234` (LABD20-HOUSE-KEEPING).

### Lineage: JC_COUNT_TBL

| Source value | Bind | Column | Where clause |
|--------------|------|--------|--------------|
| `WS-JV-COUNTER` | `:WS-JV-COUNTER` | `JC_SECTION_COUNT` (per LABD20) **OR** `JC_COUNT_NUM` (per describe file) — see RISKS-AND-GAPS.md Risk 8 | `WHERE JC_SECTION = 'MA'` (LABD20.pco:400) |

---

## 2. CONTROL_RECORD_TABLE blob lineage (LABA05)

CONTROL_RECORD_TABLE is a generic key/value blob holding "control" records.
Each row's `CONTROL_RECORD_DATA` is a 400-byte `CHAR(400)` blob whose internal
layout is defined externally by the using program (via `JV-CONTROL-REC.cpy`).

### CONTROL_RECORD_TABLE columns

| Column | Type | Notes |
|--------|------|-------|
| `CONTROL_RECORD_NAME` | CHAR(30) | PK; LABA05 always queries `'JV-CONTROL-REC'`. |
| `CONTROL_RECORD_NUMBER` | NUMBER(4) | PK; LABA05 always queries `1`. |
| `CONTROL_RECORD_DATA` | CHAR(400) | Opaque blob; layout below. |

Source: `database/descriptions/describe CONTROL_RECORD_TABLE.txt`.

### CONTROL_RECORD_DATA layout for `'JV-CONTROL-REC'`

Derived from `source/copybooks/JV-CONTROL-REC.cpy`:

| Bytes (1-based) | Slice (0-based) | Field | PIC clause | Notes |
|-----------------|------------------|-------|------------|-------|
| 1-6 | `[0:6]` | JV-CONTROL-1 | PIC X(006) | Generic control sub-field. |
| 7-12 | `[6:12]` | JV-CONTROL-2 | PIC X(006) | |
| 13-18 | `[12:18]` | JV-CONTROL-3 | PIC X(006) | |
| 19-24 | `[18:24]` | JV-CONTROL-4 | PIC X(006) | |
| 25-30 | `[24:30]` | **JV-NUMBER** | PIC 9(006) **USAGE BINARY** in legacy; **PIC 9(006)** display in modernized demo | LABA05 reads & resets this. |
| 31-39 | `[30:39]` | filler | PIC X(009) | |
| 40-45 | `[39:45]` | JV-CONTROL-5 | PIC X(006) | |
| 46-... | `[45:400]` | filler | — | Padding to 400 bytes. |

### Lineage: legacy binary ↔ display

The legacy program holds two views of the JV-CONTROL-REC blob:

- `JV-CONTROL-REC-B` — binary form, used when reading from / writing to Oracle.
  Source: `source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28`.
- `JV-CONTROL-REC-D` — display form, used in DISPLAY statements + program logic.
  Source: same file, derived REDEFINES.

Conversion path (legacy, source/procobol/CONTROL-RECORD-TABLE-IO.pco:257-266):

```
binary blob ── DB read ──► JV-CONTROL-REC-B ──MOVE──► JV-CONTROL-REC-D
                                                            │
                                                            ▼
                                                       LABA05 DISPLAY / MOVE
                                                            │
                                                            ▼
                                            JV-CONTROL-REC-D ──MOVE──► JV-CONTROL-REC-B ──► DB write
```

Modernized analog: `db_dispatcher.seed_control_record` builds the display
form; `laba05_reset._extract_jv_number` / `_replace_jv_number` carve the
JV-NUMBER slice in place. See RISKS Risk 2 for the placeholder note about
restoring `struct.unpack('>I', …)` for true binary parity.

---

## 3. REDEFINES inventory

Every REDEFINES in scope:

| File | Line | REDEFINES base | Variant |
|------|------|----------------|---------|
| `source/procobol/LABD20.pco` | 45 | `TST123-LOAN-DT-NR` | `TST123-LOAN-DT-NR-REDEF` with 8/6/2/10 numeric components. |
| `source/procobol/LABD20.pco` | (working-storage) | `WS-CONTROL-NUM` | breaks into JV-NUMBER + SECTION-ID (line 160-165). |
| `source/procobol/CONTROL-RECORD-TABLE-IO.pco` | 21-28 | `JV-CONTROL-REC-B` | `JV-CONTROL-REC-D` (display layer). |
| `source/copybooks/JV-CONTROL-REC.cpy` | (header) | — | Direct layout, no REDEFINES inside the copybook itself. |

All REDEFINES are preserved by structural means in the modernized code:
- Composite `loan_dt_nr` is exposed as a property on `CommentRecord`.
- `WS-CONTROL-NUM` is exposed as `CommentRecord.control_num`.
- `JV-CONTROL-REC-B` / `-D` are unified into a single Python `str` view because
  the demo's display form is what we read from the mock DB. **Placeholder**
  for production: re-introduce a binary view via `struct`.

---

## 4. Missing lineage paths

These dataflows are visible in source but cannot be fully traced because
upstream/downstream code was not supplied:

| Dataflow | Why missing | Inference |
|----------|-------------|-----------|
| Input → `JC_REJECTED_COMMENT_TBL` | LABD20 only reads `COUNT(*)` from this table (LABD20.pco:431-433); no INSERT visible. | Rejected-record persistence likely lives in another program; in scope for SME confirmation. |
| Input → `JC_APPLIED_COMMENT_TBL` | Same — only `COUNT(*)` (LABD20.pco:441-443). | Applied-record persistence likely lives in another program; in scope for SME confirmation. |
| `DATECONV-WS` / `DATECONV-PD` internal layouts | Copybooks not supplied. | Modernized stub uses `datetime`; exact byte layout not reproduced. |

See [`migration/analysis/dependency-map-detailed.md`](dependency-map-detailed.md)
for the full module-level dependency graph.
