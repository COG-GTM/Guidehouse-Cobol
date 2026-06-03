package com.cognition.jvmigration.laba05;

import com.cognition.jvmigration.db.DbDispatcher;
import com.cognition.jvmigration.db.DemoSchema;
import com.cognition.jvmigration.db.DispatcherResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 mirror of
 * {@code migration/converted-code/python/tests/test_laba05_reset.py}.
 */
class Laba05ResetTest {

    private static DbDispatcher freshDb() {
        DbDispatcher d = DbDispatcher.inMemory();
        DemoSchema.buildDemoSchema(d);
        return d;
    }

    @Nested
    class JvNumberByteLayout {
        @Test void sliceIs24To30() {
            assertEquals(24, Laba05Reset.JV_NUMBER_START);
            assertEquals(30, Laba05Reset.JV_NUMBER_END);
        }
        @Test void dataBlobMustBe400Bytes() {
            assertThrows(IllegalArgumentException.class,
                () -> Laba05Reset.replaceJvNumber("X".repeat(399), 1));
        }
        @Test void extractHandlesZeroPaddedDigits() {
            String data = "0".repeat(24) + "000042" + " ".repeat(400 - 30);
            assertEquals(42, Laba05Reset.extractJvNumber(data));
        }
        @Test void replacePreservesSurroundingBytes() {
            String data = "A".repeat(24) + "999999" + "B".repeat(400 - 30);
            String newData = Laba05Reset.replaceJvNumber(data, 1);
            assertTrue(newData.startsWith("A".repeat(24)));
            assertEquals("000001", newData.substring(24, 30));
            assertEquals("B".repeat(400 - 30), newData.substring(30));
            assertEquals(Laba05Reset.JV_CONTROL_DATA_LENGTH, newData.length());
        }
    }

    @Nested
    class Reset {
        @Test void succeedsWhenRowExists() {
            try (DbDispatcher db = freshDb()) {
                DemoSchema.seedControlRecord(db, 42);
                ResetOutcome outcome = Laba05Reset.run(db);
                assertTrue(outcome.ok());
                assertEquals(Laba05Reset.RC_OK, outcome.returnCode);
                assertEquals(42, outcome.beforeJvNumber);
                assertEquals(1, outcome.afterJvNumber);

                DispatcherResult res = db.selectOne(
                    "SELECT CONTROL_RECORD_DATA FROM CONTROL_RECORD_TABLE"
                        + " WHERE CONTROL_RECORD_NAME = ? AND CONTROL_RECORD_NUMBER = ?",
                    "JV-CONTROL-REC", 1);
                assertTrue(res.ok());
                assertEquals(1, Laba05Reset.extractJvNumber(String.valueOf(res.rows.get(0)[0])));
            }
        }

        @Test void returns99WhenRowMissing() {
            try (DbDispatcher db = freshDb()) {
                ResetOutcome outcome = Laba05Reset.run(db);
                assertEquals(Laba05Reset.RC_DB_ERROR, outcome.returnCode);
                assertNull(outcome.beforeJvNumber);
                assertNull(outcome.afterJvNumber);
            }
        }

        @Test void returns99WhenUpdateFails() {
            try (DbDispatcher base = freshDb()) {
                DemoSchema.seedControlRecord(base, 42);
                DbDispatcher failing = new DbDispatcher(base.connection()) {
                    @Override public DispatcherResult update(String sql, Object... params) {
                        return new DispatcherResult("9999", -1234,
                            "simulated update failure", new ArrayList<>(), 0);
                    }
                };
                ResetOutcome outcome = Laba05Reset.run(failing);
                assertEquals(Laba05Reset.RC_DB_ERROR, outcome.returnCode);
                assertEquals(42, outcome.beforeJvNumber);
                assertNull(outcome.afterJvNumber);
                assertTrue(outcome.message.contains("update failed"));
            }
        }

        @Test void returns99OnSqlException() throws Exception {
            try (DbDispatcher db = freshDb()) {
                DemoSchema.seedControlRecord(db, 42);
                try (Statement st = db.connection().createStatement()) {
                    st.execute("DROP TABLE CONTROL_RECORD_TABLE");
                }
                db.commit();
                ResetOutcome outcome = Laba05Reset.run(db);
                assertEquals(Laba05Reset.RC_DB_ERROR, outcome.returnCode);
            }
        }
    }

    @Nested
    class BinaryDisplayConversion {
        @Test void roundTrip() {
            for (int original : new int[] {1, 17, 999999}) {
                try (DbDispatcher db = freshDb()) {
                    DemoSchema.seedControlRecord(db, original);
                    ResetOutcome outcome = Laba05Reset.run(db);
                    assertEquals(original, outcome.beforeJvNumber);
                    assertEquals(1, outcome.afterJvNumber);
                }
            }
        }
    }
}
