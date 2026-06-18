// STATUS: Demo output — pending SME review
package gov.jv.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JvControlRecord} — record parsing and serialization.
 * <p>
 * Source: source/copybooks/JV-CONTROL-REC.cpy:1-9
 *         source/procobol/CONTROL-RECORD-TABLE-IO.pco:21-28 (binary/display layout)
 */
class JvControlRecordTest {

    /** A known 400-byte blob for testing. */
    private static final String KNOWN_DATA =
            "000001"  // JV-CONTROL-1 (bytes 0-5)
          + "000002"  // JV-CONTROL-2 (bytes 6-11)
          + "000003"  // JV-CONTROL-3 (bytes 12-17)
          + "000004"  // JV-CONTROL-4 (bytes 18-23)
          + "000042"  // JV-NUMBER    (bytes 24-29) — RISK 2
          + "ABCDEFGHI"  // FILLER1   (bytes 30-38)
          + "000005"  // JV-CONTROL-5 (bytes 39-44)
          + "KLMNOPQRST"  // FILLER2  (bytes 45-54)
          + " ".repeat(400 - 55);  // padding to 400

    @Test
    @DisplayName("fromControlRecordData parses known blob correctly")
    void testFromControlRecordData() {
        JvControlRecord rec = JvControlRecord.fromControlRecordData(KNOWN_DATA);

        assertEquals(1, rec.jvControl1());
        assertEquals(2, rec.jvControl2());
        assertEquals(3, rec.jvControl3());
        assertEquals(4, rec.jvControl4());
        assertEquals(42, rec.jvNumber());
        assertEquals("ABCDEFGHI", rec.filler1());
        assertEquals(5, rec.jvControl5());
        assertEquals("KLMNOPQRST", rec.filler2());
    }

    @Test
    @DisplayName("toControlRecordData round-trips correctly")
    void testToControlRecordDataRoundTrip() {
        JvControlRecord rec = JvControlRecord.fromControlRecordData(KNOWN_DATA);
        String serialized = rec.toControlRecordData();

        assertEquals(400, serialized.length());
        // First 55 bytes should match exactly
        assertEquals(KNOWN_DATA.substring(0, 55), serialized.substring(0, 55));

        // Round-trip: parse again and compare
        JvControlRecord rec2 = JvControlRecord.fromControlRecordData(serialized);
        assertEquals(rec, rec2);
    }

    @Test
    @DisplayName("withJvNumber produces correct output")
    void testWithJvNumber() {
        JvControlRecord rec = JvControlRecord.fromControlRecordData(KNOWN_DATA);
        JvControlRecord updated = rec.withJvNumber(1);

        assertEquals(1, updated.jvNumber());
        // All other fields unchanged
        assertEquals(rec.jvControl1(), updated.jvControl1());
        assertEquals(rec.jvControl2(), updated.jvControl2());
        assertEquals(rec.jvControl3(), updated.jvControl3());
        assertEquals(rec.jvControl4(), updated.jvControl4());
        assertEquals(rec.filler1(), updated.filler1());
        assertEquals(rec.jvControl5(), updated.jvControl5());
        assertEquals(rec.filler2(), updated.filler2());

        // Serialization check
        String data = updated.toControlRecordData();
        assertEquals("000001", data.substring(24, 30));
    }

    @Test
    @DisplayName("Fields map to correct byte offsets")
    void testFieldsByteOffsets() {
        // Source: JV-CONTROL-REC.cpy:2-9
        String data = "1".repeat(6)    // bytes 0-5:   jvControl1
                    + "2".repeat(6)    // bytes 6-11:  jvControl2
                    + "3".repeat(6)    // bytes 12-17: jvControl3
                    + "4".repeat(6)    // bytes 18-23: jvControl4
                    + "000099"         // bytes 24-29: jvNumber
                    + "X".repeat(9)    // bytes 30-38: filler1
                    + "5".repeat(6)    // bytes 39-44: jvControl5
                    + "Y".repeat(10)   // bytes 45-54: filler2
                    + " ".repeat(400 - 55);

        JvControlRecord rec = JvControlRecord.fromControlRecordData(data);

        assertEquals(111111, rec.jvControl1());
        assertEquals(222222, rec.jvControl2());
        assertEquals(333333, rec.jvControl3());
        assertEquals(444444, rec.jvControl4());
        assertEquals(99, rec.jvNumber());
        assertEquals("X".repeat(9), rec.filler1());
        assertEquals(555555, rec.jvControl5());
        assertEquals("Y".repeat(10), rec.filler2());
    }

    @Test
    @DisplayName("Rejects non-400-byte input")
    void testRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class,
                () -> JvControlRecord.fromControlRecordData("too short"));
        assertThrows(IllegalArgumentException.class,
                () -> JvControlRecord.fromControlRecordData("X".repeat(401)));
    }
}
