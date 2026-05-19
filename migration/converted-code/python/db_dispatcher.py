"""
db_dispatcher.py — Modernized analog of source/procobol/DBIO.pco.

STATUS: Demo output pending SME review. Generated as part of the Guidehouse JV
COBOL/Pro*COBOL modernization walkthrough.

PURPOSE
-------
DBIO.pco is a generic Oracle Pro*COBOL dispatcher that legacy programs (LABA05,
LABD20) call to perform CONNECT / SELECT / INSERT / UPDATE / DELETE / COMMIT /
ROLLBACK against Oracle.  It constructs the target IO-routine name at runtime
by string-concatenating the table name with `-IO` (see DBIO.pco:228-260) and
maps SQLCODE values to four-character DMS return codes (DBIO.pco:374-398).

This module preserves the same *contract* (one entrypoint per DB verb, with a
DMS-style return code) but replaces the dynamic-dispatch + Oracle-specific
runtime with a clean DB-API 2.0 abstraction.  Any PEP 249 connection works —
the demo wires it to sqlite3, production would wire it to oracledb / cx_Oracle.

ASSUMPTIONS (see migration/ASSUMPTIONS-AND-PLACEHOLDERS.md)
----------------------------------------------------------
- A-1: Connection credentials come from environment variables (never from the
  legacy /tst/.oralogin and /tst/.orapasswd files; see RISKS-AND-GAPS.md Risk 3).
- A-3: The SQLCODE→DMS translation reproduces the legacy mapping verbatim from
  DBIO.pco:374-398. Any additional Oracle codes the legacy site relied on but
  did not list there are NOT preserved here; SME review required.
- A-9: The dynamic-dispatch path (DBIO.pco:228-260) is replaced by direct
  method calls. Any *unlisted* table the legacy site reached via the dispatcher
  is unsupported by this module — see RISKS-AND-GAPS.md Risk 4.

PLACEHOLDERS / SME REVIEW
-------------------------
- The exact behavior on Oracle-only conditions (ROWID, RETURNING INTO,
  AUTOCOMMIT) cannot be exercised against sqlite3 in the demo. Marked inline.
"""

from __future__ import annotations

import logging
import os
import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass, field
from typing import Any, Iterable, Iterator, Mapping, Optional, Sequence

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# DMS return-code constants — mirrors DBIO.pco:374-398 (5300-TRANSLATE-SQLCODE)
# ---------------------------------------------------------------------------
# Legacy callers (LABA05, LABD20) check this 4-character DMS code (aliased
# inside the caller as ERROR-NUM), NOT the raw SQLCODE. The modernized
# wrapper preserves the same surface so behavior-equivalence reviews are
# unambiguous.
DMS_OK = "0000"               # SQLCODE 0     — DBIO.pco:377
DMS_NOT_FOUND = "0013"        # SQLCODE 100   — DBIO.pco:379 (no row found)
DMS_NOT_FOUND_SET = "0007"    # SQLCODE 100 + set-name set — DBIO.pco:382
DMS_BAD_FETCH = "0005"        # SQLCODE -1    — DBIO.pco:385
DMS_CONTINUE_8103 = "0000"    # SQLCODE -8103 — DBIO.pco:387 (legacy treats as 0)
DMS_UNHANDLED = "9999"        # any other code — DBIO.pco:397


def translate_sqlcode(
    sqlcode: int,
    *,
    set_name: str = "",
    function_type: str = "",
) -> str:
    """Reproduce 5300-TRANSLATE-SQLCODE from DBIO.pco:374-398.

    The legacy logic is verbatim:
      0     → 0000
      100   → 0013 (or 0007 if a DB-SET-NAME is non-blank AND function-type
              is not 'FETCH OWNER' — DBIO.pco:380-383)
      -1    → 0005
      -8103 → 0000 (with a stderr-style DISPLAY block — see DBIO.pco:387-395;
              we route that to the logger)
      other → 9999
    """
    if sqlcode == 0:
        return DMS_OK
    if sqlcode == 100:
        if set_name.strip() and function_type != "FETCH OWNER":
            return DMS_NOT_FOUND_SET
        return DMS_NOT_FOUND
    if sqlcode == -1:
        return DMS_BAD_FETCH
    if sqlcode == -8103:
        logger.warning(
            "RECEIVED ORACLE ERROR -8103, CONTINUE..."
        )  # PLACEHOLDER: DBIO.pco:388-395 also displays table/function/key/rowid
        return DMS_CONTINUE_8103
    return DMS_UNHANDLED


# ---------------------------------------------------------------------------
# Configuration — explicit env-var driven; never read credential files.
# ---------------------------------------------------------------------------
# ASSUMPTION A-1 (migration/ASSUMPTIONS-AND-PLACEHOLDERS.md):
#   Production deployments must populate these via a managed secrets store.
#   The demo defaults to in-memory sqlite for zero-setup walkthrough.
ENV_DB_BACKEND = "JV_DB_BACKEND"      # "sqlite" (demo) or "oracle" (prod)
ENV_DB_DSN = "JV_DB_DSN"              # Oracle DSN, or sqlite path
ENV_DB_USER = "JV_DB_USER"
ENV_DB_PASSWORD = "JV_DB_PASSWORD"    # SME-REVIEW: route via secrets manager


@dataclass
class DispatcherResult:
    """Mirrors the (DB-RTNCODE-DMS, DB-SQLCODE-NUM, DB-MESSAGE) tuple that
    DBIO.pco writes back to its callers (cf. 9999-ERROR at DBIO.pco:402-405).
    """

    rtncode_dms: str
    sqlcode: int = 0
    message: str = ""
    rows: list[tuple] = field(default_factory=list)
    rowcount: int = 0

    @property
    def ok(self) -> bool:
        return self.rtncode_dms == DMS_OK


# ---------------------------------------------------------------------------
# Dispatcher
# ---------------------------------------------------------------------------
class DBDispatcher:
    """Modernized analog of DBIO.pco.

    Public methods correspond to the DBIO verbs used by LABA05 / LABD20:
      - connect            ← DBIO.pco:188-215 (CONNECT-RTN, GET-FILE-NAMES)
      - select_one         ← used by both LABA05 (FETCH-CTRL-REC) and
                              LABD20 (duplicate-check + stats queries)
      - insert             ← LABD20.pco:342-389
      - update             ← LABD20.pco:392-405, LABA05.cbl:176-205
      - commit / rollback  ← LABD20.pco:408-413, 489+
      - count_rows         ← LABD20.pco:421-446 (stats SELECTs)

    The dynamic name-construction in DBIO.pco:228-260 (STRING table-name
    DELIMITED BY SPACE '-IO' INTO IO-SUBROUTINE) is intentionally NOT
    reproduced — see RISKS-AND-GAPS.md Risk 4. Modern callers call typed
    methods.
    """

    def __init__(self, connection: Any) -> None:
        self._conn = connection

    # ------- factory helpers ------------------------------------------------
    @classmethod
    def from_env(cls) -> "DBDispatcher":
        """Build a dispatcher from JV_DB_* environment variables.

        ASSUMPTION A-1: credentials never come from disk files like
        /tst/.oralogin (see RISKS-AND-GAPS.md Risk 3).
        """
        backend = os.environ.get(ENV_DB_BACKEND, "sqlite").lower()
        dsn = os.environ.get(ENV_DB_DSN, ":memory:")

        if backend == "sqlite":
            conn = sqlite3.connect(dsn)
            conn.row_factory = sqlite3.Row
            return cls(conn)

        # PLACEHOLDER: Oracle wiring shown for documentation; not exercised in
        # the demo because the runtime has no oracledb installed.
        if backend == "oracle":  # pragma: no cover - SME-REVIEW
            try:
                import oracledb  # type: ignore
            except ImportError as exc:  # pragma: no cover
                raise RuntimeError(
                    "JV_DB_BACKEND=oracle requires the oracledb package; "
                    "install it and re-run."
                ) from exc
            user = os.environ.get(ENV_DB_USER)
            password = os.environ.get(ENV_DB_PASSWORD)
            if not (user and password and dsn):
                raise RuntimeError(
                    f"Set {ENV_DB_USER}/{ENV_DB_PASSWORD}/{ENV_DB_DSN} "
                    "via your secrets manager."
                )
            conn = oracledb.connect(user=user, password=password, dsn=dsn)
            return cls(conn)

        raise ValueError(f"Unknown {ENV_DB_BACKEND}={backend!r}")

    # ------- read paths -----------------------------------------------------
    def select_one(
        self,
        sql: str,
        params: Sequence[Any] | Mapping[str, Any] = (),
    ) -> DispatcherResult:
        """Execute a SELECT and fetch at most one row.

        Used by:
          - LABA05.cbl FETCH-CTRL-REC (LABA05.cbl:152-174) for CONTROL_RECORD_TABLE
          - LABD20.pco DETERMINE-IF-DUPLICATE (LABD20.pco:317-339)
        """
        try:
            cur = self._conn.cursor()
            cur.execute(sql, params)
            row = cur.fetchone()
            if row is None:
                return DispatcherResult(rtncode_dms=DMS_NOT_FOUND, sqlcode=100)
            # sqlite3.Row → tuple for cross-driver consistency
            row_tuple = tuple(row)
            return DispatcherResult(
                rtncode_dms=DMS_OK, sqlcode=0, rows=[row_tuple], rowcount=1
            )
        except Exception as exc:
            return self._error(exc)

    def count_rows(self, table: str, where: str = "1=1", params: Sequence[Any] = ()) -> int:
        """Mirror the post-process stats SELECTs in LABD20.pco:421-446.

        Note: `table` is a literal here, not a bind parameter, because Oracle
        and SQL standards do not allow tables to be bound. Callers must pass a
        known table identifier — never user input.
        """
        # ASSUMPTION: identifier whitelisting (the only legal callers pass
        # static names) — see RISKS-AND-GAPS.md Risk 4 mitigation.
        sql = f"SELECT COUNT(*) FROM {table} WHERE {where}"  # noqa: S608 - whitelisted
        cur = self._conn.cursor()
        cur.execute(sql, params)
        row = cur.fetchone()
        return int(row[0]) if row else 0

    # ------- write paths ----------------------------------------------------
    def insert(self, sql: str, params: Sequence[Any] | Mapping[str, Any]) -> DispatcherResult:
        """Execute an INSERT (LABD20.pco:342-389 in spirit)."""
        return self._exec_write(sql, params)

    def update(self, sql: str, params: Sequence[Any] | Mapping[str, Any]) -> DispatcherResult:
        """Execute an UPDATE.

        Used by:
          - LABA05.cbl MODIFY-CTRL-REC (LABA05.cbl:176-205) to reset JV-NUMBER
          - LABD20.pco UPDATE JC_COUNT_TBL (LABD20.pco:392-405)
        """
        return self._exec_write(sql, params)

    def delete(self, sql: str, params: Sequence[Any] | Mapping[str, Any] = ()) -> DispatcherResult:
        """Execute a DELETE. Not used by LABA05 / LABD20 directly but exposed
        for parity with CONTROL-RECORD-TABLE-IO.pco DELETE-DB-DATA paths."""
        return self._exec_write(sql, params)

    def _exec_write(self, sql: str, params: Any) -> DispatcherResult:
        try:
            cur = self._conn.cursor()
            cur.execute(sql, params)
            return DispatcherResult(
                rtncode_dms=DMS_OK, sqlcode=0, rowcount=cur.rowcount or 0
            )
        except Exception as exc:
            return self._error(exc)

    # ------- transaction control -------------------------------------------
    def commit(self) -> DispatcherResult:
        """Mirror EXEC SQL COMMIT WORK (LABD20.pco:413)."""
        try:
            self._conn.commit()
            return DispatcherResult(rtncode_dms=DMS_OK, sqlcode=0)
        except Exception as exc:
            return self._error(exc)

    def rollback(self) -> DispatcherResult:
        """Mirror EXEC SQL ROLLBACK (LABD20.pco:9999-ROLL-BACK section)."""
        try:
            self._conn.rollback()
            return DispatcherResult(rtncode_dms=DMS_OK, sqlcode=0)
        except Exception as exc:
            return self._error(exc)

    def close(self) -> None:
        try:
            self._conn.close()
        except Exception:  # pragma: no cover
            logger.exception("Error closing DB connection")

    @contextmanager
    def transaction(self) -> Iterator["DBDispatcher"]:
        """Helper context manager: commits on clean exit, rolls back on
        exception. Maintains the LABD20.pco commit-or-rollback semantics."""
        try:
            yield self
            self.commit()
        except Exception:
            self.rollback()
            raise

    # ------- error mapping --------------------------------------------------
    def _error(self, exc: Exception) -> DispatcherResult:
        """Convert a Python exception into the DBIO 9999-ERROR shape
        (DBIO.pco:402-405) — sqlcode + message + DMS code.
        """
        sqlcode = self._derive_sqlcode(exc)
        dms = translate_sqlcode(sqlcode)
        logger.error("DBIO-equivalent error: sqlcode=%s dms=%s err=%s", sqlcode, dms, exc)
        return DispatcherResult(rtncode_dms=dms, sqlcode=sqlcode, message=str(exc))

    @staticmethod
    def _derive_sqlcode(exc: Exception) -> int:
        """Pull a SQLCODE-equivalent out of the driver's exception.

        - oracledb.DatabaseError exposes .args[0].code
        - sqlite3.Error has no equivalent; we synthesize -1 (DMS_BAD_FETCH) so
          callers still see a non-zero DMS code.

        SME-REVIEW: production deployments should add their driver here.
        """
        code = getattr(exc, "code", None)
        if code is not None:
            return int(code)
        args = getattr(exc, "args", ())
        if args and hasattr(args[0], "code"):
            try:
                return int(args[0].code)
            except (TypeError, ValueError):
                pass
        return -1


# ---------------------------------------------------------------------------
# Convenience: build the schema used by the demo + tests.
# ---------------------------------------------------------------------------
# ASSUMPTION A-7: the table shapes below reflect database/descriptions/*.txt
# (JC_SUBMITTED_COMMENT_TBL, JC_COUNT_TBL, CONTROL_RECORD_TABLE). Oracle DDL
# types are simplified to sqlite-compatible affinities for the demo runtime.
DEMO_SCHEMA_DDL: tuple[str, ...] = (
    """
    CREATE TABLE IF NOT EXISTS JC_SUBMITTED_COMMENT_TBL (
        JC_SUBMITTED                TEXT PRIMARY KEY,    -- 26 chars composite
        JC_SUBMITTED_SCHED_DOC_NO   TEXT,
        JC_SUBMITTED_COMMENT_HIST   TEXT,
        JC_SUBMITTED_COMMENT_REQUESTOR TEXT,
        JC_SUBMITTED_COMMENT_APPROVER  TEXT,
        JC_SUBMITTED_CONTROL_NUM    TEXT,
        JC_SUBMITTED_UPDT_PROG_ID   TEXT,
        JC_SUBMITTED_UPDT_PROG_DT   TEXT,                -- YYYY-MM-DD as text
        JC_SUBMITTED_NUMBER         INTEGER
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS JC_COUNT_TBL (
        JC_SECTION                  TEXT PRIMARY KEY,    -- legacy describe-file has a PK typo; see RISKS-AND-GAPS.md
        JC_COUNT_NUM                INTEGER
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS JC_REJECTED_COMMENT_TBL (
        JC_REJECTED                 TEXT PRIMARY KEY,
        JC_REJECTED_REASON          TEXT
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS JC_APPLIED_COMMENT_TBL (
        JC_APPLIED                  TEXT PRIMARY KEY
    )
    """,
    """
    CREATE TABLE IF NOT EXISTS CONTROL_RECORD_TABLE (
        CONTROL_RECORD_NAME         TEXT,
        CONTROL_RECORD_NUMBER       INTEGER,
        CONTROL_RECORD_DATA         TEXT,                -- CHAR(400) in Oracle
        PRIMARY KEY (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER)
    )
    """,
)


def build_demo_schema(dispatcher: DBDispatcher) -> None:
    """Create the demo tables. Idempotent."""
    for ddl in DEMO_SCHEMA_DDL:
        dispatcher._conn.executescript(ddl)  # type: ignore[attr-defined]
    dispatcher.commit()


def seed_control_record(dispatcher: DBDispatcher, jv_number: int = 42) -> None:
    """Insert a JV-CONTROL-REC row so LABA05 has something to reset.

    The 400-byte CONTROL_RECORD_DATA layout reflects JV-CONTROL-REC.cpy:
      bytes  1- 6  : JV-CONTROL-1
      bytes  7-12  : JV-CONTROL-2
      bytes 13-18  : JV-CONTROL-3
      bytes 19-24  : JV-CONTROL-4
      bytes 25-30  : JV-NUMBER (legacy USAGE BINARY; here display 6-digit)
      bytes 31-39  : filler
      bytes 40-45  : JV-CONTROL-5
      bytes 46-55  : filler
    See migration/analysis/field-lineage.md for the full mapping.

    ASSUMPTION A-2: For the demo we use a *display* representation of the
    JV-NUMBER (6-char zoned). Production must reproduce the legacy USAGE
    BINARY layout — see RISKS-AND-GAPS.md Risk 2.
    """
    data = (
        "000001"        # JV-CONTROL-1
        "000002"        # JV-CONTROL-2
        "000003"        # JV-CONTROL-3
        "000004"        # JV-CONTROL-4
        f"{jv_number:06d}"  # JV-NUMBER (display)
        + " " * 9       # filler
        + "000005"      # JV-CONTROL-5
        + " " * 355     # remaining filler up to 400 bytes
    )
    assert len(data) == 400, f"CONTROL_RECORD_DATA must be 400 bytes, got {len(data)}"
    dispatcher.insert(
        """
        INSERT INTO CONTROL_RECORD_TABLE
            (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER, CONTROL_RECORD_DATA)
        VALUES (?, ?, ?)
        """,
        ("JV-CONTROL-REC", 1, data),
    )
    dispatcher.commit()


__all__ = [
    "DMS_OK",
    "DMS_NOT_FOUND",
    "DMS_NOT_FOUND_SET",
    "DMS_BAD_FETCH",
    "DMS_UNHANDLED",
    "DispatcherResult",
    "DBDispatcher",
    "translate_sqlcode",
    "build_demo_schema",
    "seed_control_record",
    "DEMO_SCHEMA_DDL",
]
