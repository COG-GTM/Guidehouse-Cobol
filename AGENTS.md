# Agent Instructions

This repo is a private Guidehouse/SBA COBOL modernization demo workspace.

## Mission

Guidehouse requested a targeted follow-up demo using the supplied COBOL assets to show that Devin/Windsurf can:

1. Ingest and analyze COBOL, Pro*COBOL, copybooks, SQL/table descriptions, control files, and support scripts.
2. Derive business requirements and underlying logic.
3. Generate structured business requirements with traceability.
4. Demonstrate conversion paths to Oracle SQL and/or Python.
5. Produce dependency graphs, field-level lineage, test scenarios, scripts, and conversion-efficiency views.

This repo is an internal prep workspace, not a polished customer deliverable unless explicitly stated.

## Authoritative Inputs

Treat these as customer-provided inputs:

- `source/cobol/`
- `source/procobol/`
- `source/copybooks/`
- `source/perl/`
- `database/descriptions/`
- `database/sql/`
- `test-data/`

Do not modify customer-supplied source files under `source/` unless explicitly asked. Prefer adding derived artifacts under `analysis/`, `business-requirements/`, `converted-code/`, and `docs/`.

## Primary Source Files

- `source/cobol/LABA05.cbl`
  - Fiscal-year reset program.
  - Resets `JV-NUMBER` on `JV-CONTROL-REC` to `1`.
  - Uses `DBIO` for connect/select/update/rollback-style database access.

- `source/procobol/LABD20.pco`
  - Daily JV comment ingestion program.
  - Reads external `COMMENT` file and `CARDFILE`.
  - Parses fixed-width `TST123-COMMENT-REC`.
  - Validates comment date, JV number, section id, loan number, comment text, requestor, and approver.
  - Checks `JC_SUBMITTED_COMMENT_TBL` for duplicates.
  - Inserts accepted comments into `JC_SUBMITTED_COMMENT_TBL`.
  - Updates `JC_COUNT_TBL`.
  - Reports submitted/rejected/applied table counts.
  - Rolls back on SQL/DMS error paths.

- `source/procobol/DBIO.pco`
  - Oracle connection/transaction dispatcher.
  - Uses legacy runtime credential files `/tst/.oralogin` and `/tst/.orapasswd`; do not reproduce this pattern in modernized examples.

- `source/procobol/CONTROL-RECORD-TABLE-IO.pco`
  - Table-specific CRUD module for `CONTROL_RECORD_TABLE`.

## Known Missing Artifacts

> ~~`LABD20.pco` references these copybooks, but they were not supplied: `DATECONV-WS`, `DATECONV-PD`. Always call this out in requirements, lineage, conversion, tests, and confidence notes. Exact legacy date-validation fidelity cannot be fully verified without these copybooks.~~
>
> **Resolved 2026-05-21 (customer follow-up shipment).** Guidehouse supplied the full date-conversion subsystem closure: `source/copybooks/DATECONV-WS.cpy`, `source/copybooks/DATECONV-PD.cpy`, `source/cobol/DATECONV.cbl` (the `PROGRAM-ID. DATECONV` subprogram itself), and its four internal JDN helpers (`JDN-CONSTANTS-WS.cpy`, `JDN-PACKET-WS.cpy`, `JDN-RECORD-WS.cpy`, `JDN-RECORD-ACCESS.cpy`). Every `COPY` and `CALL` originating from `LABD20.pco` now resolves end-to-end. The IAI-2012 migration markers (`MIGRTN` comment column) inside `DATECONV.cbl` are preserved verbatim — see [`analysis/dateconv-function-inventory.md`](./analysis/dateconv-function-inventory.md) for the 42-function inventory, `DATESUB-FUNC` codes, and intrinsic-function mapping.
>
> **Effect on the demo:** [Risk 1](./migration/RISKS-AND-GAPS.md#risk-1) → CLOSED. [Assumption A-1](./migration/ASSUMPTIONS-AND-PLACEHOLDERS.md#a-1) → retired. [`BR-LABD20-006`](./migration/business-requirements/requirements-with-citations.md) → confidence LOW → HIGH. The Python `check_cymd_dt` stub in `migration/converted-code/python/labd20_loader.py` is replaced by a faithful port (`migration/converted-code/python/dateconv.py`).
>
> **Important:** the customer-supplied artifacts under `source/` are treated as the "before" (frozen, verbatim) state. The modernized Python port under `migration/converted-code/python/` is the "after" state. Do not edit the source copybooks; record any analytical commentary in `analysis/`, `business-requirements/`, or `migration/`.

## Required Output Locations

Use these paths for generated work:

- Requirements: `business-requirements/`
- Dependency and lineage analysis: `analysis/`
- SQL/Python modernization examples: `converted-code/`
- Customer/demo narrative: `docs/`
- Prompts for Cloud Devin execution: `prompts/`

Suggested high-value deliverables:

- `business-requirements/requirements-with-citations.md`
- `analysis/field-lineage.md`
- `analysis/dependency-map-detailed.md`
- `converted-code/sql/labd20_extracted_operations.sql`
- `converted-code/python/labd20_loader.py`
- `converted-code/python/tests/`
- `docs/customer-demo-script.md`
- `docs/customer-open-questions-response-draft.md`
- `docs/test-scenarios.md`

## Citation Requirements

For analysis, requirements, conversion notes, and customer-facing artifacts:

- Cite source paths and line ranges.
- Distinguish direct source facts from inferred behavior.
- Include confidence notes when source is incomplete, dynamic, generated, or missing dependencies.
- Prefer source citations from:
  - `source/procobol/LABD20.pco`
  - `source/cobol/LABA05.cbl`
  - `source/procobol/DBIO.pco`
  - `source/procobol/CONTROL-RECORD-TABLE-IO.pco`
  - `database/descriptions/*.txt`

## COBOL Modernization Rules

- Resolve copybook and embedded SQL references before deriving behavior.
- Pay close attention to:
  - fixed-width record layouts,
  - `REDEFINES`,
  - `PIC` clauses,
  - numeric formats,
  - binary/COMP usage,
  - line sequential vs other file organization,
  - external file assignments,
  - dynamic dispatch through `DBIO`,
  - legacy Oracle Pro*COBOL behavior.
- Do not overstate unsupported behavior. Mark unresolved items explicitly.

## Guidehouse Question Themes

When preparing demo artifacts, address these customer concerns:

- Can documentation be generated after modernization?
- How are `COMP`, `COMP-X`, `BINARY`, and COBOL-specific data definitions handled?
- How are record sequential, line sequential, indexed, and variable-length files handled?
- Can Perl scripts, sqlplus, and sqlldr be understood as part of the dependency graph?
- How much COBOL SME review is required?
- Can COBOL be converted to Oracle SQL and/or Python?
- Can dependencies be resolved across copybooks, includes, jobs, and runtime calls?
- Can field-level lineage be traced across programs/files/tables?
- How are dynamic calls handled?
- Is there an intermediate/canonical model?
- How is confidence measured and exposed?
- What constructs require manual remediation?
- Can test scenarios and scripts be generated?
- How are execution location, security, FedRAMP, and agent orchestration explained?

Use `docs/guidehouse-open-questions.md` as the authoritative question list.

## Conversion Guidance

For SQL examples:

- Extract embedded SQL from `LABD20.pco`.
- Extract CRUD behavior from `CONTROL-RECORD-TABLE-IO.pco`.
- Convert dynamic or string-built SQL into parameterized examples.
- Include source citations in SQL comments.
- Do not hardcode credentials.

For Python examples:

- Use explicit fixed-width parsing for `TST123-COMMENT-REC`.
- Model validation, duplicate detection, inserts, count update, commit, and rollback.
- Use configuration/secrets placeholders for DB connectivity.
- Include tests with synthetic non-production data.
- Make clear that generated code is demo output pending SME review.

## Security Constraints

- Keep this repository private.
- Do not commit credentials, tokens, runtime Oracle login files, or customer secrets.
- Do not hardcode DB passwords, API keys, or tokens.
- Modernized DB code must use parameterized queries and managed configuration/secrets.
- Never log secrets or sensitive data.
- Treat customer/sample data as sensitive unless explicitly cleared.
- Generated services should default-deny access, use generic external errors, and include audit logging for security-relevant operations.

## Verification

For documentation-only changes:

- Ensure markdown tables render correctly.
- Ensure source citations use valid file paths and line ranges.
- Ensure generated artifacts do not modify original `source/` files.

For generated Python code:

- Add unit tests using synthetic non-production fixtures.
- Run the fastest relevant tests available.
- If no full runtime is available, document assumptions and what was not executed.

For generated SQL:

- Use parameterized placeholders.
- Validate syntax by inspection if no Oracle runtime is available.
- Document execution assumptions.

## Branching/PR Expectations

For Cloud Devin work:

- Create a feature branch.
- Commit generated artifacts.
- Open a PR back to `main` when possible.
- In the PR body include:
  - summary,
  - generated artifact list,
  - source/citation approach,
  - test/validation performed,
  - assumptions/risks,
  - missing artifact notes.
