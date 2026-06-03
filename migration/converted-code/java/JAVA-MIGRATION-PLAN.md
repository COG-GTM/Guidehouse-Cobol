# JV COBOL/Pro*COBOL → Java Migration Plan

> Derived output — demo / prep, pending SME review. Does not modify customer
> source under `source/`. Mirrors the existing Python "after" deliverable under
> `migration/converted-code/python/`, this time targeting **Java 17 + Maven**.

## 1. Scope

Port the four legacy JV batch artifacts (and their now-resolved DATECONV
subsystem) to a clean, runnable, unit-tested Java module. The Java port mirrors
the Python port module-for-module so a reviewer can diff the two side by side.

| Legacy source | Python analog | Java analog (this plan) |
| --- | --- | --- |
| `source/cobol/DATECONV.cbl` (42 DATESUB-FUNC) | `dateconv.py` | `DateConv.java`, `ConvDates.java` |
| `source/procobol/DBIO.pco` | `db_dispatcher.py` | `DbDispatcher.java`, `DispatcherResult.java`, `DemoSchema.java` |
| `source/procobol/LABD20.pco` | `labd20_loader.py` | `Labd20Loader.java`, `CommentRecord.java`, `LoaderStats.java` |
| `source/cobol/LABA05.cbl` | `laba05_reset.py` | `Laba05Reset.java`, `ResetOutcome.java` |
| `demo_app.py run` | `demo_app.py` | `DemoApp.java` (CLI `run`) |

## 2. Translation strategy (per knowledge "COBOL-to-Modern-Language Migration Patterns")

- **Program → class, paragraph → method.** Each COBOL paragraph keeps its name
  (camelCased) and a source citation comment so traceability survives.
- **Fixed-width records → explicit substring offsets.** `TST123-COMMENT-REC`
  (300 bytes) and `JV-CONTROL-REC` `CONTROL_RECORD_DATA` (400 bytes) are parsed
  by the same byte offsets the Python port uses (cited from `LABD20.pco:43-55`
  and `JV-CONTROL-REC.cpy`).
- **Dynamic `DBIO` dispatch → typed JDBC methods.** The runtime
  string-concatenation that builds `<table>-IO` (`DBIO.pco:228-260`) is
  intentionally **not** reproduced (see RISKS Risk 4). Callers use typed
  methods; `countRows` keeps the table allowlist guard.
- **SQLCODE → DMS translation** reproduced verbatim from `DBIO.pco:374-398`.
- **DATECONV date math** reduces (as the 2012 IAI migration did) to
  `INTEGER-OF-DATE` / `DATE-OF-INTEGER` / `INTEGER-OF-DAY` / `DAY-OF-INTEGER`,
  implemented with `java.time.LocalDate` (proleptic Gregorian), preserving the
  two distinct century-inference thresholds (>52 local vs >72 JDN-ACC).
- **No credentials / no hardcoded secrets.** DB config is env-driven
  (`JV_DB_*`); the demo uses an in-memory **H2** database (pure-Java, zero
  external setup) standing in for Oracle.

## 3. Build & test

- `pom.xml` — Java 17, JUnit 5, H2 (in-memory), maven-surefire.
- JUnit suites mirror the pytest suites: DATECONV (all 40 active funcs + edge
  cases), LABD20 loader (layout, validation, duplicate, end-to-end on the
  21-record synthetic file), LABA05 reset (byte layout, round-trip, error
  paths), and schema/DMS-translation parity.
- Reuses the existing synthetic, non-production fixtures under
  `migration/test-data/` — no real customer data.
- Validation: `mvn test`.

## 4. Explicitly out of scope (initial Java port)

- The HTML dashboard / `serve` mode and the 38-BR `parity_engine` from
  `demo_app.py` (Python remains the canonical demo harness).
- Real Oracle wiring (documented placeholder; flip `JV_DB_BACKEND=oracle`).
- The `JV-NUMBER USAGE BINARY` production conversion (demo uses display form,
  same as Python — see RISKS Risk 2).

## 5. Deliverable layout

```
migration/converted-code/java/
├── JAVA-MIGRATION-PLAN.md        (this file)
├── README.md
├── pom.xml
└── src/
    ├── main/java/com/cognition/jvmigration/
    │   ├── dateconv/{ConvDates,DateConv}.java
    │   ├── db/{DispatcherResult,DbDispatcher,DemoSchema}.java
    │   ├── labd20/{CommentRecord,LoaderStats,Labd20Loader}.java
    │   ├── laba05/{ResetOutcome,Laba05Reset}.java
    │   └── DemoApp.java
    └── test/java/com/cognition/jvmigration/...
```
