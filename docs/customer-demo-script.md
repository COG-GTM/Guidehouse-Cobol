# Guidehouse Customer Demo Script — SBA COBOL Modernization

> Demo / prep output. ~45–60 minute walkthrough of the supplied JV comment-
> processing programs (`LABA05`, `LABD20`) and how Devin ingests, analyzes,
> generates requirements, traces data flow, converts logic to Oracle SQL /
> Python, generates tests, and exposes confidence around missing artifacts.
>
> Cross-references:
> - `docs/demo-plan.md` — high-level agenda
> - `docs/guidehouse-open-questions.md` — Q&A backbone
> - `business-requirements/requirements-with-citations.md` — citations
> - `analysis/field-lineage.md` — field-level lineage
> - `analysis/dependency-map.md` — program/file/table graph
> - `converted-code/sql/labd20_extracted_operations.sql` — parameterized SQL

## 0. Pre-Demo Checklist (5 min, before customer call)

- [ ] Confirm Devin session is connected to `COG-GTM/Guidehouse-Cobol`
      (default branch `main`).
- [ ] Confirm Devin org context is `US Federal`.
- [ ] Pull latest `main` so the analysis artifacts in `analysis/`,
      `business-requirements/`, `converted-code/`, and `docs/` reflect the
      most recent generated output.
- [ ] Have the original Guidehouse zip / source tree available for screen
      share so the audience sees we are working on **their** files.
- [ ] Have the deployment / security / FedRAMP talk track open in a side
      tab (do not improvise on those answers — Q18–Q20).
- [ ] Decide who answers each owner's questions (Charles, Jill, Margarita,
      Srinjoy, Sunil — see `docs/guidehouse-open-questions.md`).

## 1. Framing (3 min)

**Goal of this session.** Show how Devin takes the COBOL/Pro\*COBOL bundle
Guidehouse supplied and produces customer-ready, auditable modernization
artifacts: a system dependency graph, formal functional requirements with
source citations, field-level lineage into Oracle, parameterized Oracle SQL
extracted from the embedded EXEC SQL, and a generated test plan — with
explicit confidence and risk markers for anything missing.

**What we are not doing.** We are not pushing a finished, unsupervised
migration. The deliverables are designed for an "expert-in-the-loop"
workflow where COBOL SMEs at Guidehouse / SBA review high-signal diffs and
acceptance tests rather than line-by-line code reads.

**Customer artifacts in this session.**

| Deliverable | Path |
| --- | --- |
| Customer-ready requirements (with citations) | `business-requirements/requirements-with-citations.md` |
| Field-level lineage | `analysis/field-lineage.md` |
| Dependency map | `analysis/dependency-map.md` |
| Extracted parameterized Oracle SQL | `converted-code/sql/labd20_extracted_operations.sql` |
| This demo script | `docs/customer-demo-script.md` |

## 2. Repo Ingestion & Source Inventory (4 min)

Walk: `docs/source-inventory.md` → `analysis/dependency-map.md`.

**Talking points.**

- The Guidehouse zip contained COBOL (`LABA05.cbl`), Pro\*COBOL
  (`LABD20.pco`, `DBIO.pco`, `CONTROL-RECORD-TABLE-IO.pco`), 8 copybooks,
  2 Perl runner scripts, table description files, one SQL DDL fragment,
  and 2 test-data files.
- Devin indexed all of these together as one system; we don't analyze
  files in isolation. (Answers Q10.)
- The Perl wrappers (`source/perl/LABD20-JV.pl`, `source/perl/LABA05.pl`)
  are recognized as orchestration scripts that set environment variables
  (`COMMENT`, `CARDFILE`) and invoke the COBOL binary via `rtsora`.
  (Answers Q4.)
- The test data file `test-data/TST.JVCMTS.dat` is effectively empty
  (2 bytes). Live execution against real data needs synthetic input or a
  customer-provided sample.

## 3. Dependency Graph & Confidence (5 min)

Open `analysis/dependency-map.md` — show the Mermaid graph.

**Talking points.**

- Each node is a program, copybook, external file, or table; each edge
  is a resolved dependency. (Answers Q11, Q14.)
- Copybook resolution: 6 of 8 copybooks resolved (`COMCON`, `DBVAR`,
  `DMCA`, `DMCAERR`, `JV-CONTROL-REC`, `CONTROL-RECORD-TABLE`,
  `RDMS-ERR-WS`, `RDMS-ERR-RTN`). ~~**2 copybooks are missing:**
  `DATECONV-WS` and `DATECONV-PD`, referenced at
  `source/procobol/LABD20.pco:182, 531`.~~ **Resolved 2026-05-21:** `DATECONV-WS` and `DATECONV-PD` supplied in customer follow-up shipment (along with `DATECONV.cbl` + 4 JDN helpers). Devin caught the gap before the shipment arrived; dependency graph now closes end-to-end. (Answers Q9, Q15, Q16.)
- Dynamic call: `CALL 'DBIO' USING ...` is statically dispatched here
  (the literal `'DBIO'` is in source), but Devin also models heuristics
  for variable program-name calls; flagged with explicit confidence
  markers when seen. (Answers Q12.)
- Confidence model: every requirement and lineage row in the customer
  artifacts is tagged High / Medium / Low based on whether all
  dependencies resolved cleanly. (Answers Q15.)

## 4. Functional Requirements With Citations (10 min)

Open `business-requirements/requirements-with-citations.md`.

**Walk in this order.**

1. **LABA05 section (§1).** Show 9 requirements covering connect, fetch,
   reset to `1`, modify, display before/after, error stop, depart/commit,
   and the `USAGE BINARY` semantics for `JV-NUMBER`. Each requirement
   cites file + line range. (Answers Q1, Q2 — note the explicit
   `USAGE BINARY` / `PIC 9(6)` callout.)
2. **LABD20 section (§2).** Show 35 requirements grouped into
   housekeeping, read loop & per-record validation, duplicate detection,
   insert, count update, commit + EOJ stats + reporting, and rollback.
3. **Confidence & Risk (§3).** ~~Show the explicit "missing copybook" risk
   block,~~ **Show the closed "missing copybook" risk block (Risk 1 CLOSED 2026-05-21; DATECONV-WS/PD supplied)** along with the `TST123-COMMENT-APPROVER` width discrepancy, the
   `JC_COUNT_TBL` PK ambiguity, and the modernization note that
   `DBIO.pco` reads Oracle credentials from a file (`USRID`, `PASSWD`)
   that must be replaced by managed secrets. (Answers Q19.)

**Reverse-engineered tests preview.** Mention that Devin can generate
positive, negative, and edge-case test scenarios directly from these
requirements; we'll show one path in §7. (Answers Q17.)

## 5. Field-Level Lineage (8 min)

Open `analysis/field-lineage.md`.

**Walk in this order.**

1. **`TST123-COMMENT-REC` fixed-width layout (§2.2 / §3 of the lineage
   doc).** Point out byte ranges, REDEFINES (bytes 1–26 are simultaneously
   the 26-byte submitted key and a CCYYMMDD/JV#/section/loan composite),
   and the column-by-column mapping into `JC_SUBMITTED_COMMENT_TBL`.
   (Answers Q11, Q14.)
2. **Section 3.1 — duplicate-check lineage.** Show the file-field → bind
   variable → SQL `WHERE` clause chain.
3. **Section 4 — `JC_COUNT_TBL`.** Show how the legacy `IF` guard
   (`WS-JV-COUNTER > WS-JV-COUNTERS`) maps to a single guarded `UPDATE`
   in the modernized SQL.
4. **Section 6 — validation-rule lineage.** Each reject reason is cited
   back to a `source/procobol/LABD20.pco` line range, and ~~the
   calendar-date rule is flagged **Low** confidence because of the
   missing `DATECONV-*` copybooks.~~ **the calendar-date rule was flagged Low confidence pre-2026-05-21; it is now HIGH confidence after the customer follow-up shipment supplied `DATECONV-*` copybooks (faithful port + GnuCOBOL parity verified).**
5. **Section 8 — Confidence Summary.** A one-glance table for the
   reviewer: "submitted-table insert High, calendar-date Low, EOJ begin
   counts Medium." (Answers Q15.)

## 6. SQL / Python Conversion Path (8 min)

Open `converted-code/sql/labd20_extracted_operations.sql`.

**Walk in this order.**

1. **Section 0 — bind-variable map.** Demonstrates how we go from the
   legacy `:WS-...` host variables to readable `:snake_case` bind names
   while preserving 1:1 traceability via the comment header above each
   statement. (Answers Q8.)
2. **Section 1 — duplicate check.** Show the parameterized `SELECT` and
   the preserved SQLCODE semantics (0 vs 100 vs other).
3. **Section 2 — `INSERT`.** Show the full column list pulled directly
   from `source/procobol/LABD20.pco:352–372` and the
   `TO_DATE(:process_date_yyyymmdd, 'YYYYMMDD')` boundary.
4. **Section 2b — optional modernization.** The single-statement
   `INSERT ... WHERE NOT EXISTS` collapses the legacy two-statement
   dedup pattern atomically. This is a recommended diff, not a literal
   conversion — flag it as such to the customer.
5. **Sections 3–5 — count update + commit + EOJ stats.** Show how the
   purely-SQL guarded `UPDATE` mirrors the legacy `IF` branch.
6. **Section 6 — rollback.** Tie back to the legacy `9999-ROLL-BACK`
   paragraph and to `DBIO.pco`'s wrapped `EXEC SQL ROLLBACK END-EXEC`.
7. **Python conversion sketch (preview).** Point the customer at
   `prompts/python-conversion-demo.md` as the next Devin task: parse
   fixed-width record, run validators, atomic dedup-and-insert, count
   update, COMMIT/ROLLBACK with managed-secret config — and a test
   suite under `converted-code/python/tests/`. (Answers Q7a.)

**Talking-point on COBOL data types** (Q2, Q3):

- `PIC 9(n)` and `PIC X(n)` map cleanly to fixed-width string slices in
  the modernized loader and to `NUMBER`/`CHAR(n)` columns in Oracle.
- `USAGE BINARY` / COMP fields (e.g., `JV-NUMBER` in `JV-CONTROL-REC`)
  are decoded as big-endian unsigned integers at the parser layer.
- This repo's files are `LINE SEQUENTIAL`. We discussed the approach for
  record sequential, indexed, and variable-length files in `docs/`:
  same dependency graph + lineage model, with a different parser
  backend (`struct`-style decoder vs. token-based reader).

## 7. Tests & Acceptance (4 min)

**Talking points.**

- From `business-requirements/requirements-with-citations.md` we can
  auto-generate test scenarios such as:
  - BR-LABD20-008: JV number is `'00ABCD'` → expect reject + counter.
  - BR-LABD20-008: JV number is `'000000'` → expect reject (not `> 0`).
  - BR-LABD20-018: duplicate `JC_SUBMITTED` key → expect no insert + log.
  - BR-LABD20-026: post-loop counter ≤ prior count → expect no update
    on `JC_COUNT_TBL`.
  - BR-LABD20-033/034: simulate `SQLCODE = -1234` on insert → expect
    `ROLLBACK` + `RETURN-CODE = 99` + EOJ counts unchanged.
- Each generated test has a citation back to the originating requirement
  ID, so the COBOL SME reviewing the test plan can verify equivalence
  without re-reading source. (Answers Q17.)
- Mention that the empty `test-data/TST.JVCMTS.dat` (2 bytes) means we
  use synthetic non-production data in any demo run.

## 8. Conversion Efficiency Talk Track (3 min)

(Answers Q7e — "view of code conversion efficiency".)

- Per-program time savings come from three places:
  1. **Indexing once, querying many times** — the system dependency
     graph + canonical model (programs, copybooks, files, tables, fields,
     SQL ops, requirements) is reused across requirements, lineage, SQL
     extraction, and test generation. (Answers Q13.)
  2. **Citation-backed reviews** — SMEs review against source line
     ranges, not free-text claims; review cycle time drops sharply.
  3. **Confidence-tagged outputs** — high-confidence rows can be
     auto-approved; only Medium/Low rows demand SME time. ~~The current
     `LABD20` deck has 1 Low-confidence area (calendar-date validation
     because of missing copybooks) and 3 Medium-confidence rows.~~ **Updated 2026-05-21:** The previously-Low calendar-date validation is now HIGH (`DATECONV-*` copybooks supplied; faithful port + GnuCOBOL parity); only 3 Medium-confidence rows remain.
- Mention that "% of program auto-converted vs. needing review" is the
  customer-facing efficiency metric we expose post-conversion.

## 9. Post-Modernization Documentation (3 min)

(Answers Q1.)

- The artifacts in `business-requirements/`, `analysis/`, and
  `converted-code/sql/` are the **post-modernization** documentation
  surface — they describe the new Python/SQL system, not just the
  legacy COBOL.
- We can also regenerate them on each PR so the docs stay in sync with
  the converted code (instead of going stale the way generated COBOL
  docs do once the COBOL is retired).

## 10. Agent Orchestration & Inspectability (2 min)

(Answers Q22.)

- Every demo artifact in this repo was produced by a Devin session whose
  task plan, file edits, and PR diff are inspectable.
- Reviewers can pause / steer the agent before each milestone (analysis,
  requirements, lineage, SQL, tests).
- Diffs / commits / PRs are the inspection surface; nothing is
  black-box.

## 11. Q&A (10 min)

Use `docs/guidehouse-open-questions.md` as the authoritative backbone.
For each question, point at the demo artifact that directly answers it.

### By Owner

#### Charles

- **Q12 — dynamic CALL.** Static analysis resolves the literal
  `CALL 'DBIO'`; for variable-name CALLs Devin falls back to a
  heuristic + runtime-evidence layer and flags the edge with a
  confidence note. This codebase has only literal CALLs.
- **Q13 — intermediate representation.** Yes — repo-level canonical
  model (programs, copybooks, files, tables, fields, SQL ops,
  requirements) is built before any output is generated. The same
  model drives `analysis/dependency-map.md`, the requirements doc, the
  lineage doc, and the extracted SQL.
- **Q14 — end-to-end lineage.** Yes — `analysis/field-lineage.md`
  shows it for `TST123-COMMENT-REC` → `JC_SUBMITTED_COMMENT_TBL`.
- **Q15 — confidence.** Confidence-rating column on every row + an
  explicit risk block in the requirements doc.

#### Jill

- **Q1 — post-modernization docs.** Yes — see `business-requirements/`
  and `analysis/` artifacts that describe the new system.
- **Q2 — COBOL data types.** `COMP / COMP-X / BINARY` are recognized;
  `JV-NUMBER` example (USAGE BINARY) is in §3 above.
- **Q3 — file types.** Line sequential is what's in this repo;
  record sequential / indexed / variable-length use the same model
  with a different parser backend (talk through, don't claim runnable
  demo for those here).
- **Q4 — Perl, sqlplus, sqlldr.** Perl wrappers in `source/perl/` are
  ingested as orchestration scripts (env vars, `rtsora` call). No
  sqlplus / sqlldr in this corpus; mention that they are supported as
  first-class file types in our parser.
- **Q5 — labor required.** Expert-in-the-loop framing; cite
  confidence column and SME-review checkpoints.
- **Q8 — COBOL → Oracle SQL.** Show
  `converted-code/sql/labd20_extracted_operations.sql`.

#### Margarita

- **Q16 — unsupported constructs.** ~~Missing copybooks
  (`DATECONV-*`) flagged.~~ **Resolved 2026-05-21: `DATECONV-*` copybooks supplied; Risk 1 CLOSED.** Other typical "needs review" items: dynamic
  table-name construction, IMS DBs (not in this corpus), proprietary
  vendor extensions.
- **Q17 — reverse-engineered tests.** Show §7 of this script.
- **Q18 / Q19 / Q20 — deployment, data security, FedRAMP.** Use the
  pre-approved federal talk track; do not improvise.
- **Q21 — supported target languages.** Confirm matrix with
  Cognition product / GTM before final answer.
- **Q22 — agent orchestration / inspectability.** See §10 above.

#### Srinjoy

- **Q9 — copybook / dynamic resolution.** 6/8 copybooks resolved; 2
  flagged missing. Dynamic CALL handled as in Q12.
- **Q10 — system-wide graph.** Yes — `analysis/dependency-map.md`
  spans programs, copybooks, files, and tables.
- **Q11 — field-level lineage.** Yes — `analysis/field-lineage.md`.

#### Sunil

- **Q7 (a–e).** Walk through each artifact in order: SQL conversion
  (§6), formal requirements (§4), data flow / dependency (§3 + §5),
  test plans / scenarios (§7), conversion efficiency (§8).

## 12. Known Caveats To Disclose Up Front (1 min)

- ~~`DATECONV-WS.cpy` and `DATECONV-PD.cpy` are not in the supplied zip;
  calendar-date validation fidelity is Low until they are provided.~~ **Resolved 2026-05-21:** `DATECONV-WS.cpy`, `DATECONV-PD.cpy`, `DATECONV.cbl`, and 4 JDN helpers supplied in customer follow-up shipment. Calendar-date validation fidelity is now HIGH (faithful port + byte-for-byte GnuCOBOL runtime parity).
- `test-data/TST.JVCMTS.dat` is empty; any "run the loader live" demo
  uses synthetic data.
- `JC_COUNT_TBL` description text contains a copy/paste artifact about
  its primary key; we'll confirm with the DBA before generating final
  DDL.
- `TST123-COMMENT-APPROVER` has a 14-byte file width and 20-byte
  Oracle column; padding behavior must be approved.
- `DBIO.pco` reads Oracle credentials from `USRID`/`PASSWD` files; the
  modernized system uses a managed-secrets mechanism — no plaintext
  Oracle passwords on disk.

## 13. Recap & Next Steps (2 min)

**Recap.** Devin ingested the Guidehouse COBOL/Pro\*COBOL/copybook /
script / DDL bundle, built a system-wide dependency graph, derived 44
cited requirements (9 LABA05 + 35 LABD20), produced field-level lineage
into Oracle, extracted parameterized Oracle SQL ready for review, and
emitted a confidence-tagged risk surface — all in this repo's `analysis/`,
`business-requirements/`, and `converted-code/` directories.

**Suggested next sessions.**

1. Generate `converted-code/python/labd20_loader.py` + tests using
   `prompts/python-conversion-demo.md`.
2. Generate `converted-code/sql/control_record_table_io.sql` from
   `CONTROL-RECORD-TABLE-IO.pco` using `prompts/sql-extraction-demo.md`.
3. ~~Resolve `DATECONV-WS` / `DATECONV-PD` (or codify the replacement
   validator with a Guidehouse SME) and re-run the requirements +
   lineage refresh.~~ **Resolved 2026-05-21 (customer follow-up shipment):** `DATECONV-WS` / `DATECONV-PD` (+ `DATECONV.cbl` + 4 JDN helpers) supplied. Requirements + lineage refreshed; Risk 1 CLOSED, A-1 RETIRED, `BR-LABD20-006` LOW → HIGH. See `migration/converted-code/python/dateconv.py` + `migration/test-results/cobol-parity-report.html`.
4. Generate the full test plan from the cited requirements and run it
   against synthetic data.
