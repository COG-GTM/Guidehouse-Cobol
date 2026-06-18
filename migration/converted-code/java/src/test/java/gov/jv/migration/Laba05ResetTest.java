// STATUS: Demo output — pending SME review
package gov.jv.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 test suite for the LABA05 fiscal-year reset — Java 21 migration.
 * <p>
 * Ported from: migration/converted-code/python/tests/test_laba05_reset.py (12 test cases)
 * <p>
 * Source: LABA05.cbl:1-285 (program under test)
 *         JV-CONTROL-REC.cpy:1-9 (record layout)
 *         DBIO.pco:374-398 (SQLCODE→DMS translation)
 */
class Laba05ResetTest {

    private Connection conn;
    private DbDispatcher dispatcher;

    @BeforeEach
    void setUp() throws SQLException {
        // Each test gets an isolated in-memory database
        String url = "jdbc:h2:mem:" + UUID.randomUUID();
        conn = DriverManager.getConnection(url);
        conn.setAutoCommit(false);
        DbDispatcher.buildDemoSchema(conn);
        dispatcher = new DbDispatcher(conn);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // Byte-layout / binary↔display conversion (RISKS Risk 2)
    // Source: test_laba05_reset.py::TestJVNumberByteLayout
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Byte layout tests (Risk 2 surface)")
    class ByteLayoutTests {

        @Test
        @DisplayName("JV-NUMBER slice is bytes 24-30")
        void testSliceIs24To30() {
            // Source: JV-CONTROL-REC.cpy:6 — JV-NUMBER at offset 24, length 6
            String data = "0".repeat(24) + "000042" + " ".repeat(400 - 30);
            JvControlRecord rec = JvControlRecord.fromControlRecordData(data);
            assertEquals(42, rec.jvNumber());
        }

        @Test
        @DisplayName("Data blob must be 400 bytes")
        void testDataBlobMustBe400Bytes() {
            // Source: CONTROL_RECORD_DATA is CHAR(400)
            assertThrows(IllegalArgumentException.class,
                    () -> JvControlRecord.fromControlRecordData("X".repeat(399)));
        }

        @Test
        @DisplayName("Extract handles zero-padded digits")
        void testExtractHandlesZeroPaddedDigits() {
            // Source: PIC 9(006) — leading zeros
            String data = "0".repeat(24) + "000042" + " ".repeat(400 - 30);
            JvControlRecord rec = JvControlRecord.fromControlRecordData(data);
            assertEquals(42, rec.jvNumber());
        }

        @Test
        @DisplayName("Replace preserves surrounding bytes")
        void testReplacePreservesSurroundingBytes() {
            // Source: CONTROL-RECORD-TABLE-IO.pco:21-28 — binary↔display layout
            // Use valid numeric fields for PIC 9 areas, alpha for FILLER areas
            String data = "111111" + "222222" + "333333" + "444444"  // controls 1-4
                    + "999999"                                       // JV-NUMBER
                    + "BBBBBBBBB"                                    // FILLER1
                    + "555555"                                       // JV-CONTROL-5
                    + "CCCCCCCCCC"                                   // FILLER2
                    + "D".repeat(400 - 55);                          // remaining padding
            JvControlRecord rec = JvControlRecord.fromControlRecordData(data);
            JvControlRecord updated = rec.withJvNumber(1);
            String newData = updated.toControlRecordData();

            // Verify JV-NUMBER updated
            assertEquals("000001", newData.substring(24, 30));
            // Verify surrounding fields preserved
            assertEquals("111111", newData.substring(0, 6));
            assertEquals("222222", newData.substring(6, 12));
            assertEquals("333333", newData.substring(12, 18));
            assertEquals("444444", newData.substring(18, 24));
            assertEquals("BBBBBBBBB", newData.substring(30, 39));
            assertEquals("555555", newData.substring(39, 45));
            assertEquals("CCCCCCCCCC", newData.substring(45, 55));
            assertEquals(400, newData.length());
        }
    }

    // -------------------------------------------------------------------------
    // End-to-end reset
    // Source: test_laba05_reset.py::TestReset
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("End-to-end reset tests")
    class ResetTests {

        @Test
        @DisplayName("Reset succeeds when row exists — before=42, after=1")
        void testResetSucceedsWhenRowExists() {
            // Source: LABA05.cbl:87-105 (0000-MAIN-LINE success path)
            DbDispatcher.seedControlRecord(dispatcher, 42);

            Laba05Reset.ResetOutcome outcome = Laba05Reset.run(dispatcher);

            assertTrue(outcome.ok());
            assertEquals(Laba05Reset.RC_OK, outcome.returnCode());
            assertEquals(42, outcome.beforeJvNumber());
            assertEquals(1, outcome.afterJvNumber());

            // Verify the persisted row was updated
            DispatcherResult res = dispatcher.selectOne(
                    "SELECT CONTROL_RECORD_DATA FROM CONTROL_RECORD_TABLE WHERE CONTROL_RECORD_NAME = ? AND CONTROL_RECORD_NUMBER = ?",
                    "JV-CONTROL-REC", 1);
            assertTrue(res.ok());
            String persisted = (String) res.rows().get(0)[0];
            JvControlRecord persistedRec = JvControlRecord.fromControlRecordData(persisted);
            assertEquals(1, persistedRec.jvNumber());
        }

        @Test
        @DisplayName("Returns 99 when row is missing")
        void testReturns99WhenRowMissing() {
            // Source: LABA05.cbl:92-98 (ERROR-NUM not '0000' path)
            // No seed — table is empty
            Laba05Reset.ResetOutcome outcome = Laba05Reset.run(dispatcher);

            assertEquals(Laba05Reset.RC_DB_ERROR, outcome.returnCode());
            assertNull(outcome.beforeJvNumber());
            assertNull(outcome.afterJvNumber());
        }

        @Test
        @DisplayName("Returns 99 when update fails")
        void testReturns99WhenUpdateFails() throws SQLException {
            // Source: LABA05.cbl:196-200 (MODIFY-CTRL-REC error path)
            // Mirror Python test: monkeypatch DBDispatcher.update to return failure
            DbDispatcher.seedControlRecord(dispatcher, 42);

            // Override update() to simulate a failed MODIFY
            DbDispatcher failingDispatcher = new DbDispatcher(conn) {
                @Override
                public DispatcherResult update(String sql, Object... params) {
                    return new DispatcherResult("9999", -1234, "simulated update failure", null);
                }
            };

            Laba05Reset.ResetOutcome outcome = Laba05Reset.run(failingDispatcher);
            assertEquals(Laba05Reset.RC_DB_ERROR, outcome.returnCode());
            assertEquals(42, outcome.beforeJvNumber());
            assertNull(outcome.afterJvNumber());
            assertTrue(outcome.message().contains("update failed"));
        }

        @Test
        @DisplayName("Returns 99 on SQL exception (table dropped)")
        void testReturns99OnSqlException() throws SQLException {
            // Source: LABA05.cbl:83,97 — DB error → MOVE 99 TO RETURN-CODE
            DbDispatcher.seedControlRecord(dispatcher, 42);

            // Drop the table so SELECT fails
            try (Statement st = conn.createStatement()) {
                st.execute("DROP TABLE CONTROL_RECORD_TABLE");
            }
            conn.commit();

            Laba05Reset.ResetOutcome outcome = Laba05Reset.run(dispatcher);
            assertEquals(Laba05Reset.RC_DB_ERROR, outcome.returnCode());
        }
    }

    // -------------------------------------------------------------------------
    // Binary↔display round-trip (Risk 2 mitigation surface)
    // Source: test_laba05_reset.py::TestBinaryDisplayConversion
    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("Binary↔display round-trip tests (Risk 2)")
    class RoundTripTests {

        @Test
        @DisplayName("Round-trip with values 1, 17, 999999")
        void testRoundTrip() throws SQLException {
            // Source: CONTROL-RECORD-TABLE-IO.pco:21-28 — binary↔display dual layout
            for (int original : new int[]{1, 17, 999999}) {
                // Clean table for each iteration
                try (Statement st = conn.createStatement()) {
                    st.execute("DELETE FROM CONTROL_RECORD_TABLE");
                }
                conn.commit();

                DbDispatcher.seedControlRecord(dispatcher, original);
                Laba05Reset.ResetOutcome outcome = Laba05Reset.run(dispatcher);

                assertEquals(original, outcome.beforeJvNumber(),
                        "before should be " + original);
                assertEquals(1, outcome.afterJvNumber(),
                        "after should be 1 for original=" + original);
            }
        }
    }
}
