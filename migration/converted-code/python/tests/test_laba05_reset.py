"""
test_laba05_reset.py — pytest suite for the modernized LABA05 FY-reset.
"""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(REPO_ROOT / "migration" / "converted-code"))

from python import db_dispatcher  # noqa: E402
from python.db_dispatcher import (  # noqa: E402
    DBDispatcher,
    DMS_BAD_FETCH,
    DMS_NOT_FOUND,
    DMS_OK,
    build_demo_schema,
    seed_control_record,
)
from python.laba05_reset import (  # noqa: E402
    JV_CONTROL_DATA_LENGTH,
    JV_NUMBER_SLICE,
    RC_DB_ERROR,
    RC_OK,
    _extract_jv_number,
    _replace_jv_number,
    run,
)


@pytest.fixture
def fresh_db() -> DBDispatcher:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    dispatcher = DBDispatcher(conn)
    build_demo_schema(dispatcher)
    return dispatcher


# ---------------------------------------------------------------------------
# Byte-layout / binary↔display conversion (RISKS Risk 2)
# ---------------------------------------------------------------------------
class TestJVNumberByteLayout:
    def test_slice_is_24_to_30(self):
        assert JV_NUMBER_SLICE.start == 24
        assert JV_NUMBER_SLICE.stop == 30

    def test_data_blob_must_be_400_bytes(self):
        with pytest.raises(ValueError):
            _replace_jv_number("X" * 399, 1)

    def test_extract_handles_zero_padded_digits(self):
        data = ("0" * 24) + "000042" + (" " * (400 - 30))
        assert _extract_jv_number(data) == 42

    def test_replace_preserves_surrounding_bytes(self):
        data = ("A" * 24) + "999999" + ("B" * (400 - 30))
        new_data = _replace_jv_number(data, 1)
        assert new_data.startswith("A" * 24)
        assert new_data[JV_NUMBER_SLICE] == "000001"
        assert new_data[30:] == "B" * (400 - 30)
        assert len(new_data) == JV_CONTROL_DATA_LENGTH


# ---------------------------------------------------------------------------
# End-to-end reset
# ---------------------------------------------------------------------------
class TestReset:
    def test_reset_succeeds_when_row_exists(self, fresh_db: DBDispatcher):
        seed_control_record(fresh_db, jv_number=42)
        outcome = run(fresh_db)
        assert outcome.ok
        assert outcome.return_code == RC_OK
        assert outcome.before_jv_number == 42
        assert outcome.after_jv_number == 1

        # Verify the persisted row was updated.
        res = fresh_db.select_one(
            """
            SELECT CONTROL_RECORD_DATA
              FROM CONTROL_RECORD_TABLE
             WHERE CONTROL_RECORD_NAME = ?
               AND CONTROL_RECORD_NUMBER = ?
            """,
            ("JV-CONTROL-REC", 1),
        )
        assert res.ok
        data = res.rows[0][0]
        assert _extract_jv_number(data) == 1

    def test_returns_99_when_row_missing(self, fresh_db: DBDispatcher):
        # No seed.
        outcome = run(fresh_db)
        assert outcome.return_code == RC_DB_ERROR
        assert outcome.before_jv_number is None
        assert outcome.after_jv_number is None

    def test_returns_99_when_update_fails(self, fresh_db: DBDispatcher, monkeypatch):
        seed_control_record(fresh_db, jv_number=42)

        def failing_update(self, sql, params):
            return db_dispatcher.DispatcherResult(
                rtncode_dms="9999",
                sqlcode=-1234,
                message="simulated update failure",
            )

        monkeypatch.setattr(DBDispatcher, "update", failing_update)
        outcome = run(fresh_db)
        assert outcome.return_code == RC_DB_ERROR
        assert outcome.before_jv_number == 42
        assert outcome.after_jv_number is None
        assert "update failed" in outcome.message

    def test_returns_99_on_sql_exception(self, fresh_db: DBDispatcher):
        seed_control_record(fresh_db, jv_number=42)
        # Drop the table so the SELECT fails with sqlite3.OperationalError.
        cur = fresh_db._conn.cursor()  # type: ignore[attr-defined]
        cur.execute("DROP TABLE CONTROL_RECORD_TABLE")
        fresh_db.commit()

        outcome = run(fresh_db)
        assert outcome.return_code == RC_DB_ERROR


# ---------------------------------------------------------------------------
# Binary→display conversion correctness (Risk 2 mitigation surface)
# ---------------------------------------------------------------------------
class TestBinaryDisplayConversion:
    def test_round_trip(self, fresh_db: DBDispatcher):
        for original in (1, 17, 999999):
            seed_control_record(fresh_db, jv_number=original)
            outcome = run(fresh_db)
            assert outcome.before_jv_number == original
            assert outcome.after_jv_number == 1
            # Cleanup so the next iteration's seed doesn't collide on PK.
            cur = fresh_db._conn.cursor()  # type: ignore[attr-defined]
            cur.execute("DELETE FROM CONTROL_RECORD_TABLE")
            fresh_db.commit()
