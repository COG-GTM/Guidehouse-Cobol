"""
laba05_reset.py — Modernized analog of source/cobol/LABA05.cbl.

STATUS: Demo output pending SME review. Generated as part of the Guidehouse JV
COBOL/Pro*COBOL modernization walkthrough.

LEGACY BEHAVIOR (source/cobol/LABA05.cbl)
----------------------------------------
LABA05 is a fiscal-year reset utility. It:
1. Calls DBIO CONNECT to attach to Oracle. (LABA05.cbl:69-85)
2. SELECTs the JV-CONTROL-REC row from CONTROL_RECORD_TABLE keyed by
   CONTROL_RECORD_NAME='JV-CONTROL-REC' AND CONTROL_RECORD_NUMBER=1.
   (FETCH-CTRL-REC, LABA05.cbl:152-174)
3. Displays the JV-NUMBER it found.
4. MOVE 1 TO JV-NUMBER and calls DBIO UPDATE.
   (MODIFY-CTRL-REC, LABA05.cbl:176-205)
5. Displays the post-update JV-NUMBER.
6. Returns 0 on success, 99 on any DBIO error (LABA05.cbl:RETURN-CODE setting).

The CONTROL_RECORD_DATA column is a CHAR(400) blob; the JV-NUMBER lives at a
fixed byte offset and is stored as USAGE BINARY in the legacy COBOL copybook
(see source/copybooks/JV-CONTROL-REC.cpy and source/procobol/CONTROL-RECORD-
TABLE-IO.pco:21-28). Reading it requires a binary→display conversion before
display and a display→binary conversion before UPDATE. See RISKS-AND-GAPS.md
Risk 2 for details.

ASSUMPTIONS (see migration/ASSUMPTIONS-AND-PLACEHOLDERS.md)
----------------------------------------------------------
- A-2: The 400-byte CONTROL_RECORD_DATA layout for JV-CONTROL-REC is taken
  from JV-CONTROL-REC.cpy. In production the JV-NUMBER stretch is `USAGE
  BINARY`; the demo uses a 6-byte zoned-display representation (display
  side of CONTROL-RECORD-TABLE-IO.pco:25-28). SME review required to
  preserve byte-exact binary behavior in production.
- A-3: SQLCODE→DMS mapping (see db_dispatcher.translate_sqlcode) matches
  DBIO.pco:374-398 verbatim.
- A-6: Caller-visible return codes (0 / 99) match LABA05's RETURN-CODE
  conventions (LABA05.cbl: see PROGRAM-EXIT paragraphs).

PLACEHOLDERS / SME REVIEW
-------------------------
- The legacy DISPLAY statements ("PROCESS DATE IS…", "OLD JV NUMBER WAS…")
  are routed through logging.info; the literal text differs from the COBOL
  output. Tooling that scrapes the legacy stdout will need adjustment.
"""

from __future__ import annotations

import logging
import sys
from dataclasses import dataclass
from typing import Optional

from . import db_dispatcher
from .db_dispatcher import DBDispatcher, DispatcherResult

logger = logging.getLogger(__name__)


CONTROL_RECORD_NAME = "JV-CONTROL-REC"
CONTROL_RECORD_NUMBER = 1
TARGET_JV_NUMBER = 1
"""LABA05.cbl:184-189 — MOVE 1 TO JV-NUMBER OF JV-CONTROL-REC."""

# Byte offsets inside CONTROL_RECORD_DATA (400 bytes). Derived from
# source/copybooks/JV-CONTROL-REC.cpy. See db_dispatcher.seed_control_record
# for the layout the demo writes.
# ASSUMPTION A-2: byte offsets are 0-based Python slices, end-exclusive.
JV_NUMBER_SLICE = slice(24, 30)   # 6 bytes; legacy USAGE BINARY (see Risk 2)
JV_CONTROL_DATA_LENGTH = 400


RC_OK = 0
RC_DB_ERROR = 99
"""LABA05.cbl: any DBIO non-zero return triggers MOVE 99 TO RETURN-CODE."""


@dataclass
class ResetOutcome:
    """Structured result from run() — useful for tests and the demo CLI."""

    return_code: int
    before_jv_number: Optional[int]
    after_jv_number: Optional[int]
    message: str = ""

    @property
    def ok(self) -> bool:
        return self.return_code == RC_OK


def _extract_jv_number(data: str) -> int:
    """Decode the JV-NUMBER stretch out of CONTROL_RECORD_DATA.

    Legacy: source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28 distinguishes
    JV-CONTROL-REC-B (binary form, used to talk to the DB) from
    JV-CONTROL-REC-D (display form, used to talk to in-memory COBOL).

    Demo: the seeded data is the display form, so we parse 6 ASCII digits.

    # PLACEHOLDER for production: replace this with
    #   struct.unpack('>I', data[24:30])  (or equivalent for the legacy COMP)
    """
    chunk = data[JV_NUMBER_SLICE]
    return int(chunk)


def _replace_jv_number(data: str, new_value: int) -> str:
    """Write a new JV-NUMBER back into the 400-byte CONTROL_RECORD_DATA.

    The replacement preserves the surrounding bytes exactly (no padding
    drift). PLACEHOLDER for production: produce the binary form here.
    """
    if len(data) != JV_CONTROL_DATA_LENGTH:
        raise ValueError(
            f"CONTROL_RECORD_DATA must be {JV_CONTROL_DATA_LENGTH} bytes; "
            f"got {len(data)}"
        )
    encoded = f"{new_value:06d}"
    if len(encoded) != 6:
        raise ValueError("JV_NUMBER must fit in 6 zoned-display digits")
    return data[: JV_NUMBER_SLICE.start] + encoded + data[JV_NUMBER_SLICE.stop :]


def run(dispatcher: Optional[DBDispatcher] = None) -> ResetOutcome:
    """Execute the LABA05 fiscal-year reset.

    Returns a ResetOutcome with .return_code matching the LABA05 conventions:
      0  → success (LABA05 PROGRAM-EXIT default)
      99 → any DBIO non-zero (LABA05 MODIFY-CTRL-REC fall-through to error)
    """
    own_dispatcher = False
    if dispatcher is None:
        dispatcher = DBDispatcher.from_env()
        own_dispatcher = True

    try:
        # Step 1: FETCH the control record (LABA05.cbl:152-174).
        fetch = dispatcher.select_one(
            """
            SELECT CONTROL_RECORD_DATA
              FROM CONTROL_RECORD_TABLE
             WHERE CONTROL_RECORD_NAME = ?
               AND CONTROL_RECORD_NUMBER = ?
            """,
            (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER),
        )
        if not fetch.ok or not fetch.rows:
            logger.error("LABA05: fetch failed dms=%s msg=%s", fetch.rtncode_dms, fetch.message)
            return ResetOutcome(
                return_code=RC_DB_ERROR,
                before_jv_number=None,
                after_jv_number=None,
                message=f"fetch failed: dms={fetch.rtncode_dms} sqlcode={fetch.sqlcode}",
            )

        data: str = fetch.rows[0][0]
        before = _extract_jv_number(data)
        logger.info("LABA05: PRIOR JV NUMBER WAS %06d", before)

        # Step 2: MOVE 1 TO JV-NUMBER and UPDATE (LABA05.cbl:184-205).
        new_data = _replace_jv_number(data, TARGET_JV_NUMBER)
        update = dispatcher.update(
            """
            UPDATE CONTROL_RECORD_TABLE
               SET CONTROL_RECORD_DATA = ?
             WHERE CONTROL_RECORD_NAME = ?
               AND CONTROL_RECORD_NUMBER = ?
            """,
            (new_data, CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER),
        )
        if not update.ok:
            dispatcher.rollback()
            logger.error("LABA05: update failed dms=%s msg=%s", update.rtncode_dms, update.message)
            return ResetOutcome(
                return_code=RC_DB_ERROR,
                before_jv_number=before,
                after_jv_number=None,
                message=f"update failed: dms={update.rtncode_dms} sqlcode={update.sqlcode}",
            )

        dispatcher.commit()
        after = _extract_jv_number(new_data)
        logger.info("LABA05: JV NUMBER IS NOW %06d", after)
        return ResetOutcome(
            return_code=RC_OK,
            before_jv_number=before,
            after_jv_number=after,
            message="JV-NUMBER reset to 1",
        )
    finally:
        if own_dispatcher:
            dispatcher.close()


def main(argv: Optional[list[str]] = None) -> int:
    """CLI entrypoint. Mirrors LABA05's "return 0 or 99" contract."""
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    outcome = run()
    if not outcome.ok:
        logger.error("LABA05: %s", outcome.message)
    return outcome.return_code


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv[1:]))
