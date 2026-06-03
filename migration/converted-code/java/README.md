# JV COBOL → Java Modernized Port

> Derived demo output, pending SME review. Does **not** modify customer source
> under `source/`. This is a Java 17 + Maven re-implementation of the four legacy
> JV batch artifacts, mirroring the existing Python port under
> `migration/converted-code/python/` module-for-module so the two can be diffed
> side by side. See [`JAVA-MIGRATION-PLAN.md`](./JAVA-MIGRATION-PLAN.md) for the
> migration strategy.

## What this is

A clean, runnable, unit-tested Java translation of the JV comment-loading batch
suite and its date-conversion subsystem:

| Legacy source | Python analog | Java analog |
| --- | --- | --- |
| `source/cobol/DATECONV.cbl` (42 `DATESUB-FUNC`) | `dateconv.py` | `dateconv/DateConv.java`, `dateconv/ConvDates.java` |
| `source/procobol/DBIO.pco` | `db_dispatcher.py` | `db/DbDispatcher.java`, `db/DispatcherResult.java`, `db/DemoSchema.java` |
| `source/procobol/LABD20.pco` | `labd20_loader.py` | `labd20/Labd20Loader.java`, `labd20/CommentRecord.java`, `labd20/LoaderStats.java`, `labd20/LoaderConfig.java` |
| `source/cobol/LABA05.cbl` | `laba05_reset.py` | `laba05/Laba05Reset.java`, `laba05/ResetOutcome.java` |
| `demo_app.py run` | `demo_app.py` | `demo/DemoApp.java` |

Every class carries a source-citation comment linking it back to the COBOL/
Pro*COBOL line ranges (and to the Python analog) it was derived from.

## Module map

- **`dateconv/`** — `DATECONV` subprogram. `DateConv` is the dispatch engine
  (40 active `DATESUB-FUNC` codes; 29/30 reserved), reducing all date math to
  `INTEGER-OF-DATE`/`DATE-OF-INTEGER`/`INTEGER-OF-DAY`/`DAY-OF-INTEGER` over
  `java.time.LocalDate`, preserving the two century-inference thresholds
  (`>52` local, `>72` JDN-ACC). `ConvDates` is the mutable parameter block
  (`DATECONV-PD`).
- **`db/`** — `DBIO` analog. `DbDispatcher` wraps JDBC with parameterized
  statements and reproduces the `SQLCODE → DMS` translation table verbatim
  (`DBIO.pco:374-398`). The runtime string-concatenation dynamic dispatch
  (`DBIO.pco:228-260`) is intentionally **not** reproduced; callers use typed
  methods and `countRows` keeps a table allowlist guard. `DemoSchema` builds the
  in-memory demo schema and seeds the control record.
- **`labd20/`** — daily JV comment loader. Fixed-width parsing of the 300-byte
  `TST123-COMMENT-REC` by explicit offsets, the full validation pipeline,
  duplicate detection, insert, count update, commit, and rollback-on-error.
- **`laba05/`** — fiscal-year reset. Extracts/replaces `JV-NUMBER` in the
  400-byte `CONTROL_RECORD_DATA` blob and resets it to `1`.
- **`demo/`** — `DemoApp` CLI (`run` subcommand): seeds an H2 database, runs
  LABA05 then LABD20 against the synthetic fixtures, and prints the report.

## Build, test, run

Requires JDK 17+ and Maven. From this directory
(`migration/converted-code/java/`):

```bash
mvn clean test        # compile + run the JUnit 5 suites (129 tests)
mvn exec:java         # run the end-to-end demo (DemoApp `run`)
```

Expected demo output (matches the Python demo exactly):

```
Process date           : 20260115
LABA05 fiscal-year reset
  return code          : 0
  JV-NUMBER before     : 99
  JV-NUMBER after      : 1
LABD20 comment loader
  records read         : 21
  inserted             : 7
  duplicates           : 2
  rejected             : 12
  submitted total      : 7
```

## Tests

JUnit 5 suites mirror the pytest suites and run only against the synthetic,
non-production fixtures under `migration/test-data/`:

- `DateConvTest` — all 40 active `DATESUB-FUNC` codes, dispatch, and edge cases.
- `Labd20LoaderTest` — record layout, parsing, the validation rules, the
  process-date card file, `SQLCODE` translation, and end-to-end runs over the
  21-record synthetic file (inserts/duplicates/rejects, truncate, rollback).
- `Laba05ResetTest` — `JV-NUMBER` byte layout, binary↔display round-trip, and
  the missing-row / failed-update / SQL-exception error paths.
- `DbDispatcherTest` — the `SQLCODE → DMS` table plus insert/select/commit/
  rollback behavior.

```
Tests run: 129, Failures: 0, Errors: 0, Skipped: 0
```

## Configuration & security

- DB connectivity is environment-driven (`JV_DB_BACKEND`, and for Oracle
  `JV_DB_DSN`/`JV_DB_USER`/`JV_DB_PASSWORD`). **No credentials are hardcoded**
  and the legacy `/tst/.oralogin` / `/tst/.orapasswd` file pattern from
  `DBIO.pco` is deliberately not reproduced.
- The demo defaults to a fresh in-memory **H2** database (pure-Java, zero
  setup) standing in for Oracle. Oracle wiring is a documented placeholder
  (`JV_DB_BACKEND=oracle`) and is not exercised here (no Oracle driver/instance).
- All SQL uses parameterized statements.

## Out of scope (this port)

- The HTML dashboard / `serve` mode and the 38-BR parity engine from the Python
  `demo_app.py` (Python remains the canonical demo harness).
- Real Oracle execution (placeholder only).
- The `JV-NUMBER USAGE BINARY` production conversion — the demo uses the display
  form, same as the Python port (see `migration/RISKS-AND-GAPS.md` Risk 2).

## Assumptions & risks

This port preserves the same assumptions and risks documented for the Python
port in
[`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../../ASSUMPTIONS-AND-PLACEHOLDERS.md)
and [`migration/RISKS-AND-GAPS.md`](../../RISKS-AND-GAPS.md). Generated code is
demo output pending COBOL SME review.
