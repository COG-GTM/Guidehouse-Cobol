# Response to Guidehouse open questions

> **Status:** Demo output, pending SME and GTM/security review on items 18-20.
> Each answer below points to specific artifacts produced in this migration
> session. See [`migration/MIGRATION-PLAN.md`](../MIGRATION-PLAN.md) and
> [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).

Source list of questions: [`docs/guidehouse-open-questions.md`](../../docs/guidehouse-open-questions.md).

---

## Q1 (Jill) — Can the tools create *new* documentation after the modernization?

**Short answer:** Yes. Documentation can be generated from the legacy COBOL *and* from the modernized code; both views are produced in this session.

**Evidence:**
- "Before" documentation (legacy view): [`migration/business-requirements/requirements-with-citations.md`](../business-requirements/requirements-with-citations.md), [`migration/analysis/field-lineage.md`](../analysis/field-lineage.md), [`migration/analysis/dependency-map-detailed.md`](../analysis/dependency-map-detailed.md), [`migration/analysis/sql-catalog.md`](../analysis/sql-catalog.md).
- "After" documentation (modernized view): [`migration/docs/post-modernization-docs.md`](post-modernization-docs.md) — function-level reference generated from the Python modules, with source-line citations that *now point at the Python file* rather than the COBOL. This is the artifact Jill asked about specifically.

---

## Q2 (Jill) — COBOL data definitions (COMP, COMP-X, BINARY)

**Short answer:** We resolve `PIC` clauses + `USAGE` together (display vs. COMP / COMP-X / BINARY) and generate Python parsers that respect the legacy byte semantics. Where the legacy code does explicit binary↔display conversion (e.g. `CONTROL-RECORD-TABLE-IO.pco`), we preserve the conversion step.

**Evidence:**
- Layout extraction with `USAGE BINARY` call-out: [`migration/analysis/field-lineage.md` §2 + §3](../analysis/field-lineage.md).
- Binary/display split in modernized code: [`migration/converted-code/python/laba05_reset.py`](../converted-code/python/laba05_reset.py) — `_extract_jv_number` / `_replace_jv_number` with explicit `# PLACEHOLDER` markers for the production-mode `struct.unpack('>I', …)` form.
- Risk register entry: [`migration/RISKS-AND-GAPS.md` Risk 2](../RISKS-AND-GAPS.md).
- Test: `TestJVNumberByteLayout` / `TestBinaryDisplayConversion` in [`test_laba05_reset.py`](../converted-code/python/tests/test_laba05_reset.py).

---

## Q3 (Jill) — File types (record sequential, indexed, variable-length)

**Short answer:** The supplied files are line-sequential fixed-width (`COMMENT-FILE`) and 1-record card (`CARDFILE`). The modernized parser is layout-driven and works for record-sequential / variable-length so long as the layout is supplied; indexed files (VSAM/ISAM) need an ingestion pre-step (typical pattern: dump to flat + import).

**Evidence:**
- Line-sequential parser: [`labd20_loader.iter_records`](../converted-code/python/labd20_loader.py) — handles short/long records by padding/truncating to 300 bytes.
- Fixed-width layout extraction: [`migration/analysis/field-lineage.md` §1](../analysis/field-lineage.md).
- Card file parsing: [`labd20_loader.read_process_date`](../converted-code/python/labd20_loader.py) + `TestReadProcessDate` in the test suite.

---

## Q4 (Jill) — Perl scripts, sqlplus, sqlldr

**Short answer:** Yes. Perl wrappers, sqlplus invocations, and sqlldr control files are all ingestable as part of the dependency graph. The supplied repo only contains Perl wrappers; we model their env-var + `rtsora` dependencies explicitly.

**Evidence:**
- Perl-wrapper environment dependencies: [`migration/analysis/dependency-map-detailed.md` §4](../analysis/dependency-map-detailed.md) (env-var table).
- Risk register: [`RISKS-AND-GAPS.md` Risk 10](../RISKS-AND-GAPS.md) — modern orchestration equivalent.

---

## Q5 (Jill) — Required COBOL-developer labor

**Short answer:** Expert-in-the-loop, not unsupervised. Devin produces traceable analysis and conversion at speed; COBOL SMEs review the explicitly-flagged items. In this session we have produced an itemized review queue.

**Evidence:**
- SME review queue: [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md) (single source of truth) + [`migration/RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md) (11 named risks).
- Conversion efficiency breakdown: [`migration/docs/conversion-efficiency.md`](conversion-efficiency.md).

---

## Q7 (Sunil) — Five-part demo deliverable

| Sub-item | Artifact |
|----------|----------|
| a. Code conversion to Python and/or Oracle SQL | [`converted-code/python/labd20_loader.py`](../converted-code/python/labd20_loader.py), [`laba05_reset.py`](../converted-code/python/laba05_reset.py), [`db_dispatcher.py`](../converted-code/python/db_dispatcher.py); SQL: [`converted-code/sql/labd20_operations.sql`](../converted-code/sql/labd20_operations.sql), [`control_record_table_operations.sql`](../converted-code/sql/control_record_table_operations.sql) |
| b. Formal functional requirements | [`business-requirements/requirements-with-citations.md`](../business-requirements/requirements-with-citations.md) (BR-LABA05-001..006 + BR-LABD20-001..022 + 4 unresolvables) |
| c. Data flow & dependency diagram | [`analysis/field-lineage.md`](../analysis/field-lineage.md), [`analysis/dependency-map-detailed.md`](../analysis/dependency-map-detailed.md) (Mermaid) |
| d. Test plans, scenarios, scripts | [`converted-code/python/tests/`](../converted-code/python/tests/) (52 tests) + [`docs/before-after-comparison.md`](before-after-comparison.md) |
| e. Conversion efficiency view | [`docs/conversion-efficiency.md`](conversion-efficiency.md) |

---

## Q8 (Jill) — COBOL → Oracle SQL conversion

**Short answer:** Yes. Embedded SQL is extracted, parameterized, and re-emitted as Oracle-compatible bind-variable statements. Oracle-specific constructs (`TO_DATE`, `ROWID`) are preserved and flagged.

**Evidence:** [`migration/analysis/sql-catalog.md` §4](../analysis/sql-catalog.md) (Oracle-specific table) + [`converted-code/sql/`](../converted-code/sql/).

---

## Q9 (Srinjoy) — Resolve dependencies across copybooks, includes, dynamic calls

**Short answer:** Yes — and we *explicitly mark* what can't be resolved.

**Evidence:**
- Resolved dependencies: full Mermaid graph in [`analysis/dependency-map-detailed.md` §1-2](../analysis/dependency-map-detailed.md).
- Missing nodes (explicit): [§6 Missing-node summary](../analysis/dependency-map-detailed.md#6-missing-node-summary) — `DATECONV-WS`, `DATECONV-PD`, two `*-IO` dispatch targets.
- Dynamic dispatch: [`RISKS-AND-GAPS.md` Risk 4](../RISKS-AND-GAPS.md).

---

## Q10 (Srinjoy) — System-wide vs file-level

**Short answer:** System-wide. The dependency graph spans Perl → COBOL → DBIO → table-IO modules → Oracle tables, with explicit citations on every edge.

**Evidence:** [`analysis/dependency-map-detailed.md` §1 + §3 SQLCA flow](../analysis/dependency-map-detailed.md).

---

## Q11 (Srinjoy) — Field-level data lineage

**Short answer:** Yes. We trace every input-field byte through working-storage and bind variables into Oracle columns.

**Evidence:** [`analysis/field-lineage.md`](../analysis/field-lineage.md) (full lineage table including byte offsets, PIC clauses, transformations).

---

## Q12 (Charles) — Dynamically constructed program calls

**Short answer:** Static analysis first; we surface every dynamic-string call site, enumerate the call targets we can resolve from supplied source, and flag the rest as requiring runtime tracing.

**Evidence:**
- Inventory of DBIO dynamic dispatch sites: [`analysis/dependency-map-detailed.md` §5](../analysis/dependency-map-detailed.md).
- Modernization stance (typed dispatcher, no string-built names): [`RISKS-AND-GAPS.md` Risk 4](../RISKS-AND-GAPS.md) + ASSUMPTIONS A-9.

---

## Q13 (Charles) — Intermediate canonical model

**Short answer:** Yes. We build a structured project model (programs, copybooks, files, tables, SQL operations, fields, requirements) and emit every deliverable (docs, diagrams, code) from that model. This session's artifacts are the visible projection of that model.

**Evidence:** The directory shape under `migration/` — `business-requirements/`, `analysis/`, `converted-code/`, `test-data/`, `docs/`, plus the assumption/risk registers — is the canonical model. The executive summary in [`migration/executive-report.html`](../executive-report.html) reflects it visually.

---

## Q14 (Charles) — End-to-end data lineage

**Short answer:** Yes. Each Oracle column has a per-byte path back to its source field; see Q11.

---

## Q15 (Charles) — Confidence measurement

**Short answer:** Per-item HIGH/MEDIUM/LOW confidence with explicit reasons (missing artifact, dynamic dispatch, etc.).

**Evidence:**
- Per-requirement confidence column: [`business-requirements/requirements-with-citations.md`](../business-requirements/requirements-with-citations.md).
- Confidence calibration framework: [`ASSUMPTIONS-AND-PLACEHOLDERS.md` §D](../ASSUMPTIONS-AND-PLACEHOLDERS.md).
- Risk register with severity tags: [`RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md).

---

## Q16 (Margarita) — Constructs requiring manual remediation

**Short answer:** Enumerated, with severity + mitigation, in the risk register.

**Evidence:** [`RISKS-AND-GAPS.md`](../RISKS-AND-GAPS.md) — 11 named risks (missing DATECONV copybooks, binary/display conversion, credential files, dynamic dispatch, fixed-width byte precision, SQLCODE→DMS translation, transaction rollback paths, EXTERNAL shared data, empty test data, Perl env deps, PERFORM PERFORM typo).

---

## Q17 (Margarita) — Test-scenario generation

**Short answer:** Yes. 21 synthetic fixed-width records covering valid, rejected, and duplicate cases; 52 pytest assertions targeting parsing, validation, persistence, transaction, and post-process logic.

**Evidence:**
- Synthetic data: [`test-data/synthetic_comments.dat`](../test-data/synthetic_comments.dat) + [`test-data/README.md`](../test-data/README.md) coverage matrix.
- Tests: [`converted-code/python/tests/test_labd20_loader.py`](../converted-code/python/tests/test_labd20_loader.py) (40 cases), [`test_laba05_reset.py`](../converted-code/python/tests/test_laba05_reset.py) (12 cases).
- Test output: [`test-results/pytest-output.txt`](../test-results/pytest-output.txt).
- Before/after comparison + traceability matrix: [`docs/before-after-comparison.md`](before-after-comparison.md).

---

## Q18 (Margarita) — Where the tool executes / client AWS boundary

**Short answer:** Devin runs in Cognition-managed cloud today; client-managed deployments are part of the federal roadmap. **Final wording requires GTM/security confirmation before customer delivery.**

**Talk-track placeholder:** Engagement model + boundary diagrams are part of the federal deployment package; we will share that under appropriate NDA.

---

## Q19 (Margarita) — Source code and data security in processing

**Short answer:** Encryption in transit + at rest, scoped credentials, audit logging, no data egress beyond the tenant. **Final wording requires GTM/security confirmation before customer delivery.**

**Demo-grade evidence:** the modernized code itself follows the federal pattern — parameterized SQL (no concatenated user input), env-var/secrets-store credentials (never on-disk credential files; see RISKS Risk 3), no hard-coded passwords anywhere in `migration/`. The synthetic test data confirms no real customer data is committed.

---

## Q20 (Margarita) — FedRAMP authorization status / FedRAMP High timeline

**Short answer:** **Requires GTM/security confirmation before customer delivery.** Do not improvise wording.

---

## Q21 (Margarita) — Supported target languages / maturity matrix

**Short answer:** Python + Oracle SQL demonstrated end-to-end in this session. Java, C#, Spring Boot, and PL/SQL are on the roadmap — final maturity claims require product/GTM confirmation.

**Demo-grade evidence:** This session's artifacts are Python + Oracle SQL.

---

## Q22 (Margarita) — Agent orchestration / inspectability

**Short answer:** Every decision Devin made in this session is inspectable. The work was planned as a 5-phase task with a written plan, fan-out parallelism, and an explicit "what was assumed / where we used placeholders" register.

**Evidence:**
- The plan itself: [`migration/MIGRATION-PLAN.md`](../MIGRATION-PLAN.md).
- Decision trace: [`migration/executive-report.html`](../executive-report.html) — "Devin's thought process" section.
- Per-item confidence + SME-action items: [`ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).
- Diff history: the PR description on `demo/full-migration-execution` shows the full commit list.
