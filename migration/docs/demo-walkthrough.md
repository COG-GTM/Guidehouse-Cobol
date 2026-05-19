# Demo walkthrough — JV COBOL modernization

> **Audience:** Guidehouse stakeholders (Jill, Sunil, Srinjoy, Charles, Margarita).
> **Format:** Speaker notes — short sentences, conversational. Read it, don't perform it.
> **Status:** Demo output, pending SME review.

---

## 0. Opening (≈ 2 min)

> "Quick context. We took your JV COBOL programs as-is. Devin walked through the source, built the analysis, wrote modernized Python plus the Oracle SQL, generated synthetic data, and built a passing test suite. Everything we're about to look at is in the PR on the screen. Nothing under `source/` was modified."

Open the PR. Show the file tree under `migration/`.

> "Before we click around — the most important thing in this whole demo is the assumption-and-placeholder register. Devin is honest about what it couldn't confirm. Let's open that first."

Open [`migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../ASSUMPTIONS-AND-PLACEHOLDERS.md).

> "Every assumption gets a confidence level. Every placeholder is also inline in the code as a `# PLACEHOLDER` marker. The two big ones are the missing DATECONV copybooks and the binary-vs-display conversion of JV-NUMBER. We'll flag those again as they come up."

---

## 1. Before state (≈ 2 min)

Open `source/procobol/LABD20.pco`. Scroll past line 43-55.

> "This is the input record layout. Three hundred bytes. Eight for the date, six for JV number, two for section, ten for loan, ten for schedule-doc-no, two-thirty for the comment, twenty for the requestor, fourteen for the approver. There's a deprecated commented-out line right above the active one — twenty bytes for the approver. We treated the deprecated line as deprecated and used fourteen. That's noted in the risk register."

Scroll to line 261-307.

> "These are the validation rules. Eight of them. Blank, non-numeric date, invalid calendar date, JV zero or non-numeric, non-numeric section, non-numeric loan, blank comment, blank requestor, blank approver."

Open `source/cobol/LABA05.cbl`.

> "LABA05 is shorter — fiscal-year reset. Reads the JV-CONTROL-REC from CONTROL_RECORD_TABLE, sets JV-NUMBER to one, updates, commits."

---

## 2. Analysis (≈ 5 min) — *for Srinjoy and Charles*

Open [`migration/analysis/dependency-map-detailed.md`](../analysis/dependency-map-detailed.md).

> "System-wide graph. Perl wrappers, COBOL programs, the DBIO dispatcher, the table-IO modules, the Oracle tables. Notice the two dashed orange nodes — those are the missing copybooks Devin couldn't find. We're not pretending they're there."

Scroll to §5 — DBIO dispatch table.

> "Charles asked about dynamically constructed program calls. Here's what we did. DBIO concatenates the table name with `-IO` at runtime. We enumerated every dispatch path we could resolve from the supplied source, and we marked everything else as needing runtime tracing."

Open [`migration/analysis/field-lineage.md`](../analysis/field-lineage.md).

> "Srinjoy asked about field-level lineage. This is the per-byte trace from the input file through working storage into Oracle columns. Every column on JC_SUBMITTED_COMMENT_TBL is sourced. Eight of the nine columns come straight from the input record; one comes from an in-batch counter."

Open [`migration/business-requirements/requirements-with-citations.md`](../business-requirements/requirements-with-citations.md).

> "Charles also asked about confidence. Every requirement carries a HIGH/MEDIUM/LOW tag and a citation. The ones marked LOW are the ones tied to the missing DATECONV copybooks."

---

## 3. Conversion (≈ 5 min) — *for Jill and Sunil*

Open [`migration/converted-code/python/labd20_loader.py`](../converted-code/python/labd20_loader.py). Show the top-of-file ASSUMPTIONS block.

> "Every generated file leads with an assumptions block. Then inline markers for anything Devin had to guess. Jump to `check_cymd_dt`."

Scroll to `check_cymd_dt`.

> "This is the stub for the missing date copybook. Standard Gregorian calendar check. The marker explicitly says `# PLACEHOLDER: replace with legacy DATECONV-PD logic once provided`. It is not pretending to be the real thing."

Scroll to `_insert`.

> "Parameterized INSERT, all nine columns, no string concatenation. Bind variables map one-to-one with the COBOL host variables. The bind for `JC_SUBMITTED_UPDT_PROG_DT` uses `TO_DATE` for Oracle parity."

Open [`migration/converted-code/python/laba05_reset.py`](../converted-code/python/laba05_reset.py).

> "LABA05 is much shorter. Connect, fetch, reset, update, commit. Look at `_extract_jv_number`. JV-NUMBER lives at bytes twenty-four through thirty inside a four-hundred byte blob. Legacy stores it as USAGE BINARY. The placeholder marker tells you exactly what `struct.unpack` form replaces this slice for true binary parity. That's the same risk Jill called out for COMP / COMP-X / BINARY in question two."

Open [`migration/converted-code/sql/labd20_operations.sql`](../converted-code/sql/labd20_operations.sql).

> "All embedded SQL extracted to a single file, parameterized, with citations back to the COBOL line. Same for the control record table file."

---

## 4. Test & validation (≈ 4 min) — *for Sunil and Margarita*

Open the terminal. Run:

```
python3 -m pytest migration/converted-code/python/tests/ -v
```

> "Fifty-two tests, all passing. Coverage spans byte-layout, parsing, every validation rule individually, duplicate detection, INSERT parameter mapping, rollback path, count-update, fiscal-year reset, binary-display round-trip."

Open [`migration/test-data/README.md`](../test-data/README.md).

> "All synthetic. Twenty-one records covering happy path, every rejection reason, duplicates, boundary cases. No real customer data anywhere in the repo. That's important for Margarita's question on data handling."

Now run the demo app:

```
python3 migration/converted-code/python/demo_app.py run
```

> "This is the runnable end-to-end. It seeds an in-memory mock Oracle, runs the fiscal-year reset, then runs the comment loader against the synthetic data. Seven inserts, two duplicates, twelve rejected, exactly what we expect."

If you want the dashboard:

```
python3 migration/converted-code/python/demo_app.py serve
```

> "Open `http://127.0.0.1:8765` and you can see the run as a one-page dashboard. Same data, prettier."

---

## 5. Conversion efficiency (≈ 2 min) — *for Sunil*

Open [`migration/docs/conversion-efficiency.md`](conversion-efficiency.md).

> "Lines analyzed versus lines generated, requirements derived, tests produced, SQL operations extracted, SME-review items. This is the view Sunil asked for in question seven, sub-item e."

---

## 6. Open questions (≈ 3 min) — *for everyone*

Open [`migration/docs/guidehouse-open-questions-response.md`](guidehouse-open-questions-response.md).

> "Twenty-two questions, twenty-two answers, each linked to specific artifacts in this PR. The only ones we deferred are eighteen, nineteen, and twenty — the FedRAMP/deployment-boundary ones. Those need GTM and security to sign off on the wording, not me, not Devin."

---

## 7. Post-modernization docs (≈ 2 min) — *for Jill*

Open [`migration/docs/post-modernization-docs.md`](post-modernization-docs.md).

> "Jill asked the most important question of the bunch — can the tools generate documentation *after* the conversion. Look at this. The function signatures, the data flow, the deployment notes — all generated from the modernized Python, with citations now pointing into the Python files. Not the COBOL. That's the answer to her question."

---

## 8. Executive report (≈ 1 min)

Open [`migration/executive-report.html`](../executive-report.html) in a browser (file://).

> "One-page Cognition-branded report you can hand to anyone. Plan, blockers, what's missing, links to every artifact. This is what we'd leave behind after the engagement."

---

## 9. Close (≈ 2 min)

> "To recap. The session ingested your supplied COBOL, generated traceable requirements, a system-wide dependency graph, field-level lineage, a parameterized SQL catalog, runnable Python, fifty-two passing tests, synthetic data, a runnable demo app, a one-page executive report, and a per-item assumption-and-placeholder register. Everything is in the PR. Nothing under `source/` was modified."

> "Where do you want to go deeper?"

---

## Stakeholder-specific talking points (quick reference)

**Jill** — open questions 1, 2, 3, 4, 5, 8. Lead with Q1 (post-modernization docs) and Q2 (COMP/BINARY).

**Sunil** — Q7 (the five sub-items). Walk Phase 1 → Phase 2 → Phase 3, then conversion-efficiency at the end.

**Srinjoy** — Q9, Q10, Q11. Walk the dependency map, then field lineage.

**Charles** — Q12, Q13, Q14, Q15. Confidence levels, canonical model, dynamic dispatch.

**Margarita** — Q16, Q17, Q21, Q22 (answerable). Q18, Q19, Q20 — defer to GTM/security.
