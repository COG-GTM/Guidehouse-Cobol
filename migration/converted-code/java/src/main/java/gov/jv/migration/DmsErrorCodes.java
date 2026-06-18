// STATUS: Demo output — pending SME review
package gov.jv.migration;

/**
 * SQLCODE to DMS return-code translation table.
 * <p>
 * Source: source/procobol/DBIO.pco:374-398 (5300-TRANSLATE-SQLCODE paragraph)
 * <p>
 * The legacy COBOL dispatcher maps Oracle SQLCODE values to 4-character DMS
 * return codes. Callers (LABA05, LABD20) check this DMS code (aliased as
 * ERROR-NUM in the caller's DMCA area), not the raw SQLCODE.
 * <p>
 * // RISK 6: see migration/RISKS-AND-GAPS.md — SQLCODE to DMS translation
 * // ASSUMPTION A-3: This mapping reproduces DBIO.pco:374-398 verbatim.
 */
public final class DmsErrorCodes {

    /** SQLCODE 0 — success. Source: DBIO.pco:377 */
    public static final String OK = "0000";

    /** SQLCODE 100 — not found. Source: DBIO.pco:379 */
    public static final String NOT_FOUND = "0013";

    /** SQLCODE 100 + set-name present + function != FETCH OWNER — end of set. Source: DBIO.pco:382 */
    public static final String END_OF_SET = "0007";

    /** SQLCODE -1 — duplicate key. Source: DBIO.pco:385 */
    public static final String DUP_KEY = "0005";

    /** SQLCODE -8103 — treated as continue/OK. Source: DBIO.pco:387 */
    public static final String CONTINUE_8103 = "0000";

    /** Any other SQLCODE — unhandled. Source: DBIO.pco:397 */
    public static final String UNHANDLED = "9999";

    private DmsErrorCodes() {}

    /**
     * Translates a SQLCODE to a 4-character DMS return code.
     * <p>
     * Source: DBIO.pco:374-398 (5300-TRANSLATE-SQLCODE)
     * <p>
     * Uses Java 21 switch expression with pattern matching.
     *
     * @param sqlcode      the Oracle SQLCODE value
     * @param setName      the DB-SET-NAME (may be blank)
     * @param functionType the DB-FUNCTION-TYPE
     * @return 4-character DMS code
     */
    public static String translate(int sqlcode, String setName, String functionType) {
        return switch (sqlcode) {
            // Source: DBIO.pco:377
            case 0 -> OK;
            // Source: DBIO.pco:378-383
            case 100, 1403 -> {
                if (setName != null && !setName.isBlank()
                        && !"FETCH OWNER".equals(functionType)) {
                    yield END_OF_SET;  // Source: DBIO.pco:382
                }
                yield NOT_FOUND;  // Source: DBIO.pco:379
            }
            // Source: DBIO.pco:384-385
            case -1 -> DUP_KEY;
            // Source: DBIO.pco:386-395
            case -8103 -> CONTINUE_8103;
            // Source: DBIO.pco:396-397
            default -> UNHANDLED;
        };
    }
}
