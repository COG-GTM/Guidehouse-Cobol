# Demo walkthrough — end-to-end guide

> **Audience:** Jake, presenting the JV COBOL modernization demo to federal buyers (Guidehouse / SBA / IC).
> **Format:** what to open, in what order, with talking points and an honest assessment of what is live vs. static.
> **Length:** designed for a 30–45 minute session; can be compressed to 15 minutes by skipping the Python live app section.
>
> **What this document is not:** a script you read out loud. It's the inventory of every demo surface plus the order of operations and the framing that ties them together. Improvise on top of it.

---

## Quick answer to "is there a UI we can test on top of the Python?"

**Yes — three live surfaces in addition to the four static HTML reports.**

| # | Surface | Type | What it proves |
|---|---|---|---|
| 1 | `python3 migration/converted-code/python/demo_app.py serve` → `http://127.0.0.1:8765/` | **Live local dashboard** | The modernized Python actually runs LABD20 end-to-end against a mock Oracle, shows DB state before/after, and lists each rejection reason. |
| 2 | `bash migration/test-results/build/run-parity.sh` | **Live CLI** | Compiles the customer's COBOL under GnuCOBOL 3.1.2, runs 80 vectors through both binaries, diffs byte-for-byte. Headline: `79/80 matched; 1 documented modernization improvement(s); 0 unresolved` — exit 0. |
| 3 | `python3 -m pytest migration/converted-code/python/tests/` | **Live test suite** | 129 passing assertions across the Python port. Useful only if a reviewer asks "show me the tests." |
| 4 | `migration/executive-report.html` | Static HTML | T0→T6 demo-cycle timeline, "Devin caught it upfront" callout, before/after dep graph, confidence trajectory. |
| 5 | `migration/test-results/cobol-parity-report.html` | Static HTML | The 14-bugs-caught story with COBOL paragraph citations and bug-class table. |
| 6 | `migration/test-results/parity-console.html` | Static HTML | 38 business-requirement parity rows with per-row drill-down (COBOL source ↔ Python source side-by-side). |
| 7 | `migration/modernization-improvement-findings.html` | Static HTML | One-slide executive-audience callout for the 02/29/1900 finding. |

**Honest gaps:**

- The Python demo app uses an **in-memory SQLite mock of Oracle** (`DBDispatcher` with sqlite backend), not a live Oracle. Production wiring is `DBDispatcher.from_env()` reading credentials from environment variables — documented in `migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`. Frame this honestly if asked: *"This is the modernized code running end-to-end; the database adapter is the one swappable piece, and we ship both the SQLite demo backend and the Oracle production wiring."*
- The "live CLI" requires `gnucobol` installed (`apt install gnucobol`). The blueprint suggestion already covers this for future Devin sessions; on Jake's local machine confirm `cobc --version` before the demo or fall back to "here are the outputs of the last clean run."
- There is **no web-based COBOL execution** — i.e. no "click a button in the browser and watch the COBOL run." COBOL runs at the shell. The Python runs at the shell and also exposes the dashboard at port 8765.

---

## Pre-demo setup (5 minutes, before the call)

Run these in a terminal at `~/repos/Guidehouse-Cobol` (or wherever the clone lives). Keep the terminal visible during the demo — buyers like seeing the shell.

```bash
# 1. Confirm clean state on main
git status
git log -1 --oneline                  # should show the PR #8 merge commit

# 2. Confirm the COBOL compiler is installed
cobc --version                        # expect: cobc (GnuCOBOL) 3.1.2.0

# 3. Smoke-check the harness so you don't surprise yourself on stage
bash migration/test-results/build/run-parity.sh
# expect: 79/80 matched; 1 documented modernization improvement(s); 0 unresolved mismatch(es)
# exit code 0

# 4. Smoke-check the Python demo runs
python3 migration/converted-code/python/demo_app.py run | head -20

# 5. Pre-start the Python demo dashboard in a separate terminal
python3 migration/converted-code/python/demo_app.py serve &
# expect: "Serving demo dashboard at http://127.0.0.1:8765/"
```

Open these tabs in your browser **in this order** so they're ready:

1. `http://127.0.0.1:8765/` — Python demo dashboard (live)
2. `file://...repo/migration/executive-report.html` — executive board
3. `file://...repo/migration/test-results/cobol-parity-report.html` — parity report
4. `file://...repo/migration/test-results/parity-console.html` — parity console
5. `file://...repo/migration/modernization-improvement-findings.html` — modernization improvement callout
6. The terminal you'll run the live CLI from

> **Tip.** You can substitute a local HTTP server (`python3 -m http.server 8080` from the repo root) and use `http://localhost:8080/migration/executive-report.html` etc. The `file://` URLs work fine too, but the HTTP variant feels less "look I'm browsing my filesystem" if a buyer happens to glance at the address bar.

---

## Recommended demo order (the 4-beat narrative)

The story is **"Devin caught the gap before the customer shipped the missing files, and the verification stack caught 14 port-side defects before merge."** Tell it in this order:

### Beat 1 — Frame the story on the executive board (5 minutes)

**Open:** `migration/executive-report.html`

**What to point at:**
- The **T0→T6 demo-cycle timeline** at the top. Walk left-to-right.
  - *T0 — initial ingest.* Customer handed us a zip: 4 programs, 8 copybooks, 2 Perl wrappers, schema files, test data.
  - *T1 — Devin's unprompted analysis flagged DATECONV-WS/PD as Risk #1 (HIGH).* Cited in AGENTS.md, blueprint, RISKS-AND-GAPS, ASSUMPTIONS A-1, BR-LABD20-006 (LOW conf), 28+ artifacts. **This happened before the customer's follow-up shipment.**
  - *T2 — customer shipped 7 follow-up artifacts.* DATECONV.cbl + DATECONV-WS + DATECONV-PD + 4 JDN helpers.
  - *T3 — Devin auto-ingests the closure, regenerates requirements, lineage, parity, executive report in one cycle.*
  - *T4 — runtime parity loop catches 13 Python port defects* that all 77 unit tests had passed cleanly.
  - *T5 — adversarial review surfaces a 14th defect* (dual-threshold century inference). 28 additional `YY 53–72` vectors added.
  - *T6 — final state: 79/80 byte-for-byte matched, 1 classified modernization improvement, 0 unresolved.*
- The **"Devin caught it upfront" callout box.** This is the verbatim chat moment where Devin (in real time, before knowing the customer was about to ship the missing files) told Jake the DATECONV gap was real. Read this aloud — it's the strongest single line in the deck.
- The **confidence trajectory chart.** `BR-LABD20-006` went LOW → HIGH because we replaced a stub with a faithful port plus runtime parity.
- The **before/after dependency graph.** Left pane: red MISSING badges on DATECONV-WS / DATECONV-PD. Right pane: green RESOLVED badges with the 2026-05-21 timestamp.

**Talking points:**
- "This is the timeline of the engagement. You will notice T1 happened *before* T2 — that is, our analysis caught the missing piece before the customer realized they hadn't sent it. That is what 'gap detection' looks like in practice."
- "We did not pretend the gap never existed. The original 'DATECONV is missing' notes are preserved with strikethroughs across the analytical layer. The historical record stays intact; the resolution is layered on top with a timestamp."

**Time check:** 5 minutes. Don't dwell on the dep graph — it's evidence, not the headline.

---

### Beat 2 — Prove the runtime parity claim live (8 minutes)

**Open:** the terminal. Run:

```bash
bash migration/test-results/build/run-parity.sh
```

**What happens on screen:**
- `cobc -x` compiles `source/cobol/DATECONV.cbl` verbatim under GnuCOBOL 3.1.2 (no preprocessor — Unisys annotations are in the COBOL sequence area, the compiler ignores them).
- 80 test vectors are piped through both the compiled COBOL binary and the Python port (`migration/converted-code/python/dateconv.py`).
- A byte-for-byte diff runs on the 12 output fields per vector.
- Final banner: `Comparison complete: 79/80 matched; 1 documented modernization improvement(s); 0 unresolved mismatch(es)`; exit code `0`.

**What to point at:**
- The compile step. *"That's the customer's COBOL — we did not modify a byte of `source/cobol/DATECONV.cbl`. It's the file they sent us, compiled under an open-source 1986-era-compatible COBOL compiler."*
- The vector count: **80**, broken down as 52 original + 28 adversarial `YY 53–72` vectors. The 28 came from the T5 dual-threshold finding.
- The exit code 0. *"This is the kind of pass/fail signal a buyer's CI/CD pipeline can gate on."*

**Then open:** `migration/test-results/cobol-parity-report.html`

**What to point at:**
- The **headline section "What the runtime parity loop caught."** Two paragraphs. Read the first one aloud: *"Before the GnuCOBOL harness existed, the Python port of DATECONV had 13 defects that all 77 unit tests passed cleanly."* That is the demo's most important sentence.
- The **5-row bug-class table** underneath. Each row cites:
  - The COBOL paragraph that revealed the defect (e.g. `400-DIF-JUL`, `9920-CALC-YY-TO-YYYY`).
  - The Python defect in plain English.
  - The specific test vectors that exposed it.
  - The fix.
- The **dual-threshold row (Row 5).** *"This is the one the harness alone would have missed. The original 52 vectors all used `YY=24/25`, so the gap between the two century-inference rules was never exercised. Devin Review found this in an adversarial pass."*
- The **documented modernization improvements section** further down. One row: `02/29/1900`. Hold on this — it's the bridge to Beat 4.

**Talking points:**
- *"Federal buyers don't ask 'did you port it?' — they ask 'are you sure you're not silently regressing the legacy?' This is the verification stack that answers that question. We compiled the customer's actual COBOL, ran the same inputs through both sides, diffed byte-for-byte, and put every divergence in front of the SME."*
- *"77 unit tests passed before this harness existed. Unit tests test the port against the porter's assumptions. The harness tests the port against the legacy."*

**Time check:** 8 minutes. The bug-class table is dense — let the buyer skim while you talk.

---

### Beat 3 — Show the Python actually running end-to-end (8 minutes)

**Open:** `http://127.0.0.1:8765/` (the Python demo dashboard you pre-started in setup)

**What's on the page:**
- Header: "Cognition — JV modernization demo"
- Top cards: number of comment records read, accepted, rejected, duplicates, inserted.
- A run-log panel showing the LABA05 fiscal-year reset and the LABD20 comment loader output.
- A "Mock-DB final state" panel showing `CONTROL_RECORD_TABLE`, `JC_SUBMITTED_COMMENT_TBL`, `JC_COUNT_TBL` row counts and contents after the run.
- The 12 rejection reasons listed individually.

**Then open** (in a new tab) `http://127.0.0.1:8765/parity` — same content as `parity-console.html` but served live alongside the demo app.

**Talking points:**
- *"This is the modernized stack actually running. The Python port of LABD20 is reading 21 synthetic comment records, validating each one against the same rules the COBOL applies, inserting the 7 that pass, rejecting the 12 that fail, and recording 2 duplicates."*
- *"The 'mock Oracle' is in-memory SQLite — that is the swappable adapter. The same code runs against a real Oracle via the `DBDispatcher.from_env()` path, which reads credentials from environment variables rather than the legacy `/tst/.oralogin` and `/tst/.orapasswd` flat files. We do not reproduce the credential-file pattern in the modernized example."*
- *"The 12 rejection reasons in the right panel are the actual validation outcomes from the modernized validators — non-numeric section id, blank record, non-numeric date, invalid calendar date, etc. Each one corresponds to a rule documented in the business-requirements layer with COBOL line-range citations."*

**Honest framing if asked:**
- *"This is a demo dashboard — it's a Python `http.server` for live exploration during a session, not a production UI. The production-side surface for an operational stack would typically be a job-scheduling integration or a service API rather than a browser dashboard. The interactive surface here exists so a reviewer can drill into a single run and see exactly what the modernized code did with each record."*

**Time check:** 8 minutes. Don't get pulled into "can I make the UI prettier" rabbit holes — this is execution evidence, not a product mock.

---

### Beat 4 — The modernization improvement (5 minutes)

**Open:** `migration/modernization-improvement-findings.html` (the one-slide callout — new in this delivery)

**What to point at:**
- The hero number: **1 documented modernization improvement.** Not "1 unresolved mismatch."
- The behavior-comparison table:
  - `02/29/1900` — legacy accepts (wrong), modernized rejects (correct).
  - `02/29/2024`, `02/29/2000` — both agree.
  - `02/29/2100`, `02/29/2200`, `02/29/2300` — legacy accepts (wrong), modernized rejects (correct).
- The **future-hazard callout.** 1900 is the visible one; the same defect causes the legacy validator to accept three more invalid dates this century and beyond.
- The **SME decision panel.** Two options side-by-side: keep the modernization fix (recommended, shipped), or backport the COBOL Julian rule for strict byte-for-byte fidelity (re-inherits the 1900/2100/2200/2300 bug).

**Talking points:**
- *"This is the kind of finding the verification stack is for. The legacy code has a 126-year-old leap-year bug — the original COBOL uses divide-by-4 as the leap-year rule, the Gregorian calendar requires divide-by-4-except-centuries-not-divisible-by-400. The legacy accepts 02/29/1900 as a valid date. 02/29/1900 didn't exist."*
- *"A naive port that prioritized byte-for-byte fidelity above all else would have backported the bug to make the parity diff come out clean. We deliberately did not do that. Instead we classified the divergence as a `modernization_improvement` — not an `unresolved_mismatch` — and put it in front of the SME with the exact COBOL paragraph cited."*
- *"The decision is yours. Option A is what we shipped: the modernized stack rejects 02/29/1900 and the three future century-year hazards. Option B is a one-line change in `dateconv.py` if you want strict legacy fidelity. We'd recommend A — quietly carrying a known calendar bug into a modernized system isn't worth a perfect parity diff."*

**Why this is the closing beat:** It's the answer to "are you sure you're not regressing?" with a concrete example, a recommendation, and a clean handoff of decision authority. End on this.

**Time check:** 5 minutes.

---

## Total time budget

| Beat | Surface | Time |
|---|---|---|
| 1 | Executive board | 5 min |
| 2 | CLI + parity report | 8 min |
| 3 | Live Python dashboard | 8 min |
| 4 | Modernization improvement callout | 5 min |
| Q&A buffer | — | 10–15 min |
| **Total** | | **30–45 min** |

To compress to 15 minutes, drop Beat 3 (the live Python dashboard). The story still works on the strength of Beats 1, 2, 4.

---

## Possible buyer questions and where to land

| Question | Where to land |
|---|---|
| "How do you know the port is faithful?" | Beat 2 — runtime parity loop, 79/80 byte-for-byte. |
| "Have you regressed anything?" | Beat 4 — the 1 mismatch is a documented improvement, not a regression. |
| "What's the human-in-the-loop?" | Beat 4's SME decision panel. We classify, cite, recommend. SME decides. |
| "Will this work on our COBOL with our Unisys-style annotations?" | Beat 2 — we did. `source/cobol/DATECONV.cbl` has Unisys sequence-area annotations, GnuCOBOL ignores them, no preprocessor was needed. |
| "How do you handle dynamic SQL / dynamic dispatch?" | Open `analysis/dependency-map.md` — the `CALL 'DBIO'` line is statically dispatched here, but Devin models dynamic dispatch with explicit confidence markers. |
| "Where's the test coverage?" | `python3 -m pytest migration/converted-code/python/tests/` — 129 passing. Also Beat 2 runtime harness (80 vectors). |
| "Are there missing artifacts?" | Open `migration/RISKS-AND-GAPS.md` — Risk 1 (DATECONV) was closed by the customer's 2026-05-21 shipment. All other risks documented with confidence markers. |
| "Can you run this on our actual Oracle?" | Beat 3 honest framing — `DBDispatcher.from_env()` is the production wiring. Today's demo uses in-memory SQLite. |
| "What if we don't have GnuCOBOL?" | The harness builds in CI and ships outputs as static artifacts (`cobol-parity-report.html`, `cobol-parity-summary.json`). GnuCOBOL is only needed to *regenerate* on demand. |

---

## Files and surfaces — full inventory

### Live / runnable
- `migration/test-results/build/run-parity.sh` — GnuCOBOL + Python parity harness. Compiles + runs + diffs. **Demo critical.**
- `migration/converted-code/python/demo_app.py run` — runs LABA05 + LABD20 to stdout. **Demo critical for Beat 3.**
- `migration/converted-code/python/demo_app.py serve` — same, plus serves dashboard at `http://127.0.0.1:8765/`. **Demo critical for Beat 3.**
- `python3 -m pytest migration/converted-code/python/tests/` — 129 tests, ~5s. Optional, for reviewers who ask.

### Static HTML
- `migration/executive-report.html` — Beat 1.
- `migration/test-results/cobol-parity-report.html` — Beat 2.
- `migration/test-results/parity-console.html` — companion to Beat 2 (also served at `:8765/parity`).
- `migration/modernization-improvement-findings.html` — Beat 4. **New in this delivery.**

### Background / written record (do not open during the demo unless asked)
- `docs/customer-demo-script.md` — older 45-60 minute script from a previous engagement. This document supersedes it for the post-PR-#8 era.
- `analysis/dateconv-function-inventory.md` — 40-function inventory + dual-threshold + modernization improvement notes.
- `analysis/dependency-map.md` — before/after Mermaid graphs.
- `business-requirements/requirements-with-citations.md` — citation-backed requirements with confidence markers.
- `migration/RISKS-AND-GAPS.md` — Risk 1 CLOSED note.
- `migration/ASSUMPTIONS-AND-PLACEHOLDERS.md` — A-1 RETIRED note.
- `analysis/field-lineage.md` — field-level lineage with confidence promotion note.

---

## Last checks before going on stage

- [ ] `git log -1 --oneline` shows the PR #8 merge commit on `main`.
- [ ] `cobc --version` returns `cobc (GnuCOBOL) 3.1.2.0`.
- [ ] `bash migration/test-results/build/run-parity.sh` exits 0 with `79/80 matched; 1 documented modernization improvement(s); 0 unresolved mismatch(es)`.
- [ ] `python3 migration/converted-code/python/demo_app.py serve` is running, `http://127.0.0.1:8765/` returns 200.
- [ ] All five browser tabs (plus the terminal) are pre-opened in the order in **Pre-demo setup**.
- [ ] You have screen-share permission. Buyers should see your terminal AND your browser.
- [ ] You have read the "Devin caught it upfront" callout in `executive-report.html` once aloud before the demo so it doesn't trip you on stage.

If anything in this checklist fails, fall back to the static HTML artifacts — they tell the whole story without anything needing to run.
