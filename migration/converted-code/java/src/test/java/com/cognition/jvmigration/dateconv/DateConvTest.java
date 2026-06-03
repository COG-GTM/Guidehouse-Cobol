package com.cognition.jvmigration.dateconv;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.cognition.jvmigration.dateconv.DateConv.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit 5 mirror of
 * {@code migration/converted-code/python/tests/test_dateconv.py}. Covers all 40
 * active DATESUB-FUNC entry paragraphs (codes 1..42 minus 29/30), dispatch
 * behavior, edge cases, and round-trips. Same assertions as the pytest suite so
 * a reviewer can diff them directly.
 */
class DateConvTest {

    private static void assertStatusValue(String expectedStatus, int expectedValue, DateConv.StatusInt r) {
        assertEquals(expectedStatus, r.status);
        assertEquals(expectedValue, r.value);
    }

    @Nested
    class CheckCYMD {
        @Test void validDate() {
            assertEquals(new StatusBool(STATUS_OK, true), checkCymdDt("20251230"));
        }
        @Test void invalidMonth() { assertEquals(STATUS_OOR_MM, checkCymdDt("20251345").status); }
        @Test void invalidDay() { assertEquals(STATUS_OOR_DD, checkCymdDt("20240230").status); }
        @Test void yearBelow1601() { assertEquals(STATUS_OOR_YYYY, checkCymdDt("16000101").status); }
        @Test void leap2000Rule400() {
            assertEquals(new StatusBool(STATUS_OK, true), checkCymdDt("20000229"));
        }
        @Test void nonLeap1900Rule100() { assertEquals(STATUS_OOR_DD, checkCymdDt("19000229").status); }
        @Test void leap2024() { assertEquals(new StatusBool(STATUS_OK, true), checkCymdDt("20240229")); }
        @Test void nonDigit() { assertEquals(STATUS_STRANGE, checkCymdDt("ABCDEFGH").status); }
        @Test void wrongLength() { assertEquals(STATUS_STRANGE, checkCymdDt("2025123").status); }
        @Test void intInput() { assertEquals(new StatusBool(STATUS_OK, true), checkCymdDt(20251230)); }
    }

    @Nested
    class CheckMDY {
        @Test void validMdy() { assertEquals(new StatusBool(STATUS_OK, true), checkMdyDt("022924")); }
        @Test void nonLeap() { assertFalse(checkMdyDt("022925").valid); }
        @Test void centuryInferenceLe52() { assertEquals(new StatusBool(STATUS_OK, true), checkMdyDt("010100")); }
        @Test void centuryInferenceGt52() { assertEquals(new StatusBool(STATUS_OK, true), checkMdyDt("010153")); }
    }

    @Nested
    class YmdJul {
        @Test void ymdToJulJan1() { assertStatusValue(STATUS_OK, 24001, ymdToJul("240101")); }
        @Test void ymdToJulLeapEnd() { assertStatusValue(STATUS_OK, 24366, ymdToJul("241231")); }
        @Test void julToYmdRoundtrip() { assertStatusValue(STATUS_OK, 241231, julToYmd("24366")); }
        @Test void julToYmdInvalidDdd() { assertEquals(STATUS_OOR_DDD, julToYmd("25366").status); }
        @Test void mdyToJulC() { assertStatusValue(STATUS_OK, 24366, mdyToJul("123124")); }
        @Test void julToMdyC() { assertStatusValue(STATUS_OK, 123124, julToMdy("24366")); }
    }

    @Nested
    class FormatShuffles {
        @Test void mdyToYmdPure() { assertStatusValue(STATUS_OK, 241225, mdyToYmd("122524")); }
        @Test void ymdToMdyPure() { assertStatusValue(STATUS_OK, 122524, ymdToMdy("241225")); }
        @Test void ymdToCymdC() { assertStatusValue(STATUS_OK, 20241225, ymdToCymd("241225")); }
        @Test void ymdToCymdCentury19() { assertStatusValue(STATUS_OK, 19990401, ymdToCymd("990401")); }
        @Test void mdyToMdcyC() { assertStatusValue(STATUS_OK, 12252024, mdyToMdcy("122524")); }
    }

    @Nested
    class CymdConversions {
        @Test void julToCymdC() { assertStatusValue(STATUS_OK, 20241231, julToCymd("24366")); }
        @Test void cymdToJulC() { assertStatusValue(STATUS_OK, 24001, cymdToJul("20240101")); }
        @Test void cymdToIntEpoch() { assertStatusValue(STATUS_OK, 1, cymdToInt("16010101")); }
        @Test void intToCymdBelowCh645Rejected() { assertEquals(STATUS_OOR_YYYY, intToCymd(1).status); }
        @Test void intToCymdModern() {
            StatusInt n = cymdToInt("20240115");
            assertEquals(STATUS_OK, n.status);
            assertStatusValue(STATUS_OK, 20240115, intToCymd(n.value));
        }
        @Test void cymdIntRoundTrip() {
            for (String cymd : new String[] {"19530101", "20000229", "20240229", "20991231"}) {
                StatusInt n = cymdToInt(cymd);
                assertEquals(STATUS_OK, n.status);
                assertStatusValue(STATUS_OK, Integer.parseInt(cymd), intToCymd(n.value));
            }
        }
    }

    @Nested
    class IntConversions {
        @Test void julToIntC() { assertEquals(STATUS_OK, julToInt("24001").status); }
        @Test void intToJulRoundTrip() {
            StatusInt n = julToInt("24001");
            assertStatusValue(STATUS_OK, 24001, intToJul(n.value));
        }
        @Test void ymdToIntAndBack() {
            StatusInt n = ymdToInt("240101");
            assertEquals(STATUS_OK, n.status);
            assertStatusValue(STATUS_OK, 240101, intToYmd(n.value));
        }
        @Test void mdyToIntAndBack() {
            StatusInt n = mdyToInt("010124");
            assertEquals(STATUS_OK, n.status);
            assertStatusValue(STATUS_OK, 10124, intToMdy(n.value));
        }
    }

    @Nested
    class DifFamily {
        @Test void difJulFullYear() { assertStatusValue(STATUS_OK, 365, difJul("24001", "24366")); }
        @Test void difJulNegative() { assertStatusValue(STATUS_OK, -366, difJul("25001", "24001")); }
        @Test void difYmdC() { assertStatusValue(STATUS_OK, 31, difYmd("240101", "240201")); }
        @Test void difMdyC() { assertStatusValue(STATUS_OK, 31, difMdy("010124", "020124")); }
        @Test void difCymdC() { assertStatusValue(STATUS_OK, 366, difCymd("20240101", "20250101")); }
        @Test void difFySameYear() { assertStatusValue(STATUS_OK, 0, difFy("240101", "240901")); }
        @Test void difFyCenturyRollover() { assertStatusValue(STATUS_OK, 2, difFy("990101", "010101")); }
        @Test void difJul30FullYear() { assertStatusValue(STATUS_OK, 360, difJul30("24001", "25001")); }
        @Test void difCymd30C() { assertStatusValue(STATUS_OK, 30, difCymd30("20240115", "20240215")); }
        @Test void difMdy30C() { assertStatusValue(STATUS_OK, 30, difMdy30("011524", "021524")); }
        @Test void difJulNoCheckOverflow() {
            assertStatusValue(STATUS_OK, 369, difJulNoCheck("24001", "24370"));
        }
    }

    @Nested
    class AddFamily {
        @Test void addJulWrap() { assertStatusValue(STATUS_OK, 25001, addJul("24366", 1)); }
        @Test void addJulBackward() { assertStatusValue(STATUS_OK, 24366, addJul("25001", -1)); }
        @Test void addYmdMonthBoundary() { assertStatusValue(STATUS_OK, 240201, addYmd("240131", 1)); }
        @Test void addMdyC() { assertStatusValue(STATUS_OK, 20124, addMdy("013124", 1)); }
        @Test void addCymdYearWrap() { assertStatusValue(STATUS_OK, 20250101, addCymd("20241231", 1)); }
    }

    @Nested
    class AddMonthsFamily {
        @Test void toCymdEomSnapLeap() { assertStatusValue(STATUS_OK, 20240229, addMonthsToCymd("20240131", 1)); }
        @Test void toCymdEomSnapNonLeap() { assertStatusValue(STATUS_OK, 20230228, addMonthsToCymd("20230131", 1)); }
        @Test void toCymdYearWrap() { assertStatusValue(STATUS_OK, 20250115, addMonthsToCymd("20240115", 12)); }
        @Test void toYmd() { assertStatusValue(STATUS_OK, 240229, addMonthsToYmd("240131", 1)); }
        @Test void toMdy() { assertStatusValue(STATUS_OK, 22924, addMonthsToMdy("013124", 1)); }
        @Test void endJulForceEom() { assertStatusValue(STATUS_OK, 24060, addMonthsEndJul("24031", 1)); }
    }

    @Nested
    class RangeFamily {
        @Test void rangeJulInside() { assertStatusValue(STATUS_OK, 88888, rangeJul("24001", "24365", "24180")); }
        @Test void rangeJulOutside() { assertStatusValue(STATUS_OK, 77777, rangeJul("24001", "24180", "24365")); }
        @Test void rangeJulBoundary() { assertStatusValue(STATUS_OK, 88888, rangeJul("24001", "24365", "24001")); }
        @Test void rangeYmdInside() { assertStatusValue(STATUS_OK, 88888, rangeYmd("240101", "241231", "240615")); }
        @Test void rangeMdyOutside() { assertStatusValue(STATUS_OK, 77777, rangeMdy("010124", "063024", "070124")); }
    }

    @Nested
    class Dispatch {
        @Test void func1Dispatch() {
            ConvDates cd = new ConvDates();
            cd.fromCymdDt = 20240229;
            new DateConv().dispatch(1, cd);
            assertEquals("N", cd.dateErrInd);
            assertEquals(1, cd.datesubFunc);
        }
        @Test void func1InvalidSetsErrInd() {
            ConvDates cd = new ConvDates();
            cd.fromCymdDt = 20240230;
            new DateConv().dispatch(1, cd);
            assertEquals("Y", cd.dateErrInd);
            assertEquals(7, cd.dateErrReason); // OutOfRangeDD
        }
        @Test void func2DispatchPopulatesToJul() {
            ConvDates cd = new ConvDates();
            cd.fromYmdDt = 240101;
            new DateConv().dispatch(2, cd);
            assertEquals(24001, cd.toJulDt);
        }
        @Test void unknownFuncSetsErr() {
            ConvDates cd = new ConvDates();
            new DateConv().dispatch(999, cd);
            assertEquals("Y", cd.dateErrInd);
        }
        @Test void func29ReservedReturnsErr() {
            ConvDates cd = new ConvDates();
            new DateConv().dispatch(29, cd);
            assertEquals("Y", cd.dateErrInd);
        }
        @Test void thruAliasReadsTo() {
            ConvDates cd = new ConvDates();
            cd.toJulDt = 24180;
            assertEquals(24180, cd.thruJulDt());
            cd.thruJulDt(24365);
            assertEquals(24365, cd.toJulDt);
        }
    }

    @Nested
    class EdgeCases {
        @Test void month13Rejected() { assertEquals(STATUS_OOR_MM, checkCymdDt("20241301").status); }
        @Test void dayZeroRejected() { assertEquals(STATUS_OOR_DD, checkCymdDt("20240100").status); }
        @Test void feb30Rejected() { assertEquals(STATUS_OOR_DD, checkCymdDt("20240230").status); }
        @Test void yearZeroRejected() { assertEquals(STATUS_OOR_YYYY, checkCymdDt("00000101").status); }
        @Test void centuryRollover99To01() { assertStatusValue(STATUS_OK, 2, difFy("990101", "010101")); }
        @Test void statusCodesMirrorJdnConstants() {
            assertEquals("OK", STATUS_OK);
            assertEquals("OutOfRangeDD", STATUS_OOR_DD);
            assertEquals("OutOfRangeDDD", STATUS_OOR_DDD);
            assertEquals("OutOfRangeMM", STATUS_OOR_MM);
            assertEquals("OutOfRangeYYYY", STATUS_OOR_YYYY);
            assertEquals("Strange", STATUS_STRANGE);
        }
        @Test void invalidPropagatesThroughDif() {
            assertEquals(STATUS_OOR_DD, difCymd("20240230", "20240501").status);
        }
    }
}
