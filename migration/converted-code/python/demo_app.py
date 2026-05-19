"""
demo_app.py — Runnable demo entrypoint for the modernized JV comment loader
and FY-reset utilities.

STATUS: Demo output pending SME review. Generated as part of the Guidehouse
JV COBOL/Pro*COBOL modernization walkthrough.

USAGE
-----
Zero-setup demo (uses bundled synthetic data + an in-memory sqlite mock of
Oracle):

    python -m migration.converted-code.python.demo_app run

Or the alias:

    python migration/converted-code/python/demo_app.py run

Sub-commands:
  run        Seed mock DB, then execute LABA05 (FY reset) and LABD20 (comment
             loader) against migration/test-data/. Prints a structured report.
  serve      Same as `run`, but exposes a tiny HTML dashboard at
             http://127.0.0.1:8765 showing the inputs, the run log, the
             database state before/after, and the rejection reasons. Useful
             for the live demo.

ASSUMPTIONS
-----------
- The mock DB is sqlite3 in-memory. Production wiring uses oracledb via
  DBDispatcher.from_env() (see ASSUMPTIONS-AND-PLACEHOLDERS.md A-1).
- Synthetic data is used (see migration/test-data/README.md) — no real
  customer information.
"""

from __future__ import annotations

import argparse
import io
import json
import logging
import sqlite3
import sys
from contextlib import redirect_stdout
from html import escape
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(REPO_ROOT / "migration" / "converted-code"))

from python.db_dispatcher import (  # noqa: E402
    DBDispatcher,
    build_demo_schema,
    seed_control_record,
)
from python.labd20_loader import (  # noqa: E402
    LABD20Loader,
    LoaderConfig,
    LoaderStats,
)
from python.laba05_reset import ResetOutcome  # noqa: E402
from python.laba05_reset import run as laba05_run  # noqa: E402


SYNTHETIC_DATA = REPO_ROOT / "migration" / "test-data" / "synthetic_comments.dat"
SYNTHETIC_CARD = REPO_ROOT / "migration" / "test-data" / "synthetic_card.ctl"


def _build_dispatcher_with_seed(seed_jv: int = 99) -> DBDispatcher:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    dispatcher = DBDispatcher(conn)
    build_demo_schema(dispatcher)
    seed_control_record(dispatcher, jv_number=seed_jv)
    # Seed JC_COUNT_TBL row for section 'MA' so the post-process update path
    # has a baseline (per ASSUMPTIONS A-10).
    dispatcher.insert(
        "INSERT INTO JC_COUNT_TBL (JC_SECTION, JC_COUNT_NUM) VALUES (?, ?)",
        ("MA", 0),
    )
    dispatcher.commit()
    return dispatcher


def _table_state(dispatcher: DBDispatcher) -> dict[str, list[dict[str, Any]]]:
    cur = dispatcher._conn.cursor()  # type: ignore[attr-defined]
    state: dict[str, list[dict[str, Any]]] = {}
    for table in (
        "CONTROL_RECORD_TABLE",
        "JC_SUBMITTED_COMMENT_TBL",
        "JC_COUNT_TBL",
    ):
        cur.execute(f"SELECT * FROM {table}")
        rows = [dict(r) for r in cur.fetchall()]
        if table == "CONTROL_RECORD_TABLE":
            for r in rows:
                # Trim the 400-byte blob for display.
                r["CONTROL_RECORD_DATA"] = r["CONTROL_RECORD_DATA"][:30] + "..."
        state[table] = rows
    return state


def _run_full_demo(prepare_comments_path: Path) -> dict[str, Any]:
    """Build a fresh mock DB, run LABA05 + LABD20, return a structured report."""
    log_buffer = io.StringIO()
    handler = logging.StreamHandler(log_buffer)
    handler.setLevel(logging.INFO)
    root = logging.getLogger()
    prior_level = root.level
    root.addHandler(handler)
    root.setLevel(logging.INFO)

    dispatcher = _build_dispatcher_with_seed(seed_jv=99)
    try:
        before_state = _table_state(dispatcher)

        # Phase 1: FY reset
        reset_outcome: ResetOutcome = laba05_run(dispatcher)

        # Phase 2: comment load
        loader = LABD20Loader(dispatcher)
        stats: LoaderStats = loader.run(
            LoaderConfig(
                card_path=SYNTHETIC_CARD,
                comment_path=prepare_comments_path,
                truncate_after_processing=True,
            )
        )

        after_state = _table_state(dispatcher)

        report = {
            "process_date": stats.process_date,
            "laba05": {
                "return_code": reset_outcome.return_code,
                "before_jv_number": reset_outcome.before_jv_number,
                "after_jv_number": reset_outcome.after_jv_number,
                "message": reset_outcome.message,
            },
            "labd20": {
                "total_read": stats.total_read,
                "accepted": stats.accepted,
                "inserted": stats.inserted,
                "duplicates": stats.duplicates,
                "rejected": stats.rejected,
                "submitted_total": stats.submitted_total,
                "rejected_reasons": stats.rejected_reasons,
                "report": stats.format_report(),
            },
            "before_state": before_state,
            "after_state": after_state,
            "log": log_buffer.getvalue(),
        }
        return report
    finally:
        root.removeHandler(handler)
        root.setLevel(prior_level)
        dispatcher.close()


def _copy_comments(tmp_dir: Path) -> Path:
    """Copy the synthetic file so the truncate doesn't clobber the fixture."""
    tmp_dir.mkdir(parents=True, exist_ok=True)
    target = tmp_dir / "comments.dat"
    target.write_bytes(SYNTHETIC_DATA.read_bytes())
    return target


# ---------------------------------------------------------------------------
# CLI: run
# ---------------------------------------------------------------------------
def cmd_run(args: argparse.Namespace) -> int:
    work_dir = Path(args.work_dir) if args.work_dir else REPO_ROOT / "migration" / "test-results" / "demo-run"
    comments_path = _copy_comments(work_dir)

    report = _run_full_demo(comments_path)

    if args.json:
        sys.stdout.write(json.dumps(report, indent=2, default=str))
        sys.stdout.write("\n")
        return 0

    print("=" * 72)
    print("Cognition × Guidehouse — JV COBOL modernization demo")
    print("=" * 72)
    print(f"Process date           : {report['process_date']}")
    print()
    print("LABA05 fiscal-year reset")
    print(f"  return code          : {report['laba05']['return_code']}")
    print(f"  JV-NUMBER before     : {report['laba05']['before_jv_number']}")
    print(f"  JV-NUMBER after      : {report['laba05']['after_jv_number']}")
    print(f"  message              : {report['laba05']['message']}")
    print()
    print("LABD20 comment loader")
    print(f"  records read         : {report['labd20']['total_read']}")
    print(f"  inserted             : {report['labd20']['inserted']}")
    print(f"  duplicates           : {report['labd20']['duplicates']}")
    print(f"  rejected             : {report['labd20']['rejected']}")
    print(f"  submitted total      : {report['labd20']['submitted_total']}")
    print()
    print("Rejection reasons (first 12):")
    for reason in report["labd20"]["rejected_reasons"][:12]:
        print(f"  - {reason}")
    print()
    print("Mock-DB final state (truncated for readability):")
    for table, rows in report["after_state"].items():
        print(f"  [{table}]: {len(rows)} row(s)")
    print()
    print(report["labd20"]["report"])
    return 0


# ---------------------------------------------------------------------------
# CLI: serve  (tiny HTML dashboard)
# ---------------------------------------------------------------------------
HTML_TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<title>Cognition — JV modernization demo</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{
    font-family: 'Inter Tight', 'Inter', system-ui, -apple-system, sans-serif;
    margin: 0; padding: 0; background: #fafafa; color: #111;
  }}
  header {{
    background: #000; color: #fff; padding: 18px 28px;
    display: flex; align-items: center; justify-content: space-between;
  }}
  .wordmark {{ font-weight: 600; letter-spacing: 0.3px; font-size: 18px; }}
  .wordmark .mono {{ opacity: 0.8; }}
  .container {{ max-width: 1100px; margin: 0 auto; padding: 24px; }}
  h1 {{ font-size: 24px; margin: 0 0 4px; font-weight: 600; }}
  h2 {{ font-size: 16px; margin: 24px 0 8px; font-weight: 600; }}
  .subtitle {{ color: #555; margin-bottom: 24px; font-size: 14px; }}
  .grid {{ display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }}
  .card {{
    background: #fff; border: 1px solid #e2e2e2; border-radius: 10px;
    padding: 16px 20px;
  }}
  .card .label {{ color: #666; font-size: 12px; text-transform: uppercase;
                  letter-spacing: 0.6px; }}
  .card .value {{ font-size: 28px; font-weight: 600; margin-top: 4px; }}
  .stat-row {{ display: flex; gap: 12px; flex-wrap: wrap; }}
  .stat-row .card {{ flex: 1; min-width: 180px; }}
  table {{ width: 100%; border-collapse: collapse; font-size: 13px; }}
  th, td {{ padding: 8px 10px; text-align: left;
            border-bottom: 1px solid #eee; }}
  th {{ background: #f4f4f4; font-weight: 600; }}
  pre {{
    background: #0d0d0d; color: #f4f4f4; padding: 16px;
    border-radius: 8px; overflow-x: auto; font-size: 12px; line-height: 1.45;
  }}
  .pill {{ display: inline-block; padding: 2px 10px; border-radius: 999px;
            font-size: 12px; }}
  .pill.ok {{ background: #d6f5dd; color: #14532d; }}
  .pill.warn {{ background: #fde7c5; color: #7a4a00; }}
  footer {{ color: #666; font-size: 12px; padding: 24px; text-align: center; }}
</style>
</head>
<body>
<header>
  <span class="wordmark">▰ Cognition <span class="mono">/ JV demo</span></span>
  <span style="font-size:12px; opacity:0.8;">Demo output — pending SME review</span>
</header>
<div class="container">
  <h1>JV COBOL modernization — live demo</h1>
  <div class="subtitle">
    Mock Oracle (sqlite in-memory) · Synthetic data only · Process date
    <strong>{process_date}</strong>
  </div>

  <h2>LABA05 — fiscal-year JV-NUMBER reset</h2>
  <div class="stat-row">
    <div class="card"><div class="label">return code</div><div class="value">{laba05_rc} <span class="pill {laba05_pill}">{laba05_status}</span></div></div>
    <div class="card"><div class="label">JV-number before</div><div class="value">{laba05_before}</div></div>
    <div class="card"><div class="label">JV-number after</div><div class="value">{laba05_after}</div></div>
  </div>

  <h2>LABD20 — daily comment loader</h2>
  <div class="stat-row">
    <div class="card"><div class="label">read</div><div class="value">{read}</div></div>
    <div class="card"><div class="label">inserted</div><div class="value">{inserted}</div></div>
    <div class="card"><div class="label">duplicates</div><div class="value">{duplicates}</div></div>
    <div class="card"><div class="label">rejected</div><div class="value">{rejected}</div></div>
    <div class="card"><div class="label">submitted total</div><div class="value">{submitted}</div></div>
  </div>

  <h2>Rejection reasons (sampled)</h2>
  <div class="card">
    <ul style="margin:0; padding-left: 18px;">{rejection_items}</ul>
  </div>

  <h2>JC_SUBMITTED_COMMENT_TBL — final rows</h2>
  <div class="card" style="overflow-x:auto;">
    <table>
      <thead><tr>{submitted_headers}</tr></thead>
      <tbody>{submitted_rows}</tbody>
    </table>
  </div>

  <h2>Run log</h2>
  <pre>{log}</pre>
</div>
<footer>
  Cognition — federal modernization preview. All data synthetic; no real
  customer information used.
</footer>
</body>
</html>
"""


def _render_html(report: dict[str, Any]) -> str:
    submitted_rows = report["after_state"].get("JC_SUBMITTED_COMMENT_TBL", [])
    if submitted_rows:
        headers = list(submitted_rows[0].keys())
        submitted_headers = "".join(f"<th>{escape(h)}</th>" for h in headers)
        submitted_rows_html = "".join(
            "<tr>" + "".join(f"<td>{escape(str(r.get(h,'')))}</td>" for h in headers) + "</tr>"
            for r in submitted_rows
        )
    else:
        submitted_headers = "<th>(no rows)</th>"
        submitted_rows_html = ""

    rejection_items = "".join(
        f"<li>{escape(r)}</li>"
        for r in report["labd20"]["rejected_reasons"][:20]
    ) or "<li>(no rejections)</li>"

    laba05_rc = report["laba05"]["return_code"]
    return HTML_TEMPLATE.format(
        process_date=escape(report["process_date"]),
        laba05_rc=laba05_rc,
        laba05_pill="ok" if laba05_rc == 0 else "warn",
        laba05_status="success" if laba05_rc == 0 else "needs review",
        laba05_before=report["laba05"]["before_jv_number"],
        laba05_after=report["laba05"]["after_jv_number"],
        read=report["labd20"]["total_read"],
        inserted=report["labd20"]["inserted"],
        duplicates=report["labd20"]["duplicates"],
        rejected=report["labd20"]["rejected"],
        submitted=report["labd20"]["submitted_total"],
        rejection_items=rejection_items,
        submitted_headers=submitted_headers,
        submitted_rows=submitted_rows_html,
        log=escape(report["log"]),
    )


def cmd_serve(args: argparse.Namespace) -> int:
    import http.server
    import socketserver

    work_dir = Path(args.work_dir) if args.work_dir else REPO_ROOT / "migration" / "test-results" / "demo-serve"
    comments_path = _copy_comments(work_dir)
    report = _run_full_demo(comments_path)
    html = _render_html(report)

    out_path = work_dir / "index.html"
    out_path.write_text(html, encoding="utf-8")
    print(f"Wrote dashboard to {out_path}")

    if args.no_serve:
        return 0

    class Handler(http.server.SimpleHTTPRequestHandler):
        def do_GET(self):  # noqa: N802
            if self.path in ("/", "/index.html"):
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.end_headers()
                self.wfile.write(html.encode("utf-8"))
            elif self.path == "/report.json":
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(json.dumps(report, indent=2, default=str).encode())
            else:
                self.send_response(404)
                self.end_headers()

        def log_message(self, format, *fmt_args):  # noqa: A002, N802
            pass

    address = ("127.0.0.1", args.port)
    print(f"Serving demo dashboard at http://127.0.0.1:{args.port}/ — Ctrl-C to stop.")
    with socketserver.TCPServer(address, Handler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down.")
    return 0


# ---------------------------------------------------------------------------
# Entry
# ---------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="demo_app", description="Cognition × Guidehouse JV modernization demo")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_run = sub.add_parser("run", help="Run LABA05 + LABD20 against the in-memory mock DB.")
    p_run.add_argument("--work-dir", help="Where to copy synthetic input (truncate-safe).")
    p_run.add_argument("--json", action="store_true", help="Emit machine-readable JSON only.")
    p_run.set_defaults(func=cmd_run)

    p_serve = sub.add_parser("serve", help="Run + open a tiny HTML dashboard.")
    p_serve.add_argument("--port", type=int, default=8765)
    p_serve.add_argument("--no-serve", action="store_true", help="Generate the HTML and exit.")
    p_serve.add_argument("--work-dir", help="Where to copy synthetic input (truncate-safe).")
    p_serve.set_defaults(func=cmd_serve)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main(sys.argv[1:]))
