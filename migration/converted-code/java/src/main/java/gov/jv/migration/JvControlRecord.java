// STATUS: Demo output — pending SME review
package gov.jv.migration;

/**
 * Java record mapping the JV-CONTROL-REC copybook layout.
 * <p>
 * Source: source/copybooks/JV-CONTROL-REC.cpy:1-9
 * <p>
 * Byte layout within CONTROL_RECORD_DATA CHAR(400):
 * <pre>
 *   Bytes  0-5:   JV-CONTROL-1  PIC 9(006)              → int jvControl1
 *   Bytes  6-11:  JV-CONTROL-2  PIC 9(006)              → int jvControl2
 *   Bytes 12-17:  JV-CONTROL-3  PIC 9(006)              → int jvControl3
 *   Bytes 18-23:  JV-CONTROL-4  PIC 9(006)              → int jvControl4
 *   Bytes 24-29:  JV-NUMBER     PIC 9(006) USAGE BINARY → int jvNumber
 *   Bytes 30-38:  FILLER        PIC X(009)              → String filler1
 *   Bytes 39-44:  JV-CONTROL-5  PIC 9(006)              → int jvControl5
 *   Bytes 45-54:  FILLER        PIC X(010)              → String filler2
 * </pre>
 * <p>
 * // RISK 2: see migration/RISKS-AND-GAPS.md
 * The JV-NUMBER field is declared as USAGE BINARY in the copybook. In the display
 * form stored in CONTROL_RECORD_DATA CHAR(400), it occupies bytes 24-29 as zoned
 * decimal digits. For this demo, we use the display (zoned) form.
 * // ASSUMPTION A-2: Production may need struct.unpack-equivalent binary handling.
 */
public record JvControlRecord(
        int jvControl1,
        int jvControl2,
        int jvControl3,
        int jvControl4,
        int jvNumber,
        String filler1,
        int jvControl5,
        String filler2
) {
    /** Total length of CONTROL_RECORD_DATA blob. */
    public static final int DATA_LENGTH = 400;

    // Byte offset constants — Source: JV-CONTROL-REC.cpy:2-9
    private static final int OFF_CONTROL1 = 0;
    private static final int OFF_CONTROL2 = 6;
    private static final int OFF_CONTROL3 = 12;
    private static final int OFF_CONTROL4 = 18;
    private static final int OFF_JV_NUMBER = 24;
    private static final int OFF_FILLER1 = 30;
    private static final int OFF_CONTROL5 = 39;
    private static final int OFF_FILLER2 = 45;
    private static final int END_FILLER2 = 55;

    /**
     * Parses a 400-byte CONTROL_RECORD_DATA blob into a JvControlRecord.
     * Source: CONTROL-RECORD-TABLE-IO.pco:21-28 (display form layout)
     *
     * @param data400 the 400-character CONTROL_RECORD_DATA string
     * @return parsed record
     * @throws IllegalArgumentException if data is not exactly 400 characters
     */
    public static JvControlRecord fromControlRecordData(String data400) {
        if (data400.length() != DATA_LENGTH) {
            throw new IllegalArgumentException(
                    "CONTROL_RECORD_DATA must be " + DATA_LENGTH + " bytes; got " + data400.length());
        }
        return new JvControlRecord(
                parseIntField(data400, OFF_CONTROL1, OFF_CONTROL2),
                parseIntField(data400, OFF_CONTROL2, OFF_CONTROL3),
                parseIntField(data400, OFF_CONTROL3, OFF_CONTROL4),
                parseIntField(data400, OFF_CONTROL4, OFF_JV_NUMBER),
                // RISK 2: JV-NUMBER stored as zoned display in the CHAR(400) blob
                parseIntField(data400, OFF_JV_NUMBER, OFF_FILLER1),
                data400.substring(OFF_FILLER1, OFF_CONTROL5),
                parseIntField(data400, OFF_CONTROL5, OFF_FILLER2),
                data400.substring(OFF_FILLER2, END_FILLER2)
        );
    }

    /**
     * Serializes this record back to a 400-byte CONTROL_RECORD_DATA string.
     * Source: CONTROL-RECORD-TABLE-IO.pco:21-28
     *
     * @return 400-character string representation
     */
    public String toControlRecordData() {
        var sb = new StringBuilder(DATA_LENGTH);
        sb.append(String.format("%06d", jvControl1));
        sb.append(String.format("%06d", jvControl2));
        sb.append(String.format("%06d", jvControl3));
        sb.append(String.format("%06d", jvControl4));
        // RISK 2: writing JV-NUMBER as zoned display form
        sb.append(String.format("%06d", jvNumber));
        sb.append(padRight(filler1, 9));
        sb.append(String.format("%06d", jvControl5));
        sb.append(padRight(filler2, 10));
        // Pad remaining bytes to 400
        while (sb.length() < DATA_LENGTH) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Returns a copy of this record with an updated JV-NUMBER value.
     * Source: LABA05.cbl:179 — MOVE 1 TO JV-NUMBER
     *
     * @param newValue the new JV-NUMBER
     * @return new record with updated jvNumber
     */
    public JvControlRecord withJvNumber(int newValue) {
        return new JvControlRecord(
                jvControl1, jvControl2, jvControl3, jvControl4,
                newValue, filler1, jvControl5, filler2);
    }

    private static int parseIntField(String data, int start, int end) {
        String chunk = data.substring(start, end).trim();
        if (chunk.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(chunk);
    }

    private static String padRight(String s, int length) {
        if (s == null) {
            return " ".repeat(length);
        }
        if (s.length() >= length) {
            return s.substring(0, length);
        }
        return s + " ".repeat(length - s.length());
    }
}
