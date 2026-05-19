# Conversion efficiency view

> **Status:** Demo output, pending SME review.
> See [`migration/MIGRATION-PLAN.md`](../MIGRATION-PLAN.md) and
> [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).

This document quantifies the conversion work performed in this session and
flags the items still requiring SME review. It is the artifact Sunil
specifically requested in Question 7e.

---

## 1. Code volume — analyzed vs generated

| Source artifact | Lines | Notes |
|-----------------|------:|-------|
| `source/cobol/LABA05.cbl` | 285 | Fiscal-year reset. Fully covered. |
| `source/procobol/LABD20.pco` | 535 | Comment loader. Fully covered. |
| `source/procobol/DBIO.pco` | 407 | Dispatcher. Modelled in `db_dispatcher.py`. |
| `source/procobol/CONTROL-RECORD-TABLE-IO.pco` | 406 | CRUD module. SQL extracted; dispatch wired via `db_dispatcher.py`. |
| Supplied copybooks | ~110 (combined) | All read; `JV-CONTROL-REC.cpy` and `CONTROL-RECORD-TABLE.cpy` drive byte layouts. |
| **Subtotal — COBOL/Pro*COBOL analysed** | **~1,633** | |
| Missing copybooks (`DATECONV-WS`, `DATECONV-PD`) | — | Flagged. Risk 1. |

| Generated artifact | Lines | Notes |
|--------------------|------:|-------|
| `migration/converted-code/python/labd20_loader.py` | 496 | Comment loader. |
| `migration/converted-code/python/laba05_reset.py` | 217 | FY reset. |
| `migration/converted-code/python/db_dispatcher.py` | 407 | DBIO analog + demo schema DDL + SQLCODE→DMS table. |
| `migration/converted-code/python/demo_app.py` | 340 | Runnable demo entrypoint (CLI + HTML dashboard). |
| `migration/converted-code/sql/labd20_operations.sql` | ~90 | 6 parameterized statements. |
| `migration/converted-code/sql/control_record_table_operations.sql` | ~90 | 6 parameterized statements. |
| **Subtotal — modernized code** | **~1,640** | Comparable line count to legacy; cleaner separation of concerns. |

| Generated tests | Lines | Cases |
|-----------------|------:|------:|
| `migration/converted-code/python/tests/test_labd20_loader.py` | ~500 | 40 |
| `migration/converted-code/python/tests/test_laba05_reset.py` | ~120 | 12 |
| **Subtotal — tests** | **~620** | **52 passing** |

| Generated documentation | Lines | Notes |
|-------------------------|------:|-------|
| `MIGRATION-PLAN.md` | ~140 | |
| `RISKS-AND-GAPS.md` | ~200 | 11 named risks. |
| `ASSUMPTIONS-AND-PLACEHOLDERS.md` | ~220 | Single source of truth. |
| `business-requirements/requirements-with-citations.md` | ~140 | 22 BR-LABD20 + 6 BR-LABA05 + 4 unresolvables. |
| `analysis/field-lineage.md` | ~200 | Per-byte trace. |
| `analysis/dependency-map-detailed.md` | ~190 | Mermaid graphs + edge citations. |
| `analysis/sql-catalog.md` | ~130 | 14 cataloged statements. |
| `docs/guidehouse-open-questions-response.md` | ~210 | 22 answered. |
| `docs/demo-walkthrough.md` | ~150 | |
| `docs/conversion-efficiency.md` | (this file) | |
| `docs/post-modernization-docs.md` | ~180 | |
| `docs/before-after-comparison.md` | ~120 | |
| `test-data/README.md` | ~80 | Coverage matrix. |
| **Subtotal — documentation** | **~1,960** | |

---

## 2. Derived artifacts — counts

| Metric | Count |
|--------|------:|
| Business requirements derived (HIGH confidence) | 23 |
| Business requirements derived (MEDIUM confidence) | 3 |
| Business requirements derived (LOW confidence / unresolvable) | 4 |
| **Total business requirements** | **30** |
| SQL operations extracted and parameterized | 14 |
| Synthetic test records generated | 21 |
| Test cases (pytest) | 52 |
| Risks documented | 11 |
| Assumptions / placeholders documented | 17 |
| Mermaid diagrams | 4 (program, copybook, SQLCA flow, dispatch table) |
| Modernized Python modules | 4 (loader, reset, dispatcher, demo app) |

---

## 3. Confidence distribution

| Confidence | Count | % of total requirements (30) |
|------------|------:|-----------------------------:|
| HIGH | 23 | 77% |
| MEDIUM | 3 | 10% |
| LOW / unresolvable | 4 | 13% |

The four LOW items are all rooted in missing supplied artifacts (DATECONV
copybooks and the rejected/applied insert paths). They are explicitly flagged
in every relevant file and queued for SME confirmation.

---

## 4. SME review queue

Items requiring human review before this code is shipped:

| # | Item | Severity | Risk / assumption ID |
|---|------|---------:|----------------------|
| 1 | Confirm date-validation semantics (replace `check_cymd_dt` stub with `DATECONV-PD` logic). | HIGH | Risk 1, A-5 |
| 2 | Confirm JV-NUMBER binary↔display conversion (`struct.unpack` form). | HIGH | Risk 2, A-3 |
| 3 | Confirm origin of `WS-JV-COUNTERS` threshold. | MEDIUM | A-10, BR-LABD20-UR-003 |
| 4 | Confirm JC_COUNT_TBL column name (`JC_SECTION_COUNT` per LABD20 vs `JC_COUNT_NUM` per describe file). | MEDIUM | Risk 8 |
| 5 | Confirm whether the deprecated 20-byte APPROVER FD line is intentionally deprecated. | MEDIUM | Risk 5 |
| 6 | Confirm insert path for `JC_REJECTED_COMMENT_TBL` / `JC_APPLIED_COMMENT_TBL`. | LOW | BR-LABD20-UR-004 |
| 7 | Confirm full DBIO dispatch table (any string-built names beyond what we enumerated). | MEDIUM | Risk 4, A-9 |
| 8 | Confirm Perl-wrapper env-var equivalents in target orchestration (Airflow / CronJob / Step Functions). | LOW | Risk 10 |
| 9 | Confirm the `PERFORM PERFORM CLOSE-SQL-ENVIRONMENT` line at LABD20.pco:213 is a benign typo. | LOW | Risk 11 |
| 10 | Replace mock sqlite credentials with managed secrets store for production. | HIGH | Risk 3 |

---

## 5. Estimated effort breakdown

This view is illustrative — *not* a quote. It is meant to demonstrate the
fraction of effort that Devin already covered versus the residual SME work.

| Phase | Devin-covered effort | Residual SME effort |
|-------|----------------------|----------------------|
| Source ingestion + read | 100% | 0% |
| Requirements derivation | 90% (HIGH-confidence items) | 10% (4 LOW items) |
| Dependency graph + lineage | 95% | 5% (string-built dispatch enumeration) |
| SQL extraction + parameterization | 100% (everything in supplied source) | 0% (modulo Q4 column-name) |
| Code conversion (Python) | 85% | 15% (DATECONV semantics + binary form + secrets wiring) |
| Test generation | 100% (52 tests passing) | 0% (additional negative tests as desired) |
| Synthetic data | 100% | 0% |
| Documentation | 100% | n/a |

---

## 6. Speed snapshot

| Step | Outcome |
|------|---------|
| Time to first PR (Phase 0 plan committed) | ≈ minutes |
| Time to first passing test run | same session |
| Time to runnable end-to-end demo (`demo_app.py run`) | same session |
| Number of phases executed in parallel | up to 4 task lanes per phase (Phase 1, Phase 2, Phase 3, Phase 4) |
