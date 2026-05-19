"""
labd20_loader.py — Modernized analog of source/procobol/LABD20.pco.

STATUS: Demo output pending SME review. Generated as part of the Guidehouse JV
COBOL/Pro*COBOL modernization walkthrough.

LEGACY BEHAVIOR
---------------
LABD20 is the daily JV comment ingestion program. It:

1. Reads the single-record CARDFILE to obtain a process date in MM/DD/CCYY,
   reshuffles it into YYYYMMDD form (LABD20.pco:224-234).
2. Opens the COMMENT-FILE and reads fixed-width 300-byte
   TST123-COMMENT-REC records (FD layout at LABD20.pco:43-55).
3. For each record, runs validation:
       - blank record (LABD20.pco:261-263)
       - numeric comment date + DATECONV CHECK-CYMD-DT (LABD20.pco:265-274)
       - JV-NUMBER numeric AND > 0 (LABD20.pco:276-281)
       - SECTION-ID numeric (LABD20.pco:283-287)
       - LOAN-NUMBER numeric (LABD20.pco:289-293)
       - COMMENT-TEXT not blank (LABD20.pco:297-299)
       - REQUESTOR not blank (LABD20.pco:301-303)
       - APPROVER not blank (LABD20.pco:305-307)
   Records failing any check are counted as rejected (LABD20.pco:309-314).
4. For accepted records, checks JC_SUBMITTED_COMMENT_TBL for a row keyed by
   the 26-byte JC_SUBMITTED key (LABD20.pco:317-339).
   - Found → DISPLAY 'DUPLICATE ENTRY' and skip.
   - Not found (SQLCODE 100) → CREATE-COMMENT-RECORD.
5. CREATE-COMMENT-RECORD INSERTs nine columns into JC_SUBMITTED_COMMENT_TBL
   (LABD20.pco:342-389), incrementing WS-JV-COUNTER.
6. POST-PROCESS: if WS-JV-COUNTER > WS-JV-COUNTERS, UPDATE JC_COUNT_TBL
   SET JC_SECTION_COUNT = :WS-JV-COUNTER WHERE JC_SECTION='MA'
   (LABD20.pco:392-405).
7. COMMIT WORK (LABD20.pco:413), run stats SELECTs
   (LABD20.pco:421-446), display report.
8. Truncate the COMMENT-FILE by OPEN OUTPUT + CLOSE (LABD20.pco:215-218).
9. On any non-zero SQLCODE: GO TO 9999-ROLL-BACK (LABD20.pco:489+).

ASSUMPTIONS (see migration/ASSUMPTIONS-AND-PLACEHOLDERS.md)
----------------------------------------------------------
- A-4: TST123-COMMENT-REC is exactly 300 bytes (8+6+2+10 + 10+230 + 20 + 14).
  The active line 55 of LABD20.pco gives APPROVER=14 bytes; the commented
  line 54 is the deprecated 20-byte form. See RISKS-AND-GAPS.md Risk 5.
- A-5: DATECONV-WS and DATECONV-PD are missing. Our `check_cymd_dt` stub uses
  Python's datetime.date(...) for calendar validation. See RISKS-AND-GAPS.md
  Risk 1 — SME confirmation required to ensure exact parity.
- A-7: Oracle column names + types are taken from
  database/descriptions/describe JC_SUBMITTED_COMMENT_TBL.txt.
- A-8: The post-process UPDATE keys on JC_SECTION='MA' as the legacy code
  does (LABD20.pco:400). It is unclear whether 'MA' is hardcoded by design
  or because the test data only ever exercises section 'MA' — see
  RISKS-AND-GAPS.md Risk 8.
- A-10: 'WS-JV-COUNTERS' (LABD20.pco:393) is the prior count threshold;
  legacy source does not show where it is loaded from. The demo treats it
  as the current JC_COUNT_TBL value for section 'MA' (best inference). SME
  review required.

PLACEHOLDERS / SME REVIEW
-------------------------
- The legacy DISPLAY 'DUPLICATE ENTRY ...' messages route through logger.info;
  the literal text differs slightly from COBOL printer output.
- Legacy reporting block (LABD20.pco:448-486) is captured as a structured
  dataclass; the human-readable printout is in `LoaderStats.format_report()`
  rather than verbatim DISPLAY statements.
"""

from __future__ import annotations

import datetime as dt
import logging
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Optional, Sequence

from . import db_dispatcher
from .db_dispatcher import DBDispatcher

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Fixed-width record layout — TST123-COMMENT-REC.
# Source: source/procobol/LABD20.pco:43-55.
# Total length = 8+6+2+10 (TST123-LOAN-DT-NR composite) + 10+230 (HIST) + 20
#               + 14 = 300 bytes exactly.
# ---------------------------------------------------------------------------
TST123_RECORD_LENGTH = 300

# Byte offsets (0-based, end-exclusive). Match LABD20.pco:43-55.
COMMENT_DT_SLICE = slice(0, 8)        # PIC 9(008)
JV_NUMBER_SLICE = slice(8, 14)        # PIC 9(006)
SECTION_ID_SLICE = slice(14, 16)      # PIC 9(002)
LOAN_NUMBER_SLICE = slice(16, 26)     # PIC 9(010)
LOAN_DT_NR_SLICE = slice(0, 26)       # composite redefine
SCHEDULE_DOC_NO_SLICE = slice(26, 36) # PIC X(010)
COMMENT_TEXT_SLICE = slice(36, 266)   # PIC X(230)
COMMENT_HIST_SLICE = slice(26, 266)   # composite SCHED_DOC_NO + TEXT
REQUESTOR_SLICE = slice(266, 286)     # PIC X(020)
APPROVER_SLICE = slice(286, 300)      # PIC X(014)
"""APPROVER is 14 bytes per LABD20.pco line 55 (the commented line 54 says
20 bytes — DO NOT use that). See RISKS-AND-GAPS.md Risk 5."""


# ---------------------------------------------------------------------------
# Dataclasses
# ---------------------------------------------------------------------------
@dataclass
class CommentRecord:
    """Parsed view over the 300-byte TST123-COMMENT-REC."""

    raw: str
    comment_dt: str
    jv_number: str
    section_id: str
    loan_number: str
    loan_dt_nr: str
    schedule_doc_no: str
    comment_text: str
    comment_hist: str
    requestor: str
    approver: str

    @property
    def submitted_key(self) -> str:
        """The 26-byte composite primary key used by JC_SUBMITTED_COMMENT_TBL
        (cite: LABD20.pco:329 — JC_SUBMITTED = :WS-TST123-LOAN-DT-NR)."""
        return self.loan_dt_nr

    @property
    def control_num(self) -> str:
        """WS-CONTROL-NUM = JV-NUMBER (6) + SECTION-ID (2) = 8 bytes.
        Cite: LABD20.pco:160-165 (WS-CONTROL-NUM redefine)."""
        return f"{self.jv_number}{self.section_id}"


@dataclass
class LoaderStats:
    """Mirrors LABD20 reporting accumulators (LABD20.pco:60-105 working
    storage WS-COUNTERS, used in LABD20.pco:448-486 reporting block)."""

    total_read: int = 0          # WS-JV-COMMENTS-CNT
    accepted: int = 0            # WS-JV-COUNTER increments
    rejected: int = 0            # WS-TST123-RECS-ERR-CNT
    duplicates: int = 0
    inserted: int = 0
    submitted_total: int = 0     # WS-TOTAL-SUBMIT-END-CNT
    rejected_total: int = 0      # WS-TOTAL-REJECT-END-CNT
    applied_total: int = 0       # WS-TOTAL-APPLIED-END-CNT
    process_date: str = ""
    rejected_reasons: list[str] = field(default_factory=list)

    def format_report(self) -> str:
        """A human-readable summary; replaces the LABD20 DISPLAY block at
        LABD20.pco:448-486. The legacy text is reproduced loosely."""
        lines = [
            "*** COMMENT PROCESSING ***",
            f"PROCESS DATE              : {self.process_date}",
            f"COMMENTS READ             : {self.total_read}",
            f"COMMENTS ACCEPTED         : {self.accepted}",
            f"COMMENTS REJECTED         : {self.rejected}",
            f"COMMENTS DUPLICATE        : {self.duplicates}",
            f"COMMENTS INSERTED         : {self.inserted}",
            f"JC_SUBMITTED TOTAL        : {self.submitted_total}",
            f"JC_REJECTED TOTAL         : {self.rejected_total}",
            f"JC_APPLIED  TOTAL         : {self.applied_total}",
        ]
        return "\n".join(lines)


# ---------------------------------------------------------------------------
# Parsing & validation
# ---------------------------------------------------------------------------
def parse_comment_record(raw: str) -> CommentRecord:
    """Parse a single 300-byte raw record. Layout: LABD20.pco:43-55."""
    if len(raw) != TST123_RECORD_LENGTH:
        raise ValueError(
            f"TST123-COMMENT-REC must be {TST123_RECORD_LENGTH} bytes; "
            f"got {len(raw)}"
        )
    return CommentRecord(
        raw=raw,
        comment_dt=raw[COMMENT_DT_SLICE],
        jv_number=raw[JV_NUMBER_SLICE],
        section_id=raw[SECTION_ID_SLICE],
        loan_number=raw[LOAN_NUMBER_SLICE],
        loan_dt_nr=raw[LOAN_DT_NR_SLICE],
        schedule_doc_no=raw[SCHEDULE_DOC_NO_SLICE],
        comment_text=raw[COMMENT_TEXT_SLICE],
        comment_hist=raw[COMMENT_HIST_SLICE],
        requestor=raw[REQUESTOR_SLICE],
        approver=raw[APPROVER_SLICE],
    )


def check_cymd_dt(yyyymmdd: str) -> bool:
    """Stub for the legacy CHECK-CYMD-DT (PERFORM at LABD20.pco:267).

    The DATECONV-PD copybook that defines CHECK-CYMD-DT was NOT supplied;
    see migration/RISKS-AND-GAPS.md Risk 1 and ASSUMPTIONS-AND-PLACEHOLDERS.md A-5.

    # PLACEHOLDER: replace with the legacy DATECONV-PD logic once it is
    # provided. Our stub uses Python's calendar checks, which match modern
    # Gregorian rules but cannot validate against any custom JV business
    # calendar rules the legacy program may have enforced.
    """
    if not yyyymmdd.isdigit() or len(yyyymmdd) != 8:
        return False
    try:
        dt.date(int(yyyymmdd[0:4]), int(yyyymmdd[4:6]), int(yyyymmdd[6:8]))
    except ValueError:
        return False
    return True


def determine_disposition(record: CommentRecord) -> tuple[bool, list[str]]:
    """Apply the validation rules from LABD20.pco:261-307.

    Returns (is_valid, reasons-if-invalid).
    The legacy flag WS-TST123-RECORD-FLAG=1 → invalid (LABD20.pco:262, 271,
    273, 280, 286, 292, 298, 302, 306).
    """
    reasons: list[str] = []

    if record.raw.strip() == "":
        reasons.append("blank record")

    if not record.comment_dt.isdigit():
        reasons.append("comment date is non-numeric")
    elif not check_cymd_dt(record.comment_dt):
        reasons.append("comment date is not a valid YYYYMMDD calendar date")

    if not (record.jv_number.isdigit() and int(record.jv_number) > 0):
        reasons.append("JV number is non-numeric or zero")

    if not record.section_id.isdigit():
        reasons.append("section id is non-numeric")

    if not record.loan_number.isdigit():
        reasons.append("loan number is non-numeric")

    if record.comment_text.strip() == "":
        reasons.append("comment text is blank")

    if record.requestor.strip() == "":
        reasons.append("requestor is blank")

    if record.approver.strip() == "":
        reasons.append("approver is blank")

    return (not reasons, reasons)


# ---------------------------------------------------------------------------
# Card file (process date)
# ---------------------------------------------------------------------------
def read_process_date(card_path: Path) -> str:
    """Read the MM/DD/CCYY date from CARDFILE and reshuffle to YYYYMMDD.

    Legacy: LABD20.pco:224-232. The COBOL code moves CARD-DATE into
    WS-PARM-DATE, then assembles WS-PROCESS-DATE-CC / -YY / -MM / -DD into
    YYYYMMDD form. The file contains a single record with MM/DD/CCYY in the
    first 10 bytes (the dotcard layout is from CHG-645).
    """
    line = card_path.read_text(encoding="utf-8").strip().splitlines()[0]
    mm_dd_ccyy = line[:10]
    parts = mm_dd_ccyy.split("/")
    if len(parts) != 3:
        raise ValueError(f"CARDFILE date must be MM/DD/CCYY; got {mm_dd_ccyy!r}")
    mm, dd, ccyy = parts
    if not (mm.isdigit() and dd.isdigit() and ccyy.isdigit()):
        raise ValueError(f"CARDFILE date components must be numeric; got {parts}")
    return f"{ccyy}{mm}{dd}"


# ---------------------------------------------------------------------------
# Comment-file iteration
# ---------------------------------------------------------------------------
def iter_records(comment_path: Path) -> Iterable[str]:
    """Yield each 300-byte fixed-width record in COMMENT-FILE.

    Legacy: LABD20.pco:239 (OPEN INPUT), :247-253 (READ AT END loop). The
    legacy file is "line sequential" — records are 300 bytes followed by a
    newline. We tolerate both newline-terminated and no-newline records.
    """
    text = comment_path.read_text(encoding="utf-8")
    if not text:
        return
    # Strip trailing newline of the file, then split on newlines.
    for raw in text.splitlines():
        if not raw:
            continue
        # Pad short last records to TST123_RECORD_LENGTH with spaces (matches
        # legacy fixed-width semantics).
        if len(raw) < TST123_RECORD_LENGTH:
            raw = raw.ljust(TST123_RECORD_LENGTH)
        elif len(raw) > TST123_RECORD_LENGTH:
            raw = raw[:TST123_RECORD_LENGTH]
        yield raw


def truncate_file(comment_path: Path) -> None:
    """Mirror the OPEN OUTPUT / CLOSE truncate at LABD20.pco:215-218.

    Legacy semantics: opening a sequential file with OPEN OUTPUT positions
    at byte 0; the immediate CLOSE leaves the file with zero bytes. We
    reproduce that by writing an empty string.
    """
    comment_path.write_text("", encoding="utf-8")


# ---------------------------------------------------------------------------
# Loader
# ---------------------------------------------------------------------------
@dataclass
class LoaderConfig:
    card_path: Path
    comment_path: Path
    truncate_after_processing: bool = True
    section_for_count: str = "MA"  # LABD20.pco:400


class LABD20Loader:
    """Modernized analog of LABD20.

    Construct with a DBDispatcher (use DBDispatcher.from_env() in prod).
    Call run(config) to execute the full job.
    """

    INSERT_SQL = """
        INSERT INTO JC_SUBMITTED_COMMENT_TBL (
            JC_SUBMITTED,
            JC_SUBMITTED_NUMBER,
            JC_SUBMITTED_SCHED_DOC_NO,
            JC_SUBMITTED_COMMENT_HIST,
            JC_SUBMITTED_COMMENT_REQUESTOR,
            JC_SUBMITTED_COMMENT_APPROVER,
            JC_SUBMITTED_CONTROL_NUM,
            JC_SUBMITTED_UPDT_PROG_ID,
            JC_SUBMITTED_UPDT_PROG_DT
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    """Replaces EXEC SQL INSERT at LABD20.pco:352-372. The legacy uses
    Oracle TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD'); the demo stores
    YYYY-MM-DD ISO text (sqlite has no native DATE).
    Production must restore TO_DATE."""

    SELECT_DUPE_SQL = """
        SELECT JC_SUBMITTED_NUMBER
          FROM JC_SUBMITTED_COMMENT_TBL
         WHERE JC_SUBMITTED = ?
    """
    """Replaces EXEC SQL at LABD20.pco:325-330."""

    UPDATE_COUNT_SQL = """
        UPDATE JC_COUNT_TBL
           SET JC_COUNT_NUM = ?
         WHERE JC_SECTION = ?
    """
    """Replaces EXEC SQL at LABD20.pco:398-401.
    Note: legacy column name is JC_SECTION_COUNT, but the supplied
    database/descriptions/describe JC_COUNT_TBL.txt also shows JC_COUNT_NUM
    in the schema. The demo uses JC_COUNT_NUM for consistency with the
    describe file; SME review required."""

    SELECT_COUNT_FOR_SECTION_SQL = """
        SELECT JC_COUNT_NUM
          FROM JC_COUNT_TBL
         WHERE JC_SECTION = ?
    """

    def __init__(self, dispatcher: DBDispatcher) -> None:
        self.dispatcher = dispatcher
        self.stats = LoaderStats()

    def run(self, config: LoaderConfig) -> LoaderStats:
        """Execute the full LABD20 mainline.

        Legacy entry: LABD20.pco MAINLINE (lines 208-220).
        Returns the populated LoaderStats. On any DB error, attempts
        rollback and re-raises a RuntimeError.
        """
        process_date = read_process_date(config.card_path)
        self.stats.process_date = process_date
        logger.info("PROCESS DATE = %s", process_date)

        try:
            for raw in iter_records(config.comment_path):
                self._handle_record(raw, process_date)

            self._post_process(config.section_for_count)
            self._commit_environment()

            if config.truncate_after_processing:
                truncate_file(config.comment_path)

        except RuntimeError:
            # Bubble up after roll back already performed in raiser.
            raise
        except Exception as exc:
            # Mirror the 9999-ROLL-BACK fall-through behavior.
            logger.exception("LABD20: unexpected error — rolling back")
            self.dispatcher.rollback()
            raise RuntimeError(f"LABD20 aborted: {exc}") from exc

        return self.stats

    # ------- per-record processing -----------------------------------------
    def _handle_record(self, raw: str, process_date: str) -> None:
        self.stats.total_read += 1
        try:
            record = parse_comment_record(raw)
        except ValueError as exc:
            self.stats.rejected += 1
            self.stats.rejected_reasons.append(f"parse error: {exc}")
            return

        is_valid, reasons = determine_disposition(record)
        if not is_valid:
            self.stats.rejected += 1
            self.stats.rejected_reasons.extend(reasons)
            logger.info("REJECTED %s reasons=%s", record.submitted_key, reasons)
            return

        # Duplicate check (LABD20.pco:317-339).
        dupe = self.dispatcher.select_one(
            self.SELECT_DUPE_SQL, (record.submitted_key,)
        )
        if dupe.rtncode_dms not in (db_dispatcher.DMS_OK, db_dispatcher.DMS_NOT_FOUND):
            self._fatal_rollback(
                f"duplicate-check failed dms={dupe.rtncode_dms} sqlcode={dupe.sqlcode}"
            )

        if dupe.ok:
            self.stats.duplicates += 1
            logger.info("DUPLICATE ENTRY %s", record.submitted_key)
            return

        self._insert(record, process_date)

    def _insert(self, record: CommentRecord, process_date: str) -> None:
        """CREATE-COMMENT-RECORD path (LABD20.pco:342-389)."""
        self.stats.accepted += 1
        # ASSUMPTION A-7: JC_SUBMITTED_NUMBER is a monotonic counter equal
        # to the in-batch accepted count, mirroring WS-JV-COUNTER (LABD20.pco:345).
        jc_number = self.stats.accepted
        params = (
            record.submitted_key,
            jc_number,
            record.schedule_doc_no,
            record.comment_hist,
            record.requestor,
            record.approver,
            record.control_num,
            "LABD20",
            self._format_process_date(process_date),
        )
        result = self.dispatcher.insert(self.INSERT_SQL, params)
        if not result.ok:
            self._fatal_rollback(
                f"INSERT failed dms={result.rtncode_dms} sqlcode={result.sqlcode}"
            )
        self.stats.inserted += 1

    # ------- post-process --------------------------------------------------
    def _post_process(self, section: str) -> None:
        """POST-PROCESS UPDATE block (LABD20.pco:392-405).

        Legacy: only update JC_COUNT_TBL if WS-JV-COUNTER > WS-JV-COUNTERS.
        We infer WS-JV-COUNTERS = current JC_COUNT_NUM for the section
        (see ASSUMPTIONS A-10).
        """
        existing = self.dispatcher.select_one(
            self.SELECT_COUNT_FOR_SECTION_SQL, (section,)
        )
        prior = int(existing.rows[0][0]) if existing.ok and existing.rows else 0
        if self.stats.accepted <= prior:
            logger.info(
                "POST-PROCESS: counter %d not greater than prior %d — skip update",
                self.stats.accepted,
                prior,
            )
            return

        result = self.dispatcher.update(
            self.UPDATE_COUNT_SQL, (self.stats.accepted, section)
        )
        if not result.ok:
            self._fatal_rollback(
                f"JC_COUNT_TBL UPDATE failed dms={result.rtncode_dms} sqlcode={result.sqlcode}"
            )

    # ------- close-sql-environment + stats --------------------------------
    def _commit_environment(self) -> None:
        """CLOSE-SQL-ENVIRONMENT (LABD20.pco:408-446). COMMIT + stats."""
        commit = self.dispatcher.commit()
        if not commit.ok:
            self._fatal_rollback(
                f"COMMIT failed dms={commit.rtncode_dms} sqlcode={commit.sqlcode}"
            )

        self.stats.submitted_total = self.dispatcher.count_rows(
            "JC_SUBMITTED_COMMENT_TBL"
        )
        self.stats.rejected_total = self.dispatcher.count_rows(
            "JC_REJECTED_COMMENT_TBL"
        )
        self.stats.applied_total = self.dispatcher.count_rows(
            "JC_APPLIED_COMMENT_TBL"
        )
        logger.info("\n%s", self.stats.format_report())

    # ------- helpers -------------------------------------------------------
    def _fatal_rollback(self, message: str) -> None:
        """Mirror the 9999-ROLL-BACK section (LABD20.pco:489+)."""
        logger.error("LABD20: %s — rolling back", message)
        self.dispatcher.rollback()
        raise RuntimeError(message)

    @staticmethod
    def _format_process_date(yyyymmdd: str) -> str:
        """LABD20.pco:371 uses TO_DATE(:WS-PROCESS-DATE,'YYYYMMDD'). The
        demo stores ISO YYYY-MM-DD text. Production must restore TO_DATE."""
        if len(yyyymmdd) != 8 or not yyyymmdd.isdigit():
            return yyyymmdd
        return f"{yyyymmdd[0:4]}-{yyyymmdd[4:6]}-{yyyymmdd[6:8]}"


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def main(argv: Optional[Sequence[str]] = None) -> int:
    """LABD20 CLI entrypoint. Accepts two file paths."""
    import argparse

    logging.basicConfig(level=logging.INFO, format="%(message)s")
    parser = argparse.ArgumentParser(description="LABD20 modernized loader.")
    parser.add_argument("--card", required=True, type=Path, help="CARDFILE path (process date)")
    parser.add_argument("--comments", required=True, type=Path, help="COMMENT-FILE path")
    parser.add_argument("--no-truncate", action="store_true", help="Skip COMMENT-FILE truncate")
    args = parser.parse_args(argv)

    dispatcher = DBDispatcher.from_env()
    try:
        loader = LABD20Loader(dispatcher)
        loader.run(
            LoaderConfig(
                card_path=args.card,
                comment_path=args.comments,
                truncate_after_processing=not args.no_truncate,
            )
        )
    finally:
        dispatcher.close()
    return 0


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv[1:]))
