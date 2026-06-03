package com.cognition.jvmigration.dateconv;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Java port of {@code source/cobol/DATECONV.cbl} (42 DATESUB-FUNC codes, 40
 * active entry paragraphs; codes 29/30 reserved per
 * {@code source/copybooks/DATECONV-PD.cpy}). Mirrors
 * {@code migration/converted-code/python/dateconv.py} method-for-method.
 *
 * <p>STATUS: Demo output pending SME review.
 *
 * <p>The customer's DATECONV.cbl was migrated to COBOL-85 intrinsics in 2012,
 * so all date math reduces to INTEGER-OF-DATE / DATE-OF-INTEGER /
 * INTEGER-OF-DAY / DAY-OF-INTEGER, which {@link java.time.LocalDate} implements
 * exactly (proleptic Gregorian). The DATESUB-FUNC dispatch idiom is preserved
 * so a SME can map every method back to a DATECONV.cbl paragraph.
 */
public class DateConv {

    // Status codes — mirror JDN-Con-Status-Codes (JDN-CONSTANTS-WS.cpy).
    public static final String STATUS_OK = "OK";
    public static final String STATUS_OOR_DD = "OutOfRangeDD";
    public static final String STATUS_OOR_DDD = "OutOfRangeDDD";
    public static final String STATUS_OOR_MM = "OutOfRangeMM";
    public static final String STATUS_OOR_YYYY = "OutOfRangeYYYY";
    public static final String STATUS_STRANGE = "Strange";

    // COBOL INTEGER-OF-DATE epoch = 1601-01-01 (day 1).
    // Source: JDN-CONSTANTS-WS.cpy lines 35-38.
    private static final long EPOCH_EPOCHDAY = LocalDate.of(1601, 1, 1).toEpochDay();

    private static final Map<String, Integer> REASON = new HashMap<>();
    private static final Map<Integer, String> REASON_REV = new HashMap<>();

    static {
        REASON.put(STATUS_OK, 0);
        REASON.put(STATUS_OOR_DD, 7);
        REASON.put(STATUS_OOR_DDD, 8);
        REASON.put(STATUS_OOR_MM, 10);
        REASON.put(STATUS_OOR_YYYY, 11);
        REASON.put(STATUS_STRANGE, 12);
        for (Map.Entry<String, Integer> e : REASON.entrySet()) {
            REASON_REV.put(e.getValue(), e.getKey());
        }
    }

    // ------------------------------------------------------------------
    // Small result holders (Java has no tuples).
    // ------------------------------------------------------------------
    private static final class IntRes {
        final String status;
        final int value;
        IntRes(String status, int value) { this.status = status; this.value = value; }
    }

    private static final class DayRes {
        final String status;
        final int value;
        final int yyyy;
        DayRes(String status, int value, int yyyy) {
            this.status = status; this.value = value; this.yyyy = yyyy;
        }
    }

    private static final class DateRes {
        final String status;
        final LocalDate date;
        DateRes(String status, LocalDate date) { this.status = status; this.date = date; }
    }

    private static final class AddRes {
        final String status;
        final int y;
        final int m;
        final int d;
        AddRes(String status, int y, int m, int d) {
            this.status = status; this.y = y; this.m = m; this.d = d;
        }
    }

    /** Public convenience result: status + boolean validity (check_* funcs). */
    public static final class StatusBool {
        public final String status;
        public final boolean valid;
        public StatusBool(String status, boolean valid) { this.status = status; this.valid = valid; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof StatusBool)) return false;
            StatusBool b = (StatusBool) o;
            return valid == b.valid && status.equals(b.status);
        }
        @Override public int hashCode() { return status.hashCode() * 31 + (valid ? 1 : 0); }
        @Override public String toString() { return "(" + status + ", " + valid + ")"; }
    }

    /** Public convenience result: status + integer output. */
    public static final class StatusInt {
        public final String status;
        public final int value;
        public StatusInt(String status, int value) { this.status = status; this.value = value; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof StatusInt)) return false;
            StatusInt b = (StatusInt) o;
            return value == b.value && status.equals(b.status);
        }
        @Override public int hashCode() { return status.hashCode() * 31 + value; }
        @Override public String toString() { return "(" + status + ", " + value + ")"; }
    }

    // ------------------------------------------------------------------
    // Century-inference helpers (two distinct thresholds).
    // ------------------------------------------------------------------
    /** DATECONV.cbl:1054-1059 (9920-CALC-YY-TO-YYYY): YY > 52 -> 19xx else 20xx. */
    private static int ccInferred(int yy) { return yy > 52 ? 19 : 20; }

    /** JDN-RECORD-ACCESS.cpy:74-79 (JDN-Acc-CC-Inferred): YY > 72 -> 19xx else 20xx. */
    private static int ccInferredJdn(int yy) { return yy > 72 ? 19 : 20; }

    private static int[] splitDateInt(int v) {
        return new int[] { v / 10000, (v / 100) % 100, v % 100 };
    }

    private static int[] splitJul(int jul) {
        return new int[] { jul / 1000, jul % 1000 };
    }

    private static int ymdToCymd(int ymd, boolean viaJdn) {
        int[] p = splitDateInt(ymd);
        int yy = p[0], mm = p[1], dd = p[2];
        int cc = viaJdn ? ccInferredJdn(yy) : ccInferred(yy);
        return cc * 1000000 + yy * 10000 + mm * 100 + dd;
    }

    private static int mdyToCymd(int mdy, boolean viaJdn) {
        int[] p = splitDateInt(mdy);
        int mm = p[0], dd = p[1], yy = p[2];
        int cc = viaJdn ? ccInferredJdn(yy) : ccInferred(yy);
        return cc * 1000000 + yy * 10000 + mm * 100 + dd;
    }

    private static int jdnInt(LocalDate d) {
        return (int) (d.toEpochDay() - EPOCH_EPOCHDAY + 1);
    }

    private static LocalDate dateFromJdn(int jdn) {
        return LocalDate.ofEpochDay(EPOCH_EPOCHDAY + jdn - 1);
    }

    // ------------------------------------------------------------------
    // Core conversions.
    // ------------------------------------------------------------------
    private static IntRes intOfDate(int cymd) {
        int[] p = splitDateInt(cymd);
        int yyyy = p[0], mm = p[1], dd = p[2];
        if (yyyy < 1601) return new IntRes(STATUS_OOR_YYYY, 0);
        if (mm < 1 || mm > 12) return new IntRes(STATUS_OOR_MM, 0);
        try {
            LocalDate d = LocalDate.of(yyyy, mm, dd);
            return new IntRes(STATUS_OK, jdnInt(d));
        } catch (DateTimeException e) {
            return new IntRes(STATUS_OOR_DD, 0);
        }
    }

    private static DayRes intOfDay(int julYyddd) {
        return intOfDay(julYyddd, null);
    }

    private static DayRes intOfDay(int julYyddd, Integer centuryHint) {
        int[] p = splitJul(julYyddd);
        int yy = p[0], ddd = p[1];
        int cc = centuryHint != null ? centuryHint : ccInferredJdn(yy);
        int yyyy = cc * 100 + yy;
        if (yyyy < 1601) return new DayRes(STATUS_OOR_YYYY, 0, yyyy);
        if (ddd < 1 || ddd > 366) return new DayRes(STATUS_OOR_DDD, 0, yyyy);
        try {
            long ord = LocalDate.of(yyyy, 1, 1).toEpochDay() + ddd - 1;
            if (LocalDate.ofEpochDay(ord).getYear() != yyyy) {
                return new DayRes(STATUS_OOR_DDD, 0, yyyy);
            }
            return new DayRes(STATUS_OK, (int) (ord - EPOCH_EPOCHDAY + 1), yyyy);
        } catch (DateTimeException e) {
            return new DayRes(STATUS_OOR_DDD, 0, yyyy);
        }
    }

    private static DateRes dateOfInt(int jdnInt) {
        if (jdnInt < 1) return new DateRes(STATUS_OOR_YYYY, LocalDate.of(1601, 1, 1));
        try {
            return new DateRes(STATUS_OK, dateFromJdn(jdnInt));
        } catch (DateTimeException | ArithmeticException e) {
            return new DateRes(STATUS_STRANGE, LocalDate.of(1601, 1, 1));
        }
    }

    // ------------------------------------------------------------------
    // Dispatch — DATESUB-FUNC -> method (DATECONV.cbl:111-198 000-SELECT).
    // ------------------------------------------------------------------
    public ConvDates dispatch(int funcCode, ConvDates cd) {
        cd.datesubFunc = funcCode;
        cd.dateErrInd = "N";
        cd.dateErrReason = 0;
        switch (funcCode) {
            case 1:  run01CheckCymdDt(cd); break;
            case 2:  run02YmdToJul(cd); break;
            case 3:  run03JulToYmd(cd); break;
            case 4:  run04DifJul(cd); break;
            case 5:  run05DifYmd(cd); break;
            case 6:  run06DifCymd30(cd); break;
            case 7:  run07AddJul(cd); break;
            case 8:  run08AddYmd(cd); break;
            case 9:  run09CheckMdyDt(cd); break;
            case 10: run10MdyToJul(cd); break;
            case 11: run11JulToMdy(cd); break;
            case 12: run12MdyToYmd(cd); break;
            case 13: run13YmdToMdy(cd); break;
            case 14: run14DifMdy(cd); break;
            case 15: run15DifJul30(cd); break;
            case 16: run16DifMdy30(cd); break;
            case 17: run17AddMdy(cd); break;
            case 18: run18YmdToCymd(cd); break;
            case 19: run19DifCymd(cd); break;
            case 20: run20AddCymd(cd); break;
            case 21: run21AddMonthsToYmd(cd); break;
            case 22: run22AddMonthsToCymd(cd); break;
            case 23: run23JulToCymd(cd); break;
            case 24: run24CymdToJul(cd); break;
            case 25: run25CymdToInt(cd); break;
            case 26: run26IntToCymd(cd); break;
            case 27: run27MdyToMdcy(cd); break;
            case 28: run28DifJulNoCheck(cd); break;
            case 31: run31JulToInt(cd); break;
            case 32: run32IntToJul(cd); break;
            case 33: run33YmdToInt(cd); break;
            case 34: run34IntToYmd(cd); break;
            case 35: run35MdyToInt(cd); break;
            case 36: run36IntToMdy(cd); break;
            case 37: run37DifFy(cd); break;
            case 38: run38RangeJul(cd); break;
            case 39: run39RangeYmd(cd); break;
            case 40: run40RangeMdy(cd); break;
            case 41: run41AddMonthsToMdy(cd); break;
            case 42: run42AddMonthsEndJul(cd); break;
            default:
                cd.dateErrInd = "Y";
                cd.dateErrReason = 1; // JDN-Con-BadAction
        }
        return cd;
    }

    // -- Validators (codes 1, 9) ---------------------------------------
    private void run01CheckCymdDt(ConvDates cd) {
        applyStatus(cd, intOfDate(cd.fromCymdDt).status);
    }

    private void run09CheckMdyDt(ConvDates cd) {
        applyStatus(cd, intOfDate(mdyToCymd(cd.fromMdyDt, false)).status);
    }

    // -- YMD <-> Julian (codes 2, 3) -----------------------------------
    private void run02YmdToJul(ConvDates cd) {
        int cymd = ymdToCymd(cd.fromYmdDt, true);
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) { cd.toJulDt = 0; applyStatus(cd, r.status); return; }
        int yyyy = splitDateInt(cymd)[0];
        LocalDate d = LocalDate.of(yyyy, (cd.fromYmdDt / 100) % 100, cd.fromYmdDt % 100);
        int ddd = d.getDayOfYear();
        cd.fromIntDt = jdnInt(d);
        cd.toJulDt = (yyyy % 100) * 1000 + ddd;
        cd.dateErrInd = "N";
    }

    private void run03JulToYmd(ConvDates cd) {
        DayRes r = intOfDay(cd.fromJulDt);
        if (!r.status.equals(STATUS_OK)) { cd.toYmdDt = 0; applyStatus(cd, r.status); return; }
        cd.fromIntDt = r.value;
        LocalDate d = dateFromJdn(r.value);
        cd.toYmdDt = (d.getYear() % 100) * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.dateErrInd = "N";
    }

    // -- MDY <-> Julian (codes 10, 11) ---------------------------------
    private void run10MdyToJul(ConvDates cd) {
        int cymd = mdyToCymd(cd.fromMdyDt, true);
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) { cd.toJulDt = 0; applyStatus(cd, r.status); return; }
        int[] p = splitDateInt(cymd);
        LocalDate d = LocalDate.of(p[0], p[1], p[2]);
        cd.fromIntDt = jdnInt(d);
        cd.toJulDt = (p[0] % 100) * 1000 + d.getDayOfYear();
        cd.dateErrInd = "N";
    }

    private void run11JulToMdy(ConvDates cd) {
        DayRes r = intOfDay(cd.fromJulDt);
        if (!r.status.equals(STATUS_OK)) { cd.toMdyDt = 0; applyStatus(cd, r.status); return; }
        cd.fromIntDt = r.value;
        LocalDate d = dateFromJdn(r.value);
        cd.toMdyDt = d.getMonthValue() * 10000 + d.getDayOfMonth() * 100 + (d.getYear() % 100);
        cd.dateErrInd = "N";
    }

    // -- MDY <-> YMD / MDCY (codes 12, 13, 27) -------------------------
    private void run12MdyToYmd(ConvDates cd) {
        int[] p = splitDateInt(cd.fromMdyDt);
        int mm = p[0], dd = p[1], yy = p[2];
        cd.toYmdDt = yy * 10000 + mm * 100 + dd;
        cd.dateErrInd = "N";
    }

    private void run13YmdToMdy(ConvDates cd) {
        int[] p = splitDateInt(cd.fromYmdDt);
        int yy = p[0], mm = p[1], dd = p[2];
        cd.toMdyDt = mm * 10000 + dd * 100 + yy;
        cd.dateErrInd = "N";
    }

    private void run27MdyToMdcy(ConvDates cd) {
        int cymd = mdyToCymd(cd.fromMdyDt, false);
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) { cd.toMdcyDt = 0; applyStatus(cd, r.status); return; }
        int[] p = splitDateInt(cymd);
        cd.toMdcyDt = p[1] * 1000000 + p[2] * 10000 + p[0];
        cd.dateErrInd = "N";
    }

    // -- YMD <-> CYMD (code 18) ----------------------------------------
    private void run18YmdToCymd(ConvDates cd) {
        int cymd = ymdToCymd(cd.fromYmdDt, false);
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) { cd.toCymdDt = 0; applyStatus(cd, r.status); return; }
        cd.toCymdDt = cymd;
        cd.dateErrInd = "N";
    }

    // -- Julian <-> CYMD (codes 23, 24) --------------------------------
    private void run23JulToCymd(ConvDates cd) {
        DayRes r = intOfDay(cd.fromJulDt);
        if (!r.status.equals(STATUS_OK)) {
            cd.toCymdDt = 0; cd.toYmdDt = 0; applyStatus(cd, r.status); return;
        }
        cd.fromIntDt = r.value;
        LocalDate d = dateFromJdn(r.value);
        cd.toCymdDt = d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.toYmdDt = (d.getYear() % 100) * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.dateErrInd = "N";
    }

    private void run24CymdToJul(ConvDates cd) {
        IntRes r = intOfDate(cd.fromCymdDt);
        if (!r.status.equals(STATUS_OK)) { cd.toJulDt = 0; applyStatus(cd, r.status); return; }
        cd.fromIntDt = r.value;
        int[] p = splitDateInt(cd.fromCymdDt);
        LocalDate d = LocalDate.of(p[0], p[1], p[2]);
        cd.toJulDt = (p[0] % 100) * 1000 + d.getDayOfYear();
        cd.dateErrInd = "N";
    }

    // -- CYMD <-> INT (codes 25, 26) -----------------------------------
    private void run25CymdToInt(ConvDates cd) {
        IntRes r = intOfDate(cd.fromCymdDt);
        if (!r.status.equals(STATUS_OK)) { cd.toIntDt = 0; applyStatus(cd, r.status); return; }
        cd.toIntDt = r.value;
        cd.dateErrInd = "N";
    }

    private void run26IntToCymd(ConvDates cd) {
        DateRes r = dateOfInt(cd.fromIntDt);
        if (!r.status.equals(STATUS_OK) || r.date.getYear() < 1953) {
            cd.toCymdDt = 0; cd.dateErrInd = "Y"; cd.dateErrReason = 11; return;
        }
        LocalDate d = r.date;
        cd.toCymdDt = d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.dateErrInd = "N";
    }

    // -- INT <-> Julian/YMD/MDY (codes 31..36) -------------------------
    private void run31JulToInt(ConvDates cd) {
        DayRes r = intOfDay(cd.fromJulDt);
        if (!r.status.equals(STATUS_OK)) { cd.toIntDt = 0; applyStatus(cd, r.status); return; }
        cd.toIntDt = r.value;
        cd.dateErrInd = "N";
    }

    private void run32IntToJul(ConvDates cd) {
        DateRes r = dateOfInt(cd.fromIntDt);
        if (!r.status.equals(STATUS_OK) || r.date.getYear() < 1953) {
            cd.toJulDt = 0; cd.dateErrInd = "Y"; cd.dateErrReason = 11; return;
        }
        LocalDate d = r.date;
        cd.toJulDt = (d.getYear() % 100) * 1000 + d.getDayOfYear();
        cd.dateErrInd = "N";
    }

    private void run33YmdToInt(ConvDates cd) {
        int cymd = ymdToCymd(cd.fromYmdDt, true);
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) { cd.toIntDt = 0; applyStatus(cd, r.status); return; }
        cd.toIntDt = r.value;
        cd.dateErrInd = "N";
    }

    private void run34IntToYmd(ConvDates cd) {
        DateRes r = dateOfInt(cd.fromIntDt);
        if (!r.status.equals(STATUS_OK) || r.date.getYear() < 1953) {
            cd.toYmdDt = 0; cd.dateErrInd = "Y"; cd.dateErrReason = 11; return;
        }
        LocalDate d = r.date;
        cd.toYmdDt = (d.getYear() % 100) * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.dateErrInd = "N";
    }

    private void run35MdyToInt(ConvDates cd) {
        int cymd = mdyToCymd(cd.fromMdyDt, true);
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) { cd.toIntDt = 0; applyStatus(cd, r.status); return; }
        cd.toIntDt = r.value;
        cd.dateErrInd = "N";
    }

    private void run36IntToMdy(ConvDates cd) {
        DateRes r = dateOfInt(cd.fromIntDt);
        if (!r.status.equals(STATUS_OK) || r.date.getYear() < 1953) {
            cd.toMdyDt = 0; cd.dateErrInd = "Y"; cd.dateErrReason = 11; return;
        }
        LocalDate d = r.date;
        cd.toMdyDt = d.getMonthValue() * 10000 + d.getDayOfMonth() * 100 + (d.getYear() % 100);
        cd.dateErrInd = "N";
    }

    // -- DIF family (codes 4, 5, 14, 19, 37) ---------------------------
    private void run04DifJul(ConvDates cd) {
        DayRes a = intOfDay(cd.fromJulDt);
        DayRes b = intOfDay(cd.toJulDt);
        setDif(cd, a.status, b.status, a.value, b.value);
    }

    private void run05DifYmd(ConvDates cd) {
        IntRes a = intOfDate(ymdToCymd(cd.fromYmdDt, true));
        IntRes b = intOfDate(ymdToCymd(cd.toYmdDt, true));
        setDif(cd, a.status, b.status, a.value, b.value);
    }

    private void run14DifMdy(ConvDates cd) {
        IntRes a = intOfDate(mdyToCymd(cd.fromMdyDt, true));
        IntRes b = intOfDate(mdyToCymd(cd.toMdyDt, true));
        setDif(cd, a.status, b.status, a.value, b.value);
    }

    private void run19DifCymd(ConvDates cd) {
        IntRes a = intOfDate(cd.fromCymdDt);
        IntRes b = intOfDate(cd.toCymdDt);
        setDif(cd, a.status, b.status, a.value, b.value);
    }

    private void run28DifJulNoCheck(ConvDates cd) {
        int a = noCheckIntOfDay(cd.fromJulDt);
        int b = noCheckIntOfDay(cd.toJulDt);
        cd.fromIntDt = a;
        cd.toIntDt = b;
        cd.daysDif = b - a;
        cd.dateErrInd = "N";
        cd.dateErrReason = 0;
    }

    private void run37DifFy(ConvDates cd) {
        int fromYy = cd.fromYmdDt / 10000;
        int toYy = cd.toYmdDt / 10000;
        int fromYyyy = ccInferred(fromYy) * 100 + fromYy;
        int toYyyy = ccInferred(toYy) * 100 + toYy;
        cd.fromCymdDt = fromYyyy * 10000;
        cd.toCymdDt = toYyyy * 10000;
        cd.daysDif = toYyyy - fromYyyy;
        cd.dateErrInd = "N";
        cd.dateErrReason = 0;
    }

    // -- 30-day-month DIF (codes 6, 15, 16) ----------------------------
    private void run06DifCymd30(ConvDates cd) {
        IntRes a = difCymd30Int(cd.fromCymdDt);
        IntRes b = difCymd30Int(cd.toCymdDt);
        setDif(cd, a.status, b.status, a.value, b.value);
        if (b.status.equals(STATUS_OK)) {
            int[] p = splitDateInt(cd.toCymdDt);
            try {
                LocalDate dj = LocalDate.of(p[0], p[1], p[2]);
                cd.toJulDt = (p[0] % 100) * 1000 + dj.getDayOfYear();
            } catch (DateTimeException e) {
                cd.toJulDt = 0;
            }
        }
    }

    private void run15DifJul30(ConvDates cd) {
        IntRes a = difJul30Int(cd.fromJulDt);
        IntRes b = difJul30Int(cd.toJulDt);
        setDif(cd, a.status, b.status, a.value, b.value);
    }

    private void run16DifMdy30(ConvDates cd) {
        int cymdTo = mdyToCymd(cd.toMdyDt, true);
        IntRes a = difCymd30Int(mdyToCymd(cd.fromMdyDt, true));
        IntRes b = difCymd30Int(cymdTo);
        setDif(cd, a.status, b.status, a.value, b.value);
        if (b.status.equals(STATUS_OK)) {
            int[] p = splitDateInt(cymdTo);
            try {
                LocalDate dj = LocalDate.of(p[0], p[1], p[2]);
                cd.toJulDt = (p[0] % 100) * 1000 + dj.getDayOfYear();
            } catch (DateTimeException e) {
                cd.toJulDt = 0;
            }
        }
    }

    // -- ADD family (codes 7, 8, 17, 20) -------------------------------
    private void run07AddJul(ConvDates cd) {
        DayRes r = intOfDay(cd.fromJulDt);
        if (!r.status.equals(STATUS_OK)) { cd.toJulDt = 0; applyStatus(cd, r.status); return; }
        cd.fromIntDt = r.value + cd.daysDif;
        DateRes r2 = dateOfInt(cd.fromIntDt);
        if (!r2.status.equals(STATUS_OK)) { cd.toJulDt = 0; applyStatus(cd, r2.status); return; }
        LocalDate d = r2.date;
        cd.toJulDt = (d.getYear() % 100) * 1000 + d.getDayOfYear();
        cd.dateErrInd = "N";
    }

    private void run08AddYmd(ConvDates cd) {
        IntRes r = intOfDate(ymdToCymd(cd.fromYmdDt, true));
        if (!r.status.equals(STATUS_OK)) { cd.toYmdDt = 0; applyStatus(cd, r.status); return; }
        cd.fromIntDt = r.value + cd.daysDif;
        DateRes r2 = dateOfInt(cd.fromIntDt);
        if (!r2.status.equals(STATUS_OK)) { cd.toYmdDt = 0; applyStatus(cd, r2.status); return; }
        LocalDate d = r2.date;
        cd.toYmdDt = (d.getYear() % 100) * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.dateErrInd = "N";
    }

    private void run17AddMdy(ConvDates cd) {
        IntRes r = intOfDate(mdyToCymd(cd.fromMdyDt, true));
        if (!r.status.equals(STATUS_OK)) { cd.toMdyDt = 0; applyStatus(cd, r.status); return; }
        cd.fromIntDt = r.value + cd.daysDif;
        DateRes r2 = dateOfInt(cd.fromIntDt);
        if (!r2.status.equals(STATUS_OK)) { cd.toMdyDt = 0; applyStatus(cd, r2.status); return; }
        LocalDate d = r2.date;
        cd.toMdyDt = d.getMonthValue() * 10000 + d.getDayOfMonth() * 100 + (d.getYear() % 100);
        cd.dateErrInd = "N";
    }

    private void run20AddCymd(ConvDates cd) {
        IntRes r = intOfDate(cd.fromCymdDt);
        if (!r.status.equals(STATUS_OK)) {
            cd.toCymdDt = 0; cd.toYmdDt = 0; applyStatus(cd, r.status); return;
        }
        cd.fromIntDt = r.value + cd.daysDif;
        DateRes r2 = dateOfInt(cd.fromIntDt);
        if (!r2.status.equals(STATUS_OK)) {
            cd.toCymdDt = 0; cd.toYmdDt = 0; applyStatus(cd, r2.status); return;
        }
        LocalDate d = r2.date;
        cd.toCymdDt = d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.toYmdDt = (d.getYear() % 100) * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.dateErrInd = "N";
    }

    // -- ADD-MONTHS family (codes 21, 22, 41, 42) ----------------------
    private void run21AddMonthsToYmd(ConvDates cd) {
        AddRes r = addMonthsCymd(ymdToCymd(cd.fromYmdDt, true), cd.monthsToAdd, false);
        if (!r.status.equals(STATUS_OK)) { cd.toYmdDt = 0; applyStatus(cd, r.status); return; }
        cd.toYmdDt = (r.y % 100) * 10000 + r.m * 100 + r.d;
        cd.dateErrInd = "N";
    }

    private void run22AddMonthsToCymd(ConvDates cd) {
        AddRes r = addMonthsCymd(cd.fromCymdDt, cd.monthsToAdd, false);
        if (!r.status.equals(STATUS_OK)) { cd.toCymdDt = 0; applyStatus(cd, r.status); return; }
        cd.toCymdDt = r.y * 10000 + r.m * 100 + r.d;
        cd.dateErrInd = "N";
    }

    private void run41AddMonthsToMdy(ConvDates cd) {
        AddRes r = addMonthsCymd(mdyToCymd(cd.fromMdyDt, true), cd.monthsToAdd, false);
        if (!r.status.equals(STATUS_OK)) { cd.toMdyDt = 0; applyStatus(cd, r.status); return; }
        cd.toMdyDt = r.m * 10000 + r.d * 100 + (r.y % 100);
        cd.dateErrInd = "N";
    }

    private void run42AddMonthsEndJul(ConvDates cd) {
        DayRes r = intOfDay(cd.fromJulDt);
        if (!r.status.equals(STATUS_OK)) {
            cd.toJulDt = 0; cd.toCymdDt = 0; cd.toYmdDt = 0; applyStatus(cd, r.status); return;
        }
        LocalDate d = dateFromJdn(r.value);
        cd.toCymdDt = d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        cd.toYmdDt = (d.getYear() % 100) * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
        boolean snapToEom = (d.getDayOfMonth() == daysInMonth(d.getYear(), d.getMonthValue()));
        AddRes r2 = addMonthsCymd(
            d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth(),
            cd.monthsToAdd, snapToEom);
        if (!r2.status.equals(STATUS_OK)) { cd.toJulDt = 0; applyStatus(cd, r2.status); return; }
        LocalDate dt2 = LocalDate.of(r2.y, r2.m, r2.d);
        cd.toJulDt = (r2.y % 100) * 1000 + dt2.getDayOfYear();
        cd.dateErrInd = "N";
    }

    // -- RANGE family (codes 38, 39, 40) -------------------------------
    private void run38RangeJul(ConvDates cd) {
        DayRes a = intOfDay(cd.fromJulDt);
        DayRes b = intOfDay(cd.toJulDt);
        DayRes c = intOfDay(cd.betweenJulDt);
        rangeCheck(cd, a.value, b.value, c.value, a.status, b.status, c.status);
    }

    private void run39RangeYmd(ConvDates cd) {
        IntRes a = intOfDate(ymdToCymd(cd.fromYmdDt, true));
        IntRes b = intOfDate(ymdToCymd(cd.toYmdDt, true));
        IntRes c = intOfDate(ymdToCymd(cd.betweenYmdDt, true));
        rangeCheck(cd, a.value, b.value, c.value, a.status, b.status, c.status);
    }

    private void run40RangeMdy(ConvDates cd) {
        IntRes a = intOfDate(mdyToCymd(cd.fromMdyDt, true));
        IntRes b = intOfDate(mdyToCymd(cd.toMdyDt, true));
        IntRes c = intOfDate(mdyToCymd(cd.betweenMdyDt, true));
        rangeCheck(cd, a.value, b.value, c.value, a.status, b.status, c.status);
    }

    // ------------------------------------------------------------------
    // Internal helpers (status mapping + 30-day-month math + ADD-MONTHS).
    // ------------------------------------------------------------------
    private static void applyStatus(ConvDates cd, String status) {
        if (status.equals(STATUS_OK)) {
            cd.dateErrInd = "N";
            cd.dateErrReason = 0;
        } else {
            cd.dateErrInd = "Y";
            cd.dateErrReason = REASON.getOrDefault(status, 12);
        }
    }

    private static void setDif(ConvDates cd, String s1, String s2, int a, int b) {
        if (!s1.equals(STATUS_OK) || !s2.equals(STATUS_OK)) {
            cd.daysDif = 0;
            cd.dateErrInd = "Y";
            cd.dateErrReason = REASON.getOrDefault(!s1.equals(STATUS_OK) ? s1 : s2, 12);
            return;
        }
        cd.fromIntDt = a;
        cd.daysDif = b - a;
        cd.dateErrInd = "N";
    }

    private static void rangeCheck(ConvDates cd, int a, int b, int c,
                                   String s1, String s2, String s3) {
        if (!s1.equals(STATUS_OK)) {
            cd.daysDif = 0; cd.dateErrInd = "Y"; cd.dateErrReason = REASON.getOrDefault(s1, 12);
            return;
        }
        cd.fromIntDt = a;
        if (!s2.equals(STATUS_OK)) {
            cd.daysDif = 0; cd.dateErrInd = "Y"; cd.dateErrReason = REASON.getOrDefault(s2, 12);
            return;
        }
        cd.toIntDt = b;
        int between = s3.equals(STATUS_OK) ? c : 0;
        cd.daysDif = (a <= between && between <= b) ? 88888 : 77777;
        cd.dateErrInd = "N";
    }

    private static int daysInMonth(int yyyy, int mm) {
        return LocalDate.of(yyyy, mm, 1).lengthOfMonth();
    }

    private static AddRes addMonthsCymd(int cymd, int months, boolean forceEom) {
        IntRes r = intOfDate(cymd);
        if (!r.status.equals(STATUS_OK)) return new AddRes(r.status, 0, 0, 0);
        int[] p = splitDateInt(cymd);
        int yyyy = p[0], mm = p[1], dd = p[2];
        int total = (yyyy * 12 + (mm - 1)) + months;
        int newY = Math.floorDiv(total, 12);
        int newM = Math.floorMod(total, 12) + 1;
        if (newY < 1601) return new AddRes(STATUS_OOR_YYYY, 0, 0, 0);
        int eom = daysInMonth(newY, newM);
        int newD = (dd > eom || forceEom) ? eom : dd;
        return new AddRes(STATUS_OK, newY, newM, newD);
    }

    private static int noCheckIntOfDay(int julYyddd) {
        int[] p = splitJul(julYyddd);
        int yy = p[0], ddd = p[1];
        int yyyy = ccInferredJdn(yy) * 100 + yy;
        if (julYyddd == 0) return 0;
        return jdnInt(LocalDate.of(yyyy, 1, 1)) + ddd - 1;
    }

    private static IntRes difCymd30Int(int cymd) {
        int[] p = splitDateInt(cymd);
        int yyyy = p[0], mm = p[1], dd = p[2];
        if (yyyy < 1601) return new IntRes(STATUS_OOR_YYYY, 0);
        if (mm < 1 || mm > 12) return new IntRes(STATUS_OOR_MM, 0);
        if (dd < 1 || dd > 31) return new IntRes(STATUS_OOR_DD, 0);
        int ddCapped = Math.min(dd, 30);
        return new IntRes(STATUS_OK, yyyy * 360 + (mm - 1) * 30 + (ddCapped - 1));
    }

    private static final int[] CUMDAYS_COMMON =
        {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365};
    private static final int[] CUMDAYS_LEAP =
        {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366};

    private static boolean isLeap(int y) {
        return (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0);
    }

    private static int[] cumulativeDays(int yyyy) {
        return isLeap(yyyy) ? CUMDAYS_LEAP : CUMDAYS_COMMON;
    }

    private static IntRes difJul30Int(int jul) {
        int[] p = splitJul(jul);
        int yy = p[0], ddd = p[1];
        int yyyy = ccInferred(yy) * 100 + yy;
        if (yyyy < 1601) return new IntRes(STATUS_OOR_YYYY, 0);
        if (ddd < 1 || ddd > 366) return new IntRes(STATUS_OOR_DDD, 0);
        boolean leap = isLeap(yyyy);
        if (ddd > (leap ? 366 : 365)) return new IntRes(STATUS_OOR_DDD, 0);
        int[] eomReal = cumulativeDays(yyyy);
        int mm = 1;
        while (mm <= 12 && ddd > eomReal[mm]) {
            mm += 1;
        }
        if (mm > 12) return new IntRes(STATUS_OOR_DDD, 0);
        int ddReal = ddd - eomReal[mm - 1];
        int dd30 = Math.min(ddReal, 30);
        return new IntRes(STATUS_OK, yyyy * 360 + (mm - 1) * 30 + (dd30 - 1));
    }

    // ------------------------------------------------------------------
    // Module-level convenience functions (mirror dateconv.py top-level fns).
    // ------------------------------------------------------------------
    private static final DateConv ENGINE = new DateConv();

    private static String statusOf(ConvDates cd) {
        if (cd.dateErrInd.equals("N")) return STATUS_OK;
        return REASON_REV.getOrDefault(cd.dateErrReason, STATUS_STRANGE);
    }

    private static Integer toInt(String v) {
        if (v == null) return null;
        if (hasNDigits(v, v.length()) && v.length() > 0) {
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static boolean hasNDigits(String v, int n) {
        if (v == null || v.length() != n) return false;
        for (int i = 0; i < v.length(); i++) {
            if (!Character.isDigit(v.charAt(i))) return false;
        }
        return true;
    }

    public static StatusBool checkCymdDt(String yyyymmdd) {
        if (!hasNDigits(yyyymmdd, 8)) return new StatusBool(STATUS_STRANGE, false);
        ConvDates cd = new ConvDates();
        cd.fromCymdDt = Integer.parseInt(yyyymmdd);
        ENGINE.dispatch(1, cd);
        String s = statusOf(cd);
        return new StatusBool(s, s.equals(STATUS_OK));
    }

    public static StatusBool checkCymdDt(int yyyymmdd) {
        return checkCymdDt(String.format("%08d", yyyymmdd));
    }

    public static StatusBool checkMdyDt(String mmddyy) {
        if (!hasNDigits(mmddyy, 6)) return new StatusBool(STATUS_STRANGE, false);
        ConvDates cd = new ConvDates();
        cd.fromMdyDt = Integer.parseInt(mmddyy);
        ENGINE.dispatch(9, cd);
        String s = statusOf(cd);
        return new StatusBool(s, s.equals(STATUS_OK));
    }

    private static int parseOrZero(String v) {
        Integer i = toInt(v);
        return i != null ? i : 0;
    }

    private static StatusInt dispatch1(int func, java.util.function.Consumer<ConvDates> setup,
                                       java.util.function.ToIntFunction<ConvDates> read) {
        ConvDates cd = new ConvDates();
        setup.accept(cd);
        ENGINE.dispatch(func, cd);
        return new StatusInt(statusOf(cd), read.applyAsInt(cd));
    }

    public static StatusInt ymdToJul(String yymmdd) {
        return dispatch1(2, cd -> cd.fromYmdDt = parseOrZero(yymmdd), cd -> cd.toJulDt);
    }

    public static StatusInt julToYmd(String yyddd) {
        return dispatch1(3, cd -> cd.fromJulDt = parseOrZero(yyddd), cd -> cd.toYmdDt);
    }

    public static StatusInt mdyToJul(String mmddyy) {
        return dispatch1(10, cd -> cd.fromMdyDt = parseOrZero(mmddyy), cd -> cd.toJulDt);
    }

    public static StatusInt julToMdy(String yyddd) {
        return dispatch1(11, cd -> cd.fromJulDt = parseOrZero(yyddd), cd -> cd.toMdyDt);
    }

    public static StatusInt mdyToYmd(String mmddyy) {
        return dispatch1(12, cd -> cd.fromMdyDt = parseOrZero(mmddyy), cd -> cd.toYmdDt);
    }

    public static StatusInt mdyToMdcy(String mmddyy) {
        return dispatch1(27, cd -> cd.fromMdyDt = parseOrZero(mmddyy), cd -> cd.toMdcyDt);
    }

    public static StatusInt ymdToMdy(String yymmdd) {
        return dispatch1(13, cd -> cd.fromYmdDt = parseOrZero(yymmdd), cd -> cd.toMdyDt);
    }

    public static StatusInt ymdToCymd(String yymmdd) {
        return dispatch1(18, cd -> cd.fromYmdDt = parseOrZero(yymmdd), cd -> cd.toCymdDt);
    }

    public static StatusInt julToCymd(String yyddd) {
        return dispatch1(23, cd -> cd.fromJulDt = parseOrZero(yyddd), cd -> cd.toCymdDt);
    }

    public static StatusInt cymdToJul(String yyyymmdd) {
        return dispatch1(24, cd -> cd.fromCymdDt = parseOrZero(yyyymmdd), cd -> cd.toJulDt);
    }

    public static StatusInt cymdToInt(String yyyymmdd) {
        return dispatch1(25, cd -> cd.fromCymdDt = parseOrZero(yyyymmdd), cd -> cd.toIntDt);
    }

    public static StatusInt intToCymd(int n) {
        return dispatch1(26, cd -> cd.fromIntDt = n, cd -> cd.toCymdDt);
    }

    public static StatusInt julToInt(String yyddd) {
        return dispatch1(31, cd -> cd.fromJulDt = parseOrZero(yyddd), cd -> cd.toIntDt);
    }

    public static StatusInt intToJul(int n) {
        return dispatch1(32, cd -> cd.fromIntDt = n, cd -> cd.toJulDt);
    }

    public static StatusInt ymdToInt(String yymmdd) {
        return dispatch1(33, cd -> cd.fromYmdDt = parseOrZero(yymmdd), cd -> cd.toIntDt);
    }

    public static StatusInt intToYmd(int n) {
        return dispatch1(34, cd -> cd.fromIntDt = n, cd -> cd.toYmdDt);
    }

    public static StatusInt mdyToInt(String mmddyy) {
        return dispatch1(35, cd -> cd.fromMdyDt = parseOrZero(mmddyy), cd -> cd.toIntDt);
    }

    public static StatusInt intToMdy(int n) {
        return dispatch1(36, cd -> cd.fromIntDt = n, cd -> cd.toMdyDt);
    }

    public static StatusInt difJul(String fromYyddd, String thruYyddd) {
        return dispatch1(4, cd -> { cd.fromJulDt = parseOrZero(fromYyddd); cd.toJulDt = parseOrZero(thruYyddd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difJulNoCheck(String fromYyddd, String thruYyddd) {
        return dispatch1(28, cd -> { cd.fromJulDt = parseOrZero(fromYyddd); cd.toJulDt = parseOrZero(thruYyddd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difYmd(String fromYymmdd, String thruYymmdd) {
        return dispatch1(5, cd -> { cd.fromYmdDt = parseOrZero(fromYymmdd); cd.toYmdDt = parseOrZero(thruYymmdd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difMdy(String fromMmddyy, String thruMmddyy) {
        return dispatch1(14, cd -> { cd.fromMdyDt = parseOrZero(fromMmddyy); cd.toMdyDt = parseOrZero(thruMmddyy); },
            cd -> cd.daysDif);
    }

    public static StatusInt difCymd(String fromYyyymmdd, String thruYyyymmdd) {
        return dispatch1(19, cd -> { cd.fromCymdDt = parseOrZero(fromYyyymmdd); cd.toCymdDt = parseOrZero(thruYyyymmdd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difFy(String fromYymmdd, String thruYymmdd) {
        return dispatch1(37, cd -> { cd.fromYmdDt = parseOrZero(fromYymmdd); cd.toYmdDt = parseOrZero(thruYymmdd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difJul30(String fromYyddd, String thruYyddd) {
        return dispatch1(15, cd -> { cd.fromJulDt = parseOrZero(fromYyddd); cd.toJulDt = parseOrZero(thruYyddd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difCymd30(String fromYyyymmdd, String thruYyyymmdd) {
        return dispatch1(6, cd -> { cd.fromCymdDt = parseOrZero(fromYyyymmdd); cd.toCymdDt = parseOrZero(thruYyyymmdd); },
            cd -> cd.daysDif);
    }

    public static StatusInt difMdy30(String fromMmddyy, String thruMmddyy) {
        return dispatch1(16, cd -> { cd.fromMdyDt = parseOrZero(fromMmddyy); cd.toMdyDt = parseOrZero(thruMmddyy); },
            cd -> cd.daysDif);
    }

    public static StatusInt addJul(String fromYyddd, int days) {
        return dispatch1(7, cd -> { cd.fromJulDt = parseOrZero(fromYyddd); cd.daysDif = days; }, cd -> cd.toJulDt);
    }

    public static StatusInt addYmd(String fromYymmdd, int days) {
        return dispatch1(8, cd -> { cd.fromYmdDt = parseOrZero(fromYymmdd); cd.daysDif = days; }, cd -> cd.toYmdDt);
    }

    public static StatusInt addMdy(String fromMmddyy, int days) {
        return dispatch1(17, cd -> { cd.fromMdyDt = parseOrZero(fromMmddyy); cd.daysDif = days; }, cd -> cd.toMdyDt);
    }

    public static StatusInt addCymd(String fromYyyymmdd, int days) {
        return dispatch1(20, cd -> { cd.fromCymdDt = parseOrZero(fromYyyymmdd); cd.daysDif = days; }, cd -> cd.toCymdDt);
    }

    public static StatusInt addMonthsToYmd(String fromYymmdd, int months) {
        return dispatch1(21, cd -> { cd.fromYmdDt = parseOrZero(fromYymmdd); cd.monthsToAdd = months; }, cd -> cd.toYmdDt);
    }

    public static StatusInt addMonthsToCymd(String fromYyyymmdd, int months) {
        return dispatch1(22, cd -> { cd.fromCymdDt = parseOrZero(fromYyyymmdd); cd.monthsToAdd = months; }, cd -> cd.toCymdDt);
    }

    public static StatusInt addMonthsToMdy(String fromMmddyy, int months) {
        return dispatch1(41, cd -> { cd.fromMdyDt = parseOrZero(fromMmddyy); cd.monthsToAdd = months; }, cd -> cd.toMdyDt);
    }

    public static StatusInt addMonthsEndJul(String fromYyddd, int months) {
        return dispatch1(42, cd -> { cd.fromJulDt = parseOrZero(fromYyddd); cd.monthsToAdd = months; }, cd -> cd.toJulDt);
    }

    public static StatusInt rangeJul(String fromYyddd, String thruYyddd, String betweenYyddd) {
        return dispatch1(38, cd -> {
            cd.fromJulDt = parseOrZero(fromYyddd);
            cd.toJulDt = parseOrZero(thruYyddd);
            cd.betweenJulDt = parseOrZero(betweenYyddd);
        }, cd -> cd.daysDif);
    }

    public static StatusInt rangeYmd(String fromYymmdd, String thruYymmdd, String betweenYymmdd) {
        return dispatch1(39, cd -> {
            cd.fromYmdDt = parseOrZero(fromYymmdd);
            cd.toYmdDt = parseOrZero(thruYymmdd);
            cd.betweenYmdDt = parseOrZero(betweenYymmdd);
        }, cd -> cd.daysDif);
    }

    public static StatusInt rangeMdy(String fromMmddyy, String thruMmddyy, String betweenMmddyy) {
        return dispatch1(40, cd -> {
            cd.fromMdyDt = parseOrZero(fromMmddyy);
            cd.toMdyDt = parseOrZero(thruMmddyy);
            cd.betweenMdyDt = parseOrZero(betweenMmddyy);
        }, cd -> cd.daysDif);
    }
}
