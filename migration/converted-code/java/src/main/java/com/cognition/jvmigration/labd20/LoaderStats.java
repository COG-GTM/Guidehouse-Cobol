package com.cognition.jvmigration.labd20;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors LABD20 reporting accumulators (LABD20.pco:60-105 WS-COUNTERS, used in
 * the reporting block at LABD20.pco:448-486). Java analog of the
 * {@code LoaderStats} dataclass in
 * {@code migration/converted-code/python/labd20_loader.py}.
 */
public class LoaderStats {
    public int totalRead = 0;        // WS-JV-COMMENTS-CNT
    public int accepted = 0;         // WS-JV-COUNTER increments
    public int rejected = 0;         // WS-TST123-RECS-ERR-CNT
    public int duplicates = 0;
    public int inserted = 0;
    public int submittedTotal = 0;   // WS-TOTAL-SUBMIT-END-CNT
    public int rejectedTotal = 0;    // WS-TOTAL-REJECT-END-CNT
    public int appliedTotal = 0;     // WS-TOTAL-APPLIED-END-CNT
    public String processDate = "";
    public final List<String> rejectedReasons = new ArrayList<>();

    /**
     * A human-readable summary; replaces the LABD20 DISPLAY block at
     * LABD20.pco:448-486 (legacy text reproduced loosely).
     */
    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("*** COMMENT PROCESSING ***\n");
        sb.append(String.format("PROCESS DATE              : %s%n", processDate));
        sb.append(String.format("COMMENTS READ             : %d%n", totalRead));
        sb.append(String.format("COMMENTS ACCEPTED         : %d%n", accepted));
        sb.append(String.format("COMMENTS REJECTED         : %d%n", rejected));
        sb.append(String.format("COMMENTS DUPLICATE        : %d%n", duplicates));
        sb.append(String.format("COMMENTS INSERTED         : %d%n", inserted));
        sb.append(String.format("JC_SUBMITTED TOTAL        : %d%n", submittedTotal));
        sb.append(String.format("JC_REJECTED TOTAL         : %d%n", rejectedTotal));
        sb.append(String.format("JC_APPLIED  TOTAL         : %d", appliedTotal));
        return sb.toString();
    }
}
