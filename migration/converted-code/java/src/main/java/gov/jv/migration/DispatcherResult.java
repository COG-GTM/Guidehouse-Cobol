// STATUS: Demo output — pending SME review
package gov.jv.migration;

import java.util.List;

/**
 * Result record returned by {@link DbDispatcher} operations.
 * <p>
 * Mirrors the (DB-RTNCODE-DMS, DB-SQLCODE-NUM, DB-MESSAGE) tuple that
 * DBIO.pco writes back to its callers (cf. 9999-ERROR at DBIO.pco:402-405).
 * <p>
 * Source: source/copybooks/DBVAR.cpy:21 (DB-RTNCODE-DMS PIC X(4))
 *         source/copybooks/DBVAR.cpy:14 (DB-SQLCODE-NUM PIC S9(9) BINARY)
 *         source/copybooks/DBVAR.cpy:22 (DB-MESSAGE PIC X(80))
 */
public record DispatcherResult(
        String rtncodesDms,
        int sqlcode,
        String message,
        List<Object[]> rows
) {
    /**
     * Returns true if the DMS code indicates success.
     * Source: DMCA.cpy:45 — 88 DMS-OK VALUE '0000'.
     */
    public boolean ok() {
        return DmsErrorCodes.OK.equals(rtncodesDms);
    }

    /** Convenience factory for a successful result with no rows. */
    public static DispatcherResult success(String message) {
        return new DispatcherResult(DmsErrorCodes.OK, 0, message, null);
    }

    /** Convenience factory for an error result. */
    public static DispatcherResult error(int sqlcode, String message) {
        String dms = DmsErrorCodes.translate(sqlcode, "", "");
        return new DispatcherResult(dms, sqlcode, message, null);
    }
}
