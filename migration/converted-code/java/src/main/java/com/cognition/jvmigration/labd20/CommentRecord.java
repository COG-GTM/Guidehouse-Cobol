package com.cognition.jvmigration.labd20;

/**
 * Parsed view over the 300-byte TST123-COMMENT-REC. Java analog of the
 * {@code CommentRecord} dataclass in
 * {@code migration/converted-code/python/labd20_loader.py}.
 *
 * <p>Layout source: {@code source/procobol/LABD20.pco:43-55}.
 */
public class CommentRecord {
    public final String raw;
    public final String commentDt;     // PIC 9(008)  bytes 0-7
    public final String jvNumber;      // PIC 9(006)  bytes 8-13
    public final String sectionId;     // PIC 9(002)  bytes 14-15
    public final String loanNumber;    // PIC 9(010)  bytes 16-25
    public final String loanDtNr;      // composite   bytes 0-25
    public final String scheduleDocNo; // PIC X(010)  bytes 26-35
    public final String commentText;   // PIC X(230)  bytes 36-265
    public final String commentHist;   // composite   bytes 26-265
    public final String requestor;     // PIC X(020)  bytes 266-285
    public final String approver;      // PIC X(014)  bytes 286-299

    public CommentRecord(String raw, String commentDt, String jvNumber, String sectionId,
                         String loanNumber, String loanDtNr, String scheduleDocNo,
                         String commentText, String commentHist, String requestor,
                         String approver) {
        this.raw = raw;
        this.commentDt = commentDt;
        this.jvNumber = jvNumber;
        this.sectionId = sectionId;
        this.loanNumber = loanNumber;
        this.loanDtNr = loanDtNr;
        this.scheduleDocNo = scheduleDocNo;
        this.commentText = commentText;
        this.commentHist = commentHist;
        this.requestor = requestor;
        this.approver = approver;
    }

    /**
     * The 26-byte composite primary key used by JC_SUBMITTED_COMMENT_TBL
     * (LABD20.pco:329 — JC_SUBMITTED = :WS-TST123-LOAN-DT-NR).
     */
    public String submittedKey() {
        return loanDtNr;
    }

    /**
     * WS-CONTROL-NUM = JV-NUMBER (6) + SECTION-ID (2) = 8 bytes
     * (LABD20.pco:160-165 WS-CONTROL-NUM redefine).
     */
    public String controlNum() {
        return jvNumber + sectionId;
    }
}
