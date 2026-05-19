# JV COBOL modernization — generated artifact index

> **Status:** Demo output, pending SME review.
> This directory is the full output of a single modernization session. Nothing
> under `source/` was modified.

## Quick start

```bash
# Run the modernized loader + reset end-to-end against synthetic data
# (in-memory mock Oracle; zero external setup).
python3 migration/converted-code/python/demo_app.py run

# Same thing, but with an HTML dashboard at http://127.0.0.1:8765
python3 migration/converted-code/python/demo_app.py serve

# Run the test suite
python3 -m pytest migration/converted-code/python/tests/ -v
```

## Read these first

| File | What it is |
|------|------------|
| [`MIGRATION-PLAN.md`](MIGRATION-PLAN.md) | The 5-phase plan + status board. |
| [`RISKS-AND-GAPS.md`](RISKS-AND-GAPS.md) | 11 named risks with mitigations. |
| [`ASSUMPTIONS-AND-PLACEHOLDERS.md`](ASSUMPTIONS-AND-PLACEHOLDERS.md) | Single source of truth for every assumption, stub, and placeholder. |
| [`executive-report.html`](executive-report.html) | Cognition-branded one-page report. Open in a browser. |

## Analysis (Phase 1)

| File | What it is |
|------|------------|
| [`business-requirements/requirements-with-citations.md`](business-requirements/requirements-with-citations.md) | 30 business requirements, each cited to source file:line, with HIGH/MEDIUM/LOW confidence. |
| [`analysis/field-lineage.md`](analysis/field-lineage.md) | Per-byte trace from input file → working storage → Oracle column. |
| [`analysis/dependency-map-detailed.md`](analysis/dependency-map-detailed.md) | Mermaid diagrams for program, copybook, SQLCA flow, dispatch table. |
| [`analysis/sql-catalog.md`](analysis/sql-catalog.md) | 14 cataloged `EXEC SQL` statements with bind variables, transaction boundaries, Oracle-specific flags. |

## Converted code (Phase 2)

| File | What it is |
|------|------------|
| [`converted-code/python/labd20_loader.py`](converted-code/python/labd20_loader.py) | Daily comment loader (analog of LABD20). 496 lines. |
| [`converted-code/python/laba05_reset.py`](converted-code/python/laba05_reset.py) | Fiscal-year JV-NUMBER reset (analog of LABA05). 217 lines. |
| [`converted-code/python/db_dispatcher.py`](converted-code/python/db_dispatcher.py) | Connection lifecycle, transaction control, SQLCODE→DMS translation, demo schema DDL. 407 lines. |
| [`converted-code/python/demo_app.py`](converted-code/python/demo_app.py) | Runnable end-to-end (CLI + HTML dashboard). |
| [`converted-code/sql/labd20_operations.sql`](converted-code/sql/labd20_operations.sql) | 6 parameterized statements extracted from LABD20.pco. |
| [`converted-code/sql/control_record_table_operations.sql`](converted-code/sql/control_record_table_operations.sql) | 6 parameterized statements extracted from CONTROL-RECORD-TABLE-IO.pco. |

## Tests & data (Phase 3)

| File | What it is |
|------|------------|
| [`converted-code/python/tests/test_labd20_loader.py`](converted-code/python/tests/test_labd20_loader.py) | 40 pytest cases. |
| [`converted-code/python/tests/test_laba05_reset.py`](converted-code/python/tests/test_laba05_reset.py) | 12 pytest cases. |
| [`test-data/synthetic_comments.dat`](test-data/synthetic_comments.dat) | 21 synthetic 300-byte records. |
| [`test-data/synthetic_card.ctl`](test-data/synthetic_card.ctl) | Process-date card file (`01/15/2026`). |
| [`test-data/README.md`](test-data/README.md) | Per-record coverage matrix. |
| [`test-results/pytest-output.txt`](test-results/pytest-output.txt) | Captured pytest output (52 passing). |

## Customer-facing (Phase 4)

| File | What it is |
|------|------------|
| [`docs/guidehouse-open-questions-response.md`](docs/guidehouse-open-questions-response.md) | Answers to all 22 questions from `docs/guidehouse-open-questions.md`. |
| [`docs/demo-walkthrough.md`](docs/demo-walkthrough.md) | Speaker notes — short sentences, conversational. |
| [`docs/conversion-efficiency.md`](docs/conversion-efficiency.md) | Code volume, derived-artifact counts, confidence distribution, SME-review queue. |
| [`docs/post-modernization-docs.md`](docs/post-modernization-docs.md) | Documentation generated *from the modernized Python*, not from the COBOL (Jill's Q1). |
| [`docs/before-after-comparison.md`](docs/before-after-comparison.md) | Side-by-side COBOL → Python → test traceability matrix. |

## Reading order for a 30-minute demo

1. `executive-report.html` — orient the audience.
2. `MIGRATION-PLAN.md` — show the structure.
3. `business-requirements/requirements-with-citations.md` — show traceability.
4. `analysis/field-lineage.md` + `analysis/dependency-map-detailed.md` — Srinjoy/Charles concerns.
5. `converted-code/python/labd20_loader.py` (open `check_cymd_dt` and `_insert`).
6. Run `demo_app.py run`.
7. `docs/conversion-efficiency.md` — Sunil's Q7e.
8. `docs/guidehouse-open-questions-response.md` — group review.
9. `docs/post-modernization-docs.md` — Jill's Q1.
10. `ASSUMPTIONS-AND-PLACEHOLDERS.md` — close on honesty.

## Reproducibility

This entire directory is generated by Devin from the supplied source under
`source/` plus the question list at `docs/guidehouse-open-questions.md`. No
external systems are required to validate or re-run the demo. All test data
is synthetic.
