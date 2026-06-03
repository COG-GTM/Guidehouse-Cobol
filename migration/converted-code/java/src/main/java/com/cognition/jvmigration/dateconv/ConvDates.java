package com.cognition.jvmigration.dateconv;

/**
 * Mutable view over the LINKAGE SECTION CONV-DATES record
 * (source/copybooks/DATECONV-WS.cpy). Mirrors
 * migration/converted-code/python/dateconv.py {@code ConvDates}.
 *
 * <p>The COBOL field for the "second" input of DIF/RANGE operations is TO-*-DT;
 * it is kept as the canonical field. THRU-* accessors point at the same storage.
 */
public class ConvDates {
    public int datesubFunc = 0;

    // FROM-* inputs
    public int fromCymdDt = 0;   // PIC 9(8)
    public int fromJulDt = 0;    // PIC 9(5)
    public int fromYmdDt = 0;    // PIC 9(6)
    public int fromMdyDt = 0;    // PIC 9(6)
    public int fromIntDt = 0;    // PIC 9(10) COMP

    // TO-* outputs (also serve as the "thru" side of DIF/RANGE)
    public int toCymdDt = 0;
    public int toJulDt = 0;
    public int toYmdDt = 0;
    public int toMdyDt = 0;
    public int toMdcyDt = 0;
    public int toIntDt = 0;

    // BETWEEN-* (the candidate point for RANGE operations)
    public int betweenJulDt = 0;
    public int betweenYmdDt = 0;
    public int betweenMdyDt = 0;

    // arithmetic inputs
    public int monthsToAdd = 0;
    public int daysDif = 0;      // also DAYS-DIF result + 88888/77777 sentinels

    // output flags
    public String dateErrInd = "N";  // 'Y' / 'N' (DATE-IS-VALID = 'N')
    public int dateErrReason = 0;    // 0..12 (see JDN-Con-Status-Codes)

    // THRU-* convenience aliases (read/write the same storage as TO-*).
    public int thruCymdDt() { return toCymdDt; }
    public void thruCymdDt(int v) { toCymdDt = v; }
    public int thruJulDt() { return toJulDt; }
    public void thruJulDt(int v) { toJulDt = v; }
    public int thruYmdDt() { return toYmdDt; }
    public void thruYmdDt(int v) { toYmdDt = v; }
    public int thruMdyDt() { return toMdyDt; }
    public void thruMdyDt(int v) { toMdyDt = v; }
    public int thruIntDt() { return toIntDt; }
    public void thruIntDt(int v) { toIntDt = v; }
}
