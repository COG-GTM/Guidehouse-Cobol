---
name: cobol-modernization-demo-execution
description: Use during a live or rehearsed walkthrough of the JV COBOL modernization demo for Guidehouse/SBA (executive report, live dashboard, parity console, CLI proof). Covers the exact run order, talk-track guardrails, honest-framing language, and the four artifacts that must reconcile.
---

# COBOL modernization demo — execution skill

Use this when **running** the demo (not when building it). For building new artifacts, see `cobol-modernization-demo` and `AGENTS.md`.

Source-of-truth narrative: [`docs/demo-walkthrough-guide.md`](../../../docs/demo-walkthrough-guide.md). This skill is the operational checklist that companion guide is built on — keep them in sync.

## What the customer sees (5 artifacts, in this order)

1. `migration/executive-report.html` — branded executive walkthrough (open in browser). T0→T6 demo-cycle timeline + "Devin caught it upfront" callout + 5 SVG diagrams + parity hero + risk register + 21-question board.
2. **GnuCOBOL runtime parity proof** — `bash migration/test-results/build/run-parity.sh` compiles `source/cobol/DATECONV.cbl` under GnuCOBOL 3.1.2 and diffs 80 test vectors against the modernized Python. **This is the strongest single proof point in the demo.** Headline: 79/80 byte-for-byte identical, 1 documented modernization improvement, 0 unresolved mismatches. Final report: `migration/test-results/cobol-parity-report.html`.
3. `python3 migration/converted-code/python/demo_app.py serve` → `http://127.0.0.1:8765/` live dashboard.
4. `http://127.0.0.1:8765/parity` — 38-BR parity console with per-BR expandable COBOL+Python source and the honest-framing yellow callout.
5. `migration/modernization-improvement-findings.html` — one-slide executive callout for the 02/29/1900 Julian-vs-Gregorian leap-year finding (Beat 4).

CLI proof anywhere in the run: `demo_app.py run`, `demo_app.py parity`, `pytest`. These five must reconcile. If they don't, stop and fix before the demo.

## Pre-demo sanity check (run all of these once before the call)

```bash
cd <repo-root>
python3 -m pytest migration/converted-code/python/tests/ -q
bash migration/test-results/build/run-parity.sh           # GnuCOBOL ↔ Python runtime parity
python3 migration/converted-code/python/demo_app.py run
python3 migration/converted-code/python/demo_app.py parity
python3 migration/converted-code/python/demo_app.py serve &  # leave running
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8765/
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8765/parity
```

Expected (do NOT proceed if any of these is wrong):

- pytest: `129 passed`.
- `run-parity.sh` final line: `DATECONV parity: 79/80 matched; 1 documented modernization improvement(s); 0 unresolved mismatch(es)`; exit 0.
- `demo_app.py run`: `21 records read · 7 inserted · 2 duplicates · 12 rejected · JV-NUMBER reset 99 → 1`.
- `demo_app.py parity`: `PASS=38 FAIL=0 UNRESOLVED=0` (HIGH 36 / MED 1 / LOW 1 confidence breakdown).
- Both curl checks return `200`.
- Open `executive-report.html` in Chrome and confirm: sticky header, T0→T6 demo-cycle timeline with the green "Devin caught it upfront" callout, 5 SVG diagrams (not raw `graph TD` text), parity hero, Q&A board renders 21 cards.

If any number drifts (e.g. the GnuCOBOL harness shows unresolved mismatches, or a Mermaid diagram is a red error box), the harness / renderer / server is broken — do not demo until fixed.

> ⚠️ **Known stale-numbers risk in `executive-report.html`:** the T0→T6 narrative is current, but some of the page's quantitative snapshot blocks may still read `52 tests` / `28 / 28 parity` / `52 vectors` from before PR #8 / #9. If the customer is going to scrutinize those tiles, regenerate the page or open the live dashboard alongside so the live numbers anchor the conversation.

## Run order in front of the customer (4-beat narrative)

This matches the talk-track in [`docs/demo-walkthrough-guide.md`](../../../docs/demo-walkthrough-guide.md):

1. **Beat 1 — Executive board.** Open `executive-report.html`. Anchor with the branded header, the T0→T6 demo-cycle timeline (Guidehouse first contact → 2026-05-21 DATECONV closure → today), and the green "Devin caught it upfront" callout. Scroll once top-to-bottom so they see the execution board, the 5 SVG diagrams, the parity hero, the 11 risks, the 21-question board, and the traceability matrix.
2. **Beat 2 — GnuCOBOL runtime parity, live in shell.** Run `bash migration/test-results/build/run-parity.sh` in a terminal next to the browser. Read the final line out loud: *"79 of 80 vectors byte-for-byte identical, 1 documented modernization improvement, 0 unresolved mismatches."* Then open `migration/test-results/cobol-parity-report.html` and scroll the "14 bugs caught" story. This is the moment that answers federal acquisition's "are you sure you're not silently regressing the legacy?" question.
3. **Beat 3 — Python end-to-end + parity console.** Switch to the live dashboard at `http://127.0.0.1:8765/`. Point at the JV-NUMBER 99→1 cell and the 21/7/2/12 counters. Note the realistic synthetic names in `JC_SUBMITTED_COMMENT_TBL` (ALICE.SUBMITTER / BOB.APPROVER) — that's the "this isn't a toy" signal. Then open `/parity` and expand `BR-LABA05-001` to show the COBOL source pane, the Python conversion pane, and the expected-vs-actual block.
4. **Beat 4 — Modernization-improvement callout.** Open `migration/modernization-improvement-findings.html`. Walk the 02/29/1900 / 2100 / 2200 / 2300 table and the Option A / Option B SME decision card. The closing line: *"a naive port would have backported the Julian rule to make parity 80/80 — we deliberately did not, and we put the trade-off in front of you."*

CLI proof can come up anywhere — `demo_app.py parity` and `pytest` in a sidecar terminal show the same numbers without screenshots.

## Honest-framing language (say this out loud)

The parity console's *expected* column is a COBOL-source-derived golden output — not a live mainframe execution — because this VM has no Pro*COBOL precompiler or Oracle.

Use the in-page phrasing verbatim: "in a real engagement, flip one config switch and the expected column becomes a live mainframe execution against the same synthetic input, producing the byte-for-byte diff that SBA question 2 / 14 describes."

The *actual* column is always a live Python run against an in-memory mock Oracle.

This is already disclosed in the yellow callout at the top of `/parity` and in the "Honest framing" paragraph of the executive report. Saying it out loud removes any chance of a customer engineer feeling misled.

## What changed on 2026-05-21 (DATECONV closure)

Guidehouse shipped the full date-conversion subsystem closure: `source/copybooks/DATECONV-WS.cpy`, `source/copybooks/DATECONV-PD.cpy`, `source/cobol/DATECONV.cbl`, and four JDN helpers (`JDN-CONSTANTS-WS.cpy`, `JDN-PACKET-WS.cpy`, `JDN-RECORD-WS.cpy`, `JDN-RECORD-ACCESS.cpy`). Every `COPY` and `CALL` originating from `LABD20.pco` now resolves end-to-end. Risk 1 → CLOSED. Assumption A-1 → retired. `BR-LABD20-006` confidence LOW → HIGH.

The Python `check_cymd_dt` stub is replaced by the faithful port in [`migration/converted-code/python/dateconv.py`](../../../migration/converted-code/python/dateconv.py). The 42-function inventory lives in [`analysis/dateconv-function-inventory.md`](../../../analysis/dateconv-function-inventory.md). The runtime parity harness in `migration/test-results/build/` is the proof that the port matches byte-for-byte (modulo the documented leap-year improvement).

## One placeholder that is intentional (don't apologize for it)

- `laba05_reset._extract_jv_number` — JV-NUMBER USAGE BINARY conversion (`struct.unpack('>I', ...)`) against a real Oracle is the production-mode swap; demo uses the display form. This is the single remaining flagged item on the parity console; frame it as a config switch, not a gap. (`labd20_loader.check_cymd_dt` is **no longer** a placeholder as of 2026-05-21 — it now calls into the faithful `dateconv.py` port.)

## Aspirational vs source-grounded answers

On the 21-question board, **gray chips = file/line citation from this PR**; **amber chips = aspirational / platform-level claim** (security posture, FedRAMP timeline, Windsurf IDE — Margarita's questions). If a customer asks about an amber-chipped answer, route it to the account team — don't try to prove it from the four files in this PR.

## Numbers that must reconcile across all five artifacts

| Number | Where it shows up |
|---|---|
| 129 pytest passed | exec report verification section · `pytest` |
| 38 PASS / 0 FAIL / 0 UNRESOLVED (HIGH 36 / MED 1 / LOW 1) | `/parity` header · `demo_app.py parity` · exec report parity hero |
| 79 / 80 vectors byte-for-byte; 1 documented modernization improvement; 0 unresolved | `run-parity.sh` output · `cobol-parity-report.html` · `modernization-improvement-findings.html` |
| 21 read / 7 inserted / 2 dups / 12 rejected | exec report live-demo section · `/` dashboard · `demo_app.py run` |
| JV-NUMBER 99 → 1 | exec report live-demo section · `/` dashboard · `demo_app.py run` |
| 21 / 21 questions answered | exec report quantitative snapshot · Q&A board count |

If any one of these diverges, the demo is broken — fix before proceeding.

## Common pitfalls during live execution

- Don't kill the `serve` process between artifacts. Keep it running in a background shell so `/` and `/parity` stay reachable.
- Don't open `migration/test-results/parity-console.html` as a substitute for `/parity` unless the server is down. The static console and the live one have the same content, but the live one matches the "served by demo_app" story the executive report tells.
- If the customer asks "did this actually run against Oracle?" → no, the demo uses an in-memory mock Oracle (SQLite in-memory). The Python is the modernized path; the COBOL **was** re-executed (via GnuCOBOL) for the date subsystem — that's the byte-for-byte 79/80 number. Use the in-page yellow callout's exact wording for everything else.
- Don't claim "byte-for-byte parity" without re-running `run-parity.sh` first. The number is real — make sure you've earned it on this VM.
- Don't open `RISKS-AND-GAPS.md` in a separate tab during the demo — the executive report's risks section already summarizes all 11 and links out for SMEs. Save the raw markdown for the SME review session after the demo.

## Where to look when something breaks

- Server returns non-200 on `/` or `/parity`: kill and restart `demo_app.py serve`.
- A diagram in `executive-report.html` shows as a red box: Mermaid CDN didn't load. Open browser devtools → Network and confirm the Mermaid script. If offline, the static `migration/test-results/parity-console.html` still works for the parity portion of the demo.
- Parity hero shows `0 / 38`: the parity engine didn't run. Re-run `demo_app.py parity` to confirm `PASS=38`, then refresh the dashboard.
- `run-parity.sh` fails to compile: verify GnuCOBOL 3.1.2 is on PATH (`cobc --version`). If missing, `sudo apt-get install -y gnucobol`. The harness needs no network and no Oracle.

## Devin secrets needed

None for the demo path itself. The repo is private and demo-only; no live Oracle, no real customer data, no auth.

## Out of scope for this skill

- Modifying customer-supplied source files under `source/` (forbidden by `AGENTS.md`).
- Generating new business requirements or lineage — that's `cobol-modernization-demo`'s job.
- Claiming Pro*COBOL or Oracle ran. They didn't.
