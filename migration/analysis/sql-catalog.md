# Embedded SQL Catalog — LABD20 / CONTROL-RECORD-TABLE-IO / DBIO

> **Label:** demo / prep — pending SME review.
> **Phase:** 1D (Migration walkthrough).
> **Companions:**
> [`../MIGRATION-PLAN.md`](../MIGRATION-PLAN.md) ·
> [`../RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md) ·
> [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).
>
> This catalog enumerates **every** `EXEC SQL` (and `EXEC ORACLE`) directive in the three supplied Pro*COBOL modules. For each statement we record: ID, source file + line range, statement type, tables touched, bind variables (`:host-var` form), transaction boundary, Oracle-specific constructs, and the error-handling pattern that follows the statement.
>
> The two control-flow / connection statements that LABD20 issues through the `DBIO` dispatcher (`CONNECT`, `ROLLBACK`) are also catalogued — they are not `EXEC SQL` lines inside LABD20 itself, but they are the SQL boundaries the modernized system must reproduce.
>
> Risks cross-referenced inline:
>
> - [Risk 6 — SQLCODE → DMS-style return-code translation](../RISKS-AND-GAPS.md#risk-6--sqlcode--dms-style-return-code-translation)
> - [Risk 7 — Transaction rollback paths (SQL and DMS)](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms)

---

## 0. Conventions

- **Statement IDs** use the prefix of the originating module: `SQL-LABD20-NNN`, `SQL-CRTIO-NNN` (CONTROL-RECORD-TABLE-IO), `SQL-DBIO-NNN`.
- **Bind variables** are the host variables that Pro*COBOL substitutes at execute time, written with the `:` prefix exactly as they appear in source.
- **Transaction boundary** — answers "does this single statement open, hold, commit, or roll back a transaction?" `EXEC SQL COMMIT` and `EXEC SQL ROLLBACK` are explicit boundaries; DML statements (INSERT/UPDATE/DELETE) extend the in-flight transaction without committing.
- **Oracle-specific** — calls out `TO_DATE`, `SYSDATE`, `ROWID`, `RETURNING INTO`, and the `EXEC ORACLE OPTION` directive that have no portable ANSI equivalent.
- **Error handling** — the lines immediately after the statement that check `SQLCODE` (or rely on the active `WHENEVER` handler) and branch to `9999-ROLL-BACK` / `9999-ERROR`.
- All citations are `path:line` against the supplied source tree.

---

## 1. Catalog summary table

| ID | File | Lines | Type | Tables | Bind vars / outputs | Txn boundary | Oracle-isms | Error handling |
|---|---|---|---|---|---|---|---|---|
| SQL-LABD20-001 | source/procobol/LABD20.pco | 124 | BEGIN DECLARE SECTION | — | — | — | Pro*COBOL declarative | — |
| SQL-LABD20-002 | source/procobol/LABD20.pco | 135 | INCLUDE SQLCA | — | — | — | Pro*COBOL declarative | — |
| SQL-LABD20-003 | source/procobol/LABD20.pco | 144 | END DECLARE SECTION | — | — | — | Pro*COBOL declarative | — |
| SQL-LABD20-004 | source/procobol/LABD20.pco | 325-330 | SELECT (duplicate check) | `JC_SUBMITTED_COMMENT_TBL` | in: `:WS-TST123-LOAN-DT-NR` · out: `:WS-CHECK-NUMBER` | extends txn | — | `IF SQLCODE NOT = 0 AND 100 GO TO 9999-ROLL-BACK` (331-333); `SQLCODE = 100` → fall through to INSERT |
| SQL-LABD20-005 | source/procobol/LABD20.pco | 352-372 | INSERT | `JC_SUBMITTED_COMMENT_TBL` | `:WS-TST123-LOAN-DT-NR`, `:WS-JV-COUNTER`, `:WS-TST123-SCHEDULE-DOC-NO`, `:WS-TST123-COMMENT-HIST`, `:WS-TST123-COMMENT-REQUESTOR`, `:WS-TST123-COMMENT-APPROVER`, `:WS-CONTROL-NUM`, literal `'LABD20'`, `:WS-PROCESS-DATE` (wrapped in `TO_DATE`) | extends txn | `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` at line 371 | `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (373-375) |
| SQL-LABD20-006 | source/procobol/LABD20.pco | 398-401 | UPDATE | `JC_COUNT_TBL` | in: `:WS-JV-COUNTER`; literal `WHERE JC_SECTION = 'MA'` | extends txn | — | `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (402-404) |
| SQL-LABD20-007 | source/procobol/LABD20.pco | 413 | COMMIT WORK | — | — | **COMMITS** all DML since the last COMMIT/CONNECT | — | `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (414-416) |
| SQL-LABD20-008 | source/procobol/LABD20.pco | 421-423 | SELECT (aggregate) | `JC_SUBMITTED_COMMENT_TBL` | out: `:WS-TOTAL-SUBMIT-END-CNT` | extends txn (post-COMMIT, new txn) | `COUNT(*)` | `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (424-426) |
| SQL-LABD20-009 | source/procobol/LABD20.pco | 431-433 | SELECT (aggregate) | `JC_REJECTED_COMMENT_TBL` | out: `:WS-TOTAL-REJECT-END-CNT` | extends txn | `COUNT(*)` | `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (434-436) |
| SQL-LABD20-010 | source/procobol/LABD20.pco | 441-443 | SELECT (aggregate) | `JC_APPLIED_COMMENT_TBL` | out: `:WS-TOTAL-APPLIED-END-CNT` | extends txn | `COUNT(*)` | `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (444-446) |
| SQL-LABD20-R | source/procobol/LABD20.pco | 489-529 | ROLLBACK (indirect, via DBIO) | — | — | **ROLLS BACK** via `CALL 'DBIO'` with `DB-FUNCTION = 'ROLLBACK'` (515, 518) → dispatches to SQL-DBIO-009 | — | Terminal error paragraph; `STOP RUN` with `RETURN-CODE = 99` (528-529) |
| SQL-CRTIO-001 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 100-102 | BEGIN DECLARE SECTION | — | — | — | Pro*COBOL declarative | — |
| SQL-CRTIO-002 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 117-119 | END DECLARE SECTION | — | — | — | Pro*COBOL declarative | — |
| SQL-CRTIO-003 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 121-123 | INCLUDE SQLCA | — | — | — | Pro*COBOL declarative | — |
| SQL-CRTIO-004 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 148-150 | WHENEVER NOT FOUND CONTINUE | — | — | — | Pro*COBOL handler | Installs module-wide policy |
| SQL-CRTIO-005 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 151-153 | WHENEVER SQLWARNING CONTINUE | — | — | — | Pro*COBOL handler | Installs module-wide policy |
| SQL-CRTIO-006 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 154-156 | WHENEVER SQLERROR GO TO 9999-ERROR | — | — | — | Pro*COBOL handler | Active handler for every following EXEC SQL in this module |
| SQL-CRTIO-007 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 161 | EXEC ORACLE OPTION (SELECT_ERROR=NO) | — | — | — | **Oracle-only** precompiler directive — suppress `SQLCODE ≠ 0` when a SELECT returns >1 row | Compiler-time directive |
| SQL-CRTIO-008 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 306 | PREPARE | dynamic — resolved to `CONTROL_RECORD_TABLE` | in: `:PREPARE-STRING` (the dynamically built SELECT/FROM/WHERE) | extends txn | Dynamic SQL Method 3 (PREPARE/DECLARE/OPEN/FETCH/CLOSE) | Falls through to `WHENEVER SQLERROR` → 9999-ERROR |
| SQL-CRTIO-009 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 307 | DECLARE CURSOR | dynamic | `C1` (named statement) | — | Dynamic SQL Method 3 | (handled by WHENEVER) |
| SQL-CRTIO-010 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 308 | OPEN CURSOR | dynamic | — | extends txn | Dynamic SQL Method 3 | (handled by WHENEVER) |
| SQL-CRTIO-011 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 318-324 | FETCH | `CONTROL_RECORD_TABLE` (via cursor `DB`) | out: `:W-CONTROL-RECORD-NAME`, `:W-CONTROL-RECORD-NUMBER`, `:W-CONTROL-RECORD-DATA`, `:W-ROWID` | extends txn | Returns `ROWID` value into host var | `MOVE SQLCODE TO DB-SQLCODE-NUM`; `IF SQLCODE = 100 PERFORM CLS-DB` (325-328) |
| SQL-CRTIO-012 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 337-347 | INSERT … RETURNING | `CONTROL_RECORD_TABLE` | in: `:W-CONTROL-RECORD-NAME`, `:W-CONTROL-RECORD-NUMBER`, `:W-CONTROL-RECORD-DATA` · out: `:W-ROWID` | extends txn | **`RETURNING ROWID INTO :W-ROWID`** | `MOVE SQLCODE TO DB-SQLCODE-NUM` (348); WHENEVER SQLERROR active |
| SQL-CRTIO-013 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 358-369 | UPDATE | `CONTROL_RECORD_TABLE` | in: `:W-CONTROL-RECORD-NAME`, `:W-CONTROL-RECORD-NUMBER`, `:W-CONTROL-RECORD-DATA`, `:W-ROWID` | extends txn | **`SYSDATE`** on `LAST_UPDATE_TIMESTAMP` (367); **`WHERE ROWID = :W-ROWID`** | `MOVE SQLCODE TO DB-SQLCODE-NUM` (370); WHENEVER SQLERROR active |
| SQL-CRTIO-014 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 378-381 | DELETE | `CONTROL_RECORD_TABLE` | in: `:W-ROWID` | extends txn | **`WHERE ROWID = :W-ROWID`** | `MOVE SQLCODE TO DB-SQLCODE-NUM` (382); WHENEVER SQLERROR active |
| SQL-CRTIO-015 | source/procobol/CONTROL-RECORD-TABLE-IO.pco | 391 | CLOSE CURSOR | `CONTROL_RECORD_TABLE` (via cursor `DB`) | — | extends txn | Dynamic SQL Method 3 | (handled by WHENEVER) |
| SQL-DBIO-001 | source/procobol/DBIO.pco | 52-54 | INCLUDE SQLCA | — | — | — | Pro*COBOL declarative | — |
| SQL-DBIO-002 | source/procobol/DBIO.pco | 57-59 | BEGIN DECLARE SECTION | — | — | — | Pro*COBOL declarative | — |
| SQL-DBIO-003 | source/procobol/DBIO.pco | 64-66 | END DECLARE SECTION | — | — | — | Pro*COBOL declarative | — |
| SQL-DBIO-004 | source/procobol/DBIO.pco | 147-149 | WHENEVER NOT FOUND CONTINUE | — | — | — | Pro*COBOL handler | Installs module-wide policy |
| SQL-DBIO-005 | source/procobol/DBIO.pco | 151-153 | WHENEVER SQLWARNING CONTINUE | — | — | — | Pro*COBOL handler | Installs module-wide policy |
| SQL-DBIO-006 | source/procobol/DBIO.pco | 155-157 | WHENEVER SQLERROR GO TO 9999-ERROR | — | — | — | Pro*COBOL handler | Active handler for every following EXEC SQL in this module |
| SQL-DBIO-007 | source/procobol/DBIO.pco | 171-173 | CONNECT | — | in: `:USR-PWD-DBNAME` (`user/pass@SID` built at 168-169 from `/tst/.oralogin` and `/tst/.orapasswd`) | **OPENS a session / starts txn** | Oracle Pro*COBOL syntax — `EXEC SQL CONNECT :user_password_dbname` | Falls through to WHENEVER SQLERROR → `9999-ERROR` (402-405); credential mechanism is [Risk 3](../RISKS-AND-GAPS.md#risk-3--credential-files-tstoralogin-tstorapasswd) |
| SQL-DBIO-008 | source/procobol/DBIO.pco | 189 | COMMIT WORK | — | — | **COMMITS** | — | `MOVE 0 TO DB-SQLCODE-NUM` then `PERFORM 5300-TRANSLATE-SQLCODE` (190-191); see [Risk 6](../RISKS-AND-GAPS.md#risk-6--sqlcode--dms-style-return-code-translation) |
| SQL-DBIO-009 | source/procobol/DBIO.pco | 194 | ROLLBACK | — | — | **ROLLS BACK** | — | `MOVE 0 TO DB-SQLCODE-NUM` then `PERFORM 5300-TRANSLATE-SQLCODE` (195-196) |
| SQL-DBIO-010 | source/procobol/DBIO.pco | 199 | COMMIT | — | — | **COMMITS** (`DB-DONE` path — explicit module-shutdown commit) | — | `MOVE 0 TO DB-SQLCODE-NUM` then `PERFORM 5300-TRANSLATE-SQLCODE` (200-201) |

---

## 2. Per-statement detail

### 2.1 LABD20.pco

#### SQL-LABD20-001 / -002 / -003 — Declare section & SQLCA

```pco
EXEC SQL BEGIN DECLARE SECTION END-EXEC.            *> LABD20.pco:124
...
EXEC SQL INCLUDE SQLCA END-EXEC.                    *> LABD20.pco:135
...
EXEC SQL END DECLARE SECTION END-EXEC.              *> LABD20.pco:144
```

- Declares the host variables that the embedded statements bind to. The bind set is: `WS-SQLCODE`, `WS-PROCESSING-SW`, `WS-PARA-NAME`, `WS-TABLE-NAME`, `WS-COMMAND-NAME`, `WS-NULL`, `WS-TST123-COMMENT-REC` (with subfields `WS-TST123-LOAN-DT-NR`, `WS-TST123-COMMENT-HIST`, `WS-TST123-COMMENT-REQUESTOR`, `WS-TST123-COMMENT-APPROVER`), `WS-TST123-SCHEDULE-DOC-NO`. (`LABD20.pco:126-142`).
- `SQLCA` is the standard SQL Communications Area struct that holds `SQLCODE`, `SQLERRMC`, etc. Pro*COBOL injects it into working storage.
- LABD20 also relies on `WS-PROCESS-DATE` (declared outside this section at `LABD20.pco:92`) being usable as a bind variable — this is the legacy Pro*COBOL "all working-storage 01-level items are usable as host vars" behavior. The modernized form should treat `WS-PROCESS-DATE` as an explicit bind parameter.

Modernized form (Python / `oracledb`): no analogue — bind parameters are declared inline at execute time (`cur.execute(sql, params)`); no separate declare section needed.

#### SQL-LABD20-004 — Duplicate-detect SELECT against JC_SUBMITTED_COMMENT_TBL

```pco
EXEC SQL                                            *> LABD20.pco:325
     SELECT JC_SUBMITTED_NUMBER
     INTO :WS-CHECK-NUMBER
     FROM JC_SUBMITTED_COMMENT_TBL
     WHERE JC_SUBMITTED = :WS-TST123-LOAN-DT-NR
END-EXEC.                                           *> LABD20.pco:330
```

- **Tables:** `JC_SUBMITTED_COMMENT_TBL` (primary key is `JC_SUBMITTED`; `LABD20.pco:323` and the table description `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt:5,17`).
- **Bind variables:** input `:WS-TST123-LOAN-DT-NR` (CHAR(26) — date+number composite from `WS-TST123-COMMENT-REC` subfield `LABD20.pco:138`). Output `:WS-CHECK-NUMBER` (PIC 9(12) `LABD20.pco:147`).
- **Transaction boundary:** none. Extends the current transaction.
- **Oracle-specific:** none used in this statement.
- **Error handling:**
  ```pco
  IF SQLCODE NOT = 0 AND 100                        *> LABD20.pco:331
     GO TO 9999-ROLL-BACK
  ```
  - `SQLCODE = 0` → duplicate found → DISPLAY warning, skip insert.
  - `SQLCODE = 100` (no rows found) → `PERFORM CREATE-COMMENT-RECORD` (LABD20.pco:339) → proceeds to SQL-LABD20-005.
  - Any other code → rollback. See [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms).

Modernized form (parameterized; see `migration/converted-code/sql/` once Phase 2 lands):

```sql
SELECT jc_submitted_number
  FROM jc_submitted_comment_tbl
 WHERE jc_submitted = :loan_dt_nr;
```

Application checks `cur.rowcount`: `1` → duplicate, `0` → insert. The "`SQLCODE` = 100" branch becomes a `cur.fetchone() is None` check.

#### SQL-LABD20-005 — Insert accepted comment

```pco
EXEC SQL INSERT INTO JC_SUBMITTED_COMMENT_TBL       *> LABD20.pco:352
         (JC_SUBMITTED,
          JC_SUBMITTED_NUMBER,
          JC_SUBMITTED_SCHED_DOC_NO,
          JC_SUBMITTED_COMMENT_HIST,
          JC_SUBMITTED_COMMENT_REQUESTOR,
          JC_SUBMITTED_COMMENT_APPROVER,
          JC_SUBMITTED_CONTROL_NUM,
          JC_SUBMITTED_UPDT_PROG_ID,
          JC_SUBMITTED_UPDT_PROG_DT)
 VALUES
         (:WS-TST123-LOAN-DT-NR,
          :WS-JV-COUNTER,
          :WS-TST123-SCHEDULE-DOC-NO,
          :WS-TST123-COMMENT-HIST,
          :WS-TST123-COMMENT-REQUESTOR,
          :WS-TST123-COMMENT-APPROVER,
          :WS-CONTROL-NUM,
          'LABD20',
          TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD'))     *> LABD20.pco:371
END-EXEC.                                           *> LABD20.pco:372
```

- **Tables:** `JC_SUBMITTED_COMMENT_TBL`.
- **Bind variables (in):**
  - `:WS-TST123-LOAN-DT-NR` → `JC_SUBMITTED` (CHAR(26) PK).
  - `:WS-JV-COUNTER` → `JC_SUBMITTED_NUMBER` (NUMBER; sequence-style counter from `WS-COUNTERS` `LABD20.pco:149`).
  - `:WS-TST123-SCHEDULE-DOC-NO` → `JC_SUBMITTED_SCHED_DOC_NO` (CHAR(10)).
  - `:WS-TST123-COMMENT-HIST` → `JC_SUBMITTED_COMMENT_HIST` (CHAR(240)).
  - `:WS-TST123-COMMENT-REQUESTOR` → `JC_SUBMITTED_COMMENT_REQUESTOR` (CHAR(20)).
  - `:WS-TST123-COMMENT-APPROVER` → `JC_SUBMITTED_COMMENT_APPROVER` (CHAR(20)). See [Risk 5 / A-4](../RISKS-AND-GAPS.md#risk-5--fixed-width-record-layout-byte-precision) about the 14 vs. 20 width.
  - `:WS-CONTROL-NUM` → `JC_SUBMITTED_CONTROL_NUM` (CHAR(8) — composite of JV number + section ID; redefined at `LABD20.pco:163-165`).
  - Literal `'LABD20'` → `JC_SUBMITTED_UPDT_PROG_ID` (program-self-identifier).
  - `:WS-PROCESS-DATE` → wrapped in `TO_DATE(:val,'YYYYMMDD')` → `JC_SUBMITTED_UPDT_PROG_DT` (Oracle DATE).
- **Transaction boundary:** none. Extends the current transaction.
- **Oracle-specific:** `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` (line 371) — see §3 for callout and modernized form.
- **Error handling:**
  ```pco
  IF SQLCODE NOT = 0                                *> LABD20.pco:373
     GO TO 9999-ROLL-BACK
  ```
  No `SQLCODE = 100` fallthrough; any non-zero is a hard error. See [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms).

#### SQL-LABD20-006 — Update JC_COUNT_TBL for section MA

```pco
EXEC SQL UPDATE JC_COUNT_TBL                        *> LABD20.pco:398
   SET JC_SECTION_COUNT = :WS-JV-COUNTER
   WHERE JC_SECTION = 'MA'
END-EXEC                                            *> LABD20.pco:401
```

- **Tables:** `JC_COUNT_TBL` (PK = `JC_SECTION` per `database/descriptions/describe JC_COUNT_TBL.txt:10`; the description file lists `JC_SUBMITTED` but the schema clearly keys on `JC_SECTION` — flag for SME).
- **Bind variables (in):** `:WS-JV-COUNTER` (running counter). The `'MA'` is a string **literal**, not a bind variable — hardcoded section. See [Assumption A-5](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-5--section-ma-is-the-only-section-updated-by-post-process).
- **Transaction boundary:** none. Extends the current transaction.
- **Oracle-specific:** none.
- **Error handling:** `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (LABD20.pco:402-404).

#### SQL-LABD20-007 — Commit work

```pco
EXEC SQL COMMIT WORK END-EXEC.                      *> LABD20.pco:413
```

- **Transaction boundary:** **COMMITS**. This is the only explicit commit in LABD20.pco. The cycle is: validate → SELECT duplicate → INSERT accepted comment → ... → (after all records) UPDATE section count → COMMIT.
- **Error handling:** `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (LABD20.pco:414-416). A commit failure is treated as a hard error — rollback (which in the rolled-back-state is a no-op for the COMMIT'd portion but still attempts to roll back any post-COMMIT work).

#### SQL-LABD20-008 / -009 / -010 — End-of-job row-count reports

```pco
EXEC SQL SELECT COUNT(*) INTO :WS-TOTAL-SUBMIT-END-CNT     *> LABD20.pco:421
   FROM JC_SUBMITTED_COMMENT_TBL
END-EXEC.                                                  *> LABD20.pco:423
...
EXEC SQL SELECT COUNT(*) INTO :WS-TOTAL-REJECT-END-CNT     *> LABD20.pco:431
   FROM JC_REJECTED_COMMENT_TBL
END-EXEC.                                                  *> LABD20.pco:433
...
EXEC SQL SELECT COUNT(*) INTO :WS-TOTAL-APPLIED-END-CNT    *> LABD20.pco:441
   FROM JC_APPLIED_COMMENT_TBL
END-EXEC.                                                  *> LABD20.pco:443
```

- **Tables:** `JC_SUBMITTED_COMMENT_TBL`, `JC_REJECTED_COMMENT_TBL`, `JC_APPLIED_COMMENT_TBL`.
- **Bind variables (out):** `:WS-TOTAL-SUBMIT-END-CNT`, `:WS-TOTAL-REJECT-END-CNT`, `:WS-TOTAL-APPLIED-END-CNT` (each PIC 9(12) `LABD20.pco:157-160`).
- **Transaction boundary:** none. Each begins (Oracle implicitly) a new read-only transaction after SQL-LABD20-007 committed.
- **Oracle-specific:** `COUNT(*)` aggregate (ANSI, but typical Oracle idiom for batch-report counts).
- **Error handling:** Each has the same guard — `IF SQLCODE NOT = 0 GO TO 9999-ROLL-BACK` (LABD20.pco:424-426, 434-436, 444-446).

#### SQL-LABD20-R — 9999-ROLL-BACK paragraph (indirect rollback through DBIO)

LABD20 does **not** issue `EXEC SQL ROLLBACK` directly. Its terminal error paragraph calls the DBIO dispatcher with `DB-FUNCTION = 'ROLLBACK'`, which then issues SQL-DBIO-009. The paragraph also reports SQLCODE/DMS context before terminating the process with `RETURN-CODE = 99`.

```pco
9999-ROLL-BACK.                                    *> LABD20.pco:489
    DISPLAY 'LABD20 ABORTED' UPON PRINTER.
    DISPLAY 'ROLL BACK' UPON PRINTER.
    ...
    IF SQL-PROCESSING
        ... MOVE SQLCODE TO WS-SQLCODE
        ... PERFORM RDMS-ERR-RTN
    ELSE
        IF DMS-PROCESSING
            DISPLAY 'DMS ERROR' ... DISPLAY ERROR-NUM ...
    END-IF.
    INITIALIZE                         DB-DMSREC-NAME
    MOVE 'ROLLBACK'                 TO DB-FUNCTION   *> LABD20.pco:515
    MOVE 'DEPART'                   TO DB-FUNCTION-TYPE
    INITIALIZE                         DB-SET-NAME
    CALL 'DBIO' USING DB-DMSREC-NAME                  *> LABD20.pco:518
                      DB-FUNCTION
                      DB-FUNCTION-TYPE
                      DB-SET-NAME
                      DB-DATA
                      DB-ROWID
                      DB-MISC
                      DB-RETURN-CODE
                      DB-MESSAGE
    PERFORM DMCA-ERRFLDS THRU DMCA-ERRFLDS-EXIT.
    MOVE 99 TO RETURN-CODE.                           *> LABD20.pco:528
    STOP RUN.                                         *> LABD20.pco:529
```

- **Function-type `'DEPART'`** is the DMS-style hint that this is a final rollback (program is exiting). DBIO does **not** distinguish `DEPART` vs. mid-job rollback — it dispatches the same `EXEC SQL ROLLBACK` either way (SQL-DBIO-009).
- **Transaction boundary:** **ROLLS BACK** via SQL-DBIO-009.
- **Error handling reported before rollback** (this is the dual SQL/DMS path that motivates [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms)):
  - If `WS-PROCESSING-SW = 'S'` (`SQL-PROCESSING`): logs `WS-TABLE-NAME`, `WS-COMMAND-NAME`, `SQLCODE`, and the RDMS error message via the `RDMS-ERR-RTN` copybook.
  - If `WS-PROCESSING-SW = 'D'` (`DMS-PROCESSING`): logs `DB-DMSREC-NAME`, `DB-FUNCTION-TYPE`, and `ERROR-NUM` (DMS-style 4-char code).

In the modernized form, this becomes a single `except` handler that captures both Oracle exception context (the equivalent of SQLCODE) and the dispatcher-level context (table name, function), then calls `connection.rollback()` and re-raises.

---

### 2.2 CONTROL-RECORD-TABLE-IO.pco

This module is a CRUD wrapper around `CONTROL_RECORD_TABLE` invoked through DBIO (dynamic dispatch path overridden at `DBIO.pco:268-276` when `DB-DMSREC-NAME = 'JV-CONTROL-REC'`). It uses **Pro*COBOL dynamic SQL Method 3** (`PREPARE`/`DECLARE`/`OPEN`/`FETCH`/`CLOSE`) for SELECT, and static parameterized statements for INSERT/UPDATE/DELETE.

#### SQL-CRTIO-001 / -002 / -003 — Declare section & SQLCA

```pco
EXEC SQL                                            *> CONTROL-RECORD-TABLE-IO.pco:100
     BEGIN DECLARE SECTION
END-EXEC.
... PREPARE-STRING PIC X(380) VALUE SPACES.         *> .pco:107
... W-CONTROL-RECORD-TABLE host record (...)        *> .pco:111-115
EXEC SQL                                            *> .pco:117
     END DECLARE SECTION
END-EXEC.
EXEC SQL                                            *> .pco:121
     INCLUDE SQLCA
END-EXEC.
```

- Declares the cursor host variables (`W-CONTROL-RECORD-NAME` CHAR(30), `W-CONTROL-RECORD-NUMBER` NUMBER(4), `W-CONTROL-RECORD-DATA` CHAR(400), `W-ROWID` CHAR(18)) and the dynamic-SQL text buffer `PREPARE-STRING` (CHAR(380)).
- The width `380` of `PREPARE-STRING` is **the upper bound** the original developer chose; the dynamic SELECT can be up to 380 characters. See §3.

#### SQL-CRTIO-004 / -005 / -006 — WHENEVER handlers

```pco
EXEC SQL                                            *> CONTROL-RECORD-TABLE-IO.pco:148
    WHENEVER NOT FOUND CONTINUE
END-EXEC.
EXEC SQL                                            *> .pco:151
    WHENEVER SQLWARNING CONTINUE
END-EXEC.
EXEC SQL                                            *> .pco:154
    WHENEVER SQLERROR GO TO 9999-ERROR
END-EXEC.
```

- `WHENEVER` is Pro*COBOL's "install a global error handler for everything that follows in this module" directive. The active handler at any point is determined by the **lexically-most-recent** `WHENEVER` of that condition (NOT FOUND / SQLWARNING / SQLERROR).
- Net policy installed:
  - **NOT FOUND** (SQLCODE = 100): `CONTINUE` — let the caller inspect `SQLCODE`.
  - **SQLWARNING** (SQLCODE > 0): `CONTINUE`.
  - **SQLERROR** (SQLCODE < 0): unconditional `GO TO 9999-ERROR`.
- Every later EXEC SQL in this module that does **not** override the handler inherits this policy. In particular, SQL-CRTIO-008/009/010 (PREPARE/DECLARE/OPEN) have no explicit `IF SQLCODE …` check — they rely on `WHENEVER SQLERROR` to redirect.

#### SQL-CRTIO-007 — EXEC ORACLE OPTION precompiler directive

```pco
EXEC ORACLE OPTION (SELECT_ERROR=NO) END-EXEC.      *> CONTROL-RECORD-TABLE-IO.pco:161
```

- **Oracle-specific** Pro*COBOL precompiler directive. By default, Oracle Pro*COBOL flags `SQLCODE != 0` when a SELECT INTO returns more than one row. `SELECT_ERROR=NO` disables that behavior so that the cursor pattern (PREPARE/OPEN/FETCH multiple rows) works without spurious errors.
- No runtime equivalent in modern Python — `oracledb`/`cx_Oracle` does not have an equivalent flag because the cursor pattern is the only way to fetch multiple rows (no implicit "SELECT INTO too many rows" trap).

#### SQL-CRTIO-008 / -009 / -010 — PREPARE / DECLARE / OPEN (dynamic SQL Method 3)

```pco
EXEC SQL PREPARE C1 FROM :PREPARE-STRING END-EXEC.  *> CONTROL-RECORD-TABLE-IO.pco:306
EXEC SQL DECLARE DB CURSOR FOR C1        END-EXEC.  *> .pco:307
EXEC SQL OPEN DB END-EXEC.                          *> .pco:308
```

- `:PREPARE-STRING` is the dynamically-built SELECT text (see §3 below).
- `C1` is the **prepared statement name**; `DB` is the **cursor name** bound to `C1`.
- **Transaction boundary:** `OPEN` starts a read-consistent transaction (Oracle uses snapshot isolation from the OPEN point).
- **Error handling:** inherits `WHENEVER SQLERROR GO TO 9999-ERROR` (SQL-CRTIO-006).

#### SQL-CRTIO-011 — FETCH

```pco
EXEC SQL                                            *> CONTROL-RECORD-TABLE-IO.pco:318
FETCH DB INTO
     :W-CONTROL-RECORD-NAME,
     :W-CONTROL-RECORD-NUMBER,
     :W-CONTROL-RECORD-DATA,
     :W-ROWID
END-EXEC.
MOVE SQLCODE TO DB-SQLCODE-NUM.                     *> .pco:325
IF SQLCODE = 100                                    *> .pco:326
  PERFORM CLS-DB THRU CLS-DB-EXIT
END-IF.
```

- **Out variables:** the four host variables. Note `:W-ROWID` — the SELECT list at lines 37-52 includes the pseudo-column `ROWID` (line 42), so this is the **persisted ROWID** returned by Oracle for the row just fetched. See §3 and [§4 Oracle-specific construct callouts](#4-oracle-specific-construct-callouts).
- **Error handling:** explicit `SQLCODE = 100` (no more rows) path → `CLS-DB` (closes the cursor); other failures fall through to `WHENEVER SQLERROR`.

#### SQL-CRTIO-012 — INSERT … RETURNING ROWID

```pco
EXEC SQL                                            *> CONTROL-RECORD-TABLE-IO.pco:337
INSERT INTO CONTROL_RECORD_TABLE
(CONTROL_RECORD_NAME,
 CONTROL_RECORD_NUMBER,
 CONTROL_RECORD_DATA)
VALUES
(:W-CONTROL-RECORD-NAME,
 :W-CONTROL-RECORD-NUMBER,
 :W-CONTROL-RECORD-DATA)
RETURNING ROWID INTO :W-ROWID                       *> .pco:346
END-EXEC.
MOVE SQLCODE TO DB-SQLCODE-NUM.                     *> .pco:348
MOVE W-ROWID TO DB-ROWID.                           *> .pco:349
```

- **Tables:** `CONTROL_RECORD_TABLE`.
- **Bind variables (in):** `:W-CONTROL-RECORD-NAME`, `:W-CONTROL-RECORD-NUMBER`, `:W-CONTROL-RECORD-DATA`. **Out:** `:W-ROWID`.
- **Transaction boundary:** extends the current transaction.
- **Oracle-specific:** **`RETURNING ROWID INTO :W-ROWID`** — Oracle DML extension that returns server-assigned values (here, the inserted row's `ROWID`) in the same round trip. The DB stores this in `DB-ROWID` so the caller can re-fetch / re-update by ROWID later without an extra SELECT round trip.
- **Error handling:** WHENEVER SQLERROR active.

Modernized form (parameterized; ANSI-leaning):

```sql
-- Option A: identical Oracle semantics
INSERT INTO control_record_table
       (control_record_name, control_record_number, control_record_data)
VALUES (:rec_name, :rec_number, :rec_data)
RETURNING ROWID INTO :rowid_out;

-- Option B: portable — drop ROWID, re-key on the natural composite PK
INSERT INTO control_record_table
       (control_record_name, control_record_number, control_record_data)
VALUES (:rec_name, :rec_number, :rec_data);
-- and use (control_record_name, control_record_number) as the lookup key.
```

#### SQL-CRTIO-013 — UPDATE WHERE ROWID = :W-ROWID, SYSDATE

```pco
EXEC SQL                                            *> CONTROL-RECORD-TABLE-IO.pco:358
UPDATE CONTROL_RECORD_TABLE SET
       CONTROL_RECORD_NAME   = :W-CONTROL-RECORD-NAME,
       CONTROL_RECORD_NUMBER = :W-CONTROL-RECORD-NUMBER,
       CONTROL_RECORD_DATA   = :W-CONTROL-RECORD-DATA,
       LAST_UPDATE_TIMESTAMP = SYSDATE
WHERE  ROWID = :W-ROWID
END-EXEC.
MOVE SQLCODE TO DB-SQLCODE-NUM.                     *> .pco:370
```

- **Tables:** `CONTROL_RECORD_TABLE`.
- **Bind variables (in):** `:W-CONTROL-RECORD-NAME`, `:W-CONTROL-RECORD-NUMBER`, `:W-CONTROL-RECORD-DATA`, `:W-ROWID`.
- **Transaction boundary:** extends the current transaction.
- **Oracle-specific:**
  - `SYSDATE` — Oracle server clock as `DATE`.
  - `WHERE ROWID = :W-ROWID` — physical-row addressing.
- **Error handling:** WHENEVER SQLERROR active.

Modernized form (parameterized):

```sql
-- Option A: keep Oracle semantics (ROWID + SYSDATE).
UPDATE control_record_table
   SET control_record_name   = :rec_name,
       control_record_number = :rec_number,
       control_record_data   = :rec_data,
       last_update_timestamp = SYSDATE
 WHERE ROWID = :rowid_bind;

-- Option B: portable — natural PK + CURRENT_TIMESTAMP.
UPDATE control_record_table
   SET control_record_name   = :rec_name,
       control_record_number = :rec_number,
       control_record_data   = :rec_data,
       last_update_timestamp = CURRENT_TIMESTAMP
 WHERE control_record_name   = :pk_rec_name
   AND control_record_number = :pk_rec_number;
```

#### SQL-CRTIO-014 — DELETE WHERE ROWID

```pco
EXEC SQL                                            *> CONTROL-RECORD-TABLE-IO.pco:378
  DELETE FROM CONTROL_RECORD_TABLE
  WHERE ROWID = :W-ROWID
END-EXEC.
MOVE SQLCODE TO DB-SQLCODE-NUM.                     *> .pco:382
```

- Same ROWID-based pattern. Portable rewrite uses the natural composite PK.

#### SQL-CRTIO-015 — CLOSE cursor

```pco
EXEC SQL CLOSE DB END-EXEC                          *> CONTROL-RECORD-TABLE-IO.pco:391
```

- Closes the dynamic cursor `DB`. Called from `CLS-DB` after `SQLCODE = 100` (end of rowset) or before reopening (`OPN-DB` re-checks `DB-OPEN` and closes first; `.pco:297-309`).

---

### 2.3 DBIO.pco

DBIO is the central dispatcher. It owns: (a) the connection lifecycle, (b) the SQL→DMS error-code translation table, (c) the `WHENEVER` policy that governs all dispatched statements, and (d) the COMMIT/ROLLBACK statements that callers reach via the `DB-COMMIT`/`DB-ROLLBACK`/`DB-DONE` function strings.

#### SQL-DBIO-001 / -002 / -003 — Declare section & SQLCA

```pco
EXEC SQL                                            *> DBIO.pco:52
     INCLUDE SQLCA
END-EXEC
EXEC SQL                                            *> .pco:57
    BEGIN DECLARE SECTION
END-EXEC
... SRVR PIC X(20). ...
... USR-PWD-DBNAME PIC X(40).                       *> .pco:63
EXEC SQL                                            *> .pco:64
    END DECLARE SECTION
END-EXEC
```

- Two host vars: `SRVR` (Oracle SID from `ORACLE_SID` env var, line 165-166) and `USR-PWD-DBNAME` (the assembled `user/password@SID` string used by `CONNECT`, line 168-169).

#### SQL-DBIO-004 / -005 / -006 — WHENEVER handlers

```pco
EXEC SQL WHENEVER NOT FOUND CONTINUE END-EXEC.      *> DBIO.pco:147
EXEC SQL WHENEVER SQLWARNING CONTINUE END-EXEC.     *> .pco:151
EXEC SQL WHENEVER SQLERROR GO TO 9999-ERROR END-EXEC. *> .pco:155
```

- Same shape as the CONTROL-RECORD-TABLE-IO handlers (SQL-CRTIO-004/-005/-006). `9999-ERROR` is the local DBIO error paragraph at `.pco:402-405`: copies `SQLCODE`/`SQLERRMC` to the DBIO output parameters and `GOBACK`s. Note: **it does not rollback** — the caller's 9999-ROLL-BACK paragraph is responsible for issuing the second `CALL 'DBIO'` with `DB-FUNCTION = 'ROLLBACK'`.

#### SQL-DBIO-007 — CONNECT

```pco
OPEN INPUT USRID PASSWD                              *> DBIO.pco:162
READ USRID
READ PASSWD
DISPLAY 'ORACLE_SID' UPON ENVIRONMENT-NAME
ACCEPT SRVR FROM ENVIRONMENT-VALUE
MOVE SPACES TO USR-PWD-DBNAME
STRING USRID-REC '/' PASSWD-REC '@' SRVR
       DELIMITED BY SPACE INTO USR-PWD-DBNAME       *> .pco:169
CLOSE USRID PASSWD
EXEC SQL                                            *> .pco:171
    CONNECT :USR-PWD-DBNAME
END-EXEC
```

- **Oracle Pro*COBOL** `CONNECT :host_var` syntax. The host variable is the `user/password@SID` triplet.
- **Transaction boundary:** opens a new database session; implicitly starts the first transaction.
- **Credentials source — [Risk 3](../RISKS-AND-GAPS.md#risk-3--credential-files-tstoralogin-tstorapasswd):**
  - User from line-sequential file `/tst/.oralogin` (`DBIO.pco:33-35`).
  - Password from line-sequential file `/tst/.orapasswd` (`DBIO.pco:36-38`).
  - SID from environment variable `ORACLE_SID` (line 165-166).
- **Error handling:** falls through to `WHENEVER SQLERROR` → `9999-ERROR` (`.pco:402-405`).

Modernized form: the connection step is owned by `migration/converted-code/python/db_dispatcher.py` and reads `ORACLE_USER`, `ORACLE_PASSWORD`, `ORACLE_DSN` from environment / managed-secrets (no flat files; no plaintext password on disk).

#### SQL-DBIO-008 — COMMIT WORK (caller asked for `DB-COMMIT`)

```pco
WHEN DB-COMMIT                                       *> DBIO.pco:188
    EXEC SQL COMMIT WORK END-EXEC                    *> .pco:189
    MOVE 0 TO DB-SQLCODE-NUM
    PERFORM 5300-TRANSLATE-SQLCODE
    GO TO 0000-EXIT
```

- LABA05's normal-path commit goes through here (`LABA05.cbl` → `CALL 'DBIO'` with `DB-FUNCTION = 'COMMIT'`); LABD20's normal-path commit goes through SQL-LABD20-007 instead (LABD20 commits its own work directly via `EXEC SQL COMMIT WORK`, not via DBIO).
- **Transaction boundary:** **COMMITS**.
- **Error handling:** the `MOVE 0 TO DB-SQLCODE-NUM` line zeroes the SQLCODE pass-through even on success; non-success goes through `WHENEVER SQLERROR` → `9999-ERROR`. `5300-TRANSLATE-SQLCODE` ([Risk 6](../RISKS-AND-GAPS.md#risk-6--sqlcode--dms-style-return-code-translation)) maps to DMS-style codes.

#### SQL-DBIO-009 — ROLLBACK (caller asked for `DB-ROLLBACK`)

```pco
WHEN DB-ROLLBACK                                     *> DBIO.pco:193
    EXEC SQL ROLLBACK END-EXEC                       *> .pco:194
    MOVE 0 TO DB-SQLCODE-NUM
    PERFORM 5300-TRANSLATE-SQLCODE
    GO TO 0000-EXIT
```

- **Single rollback path** for both LABA05 and LABD20 — both modules reach this same statement via `CALL 'DBIO'` with `DB-FUNCTION = 'ROLLBACK'` (LABD20.pco:515-526; LABA05.cbl rollback path).
- **Transaction boundary:** **ROLLS BACK**.
- See [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms).

#### SQL-DBIO-010 — COMMIT (caller asked for `DB-DONE`)

```pco
WHEN DB-DONE                                         *> DBIO.pco:198
    EXEC SQL COMMIT END-EXEC                         *> .pco:199
    MOVE 0 TO DB-SQLCODE-NUM
    PERFORM 5300-TRANSLATE-SQLCODE
    GO TO 0000-EXIT
```

- A second commit path used by callers that want a single "commit and signal end-of-module" instruction (LABD20's normal exit issues `DB-FUNCTION = 'DEPART'` instead of `DONE`, but the SQL semantics are identical).
- **Transaction boundary:** **COMMITS**.

---

## 3. Dynamic SQL — PREPARE-STRING pattern in CONTROL-RECORD-TABLE-IO

This is the only dynamic-SQL site in the supplied source. The rest of the catalog is static `EXEC SQL`.

### 3.1 Literal SELECT/FROM/INTO chunks

The static text is assembled from four FILLER blocks in working storage (`CONTROL-RECORD-TABLE-IO.pco:37-52`):

```pco
01  W-DB-SELECT.                                    *> .pco:37
    05 FILLER PIC X(07) VALUE 'SELECT '.
    05 FILLER PIC X(20) VALUE 'CONTROL_RECORD_NAME,'.
    05 FILLER PIC X(22) VALUE 'CONTROL_RECORD_NUMBER,'.
    05 FILLER PIC X(20) VALUE 'CONTROL_RECORD_DATA,'.
    05 FILLER PIC X(05) VALUE 'ROWID'.

01  W-DB-TABLE PIC X(27) VALUE                      *> .pco:44
             ' FROM CONTROL_RECORD_TABLE'.

01  W-DB-INTO.                                      *> .pco:47
    05 FILLER PIC X(05) VALUE 'INTO '.
    05 FILLER PIC X(23) VALUE ':W-CONTROL-RECORD-NAME,'.
    05 FILLER PIC X(25) VALUE ':W-CONTROL-RECORD-NUMBER,'.
    05 FILLER PIC X(23) VALUE ':W-CONTROL-RECORD-DATA,'.
    05 FILLER PIC X(08) VALUE ':W-ROWID'.
```

> **Note about `W-DB-INTO`:** it is declared but **never concatenated into `PREPARE-STRING`** (see §3.3 — only `W-DB-SELECT`, `W-DB-TABLE`, `W-DB-WHERE`, and `W-DB-ORDERBY` are STRINGed). The INTO list is supplied by the **`FETCH DB INTO ...`** statement (SQL-CRTIO-011) at fetch time, not by the prepared SELECT. The dead-code `W-DB-INTO` is a generator artifact and is safe to drop in the modernized form.

### 3.2 The two WHERE-clause shapes

```pco
01  W-DB-WHERE-ALL-KEYS.                            *> .pco:58
    05  W-DB-ROWID-KEY.
        10  FILLER         PIC X(07) VALUE ' WHERE '.
        10  FILLER         PIC X(05) VALUE 'ROWID'.
        10  FILLER         PIC X(05) VALUE ' = '.
        10  FILLER         PIC X(01) VALUE ''''.
        10  W-ROWID-K      PIC X(18).
        10  FILLER         PIC X(01) VALUE ''''.

    05  W-DB-PRIMARY-KEY.
        10  FILLER         PIC X(07) VALUE ' WHERE '.
        10  FILLER         PIC X(19) VALUE 'CONTROL_RECORD_NAME'.
        10  FILLER         PIC X(05) VALUE 'XXXXX'.
        10  FILLER         PIC X(01) VALUE ''''.
        10  WCONTROL-RECORD-NAME-K   PIC X(30).
        10  FILLER         PIC X(01) VALUE ''''.
        10  FILLER         PIC X(07) VALUE ' AND '.
        10  FILLER         PIC X(21) VALUE 'CONTROL_RECORD_NUMBER'.
        10  FILLER         PIC X(05) VALUE 'XXXXX'.
        10  WCONTROL-RECORD-NUMBER-K PIC 9(04).
```

The `'XXXXX'` markers are sentinel substrings that `OPN-DB` overwrites at runtime with either `' =   '` or `' >=  '` (the `EQ-OP` / `GT-EQ-OP` literals at `.pco:87-88`) using `INSPECT … REPLACING ALL 'XXXXX' BY …` (`.pco:217-221`). This is how the same `W-DB-PRIMARY-KEY` template serves both `=` lookups and `>=` range-start scans (FETCH FIRST when the cursor walks the table in PK order).

How `W-DB-WHERE` is populated — driven by `DB-FUNCTION-TYPE` in the EVALUATE block at `.pco:189-228`:

| DB-FUNCTION-TYPE | WHERE source | Operator after INSPECT | Built WHERE example |
|---|---|---|---|
| `FETCH CURR` | `W-DB-ROWID-KEY` | n/a (literal `=`) | ` WHERE ROWID = 'AAA...rowid18...'` |
| `FETCH FIRST` w/ `DB-SET-NAME = SPACES` | `W-DB-PRIMARY-KEY` | `' >=  '` | ` WHERE CONTROL_RECORD_NAME >=   'JV-CONTROL-REC' AND CONTROL_RECORD_NUMBER >=   1` |
| `FETCH FIRST` w/ `DB-SET-NAME > SPACES`; `FETCH`; `FETCH DBK`; `FIND DBK` | `W-DB-PRIMARY-KEY` | `' =   '` | ` WHERE CONTROL_RECORD_NAME =   'JV-CONTROL-REC' AND CONTROL_RECORD_NUMBER =   1` |
| `FETCH OWNER` | `W-DB-PRIMARY-KEY` (PK values copied from `DB-CONSTRAINT`) | `' =   '` | same shape as above |

### 3.3 PREPARE → DECLARE → OPEN → FETCH → CLOSE

```pco
OPN-DB.                                             *> .pco:297
    IF DB-OPEN
       PERFORM CLS-DB THRU CLS-DB-EXIT              *> close prior cursor if open
    END-IF.

    INITIALIZE PREPARE-STRING.                      *> .pco:302
    STRING W-DB-SELECT W-DB-TABLE W-DB-WHERE W-DB-ORDERBY
           DELIMITED BY SIZE INTO PREPARE-STRING.   *> .pco:303-304

    EXEC SQL PREPARE C1 FROM :PREPARE-STRING END-EXEC.  *> .pco:306
    EXEC SQL DECLARE DB CURSOR FOR C1        END-EXEC.  *> .pco:307
    EXEC SQL OPEN DB END-EXEC.                          *> .pco:308
    MOVE "Y" TO DB-OPEN-SW.
```

Sequence of operations the runtime performs:

1. **STRING** concatenates the four chunks (SELECT-list, FROM-clause, WHERE-clause, ORDER-BY) into `PREPARE-STRING` (380 bytes; trailing spaces remain — `DELIMITED BY SIZE` preserves padding).
2. **PREPARE C1 FROM :PREPARE-STRING** — Oracle parses the text and compiles a statement named `C1` for the current session. Bind variables embedded in the text (only `:W-ROWID` in the ROWID-key form — but here the ROWID is *concatenated into the text as a literal*, so the prepared statement actually has **zero binds**) are bound at execute time.
3. **DECLARE DB CURSOR FOR C1** — names a cursor `DB` over statement `C1`.
4. **OPEN DB** — runs the SELECT; cursor is now positioned before the first row.
5. **FETCH DB INTO :W-CONTROL-RECORD-NAME, :W-CONTROL-RECORD-NUMBER, :W-CONTROL-RECORD-DATA, :W-ROWID** (SQL-CRTIO-011) — fetches the next row.
6. **CLOSE DB** when SQLCODE = 100 or before reopening (SQL-CRTIO-015).

**Important security observation:** the `W-DB-PRIMARY-KEY` template **concatenates the key values into the SQL text as quoted literals** (`'XXXXX'` is the placeholder for the COBOL value, but `WCONTROL-RECORD-NAME-K` and the ROWID value are inserted as literal text — *not* as bind variables). This is **dynamic SQL Method 1-ish** even though it uses PREPARE — and means the prepared statement is **recompiled by Oracle every time** because the text differs (no cursor reuse / no SQL-cache hit). It also means the legacy code is **technically a SQL-injection vector** if `CONTROL-RECORD-NAME` ever contains a single quote — though in practice the COBOL caller controls this fully (the only producer is the `'JV-CONTROL-REC'` literal in `DBIO.pco:268-276`).

### 3.4 Equivalent modernized form — static parameterized SELECT

Because there are only two WHERE shapes (ROWID-equality, primary-key-equality/range), the dynamic-PREPARE pattern is **strictly unnecessary**. The modernized form expresses both shapes as static, parameterized statements with bind variables — eliminating the per-call recompile and removing the injection vector:

```sql
-- Static PK lookup (replaces DB-FUNCTION-TYPE in {FETCH, FETCH DBK, FETCH OWNER, FETCH FIRST with set})
SELECT control_record_name,
       control_record_number,
       control_record_data,
       ROWID                   AS rowid_out
  FROM control_record_table
 WHERE control_record_name   = :rec_name
   AND control_record_number = :rec_number;

-- Static PK range scan (replaces DB-FUNCTION-TYPE = FETCH FIRST with empty set name)
SELECT control_record_name,
       control_record_number,
       control_record_data,
       ROWID                   AS rowid_out
  FROM control_record_table
 WHERE control_record_name   >= :rec_name
   AND control_record_number >= :rec_number
 ORDER BY control_record_name, control_record_number;

-- Static ROWID lookup (replaces DB-FUNCTION-TYPE = FETCH CURR)
SELECT control_record_name,
       control_record_number,
       control_record_data,
       ROWID                   AS rowid_out
  FROM control_record_table
 WHERE ROWID = :rowid_bind;
```

In `oracledb` / `cx_Oracle`:

```python
PK_LOOKUP_SQL = """
    SELECT control_record_name,
           control_record_number,
           control_record_data,
           ROWID AS rowid_out
      FROM control_record_table
     WHERE control_record_name   = :rec_name
       AND control_record_number = :rec_number
"""

with conn.cursor() as cur:
    cur.execute(PK_LOOKUP_SQL, rec_name=name, rec_number=number)
    row = cur.fetchone()
```

The dispatcher (`db_dispatcher.py`) selects which of the three static statements to execute based on the same `DB-FUNCTION-TYPE` enum — a one-page lookup table replaces the entire 380-byte string-builder + PREPARE/DECLARE/OPEN/FETCH/CLOSE choreography.

If the modernized form chooses **portable** WHERE shape (drop ROWID for natural PK), the third statement disappears entirely.

---

## 4. Oracle-specific construct callouts

| Construct | Where | Why it matters | Modernized handling |
|---|---|---|---|
| `TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD')` | LABD20.pco:371 (SQL-LABD20-005) | Oracle-only date-from-string function; format mask `'YYYYMMDD'` parses the 8-char zoned-decimal date carried in `WS-PROCESS-DATE` (PIC 9(08) `LABD20.pco:92`). | Parse the 8 digits in Python (`datetime.strptime(value, "%Y%m%d")`), bind as a Python `datetime.date`. The Oracle driver maps it to native `DATE` automatically — no `TO_DATE` needed in the SQL text. Eliminates the format-string-on-server idiom. |
| `ROWID` (pseudo-column, in SELECT list) | CONTROL-RECORD-TABLE-IO.pco:42 (SELECT list); .pco:52 (INTO list) | Oracle physical-row identifier; used to round-trip from SELECT → later UPDATE/DELETE WHERE ROWID = :saved_rowid (avoids re-keying). | Either (a) keep ROWID as an Oracle-portable identifier in the model layer, exposed as an opaque string, **or** (b) replace with the natural composite PK `(control_record_name, control_record_number)`. Phase-2 modernized form prefers (b) — see §3.4 and `db_dispatcher.py`. |
| `WHERE ROWID = :W-ROWID` | CONTROL-RECORD-TABLE-IO.pco:60-65 (WHERE template), 368 (UPDATE), 380 (DELETE) | Physical-row addressing instead of PK lookup — fast, but cross-DB-incompatible and brittle across reorgs/exports. | See above; switch to natural PK. |
| `RETURNING ROWID INTO :W-ROWID` | CONTROL-RECORD-TABLE-IO.pco:346 (SQL-CRTIO-012) | Oracle DML extension that returns server-assigned columns in the same round trip. | `oracledb` cursor variables: `out_rowid = cur.var(oracledb.STRING); cur.execute(sql, ..., rowid_out=out_rowid); rowid = out_rowid.getvalue()`. Or drop the RETURNING and rely on the natural PK. |
| `SYSDATE` | CONTROL-RECORD-TABLE-IO.pco:367 (SQL-CRTIO-013) | Oracle server clock as `DATE` (no time-zone, second precision). | Portable rewrite uses `CURRENT_TIMESTAMP` (TIMESTAMP WITH TIME ZONE) — matches the `LAST_UPDATE_TIMESTAMP TIMESTAMP(6)` column type (`database/descriptions/describe CONTROL_RECORD_TABLE.txt:9`). |
| `EXEC ORACLE OPTION (SELECT_ERROR=NO)` | CONTROL-RECORD-TABLE-IO.pco:161 (SQL-CRTIO-007) | Pro*COBOL precompiler directive — disables "SELECT returned >1 row" error in implicit `SELECT INTO`. | No analogue in modern drivers (no implicit `SELECT INTO` trap exists); silently dropped on conversion. |
| `EXEC SQL CONNECT :USR-PWD-DBNAME` | DBIO.pco:171-173 (SQL-DBIO-007) | Oracle Pro*COBOL connect syntax; `user/password@SID` literal. | Use `oracledb.connect(user=..., password=..., dsn=...)` with credentials sourced from environment or secrets manager. See [Risk 3](../RISKS-AND-GAPS.md#risk-3--credential-files-tstoralogin-tstorapasswd). |
| `WHENEVER NOT FOUND CONTINUE` / `SQLWARNING CONTINUE` / `SQLERROR GO TO label` | DBIO.pco:147-157; CONTROL-RECORD-TABLE-IO.pco:148-156 | Precompiler-installed implicit GO TO. | No analogue in Python — each `cur.execute()` is wrapped in try/except in the modernized dispatcher. |
| `INSERT ... 'LABD20'` (program-self-identifier literal) | LABD20.pco:370 | Not Oracle-specific, but worth noting — the program-id literal is embedded in the INSERT rather than passed as a bind variable. | Bind as `:program_id` so the same INSERT statement can be reused across modules. |

---

## 5. Tables touched (round-up)

| Table | Statements | Description file |
|---|---|---|
| `JC_SUBMITTED_COMMENT_TBL` | SQL-LABD20-004 (SELECT), SQL-LABD20-005 (INSERT), SQL-LABD20-008 (SELECT COUNT) | `database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt` |
| `JC_REJECTED_COMMENT_TBL` | SQL-LABD20-009 (SELECT COUNT) | `database/descriptions/describe JC_REJECTED_COMMENT_TBL.txt` |
| `JC_APPLIED_COMMENT_TBL` | SQL-LABD20-010 (SELECT COUNT) | `database/descriptions/describe JC_APPLIED_COMMENT_TBL.txt` |
| `JC_COUNT_TBL` | SQL-LABD20-006 (UPDATE) | `database/descriptions/describe JC_COUNT_TBL.txt` |
| `CONTROL_RECORD_TABLE` | SQL-CRTIO-008..015 (dynamic SELECT/INSERT/UPDATE/DELETE) | `database/descriptions/describe CONTROL_RECORD_TABLE.txt` |
| (session level) | SQL-DBIO-007 (CONNECT), SQL-DBIO-008/-010 (COMMIT), SQL-DBIO-009 (ROLLBACK), SQL-LABD20-007 (COMMIT WORK) | — |

---

## 6. Transaction-boundary summary (end-to-end LABD20 run)

```
SQL-DBIO-007   CONNECT :USR-PWD-DBNAME            ← session open, txn 1 begins
  ... LABD20 reads CARDFILE, COMMENT-FILE ...
  for each accepted record:
    SQL-LABD20-004  SELECT … JC_SUBMITTED_COMMENT_TBL    (duplicate check)
    SQL-LABD20-005  INSERT INTO JC_SUBMITTED_COMMENT_TBL (TO_DATE)
  (after loop)
  SQL-LABD20-006   UPDATE JC_COUNT_TBL WHERE JC_SECTION = 'MA'
  SQL-LABD20-007   COMMIT WORK                    ← txn 1 commits
  SQL-LABD20-008   SELECT COUNT(*) JC_SUBMITTED_COMMENT_TBL   (txn 2)
  SQL-LABD20-009   SELECT COUNT(*) JC_REJECTED_COMMENT_TBL
  SQL-LABD20-010   SELECT COUNT(*) JC_APPLIED_COMMENT_TBL
  -- normal exit: no explicit COMMIT/ROLLBACK; SELECT-only txn ends implicitly

ERROR PATH (from any of LABD20-004..-010):
  GO TO 9999-ROLL-BACK
  CALL 'DBIO' with DB-FUNCTION='ROLLBACK', DB-FUNCTION-TYPE='DEPART'
  → SQL-DBIO-009   EXEC SQL ROLLBACK              ← txn 1 (or 2) rolls back
  STOP RUN with RETURN-CODE = 99
```

**One-and-only-one** COMMIT (`SQL-LABD20-007`) in the LABD20 happy path; **one-and-only-one** ROLLBACK route (`9999-ROLL-BACK` → `SQL-DBIO-009`).

For LABA05 (out of scope for this catalog but cited by the dependency map), all commits and rollbacks go through DBIO — there is no `EXEC SQL` inside LABA05 itself.

---

## 7. Counts

| Metric | Count |
|---|---|
| Total `EXEC SQL` / `EXEC ORACLE` statements catalogued | 35 |
| LABD20.pco | 10 |
| CONTROL-RECORD-TABLE-IO.pco | 15 |
| DBIO.pco | 10 |
| DML statements (SELECT/INSERT/UPDATE/DELETE/FETCH) | 12 |
| Transaction-boundary statements (CONNECT/COMMIT/ROLLBACK) | 5 |
| Declarative (`BEGIN/END DECLARE SECTION`, `INCLUDE SQLCA`, `WHENEVER`, `EXEC ORACLE OPTION`) | 16 |
| Cursor lifecycle (`PREPARE`/`DECLARE CURSOR`/`OPEN`/`CLOSE`) | 4 |
| Statements using `:host` bind variables | 9 (LABD20-004, -005, -006, -008, -009, -010; CRTIO FETCH, INSERT, UPDATE, DELETE — note DELETE uses ROWID bind) |
| Statements using Oracle-specific constructs | 6 (LABD20-005 `TO_DATE`; CRTIO 007 OPTION, 011 ROWID in SELECT, 012 RETURNING, 013 SYSDATE + ROWID, 014 ROWID) |

---

## 8. Cross-references

- Risk register: [`../RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md) — specifically [Risk 6](../RISKS-AND-GAPS.md#risk-6--sqlcode--dms-style-return-code-translation) (SQLCODE→DMS translation) and [Risk 7](../RISKS-AND-GAPS.md#risk-7--transaction-rollback-paths-sql-and-dms) (transaction rollback paths).
- Assumptions: [`../ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md) — [A-5](../ASSUMPTIONS-AND-PLACEHOLDERS.md#a-5--section-ma-is-the-only-section-updated-by-post-process) (the literal `'MA'` in SQL-LABD20-006).
- Dependency map (Phase 1A baseline): [`../../analysis/dependency-map.md`](../../analysis/dependency-map.md).
- Table schemas: `database/descriptions/describe *.txt`.
