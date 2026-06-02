---
name: cobol-modernization-demo
description: Use for Guidehouse/SBA COBOL modernization demo tasks involving COBOL/Pro*COBOL analysis, requirements extraction, lineage, SQL/Python conversion, and customer demo artifacts.
---

# COBOL Modernization Demo Skill

Use this skill when working in `COG-GTM/Guidehouse-Cobol`.

## Goal

Create traceable, customer-demo-ready artifacts from the supplied COBOL/Pro*COBOL assets.

## Required First Steps

1. Read `README.md`.
2. Read `AGENTS.md` (note the 2026-05-21 DATECONV closure block — every `COPY` and `CALL` from `LABD20.pco` now resolves).
3. Read `docs/email-context.md`.
4. Read `docs/guidehouse-open-questions.md`.
5. Read `docs/source-inventory.md`.
6. Read `docs/demo-walkthrough-guide.md` (the 4-beat customer narrative).
7. Read `analysis/dependency-map.md` and `analysis/dateconv-function-inventory.md` (42-function DATECONV inventory).
8. Inspect relevant source:
   - `source/procobol/LABD20.pco`
   - `source/cobol/LABA05.cbl`
   - `source/cobol/DATECONV.cbl` (the date-conversion subprogram — customer-supplied 2026-05-21)
   - `source/procobol/DBIO.pco`
   - `source/procobol/CONTROL-RECORD-TABLE-IO.pco`
   - relevant copybooks under `source/copybooks/` (including `DATECONV-WS.cpy`, `DATECONV-PD.cpy`, `JDN-CONSTANTS-WS.cpy`, `JDN-PACKET-WS.cpy`, `JDN-RECORD-WS.cpy`, `JDN-RECORD-ACCESS.cpy`)
   - relevant table descriptions under `database/descriptions/`

## Source Anchors

### LABA05

Use `source/cobol/LABA05.cbl` for fiscal-year JV control reset behavior.

Key behaviors:
- Connect to DB through `DBIO`.
- Fetch `JV-CONTROL-REC`.
- Set `JV-NUMBER` to `1`.
- Update persisted control record.
- Stop with non-zero return code on key failures.

### LABD20

Use `source/procobol/LABD20.pco` for JV comment ingestion behavior.

Key behaviors:
- Read `CARDFILE` for process date.
- Read external `COMMENT` file line sequentially.
- Parse `TST123-COMMENT-REC`.
- Validate required numeric/date/text fields.
- Check duplicates in `JC_SUBMITTED_COMMENT_TBL`.
- Insert accepted comments into `JC_SUBMITTED_COMMENT_TBL`.
- Update `JC_COUNT_TBL` for section `MA`.
- Commit successful work.
- Query end-of-job table counts.
- Roll back and report context on SQL/DMS errors.

## DATECONV subsystem (closed 2026-05-21)

The customer shipped the full 42-function date-conversion subsystem on 2026-05-21. Treat it as fully supplied:

- `source/cobol/DATECONV.cbl` — the `PROGRAM-ID. DATECONV` subprogram (preserves IAI-2012 `MIGRTN` markers verbatim).
- `source/copybooks/DATECONV-WS.cpy`, `DATECONV-PD.cpy` — the working-storage + procedure-division copybooks every `LABD20.pco` `COPY` reference depends on.
- `source/copybooks/JDN-CONSTANTS-WS.cpy`, `JDN-PACKET-WS.cpy`, `JDN-RECORD-WS.cpy`, `JDN-RECORD-ACCESS.cpy` — the four JDN (Julian Day Number) helper copybooks.
- `analysis/dateconv-function-inventory.md` — 42-function inventory with `DATESUB-FUNC` codes and intrinsic-function mapping.

Effect on the demo: Risk 1 → CLOSED. Assumption A-1 → retired. `BR-LABD20-006` confidence LOW → HIGH. The Python `check_cymd_dt` stub in `migration/converted-code/python/labd20_loader.py` is replaced by a faithful port in `migration/converted-code/python/dateconv.py`. The faithful port is byte-for-byte verified against the GnuCOBOL build of `DATECONV.cbl` (see Runtime Parity Harness below).

## Remaining gaps to call out

- `TST.JVCMTS.dat` is effectively empty, so runnable tests use synthetic non-production data.
- `JV-NUMBER` USAGE BINARY conversion against a real Oracle is the one remaining production-mode placeholder in `laba05_reset._extract_jv_number` (`struct.unpack('>I', ...)`). Demo uses the display form; flip one config switch for production.
- One documented modernization improvement (Julian-vs-Gregorian leap-year rule for 02/29/1900) is intentionally surfaced in `migration/modernization-improvement-findings.html` for SME decision — not a regression.

## Output Types

### Requirements

Place in `business-requirements/`.

Requirements must:
- be structured,
- include source citations,
- include confidence/risk notes,
- separate source facts from inferred behavior.

### Dependency/Lineage

Place in `analysis/`.

Lineage must:
- trace input fields to table columns,
- include file/table/copybook dependencies,
- identify missing artifacts and dynamic dispatch.

### SQL Conversion

Place in `converted-code/sql/`.

SQL output must:
- use parameterized Oracle SQL examples,
- include citations in comments,
- avoid credentials,
- document transaction semantics.

### Python Conversion

Place in `migration/converted-code/python/`.

Current modules:
- `labd20_loader.py` — daily comment ingestion port.
- `laba05_reset.py` — fiscal-year JV-NUMBER reset port.
- `dateconv.py` — faithful port of `DATECONV.cbl` (42-function subsystem; replaces the old `check_cymd_dt` stub).
- `parity_engine.py` + `parity_data.py` — 38-BR parity engine that drives `/parity` and `demo_app.py parity`.
- `demo_app.py` — entrypoint (`run` / `parity` / `serve` subcommands).
- `tests/` — 129 pytest cases (run with `python3 -m pytest migration/converted-code/python/tests/ -q`).

Python output must:
- model fixed-width parsing,
- implement validation rules,
- use parameterized DB access placeholders,
- avoid hardcoded secrets,
- include synthetic tests.

### Customer Demo

Place in `docs/`.

Customer-facing drafts must:
- be concise,
- avoid unverified compliance claims,
- call out assumptions,
- map to Guidehouse open questions.

## Runtime Parity Harness (the strongest single proof point)

`migration/test-results/build/` contains a GnuCOBOL 3.1.2 build of `source/cobol/DATECONV.cbl` plus a driver that diffs 80 test vectors against the Python `dateconv.py` port. Run with:

```bash
bash migration/test-results/build/run-parity.sh
```

Expected output (regenerate `migration/test-results/cobol-parity-report.html` from this run):

```
DATECONV parity: 79/80 matched; 1 documented modernization improvement(s); 0 unresolved mismatch(es)
```

This is the answer to federal acquisition's "are you sure you're not silently regressing the legacy?" question. Do not claim byte-for-byte parity without re-running this harness first — the number is real, earn it on the current VM.

## Quality Bar

Before completing:
- Do not modify original customer source files under `source/`.
- Verify generated markdown links/paths.
- Include source citations for every material claim.
- Clearly label generated outputs as demo/prep artifacts.
- Include validation/test notes.
- If you touched any DATECONV-adjacent code, re-run `bash migration/test-results/build/run-parity.sh` and confirm 79/80, 1 improvement, 0 unresolved.
- If you touched the 38-BR parity engine, re-run `python3 migration/converted-code/python/demo_app.py parity` and confirm `PASS=38 FAIL=0 UNRESOLVED=0`.
