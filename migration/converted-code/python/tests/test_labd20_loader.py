"""
test_labd20_loader.py — pytest suite for the modernized LABD20 loader.

All tests use synthetic non-production data (per AGENTS.md). Test data lives
under migration/test-data/synthetic_comments.dat (21 records covering valid,
rejected, and duplicate cases).
"""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

import pytest

# Repo root path for the converted-code/python package.
REPO_ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(REPO_ROOT / "migration" / "converted-code"))

from python import db_dispatcher  # noqa: E402
from python.db_dispatcher import (  # noqa: E402
    DBDispatcher,
    DMS_NOT_FOUND,
    DMS_OK,
    build_demo_schema,
    translate_sqlcode,
)
from python.labd20_loader import (  # noqa: E402
    APPROVER_SLICE,
    COMMENT_DT_SLICE,
    COMMENT_TEXT_SLICE,
    JV_NUMBER_SLICE,
    LABD20Loader,
    LOAN_DT_NR_SLICE,
    LOAN_NUMBER_SLICE,
    LoaderConfig,
    REQUESTOR_SLICE,
    SCHEDULE_DOC_NO_SLICE,
    SECTION_ID_SLICE,
    TST123_RECORD_LENGTH,
    check_cymd_dt,
    determine_disposition,
    iter_records,
    parse_comment_record,
    read_process_date,
    truncate_file,
)


SYNTHETIC_DATA_PATH = REPO_ROOT / "migration" / "test-data" / "synthetic_comments.dat"
SYNTHETIC_CARD_PATH = REPO_ROOT / "migration" / "test-data" / "synthetic_card.ctl"


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------
@pytest.fixture
def fresh_db() -> DBDispatcher:
    """In-memory sqlite3 wrapped by the modernized dispatcher."""
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    dispatcher = DBDispatcher(conn)
    build_demo_schema(dispatcher)
    # Seed JC_COUNT_TBL with a prior count for section MA so the post-process
    # path has a baseline to compare against (ASSUMPTIONS A-10).
    dispatcher.insert(
        "INSERT INTO JC_COUNT_TBL (JC_SECTION, JC_COUNT_NUM) VALUES (?, ?)",
        ("MA", 0),
    )
    dispatcher.commit()
    return dispatcher


def _make_record(
    *,
    date: str = "20260101",
    jv: str = "000100",
    section: str = "01",
    loan: str = "9000000001",
    sched: str = "SCH0000001",
    text: str = "Demo comment",
    requestor: str = "ALICE.SUBMITTER",
    approver: str = "BOB.APPROVER",
) -> str:
    s = (
        f"{date:8}{jv:6}{section:2}{loan:10}"
        f"{sched:<10}{text:<230}"
        f"{requestor:<20}{approver:<14}"
    )
    assert len(s) == TST123_RECORD_LENGTH
    return s


# ---------------------------------------------------------------------------
# Layout tests — byte boundaries of TST123-COMMENT-REC (LABD20.pco:43-55)
# ---------------------------------------------------------------------------
class TestRecordLayout:
    def test_total_length_is_300(self):
        assert TST123_RECORD_LENGTH == 300

    def test_slice_boundaries(self):
        # All slice ends must match the next slice's start, with no gap.
        boundaries = [
            (COMMENT_DT_SLICE, 0, 8),
            (JV_NUMBER_SLICE, 8, 14),
            (SECTION_ID_SLICE, 14, 16),
            (LOAN_NUMBER_SLICE, 16, 26),
            (SCHEDULE_DOC_NO_SLICE, 26, 36),
            (COMMENT_TEXT_SLICE, 36, 266),
            (REQUESTOR_SLICE, 266, 286),
            (APPROVER_SLICE, 286, 300),
        ]
        for sl, start, stop in boundaries:
            assert sl.start == start, (sl, start)
            assert sl.stop == stop, (sl, stop)

    def test_composite_loan_dt_nr_covers_first_26_bytes(self):
        assert LOAN_DT_NR_SLICE.start == 0
        assert LOAN_DT_NR_SLICE.stop == 26

    def test_approver_is_14_not_20_bytes(self):
        # ASSUMPTIONS A-4: APPROVER is 14 bytes per LABD20.pco active line 55,
        # not 20 bytes from the commented line 54.
        assert APPROVER_SLICE.stop - APPROVER_SLICE.start == 14


class TestParseCommentRecord:
    def test_parses_each_field_at_correct_offset(self):
        rec = parse_comment_record(_make_record())
        assert rec.comment_dt == "20260101"
        assert rec.jv_number == "000100"
        assert rec.section_id == "01"
        assert rec.loan_number == "9000000001"
        assert rec.loan_dt_nr == "20260101000100019000000001"
        assert rec.schedule_doc_no == "SCH0000001"
        assert rec.comment_text.strip() == "Demo comment"
        assert rec.requestor.strip() == "ALICE.SUBMITTER"
        assert rec.approver.strip() == "BOB.APPROVER"

    def test_rejects_wrong_length(self):
        with pytest.raises(ValueError, match="must be 300 bytes"):
            parse_comment_record("X" * 299)

    def test_control_num_concatenates_jv_and_section(self):
        rec = parse_comment_record(_make_record(jv="000123", section="07"))
        assert rec.control_num == "00012307"

    def test_submitted_key_is_first_26_bytes(self):
        rec = parse_comment_record(_make_record())
        assert rec.submitted_key == rec.raw[:26]


# ---------------------------------------------------------------------------
# Validation rules — LABD20.pco:261-307
# ---------------------------------------------------------------------------
class TestValidationRules:
    def test_blank_record_rejected(self):
        rec = parse_comment_record(" " * 300)
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert "blank record" in reasons

    def test_happy_path_accepted(self):
        rec = parse_comment_record(_make_record())
        ok, reasons = determine_disposition(rec)
        assert ok, reasons
        assert reasons == []

    def test_non_numeric_date_rejected(self):
        rec = parse_comment_record(_make_record(date="20XXXXXX"))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("comment date" in r for r in reasons)

    def test_invalid_calendar_date_rejected(self):
        rec = parse_comment_record(_make_record(date="20261345"))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("calendar" in r for r in reasons)

    def test_jv_number_zero_rejected(self):
        rec = parse_comment_record(_make_record(jv="000000"))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("JV number" in r for r in reasons)

    def test_jv_number_non_numeric_rejected(self):
        rec = parse_comment_record(_make_record(jv="ABC123"))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("JV number" in r for r in reasons)

    def test_non_numeric_section_rejected(self):
        rec = parse_comment_record(_make_record(section="MA"))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("section id" in r for r in reasons)

    def test_non_numeric_loan_rejected(self):
        rec = parse_comment_record(_make_record(loan="ABCDE12345"))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("loan number" in r for r in reasons)

    def test_blank_comment_rejected(self):
        rec = parse_comment_record(_make_record(text=""))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("comment text" in r for r in reasons)

    def test_blank_requestor_rejected(self):
        rec = parse_comment_record(_make_record(requestor=""))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("requestor" in r for r in reasons)

    def test_blank_approver_rejected(self):
        rec = parse_comment_record(_make_record(approver=""))
        ok, reasons = determine_disposition(rec)
        assert not ok
        assert any("approver" in r for r in reasons)


# ---------------------------------------------------------------------------
# check_cymd_dt placeholder
# ---------------------------------------------------------------------------
class TestCheckCYMD:
    def test_valid_date(self):
        assert check_cymd_dt("20260101") is True

    def test_invalid_month(self):
        assert check_cymd_dt("20261301") is False

    def test_invalid_day(self):
        assert check_cymd_dt("20260132") is False

    def test_leap_day_valid(self):
        assert check_cymd_dt("20240229") is True

    def test_non_leap_feb29_invalid(self):
        assert check_cymd_dt("20250229") is False

    def test_non_numeric(self):
        assert check_cymd_dt("2026010A") is False

    def test_wrong_length(self):
        assert check_cymd_dt("2026101") is False


# ---------------------------------------------------------------------------
# Process-date / card-file
# ---------------------------------------------------------------------------
class TestReadProcessDate:
    def test_reshuffles_mmddccyy_to_yyyymmdd(self, tmp_path: Path):
        card = tmp_path / "card.ctl"
        card.write_text("03/15/2026\n", encoding="utf-8")
        assert read_process_date(card) == "20260315"

    def test_synthetic_card_file_parses(self):
        # Validates the committed synthetic test fixture.
        assert read_process_date(SYNTHETIC_CARD_PATH) == "20260115"

    def test_rejects_malformed(self, tmp_path: Path):
        card = tmp_path / "card.ctl"
        card.write_text("2026-01-15\n", encoding="utf-8")
        with pytest.raises(ValueError):
            read_process_date(card)


# ---------------------------------------------------------------------------
# SQLCODE→DMS translation — DBIO.pco:374-398
# ---------------------------------------------------------------------------
class TestSQLCodeTranslation:
    def test_zero_is_ok(self):
        assert translate_sqlcode(0) == "0000"

    def test_100_default(self):
        assert translate_sqlcode(100) == "0013"

    def test_100_with_set_name(self):
        assert translate_sqlcode(100, set_name="X", function_type="FIND") == "0007"

    def test_100_with_set_name_but_fetch_owner_returns_default(self):
        assert translate_sqlcode(100, set_name="X", function_type="FETCH OWNER") == "0013"

    def test_minus_one(self):
        assert translate_sqlcode(-1) == "0005"

    def test_minus_8103_logged_as_ok(self):
        assert translate_sqlcode(-8103) == "0000"

    def test_other_returns_9999(self):
        assert translate_sqlcode(-42) == "9999"
        assert translate_sqlcode(1000) == "9999"


# ---------------------------------------------------------------------------
# End-to-end against synthetic data
# ---------------------------------------------------------------------------
class TestLoaderEndToEnd:
    def test_runs_synthetic_dataset(self, tmp_path: Path, fresh_db: DBDispatcher):
        # Copy the comment fixture so the truncate doesn't clobber the source.
        comments = tmp_path / "comments.dat"
        comments.write_bytes(SYNTHETIC_DATA_PATH.read_bytes())

        loader = LABD20Loader(fresh_db)
        stats = loader.run(
            LoaderConfig(
                card_path=SYNTHETIC_CARD_PATH,
                comment_path=comments,
                truncate_after_processing=True,
            )
        )

        # 21 records in the fixture (see migration/test-data/README.md).
        assert stats.total_read == 21
        # Records 1, 2, 15, 16, 18, 19, 20 = 7 expected inserts.
        assert stats.inserted == 7
        # Records 4 and 21 should be duplicate-detected.
        assert stats.duplicates == 2
        # Remainder should be rejected.
        assert stats.rejected == 21 - 7 - 2

        # Stats query should reflect the inserts.
        assert stats.submitted_total == 7

        # Truncate behavior: comment file is now zero bytes.
        assert comments.stat().st_size == 0

    def test_insert_parameter_mapping_uses_all_nine_columns(
        self, tmp_path: Path, fresh_db: DBDispatcher
    ):
        # Single happy-path record.
        comments = tmp_path / "one.dat"
        comments.write_text(
            _make_record(text="single insert") + "\n", encoding="utf-8"
        )

        loader = LABD20Loader(fresh_db)
        loader.run(
            LoaderConfig(
                card_path=SYNTHETIC_CARD_PATH,
                comment_path=comments,
                truncate_after_processing=False,
            )
        )

        cur = fresh_db._conn.cursor()  # type: ignore[attr-defined]
        cur.execute("SELECT * FROM JC_SUBMITTED_COMMENT_TBL")
        rows = cur.fetchall()
        assert len(rows) == 1
        row = dict(rows[0])
        assert row["JC_SUBMITTED"].startswith("20260101000100019000000001")
        assert row["JC_SUBMITTED_NUMBER"] == 1
        assert row["JC_SUBMITTED_SCHED_DOC_NO"] == "SCH0000001"
        assert "single insert" in row["JC_SUBMITTED_COMMENT_HIST"]
        assert row["JC_SUBMITTED_COMMENT_REQUESTOR"].strip() == "ALICE.SUBMITTER"
        assert row["JC_SUBMITTED_COMMENT_APPROVER"].strip() == "BOB.APPROVER"
        # WS-CONTROL-NUM = JV (6) + SECTION (2)
        assert row["JC_SUBMITTED_CONTROL_NUM"] == "00010001"
        assert row["JC_SUBMITTED_UPDT_PROG_ID"] == "LABD20"
        # YYYY-MM-DD ISO form from the card file (see ASSUMPTIONS A-7)
        assert row["JC_SUBMITTED_UPDT_PROG_DT"] == "2026-01-15"

    def test_duplicate_record_does_not_insert_twice(
        self, tmp_path: Path, fresh_db: DBDispatcher
    ):
        comments = tmp_path / "dupes.dat"
        rec = _make_record()
        comments.write_text(rec + "\n" + rec + "\n", encoding="utf-8")

        loader = LABD20Loader(fresh_db)
        stats = loader.run(
            LoaderConfig(
                card_path=SYNTHETIC_CARD_PATH,
                comment_path=comments,
                truncate_after_processing=False,
            )
        )
        assert stats.inserted == 1
        assert stats.duplicates == 1

    def test_rollback_on_insert_failure(
        self, tmp_path: Path, fresh_db: DBDispatcher
    ):
        # Force the dispatcher to fail INSERTs by dropping the target table.
        cur = fresh_db._conn.cursor()  # type: ignore[attr-defined]
        cur.execute("DROP TABLE JC_SUBMITTED_COMMENT_TBL")
        fresh_db.commit()

        comments = tmp_path / "one.dat"
        comments.write_text(_make_record() + "\n", encoding="utf-8")
        loader = LABD20Loader(fresh_db)
        with pytest.raises(RuntimeError):
            loader.run(
                LoaderConfig(
                    card_path=SYNTHETIC_CARD_PATH,
                    comment_path=comments,
                    truncate_after_processing=False,
                )
            )


# ---------------------------------------------------------------------------
# Truncate semantics — LABD20.pco:215-218
# ---------------------------------------------------------------------------
def test_truncate_file_zeros_it(tmp_path: Path):
    f = tmp_path / "data.dat"
    f.write_text("not empty\n")
    truncate_file(f)
    assert f.read_text() == ""


# ---------------------------------------------------------------------------
# iter_records padding
# ---------------------------------------------------------------------------
def test_iter_records_pads_short_records(tmp_path: Path):
    f = tmp_path / "short.dat"
    f.write_text("X" * 100 + "\n", encoding="utf-8")
    records = list(iter_records(f))
    assert len(records) == 1
    assert len(records[0]) == TST123_RECORD_LENGTH


def test_iter_records_truncates_long_records(tmp_path: Path):
    f = tmp_path / "long.dat"
    f.write_text("X" * 500 + "\n", encoding="utf-8")
    records = list(iter_records(f))
    assert len(records) == 1
    assert len(records[0]) == TST123_RECORD_LENGTH
