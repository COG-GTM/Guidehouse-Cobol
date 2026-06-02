# DATECONV Function Inventory

> Companion artifact to [`dependency-map.md`](./dependency-map.md), [`field-lineage.md`](./field-lineage.md), [`../migration/RISKS-AND-GAPS.md`](../migration/RISKS-AND-GAPS.md), and [`../migration/ASSUMPTIONS-AND-PLACEHOLDERS.md`](../migration/ASSUMPTIONS-AND-PLACEHOLDERS.md).
>
> This file documents the customer-supplied date-conversion subsystem received in the **2026-05-21 customer follow-up shipment**. It is the canonical reference for the 40 entry paragraphs that `LABD20.pco` (and any other caller) can invoke through `DATECONV-PD.cpy` against the `DATECONV` subprogram.

## 1. Subsystem Closure

The 2026-05-21 follow-up closed the dependency chain that originated at [`source/procobol/LABD20.pco:182` and `:531`](../source/procobol/LABD20.pco):

```
LABD20.pco
   │ COPY DATECONV-WS.       ← caller-side data contract       (was MISSING; now source/copybooks/DATECONV-WS.cpy)
   │ COPY DATECONV-PD.       ← 40 caller-side entry paragraphs (was MISSING; now source/copybooks/DATECONV-PD.cpy)
   ▼
DATECONV-PD entry paragraph    ← sets DATESUB-FUNC, CALL 'DATECONV' USING CONV-DATES
   │
   │ CALL 'DATECONV' USING CONV-DATES
   ▼
DATECONV.cbl                   ← PROGRAM-ID. DATECONV (was MISSING; now source/cobol/DATECONV.cbl)
   │
   │ Internal dispatch on DATESUB-FUNC in 000-SELECT
   │ Internal data layouts:
   │   COPY JDN-CONSTANTS-WS.  ← (was MISSING; now source/copybooks/JDN-CONSTANTS-WS.cpy)
   │   COPY JDN-PACKET-WS.     ← (was MISSING; now source/copybooks/JDN-PACKET-WS.cpy)
   │   COPY JDN-RECORD-WS.     ← (was MISSING; now source/copybooks/JDN-RECORD-WS.cpy)
   ▼
JDN-RECORD-ACCESS section      ← (was MISSING; now source/copybooks/JDN-RECORD-ACCESS.cpy)
   │
   │ COBOL-85 intrinsic functions
   ▼
FUNCTION INTEGER-OF-DATE / DATE-OF-INTEGER / INTEGER-OF-DAY / DAY-OF-INTEGER
```

## 2. IAI-2012 Migration Markers — Preserved Verbatim

The customer's `DATECONV.cbl` contains `MIGRTN` markers in the comment column documenting an internal migration that replaced the legacy `JDNSUB` subroutine call with COBOL-85 intrinsic functions. **These markers are preserved verbatim in `source/cobol/DATECONV.cbl`** — they are not stripped, reformatted, or rewritten in any analytical artifact. Representative markers:

```cobol
MIGRTN     COMPUTE JDN-Int =
MIGRTN             FUNCTION INTEGER-OF-DATE (JDN-YYYYMMDD)
MIGRTN     COMPUTE JDN-Int =
MIGRTN             FUNCTION INTEGER-OF-DAY (JDN-YYYYDDD)
MIGRTN     COMPUTE JDN-YYYYMMDD =
MIGRTN             FUNCTION DATE-OF-INTEGER (JDN-Int)
MIGRTN     COMPUTE JDN-YYYYDDD =
MIGRTN             FUNCTION DAY-OF-INTEGER (JDN-Int)
```

> `JDNSUB` is referenced only inside these comment lines — it is not called from any live PROCEDURE DIVISION statement. The post-migration program implements all date math through `JDN-RECORD-ACCESS.cpy`'s intrinsic-function-based paragraphs.

## 3. The 40 DATECONV-PD Entry Paragraphs

The wrappers below are the public surface of the date-conversion subsystem. Each paragraph:

1. Sets `DATESUB-FUNC` (defined in `DATECONV-WS.cpy`).
2. `CALL 'DATECONV' USING CONV-DATES`.

The internal dispatcher `000-SELECT` in `DATECONV.cbl` routes to the corresponding `NNNN-…` paragraph. Codes **29 and 30** are reserved / unused in `000-SELECT` (no wrapper, no internal paragraph). Codes range from 1 through 42.

| # | Paragraph (DATECONV-PD) | `DATESUB-FUNC` | Internal paragraph (DATECONV.cbl) | Category | Inputs (`CONV-DATES`) | Outputs (`CONV-DATES`) | Used by LABD20? |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `CHECK-CYMD-DT` | 1 | `100-CHECK-CYMD-DT` | Validate | `FROM-CYMD-DT` | `DATE-ERR-IND` (`DATE-IS-VALID` / `DATE-ERR`) | **Yes** — `LABD20.pco:266-274` |
| 2 | `CHECK-MDY-DT` | 9 | `900-CHECK-MDY-DT` | Validate | `FROM-MDY-DT` | `DATE-ERR-IND` | No |
| 3 | `YMD-TO-JUL` | 2 | `200-YMD-TO-JUL` | Convert | `FROM-YMD-DT` | `TO-JUL-DT` (5-digit Julian) | No |
| 4 | `JUL-TO-YMD` | 3 | `300-JUL-TO-YMD` | Convert | `FROM-JUL-DT` | `TO-YMD-DT` | No |
| 5 | `MDY-TO-JUL` | 10 | `1000-MDY-TO-JUL` | Convert | `FROM-MDY-DT` | `TO-JUL-DT` | No |
| 6 | `JUL-TO-MDY` | 11 | `1100-JUL-TO-MDY` | Convert | `FROM-JUL-DT` | `TO-MDY-DT` | No |
| 7 | `MDY-TO-YMD` | 12 | `1200-MDY-TO-YMD` | Convert | `FROM-MDY-DT` | `TO-YMD-DT` | No |
| 8 | `MDY-TO-MDCY` | 27 | `2700-MDY-TO-MDCY` | Convert | `FROM-MDY-DT` | `TO-MDCY-DT` (MMDDCCYY) | No |
| 9 | `YMD-TO-MDY` | 13 | `1300-YMD-TO-MDY` | Convert | `FROM-YMD-DT` | `TO-MDY-DT` | No |
| 10 | `YMD-TO-CYMD` | 18 | `1800-YMD-TO-CYMD` | Convert | `FROM-YMD-DT` | `TO-CYMD-DT` | No |
| 11 | `JUL-TO-CYMD` | 23 | `2300-JUL-TO-CYMD` | Convert | `FROM-JUL-DT` | `TO-CYMD-DT` | No |
| 12 | `CYMD-TO-JUL` | 24 | `2400-CYMD-TO-JUL` | Convert | `FROM-CYMD-DT` | `TO-JUL-DT` | No |
| 13 | `CYMD-TO-INT` | 25 | `2500-CYMD-TO-INT` | Convert | `FROM-CYMD-DT` | `TO-INT-DT` (integer of date) | No |
| 14 | `INT-TO-CYMD` | 26 | `2600-INT-TO-CYMD` | Convert | `FROM-INT-DT` | `TO-CYMD-DT` | No |
| 15 | `JUL-TO-INT` | 31 | `3100-JUL-TO-INT` | Convert | `FROM-JUL-DT` | `TO-INT-DT` | No |
| 16 | `INT-TO-JUL` | 32 | `3200-INT-TO-JUL` | Convert | `FROM-INT-DT` | `TO-JUL-DT` | No |
| 17 | `YMD-TO-INT` | 33 | `3300-YMD-TO-INT` | Convert | `FROM-YMD-DT` | `TO-INT-DT` | No |
| 18 | `INT-TO-YMD` | 34 | `3400-INT-TO-YMD` | Convert | `FROM-INT-DT` | `TO-YMD-DT` | No |
| 19 | `MDY-TO-INT` | 35 | `3500-MDY-TO-INT` | Convert | `FROM-MDY-DT` | `TO-INT-DT` | No |
| 20 | `INT-TO-MDY` | 36 | `3600-INT-TO-MDY` | Convert | `FROM-INT-DT` | `TO-MDY-DT` | No |
| 21 | `DIF-JUL` | 4 | `400-DIF-JUL` | Diff | `FROM-JUL-DT`, `THRU-JUL-DT` | `DAYS-DIF` | No |
| 22 | `DIF-JUL-NO-CHECK` | 28 | `2800-DIF-JUL-NO-CHECK` | Diff | `FROM-JUL-DT`, `THRU-JUL-DT` | `DAYS-DIF` (no validation) | No |
| 23 | `DIF-YMD` | 5 | `500-DIF-YMD` | Diff | `FROM-YMD-DT`, `THRU-YMD-DT` | `DAYS-DIF` | No |
| 24 | `DIF-MDY` | 14 | `1400-DIF-MDY` | Diff | `FROM-MDY-DT`, `THRU-MDY-DT` | `DAYS-DIF` | No |
| 25 | `DIF-CYMD` | 19 | `1900-DIF-CYMD` | Diff | `FROM-CYMD-DT`, `THRU-CYMD-DT` | `DAYS-DIF` | No |
| 26 | `DIF-FY` | 37 | `3700-DIF-FY` | Diff | `FROM-CYMD-DT`, `THRU-CYMD-DT` | `DAYS-DIF` (federal fiscal year) | No |
| 27 | `DIF-JUL-30` | 15 | `1500-DIF-JUL-30` | Diff | `FROM-JUL-DT`, `THRU-JUL-DT` | `DAYS-DIF` (30-day-month) | No |
| 28 | `DIF-CYMD-30` | 6 | `600-DIF-CYMD-30` | Diff | `FROM-CYMD-DT`, `THRU-CYMD-DT` | `DAYS-DIF` (30-day-month) | No |
| 29 | `DIF-MDY-30` | 16 | `1600-DIF-MDY-30` | Diff | `FROM-MDY-DT`, `THRU-MDY-DT` | `DAYS-DIF` (30-day-month) | No |
| 30 | `ADD-JUL` | 7 | `700-ADD-JUL` | Add | `FROM-JUL-DT`, `DAYS-DIF` | `TO-JUL-DT` | No |
| 31 | `ADD-YMD` | 8 | `800-ADD-YMD` | Add | `FROM-YMD-DT`, `DAYS-DIF` | `TO-YMD-DT` | No |
| 32 | `ADD-MDY` | 17 | `1700-ADD-MDY` | Add | `FROM-MDY-DT`, `DAYS-DIF` | `TO-MDY-DT` | No |
| 33 | `ADD-CYMD` | 20 | `2000-ADD-CYMD` | Add | `FROM-CYMD-DT`, `DAYS-DIF` | `TO-CYMD-DT` | No |
| 34 | `ADD-MONTHS-TO-YMD` | 21 | `2100-ADD-MONTHS-TO-YMD` | Add months | `FROM-YMD-DT`, `MONTHS-TO-ADD` | `TO-YMD-DT` | No |
| 35 | `ADD-MONTHS-TO-CYMD` | 22 | `2200-ADD-MONTHS-TO-CYMD` | Add months | `FROM-CYMD-DT`, `MONTHS-TO-ADD` | `TO-CYMD-DT` | No |
| 36 | `ADD-MONTHS-TO-MDY` | 41 | `4100-ADD-MONTHS-TO-MDY` | Add months | `FROM-MDY-DT`, `MONTHS-TO-ADD` | `TO-MDY-DT` | No |
| 37 | `ADD-MONTHS-END-JUL` | 42 | `4200-ADD-MONTHS-END-JUL` | Add months | `FROM-JUL-DT`, `MONTHS-TO-ADD` | `TO-JUL-DT` (end-of-month) | No |
| 38 | `RANGE-JUL` | 38 | `3800-RANGE-JUL` | Range | `FROM-JUL-DT`, `THRU-JUL-DT` | `DATE-ERR-IND` (validation only) | No |
| 39 | `RANGE-YMD` | 39 | `3900-RANGE-YMD` | Range | `FROM-YMD-DT`, `THRU-YMD-DT` | `DATE-ERR-IND` | No |
| 40 | `RANGE-MDY` | 40 | `4000-RANGE-MDY` | Range | `FROM-MDY-DT`, `THRU-MDY-DT` | `DATE-ERR-IND` | No |

Codes **29** and **30** are skipped in `DATECONV.cbl` `000-SELECT` — no entry paragraph, no internal paragraph. Treat as reserved.

## 4. `LABD20`-Actually-Invoked Subset

`source/procobol/LABD20.pco` only exercises **one** of these 40 entry paragraphs:

| Caller site | Wrapper paragraph | Effect |
| --- | --- | --- |
| `LABD20.pco:266` (`MOVE TST123-COMMENT-DT TO FROM-CYMD-DT`) → `LABD20.pco:267` (`PERFORM CHECK-CYMD-DT`) | `CHECK-CYMD-DT` (`DATESUB-FUNC = 1`) | Validates that the 8-digit `TST123-COMMENT-DT` field is a real Gregorian date; sets `DATE-IS-VALID` / `DATE-ERR`. If `DATE-ERR`, `LABD20` flags the record with `WS-TST123-RECORD-FLAG = 1`. |

That said, the other 39 paragraphs are still valuable because:
- They expose a **complete** subsystem to any future modernization (other SBA programs may use the same `DATECONV` library).
- They allow a **byte-for-byte** runtime parity diff to validate the modernization story end-to-end against the full surface of the customer's subprogram, not just the single path `LABD20` exercises.
- They strengthen the demo claim that Cognition can ingest a complete subsystem and produce parity across every entry point, not just the ones the calling program happens to hit.

## 5. JDN Constants (from `JDN-CONSTANTS-WS.cpy`)

The DATECONV subprogram uses these named constants internally:

| Group | Constant | Value | Meaning |
| --- | --- | --- | --- |
| Action | `JDN-Con-DateOfInt` | 1 | Convert integer-of-date → YYYYMMDD |
| Action | `JDN-Con-DayOfInt` | 2 | Convert integer-of-date → YYYYDDD |
| Action | `JDN-Con-IntOfDate` | 3 | Convert YYYYMMDD → integer-of-date |
| Action | `JDN-Con-IntOfDay` | 4 | Convert YYYYDDD → integer-of-date |
| Status | `JDN-Con-OK` | 0 | Operation succeeded |
| Status | `JDN-Con-OutOfRangeDD` | 8 | Day-of-month out of range |
| Status | `JDN-Con-OutOfRangeDDD` | 9 | Day-of-year out of range |
| Status | `JDN-Con-OutOfRangeMM` | 10 | Month out of range |
| Status | `JDN-Con-OutOfRangeYYYY` | 11 | Year out of range |
| Status | `JDN-Con-Strange` | 12 | Inconsistent / impossible date |
| Year flag | `JDN-Con-Common` | 0 | Common (non-leap) year |
| Year flag | `JDN-Con-Leap` | 1 | Leap year |
| Offset | `JDN-Con-USNO-Offset` | 2305813 | USNO Julian Day Number offset (delta between COBOL `INTEGER-OF-DATE` 1601-01-01 baseline and the astronomical Julian Day Number baseline 4713-11-24 BC proleptic) |

## 6. JDN Record Layout (from `JDN-RECORD-WS.cpy`)

The internal `JDN-Record` group is the workhorse data layout:

```
01  JDN-Record.
    05  JDN-Date.
        10  JDN-CC              PIC 9(02).        ← century (e.g. 20)
        10  JDN-YYMMDD          PIC 9(06).
        10  JDN-Filler1 REDEFINES JDN-YYMMDD.
            15  JDN-YY          PIC 9(02).
            15  JDN-MM          PIC 9(02).
            15  JDN-DD          PIC 9(02).
    05  JDN-CCYYMMDD REDEFINES JDN-Date PIC 9(08).
    05  JDN-YYYYMMDD REDEFINES JDN-Date PIC 9(08).
    05  JDN-Filler2 REDEFINES JDN-Date.
        10  JDN-Day.
            15  JDN-CCYY        PIC 9(04).
            15  JDN-YYYY REDEFINES JDN-CCYY PIC 9(04).
            15  JDN-DDD         PIC 9(03).
        10  JDN-CCYYDDD REDEFINES JDN-Day PIC 9(07).
        10  JDN-YYYYDDD REDEFINES JDN-Day PIC 9(07).
        10  JDN-Filler3 REDEFINES JDN-Day.
            15  FILLER          PIC X(02).
            15  JDN-YYDDD       PIC 9(05).
    10  FILLER                  PIC X(01).
    05  JDN-Int                 PIC 9(07).
```

The `REDEFINES` chain lets the same 8-byte memory region be viewed simultaneously as CCYYMMDD, YYYYMMDD, CCYYDDD, YYYYDDD, or YYDDD. `JDN-Int` is the COBOL `INTEGER-OF-DATE` value.

## 7. Intrinsic-Function Core (from `JDN-RECORD-ACCESS.cpy`)

All date math reduces to these four COBOL-85 intrinsics:

| JDN-RECORD-ACCESS paragraph | COBOL intrinsic | Equivalent Python (in our port) |
| --- | --- | --- |
| `JDN-Acc-Int-Of-Date` | `FUNCTION INTEGER-OF-DATE (JDN-YYYYMMDD)` | `date(yyyy,mm,dd).toordinal() - date(1601,1,1).toordinal() + 1` |
| `JDN-Acc-Date-Of-Int` | `FUNCTION DATE-OF-INTEGER (JDN-Int)` | `date.fromordinal(date(1601,1,1).toordinal() + n - 1)` → format `YYYYMMDD` |
| `JDN-Acc-Int-Of-Day` | `FUNCTION INTEGER-OF-DAY (JDN-YYYYDDD)` | `date(yyyy,1,1).toordinal() + ddd - 1 - date(1601,1,1).toordinal() + 1` |
| `JDN-Acc-Day-Of-Int` | `FUNCTION DAY-OF-INTEGER (JDN-Int)` | `date.fromordinal(date(1601,1,1).toordinal() + n - 1)` → format `YYYYDDD` |

Plus a small inference helper:

| Paragraph | Purpose |
| --- | --- |
| `JDN-Acc-CC-Inferred` | If `JDN-YY` is non-numeric or > 72, infer century 19; otherwise 20. Provides backward compatibility with 2-digit-year callers. |

### 7.1 Two distinct two-digit-year rules (added 2026-05-21 post-Devin-Review)

DATECONV does **not** have a single century-inference rule — it has two, and the
thresholds differ. Any port that collapses both into one helper silently
diverges on `YY` in 53–72. The original 52-vector harness all used `YY=24/25`,
so the gap was never exercised and the byte-for-byte parity claim looked
clean before the rule split was caught by adversarial code review.

| Rule | Source | Threshold | Used by |
| --- | --- | --- | --- |
| Local CYMD validation | `source/cobol/DATECONV.cbl:1054-1059` (`9920-CALC-YY-TO-YYYY`, change marker `CH-001`) | `YY > 52 → 19xx` | `900-CHECK-MDY-DT`, `1500-DIF-JUL-30`, `1800-YMD-TO-CYMD`, `2700-MDY-TO-MDCY`, `4000-DIF-FY`, plus the validation prelude inside `1000-MDY-TO-JUL` and `2800-DIF-JUL-NO-CHECK` |
| JDN-Acc core | `source/copybooks/JDN-RECORD-ACCESS.cpy:74-79` (`JDN-Acc-CC-Inferred`, change marker `CHG-002`) | `YY > 72 → 19xx` | Every paragraph that PERFORMs `JDN-Acc-Int-Of-Date` or `JDN-Acc-Int-Of-Day` with `JDN-CC = ZERO` (i.e. 200/300/400/500/700/800/1000-conversion/1100/1400/1700/2100/2300/2800-conversion/3100/3300/3500/4100/4200/4300/4400/4500) |

The Python port mirrors this in `migration/converted-code/python/dateconv.py`
as two helpers — `_cc_inferred` (rule 1, `> 52`) and `_cc_inferred_jdn`
(rule 2, `> 72`) — plus a `via_jdn=True` keyword on `_ymd_to_cymd` /
`_mdy_to_cymd` so every callsite mirrors the threshold its COBOL paragraph
actually emits. The parity harness exercises the threshold gap with 28
adversarial vectors at `migration/test-results/dateconv-test-vectors.txt`
Section 7 (`YY 60/72/73` across FUNC 2/3/5/8/9/10/11/14/17/18/21/23/27/28/31/33/35/37/38/39/40/41/42).

## 8. Modernized Port

The Python port at `migration/converted-code/python/dateconv.py` re-implements all 40 entry paragraphs through a single `DateConv` class (and module-level convenience functions). The dispatcher is preserved as `DATESUB-FUNC` to keep diagnostic traceability against `DATECONV.cbl` line numbers. See `migration/converted-code/python/dateconv.py` and `migration/test-results/cobol-parity-report.html` for parity evidence.

### 8.1 Implementation map (line citations into `dateconv.py`)

Phase B port shipped 2026-05-21. Concrete entry points:

| Artifact | Where to look |
| --- | --- |
| `ConvDates` dataclass (mirrors `DATECONV-WS` `CONV-DATES` group) | `migration/converted-code/python/dateconv.py:48-100` |
| Status-code constants (mirror `JDN-CONSTANTS-WS` `JDN-Con-*`) | `migration/converted-code/python/dateconv.py:30-42` |
| `DateConv.dispatch` (replaces `000-SELECT` IF/ELSE chain) | `migration/converted-code/python/dateconv.py:185-193` |
| `_DISPATCH` table (`DATESUB-FUNC` → method) | `migration/converted-code/python/dateconv.py:627-669` |
| `_run01_check_cymd_dt` ← `100-CHECK-CYMD-DT` (`DATECONV.cbl:225-229`) | `migration/converted-code/python/dateconv.py:195-201` |
| `_run07_add_jul` ← `700-ADD-JUL` (`DATECONV.cbl:337-351`) | `migration/converted-code/python/dateconv.py:476-491` |
| `_run22_add_months_to_cymd` ← `2200-ADD-MONTHS-TO-CYMD` (`DATECONV.cbl:589-600`) | `migration/converted-code/python/dateconv.py:553-561` |
| `_run37_dif_fy` ← `4000-DIF-FY` (`DATECONV.cbl:847-859`) | `migration/converted-code/python/dateconv.py:445-453` |
| Module-level convenience functions (40 names) | `migration/converted-code/python/dateconv.py:836-1082` |
| Pytest coverage (77 cases across 10 test classes) | `migration/converted-code/python/tests/test_dateconv.py` |
| Parity rows BR-DATECONV-001…010 | `migration/converted-code/python/parity_engine.py:880-1057` |
| `BR-LABD20-006` confidence flip (LOW → HIGH) | `migration/converted-code/python/parity_engine.py:484-503` |

The `check_cymd_dt` stub that previously lived in `migration/converted-code/python/labd20_loader.py:196-214` was retired and now delegates here via a one-line import (`labd20_loader.py:196-204`).

## 9. Citations

- `source/cobol/DATECONV.cbl` — `PROGRAM-ID. DATECONV`; internal dispatcher at `000-SELECT`; IAI-2012 `MIGRTN` markers preserved verbatim.
- `source/copybooks/DATECONV-WS.cpy` — `CONV-DATES` parameter group; `DATESUB-FUNC`; `DATE-ERR-IND` (88s `DATE-IS-VALID`, `DATE-ERR`).
- `source/copybooks/DATECONV-PD.cpy` — 40 entry paragraphs.
- `source/copybooks/JDN-CONSTANTS-WS.cpy` — action / status / year-type constants + USNO offset.
- `source/copybooks/JDN-PACKET-WS.cpy` — internal action packet.
- `source/copybooks/JDN-RECORD-WS.cpy` — internal date record layout.
- `source/copybooks/JDN-RECORD-ACCESS.cpy` — intrinsic-function-based core.
- `source/procobol/LABD20.pco:182` — `COPY DATECONV-WS.`
- `source/procobol/LABD20.pco:266-274` — `MOVE TST123-COMMENT-DT TO FROM-CYMD-DT` / `PERFORM CHECK-CYMD-DT`.
- `source/procobol/LABD20.pco:531` — `COPY DATECONV-PD.`

## Documented modernization improvements

The Python port at `migration/converted-code/python/dateconv.py` intentionally diverges from the legacy COBOL in one place. This is a deliberate, customer-acknowledged modernization improvement; the runtime parity harness classifies it separately from regressions and does not fail the build.

### `9950-VALIDATE-YYYY` accepts 02/29/1900 (Julian leap rule)

- **Legacy COBOL behavior.** `source/cobol/DATECONV.cbl` `9950-VALIDATE-YYYY` (paragraph at lines 1111-1127) uses the every-4-years leap-year rule: `DIVIDE WRK-CYMD-YYYY BY 4 ... IF DAYS-TO-ADD > 0 MOVE 2 TO LEAP-YEAR`. There is no `DIVIDE BY 100` / `DIVIDE BY 400` adjustment. Therefore `CHECK-CYMD-DT(19000229)` returns `DATE-ERR-IND = "N"` (valid).
- **Python port behavior.** `_run01_check_cymd_dt` delegates to `_int_of_date`, which uses Python's `datetime.date` constructor (proleptic Gregorian calendar). `19000229` raises and is reported as `DATE-ERR-IND = "Y"`, `DATE-ERR-REASON = 07` (`OutOfRangeDD`).
- **Why this is the correct call.** The Gregorian rule (the actual civil calendar in use since 1582) excludes centurial years that are not divisible by 400. 1900 IS divisible by 4 but is NOT a leap year. The COBOL behavior is a known defect carried over from the original Julian-style validator; replicating it in the Python port would propagate a date-validation bug into modernized code.
- **How it is documented in the harness.** `migration/test-results/run_cobol_parity.py` marks the vector `N|01|0|0|19000229|0|0|0|0|0|0|0|0|0|0` in the `MODERNIZATION_IMPROVEMENTS` map. The classifier returns `modernization_improvement` (not `mismatched`) and the harness exits 0.

This is the only intentional divergence between the Python port and the legacy COBOL. Every other behavior — including `JDN-Acc-Int-Of-Date` returning `0` silently for an all-zero BETWEEN input, `400-DIF-JUL` leaving `TO-INT-DT` untouched, `9940-CONV-JUL-30` collapsing Day-31 onto Day-30, and the `2300-JUL-TO-CYMD` / `4500-ADD-MONTHS-END-JUL` side-effects on alias `TO-*` fields — is faithfully reproduced.

## Verification-loop discoveries (2026-05-21)

The runtime parity harness against GnuCOBOL caught **13 Python-port defects** before merge. All 77 Python unit tests passed cleanly; the runtime diff against the customer's compiled COBOL is what exposed them. They were patched in the same commit as the harness landed. Summary:

| # | Bug class | COBOL paragraphs | Python defect | Fix |
| - | --- | --- | --- | --- |
| 1 | `TO-INT-DT` leaked intermediate JDN in DIF operations | `400-DIF-JUL`, `500-DIF-YMD`, `1400-DIF-MDY`, `1900-DIF-CYMD` | `_set_dif` wrote `cd.to_int_dt = b`, exposing the intermediate JDN to callers | Drop `cd.to_int_dt` assignment in `_set_dif` |
| 2 | Conversion side-effects not propagated to alias `TO-*` fields | `2300-JUL-TO-CYMD`, `2000-ADD-CYMD`, `4500-ADD-MONTHS-END-JUL`, `4000-DIF-FY` | Python port assigned only the "primary" output field; callers reading `TO-YMD-DT` / `TO-CYMD-YYYY` saw stale zeros | Mirror each `PERFORM` side-effect in `_run23`, `_run20`, `_run37`, `_run42` |
| 3 | 30-day-month DIF counted Day-31 separately | `600-DIF-CYMD-30`, `1500-DIF-JUL-30`, `1600-DIF-MDY-30` | Python returned 30 instead of 31 and missed the TO-JUL side-effect | Cap `dd` at 30 in `_dif_cymd_30_int`; add TO-JUL side-effect to `_run06` / `_run16` |
| 4 | `4300-RANGE-MDY` rejected all-zero BETWEEN input | `4300-RANGE-MDY` | Python's `_int_of_date` validated explicitly and short-circuited with `DATE-ERR-IND = Y` | Make `_range_check` lenient on BETWEEN — fall through with `c = 0` when only BETWEEN failed validation |

This is the substantive value of a runtime parity loop: "byte-for-byte parity" claims that ship without a runtime diff against the customer's compiled COBOL are unverifiable. The harness regenerates [`migration/test-results/cobol-parity-report.html`](../migration/test-results/cobol-parity-report.html) on every push.
