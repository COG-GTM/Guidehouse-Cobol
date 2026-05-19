# Guidehouse COBOL Modernization Demo

Private working repository for the Guidehouse/VA COBOL modernization follow-up demo.

> **Generated migration output:** see [`migration/`](migration/) for the full
> end-to-end deliverable — plan, risk register, business requirements with
> citations, field lineage, dependency map, SQL catalog, modernized Python +
> Oracle SQL, synthetic test data, 52 passing pytest cases, customer-facing
> docs, an executive HTML report, and a runnable demo entrypoint
> (`migration/converted-code/python/demo_app.py`).

## Demo Objective

Guidehouse requested a targeted demo using the supplied COBOL assets to show that Devin/Windsurf can:

1. Ingest and analyze COBOL, Pro*COBOL, copybooks, SQL, control files, and support scripts.
2. Derive business requirements and underlying processing logic.
3. Generate structured business requirements and traceability artifacts.
4. Demonstrate conversion patterns to Oracle SQL and/or Python.
5. Produce dependency, data-flow, test-plan, and conversion-efficiency views.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `source/cobol/` | Original COBOL source programs. |
| `source/procobol/` | Pro*COBOL / embedded SQL programs and generated DB access modules. |
| `source/copybooks/` | COBOL copybooks supplied in the zip. |
| `source/perl/` | Perl wrapper / execution scripts supplied in the zip. |
| `database/sql/` | SQL files supplied in the zip. |
| `database/descriptions/` | Table description notes and sample rows. |
| `test-data/` | Control files and sample data files supplied in the zip. |
| `docs/` | Demo context, customer questions, source inventory, and execution plan. |
| `analysis/` | Derived dependency and data-flow analysis artifacts. |
| `business-requirements/` | Derived functional/business requirements. |
| `converted-code/` | Placeholder for SQL/Python modernization outputs. |

## Source Snapshot

The supplied assets center on a VA JV comment-processing workflow:

- `LABA05` resets `JV-NUMBER` on `JV-CONTROL-REC` to `1` at fiscal-year rollover.
- `LABD20` reads comment records from a daily comment file, validates required fields, checks for duplicates, inserts accepted records into `JC_SUBMITTED_COMMENT_TBL`, and updates `JC_COUNT_TBL`.
- `DBIO` dispatches database operations to table-specific modules.
- `CONTROL-RECORD-TABLE-IO` maps legacy control-record access to Oracle `CONTROL_RECORD_TABLE` CRUD operations.

## Known Gaps In Supplied Source

`LABD20.pco` references `COPY DATECONV-WS` and `COPY DATECONV-PD`, but those copybooks were not present in the provided zip. Any compile/run reproduction will need either the missing copybooks or a stub of their date-validation routines.

## Security Notes

- Keep this repository private; the contents were provided for a customer-specific demo.
- Do not commit credentials or runtime files such as `.oralogin` or `.orapasswd`.
- Any generated modernization code should use parameterized database access and avoid hardcoded secrets.
- Treat all customer/sample data as sensitive unless explicitly cleared for broader sharing.

## Suggested Demo Flow

1. Open with repository indexing and source inventory.
2. Show dependency map from programs to copybooks, DB modules, tables, files, and scripts.
3. Walk through `LABD20` business rules and generated requirements.
4. Show proposed SQL/Python conversion approach for `LABD20` and `LABA05`.
5. Generate tests from extracted validation and duplicate-detection rules.
6. Close with the open-question response themes in `docs/guidehouse-open-questions.md`.
