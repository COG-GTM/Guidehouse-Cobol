package com.cognition.jvmigration.labd20;

import com.cognition.jvmigration.db.DbDispatcher;
import com.cognition.jvmigration.db.DemoSchema;
import com.cognition.jvmigration.db.DispatcherResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 mirror of
 * {@code migration/converted-code/python/tests/test_labd20_loader.py}. All tests
 * use synthetic non-production data (per AGENTS.md), the same 21-record fixture
 * under {@code migration/test-data/synthetic_comments.dat}.
 */
class Labd20LoaderTest {

    private static final Path REPO_ROOT = locateRepoRoot();
    private static final Path SYNTHETIC_DATA = REPO_ROOT.resolve("migration/test-data/synthetic_comments.dat");
    private static final Path SYNTHETIC_CARD = REPO_ROOT.resolve("migration/test-data/synthetic_card.ctl");

    private static Path locateRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("migration").resolve("test-data"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not locate repo root");
    }

    private static DbDispatcher freshDb() {
        DbDispatcher d = DbDispatcher.inMemory();
        DemoSchema.buildDemoSchema(d);
        d.insert("INSERT INTO JC_COUNT_TBL (JC_SECTION, JC_COUNT_NUM) VALUES (?, ?)", "MA", 0);
        d.commit();
        return d;
    }

    private static String pad(String s, int width) {
        StringBuilder sb = new StringBuilder(s == null ? "" : s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** Mirror of the Python _make_record() helper. */
    private static String makeRecord(String date, String jv, String section, String loan,
                                     String sched, String text, String requestor, String approver) {
        String s = pad(date, 8) + pad(jv, 6) + pad(section, 2) + pad(loan, 10)
            + pad(sched, 10) + pad(text, 230) + pad(requestor, 20) + pad(approver, 14);
        if (s.length() != Labd20Loader.TST123_RECORD_LENGTH) {
            throw new IllegalStateException("record length " + s.length());
        }
        return s;
    }

    private static String makeRecord() {
        return makeRecord("20260101", "000100", "01", "9000000001",
            "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER");
    }

    @Nested
    class RecordLayout {
        @Test void totalLengthIs300() {
            assertEquals(300, Labd20Loader.TST123_RECORD_LENGTH);
        }
        @Test void sliceBoundariesAreContiguous() {
            assertEquals(0, Labd20Loader.COMMENT_DT_S);
            assertEquals(8, Labd20Loader.COMMENT_DT_E);
            assertEquals(8, Labd20Loader.JV_NUMBER_S);
            assertEquals(14, Labd20Loader.JV_NUMBER_E);
            assertEquals(14, Labd20Loader.SECTION_ID_S);
            assertEquals(16, Labd20Loader.SECTION_ID_E);
            assertEquals(16, Labd20Loader.LOAN_NUMBER_S);
            assertEquals(26, Labd20Loader.LOAN_NUMBER_E);
            assertEquals(26, Labd20Loader.SCHEDULE_DOC_NO_S);
            assertEquals(36, Labd20Loader.SCHEDULE_DOC_NO_E);
            assertEquals(36, Labd20Loader.COMMENT_TEXT_S);
            assertEquals(266, Labd20Loader.COMMENT_TEXT_E);
            assertEquals(266, Labd20Loader.REQUESTOR_S);
            assertEquals(286, Labd20Loader.REQUESTOR_E);
            assertEquals(286, Labd20Loader.APPROVER_S);
            assertEquals(300, Labd20Loader.APPROVER_E);
        }
        @Test void compositeLoanDtNrCoversFirst26() {
            assertEquals(0, Labd20Loader.LOAN_DT_NR_S);
            assertEquals(26, Labd20Loader.LOAN_DT_NR_E);
        }
        @Test void approverIs14Not20() {
            assertEquals(14, Labd20Loader.APPROVER_E - Labd20Loader.APPROVER_S);
        }
    }

    @Nested
    class ParseCommentRecord {
        @Test void parsesEachFieldAtCorrectOffset() {
            CommentRecord rec = Labd20Loader.parseCommentRecord(makeRecord());
            assertEquals("20260101", rec.commentDt);
            assertEquals("000100", rec.jvNumber);
            assertEquals("01", rec.sectionId);
            assertEquals("9000000001", rec.loanNumber);
            assertEquals("20260101000100019000000001", rec.loanDtNr);
            assertEquals("SCH0000001", rec.scheduleDocNo);
            assertEquals("Demo comment", rec.commentText.trim());
            assertEquals("ALICE.SUBMITTER", rec.requestor.trim());
            assertEquals("BOB.APPROVER", rec.approver.trim());
        }
        @Test void rejectsWrongLength() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Labd20Loader.parseCommentRecord("X".repeat(299)));
            assertTrue(ex.getMessage().contains("must be 300 bytes"));
        }
        @Test void controlNumConcatenatesJvAndSection() {
            CommentRecord rec = Labd20Loader.parseCommentRecord(
                makeRecord("20260101", "000123", "07", "9000000001",
                    "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertEquals("00012307", rec.controlNum());
        }
        @Test void submittedKeyIsFirst26Bytes() {
            CommentRecord rec = Labd20Loader.parseCommentRecord(makeRecord());
            assertEquals(rec.raw.substring(0, 26), rec.submittedKey());
        }
    }

    @Nested
    class ValidationRules {
        private Labd20Loader.Disposition disp(String raw) {
            return Labd20Loader.determineDisposition(Labd20Loader.parseCommentRecord(raw));
        }
        @Test void blankRecordRejected() {
            Labd20Loader.Disposition d = disp(" ".repeat(300));
            assertFalse(d.valid);
            assertTrue(d.reasons.contains("blank record"));
        }
        @Test void happyPathAccepted() {
            Labd20Loader.Disposition d = disp(makeRecord());
            assertTrue(d.valid, d.reasons.toString());
            assertTrue(d.reasons.isEmpty());
        }
        @Test void nonNumericDateRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20XXXXXX", "000100", "01",
                "9000000001", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("comment date")));
        }
        @Test void invalidCalendarDateRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20261345", "000100", "01",
                "9000000001", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("calendar")));
        }
        @Test void jvNumberZeroRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "000000", "01",
                "9000000001", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("JV number")));
        }
        @Test void jvNumberNonNumericRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "ABC123", "01",
                "9000000001", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("JV number")));
        }
        @Test void nonNumericSectionRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "000100", "MA",
                "9000000001", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("section id")));
        }
        @Test void nonNumericLoanRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "000100", "01",
                "ABCDE12345", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("loan number")));
        }
        @Test void blankCommentRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "000100", "01",
                "9000000001", "SCH0000001", "", "ALICE.SUBMITTER", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("comment text")));
        }
        @Test void blankRequestorRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "000100", "01",
                "9000000001", "SCH0000001", "Demo comment", "", "BOB.APPROVER"));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("requestor")));
        }
        @Test void blankApproverRejected() {
            Labd20Loader.Disposition d = disp(makeRecord("20260101", "000100", "01",
                "9000000001", "SCH0000001", "Demo comment", "ALICE.SUBMITTER", ""));
            assertFalse(d.valid);
            assertTrue(d.reasons.stream().anyMatch(r -> r.contains("approver")));
        }
    }

    @Nested
    class CheckCymd {
        @Test void validDate() { assertTrue(Labd20Loader.checkCymdDt("20260101")); }
        @Test void invalidMonth() { assertFalse(Labd20Loader.checkCymdDt("20261301")); }
        @Test void invalidDay() { assertFalse(Labd20Loader.checkCymdDt("20260132")); }
        @Test void leapDayValid() { assertTrue(Labd20Loader.checkCymdDt("20240229")); }
        @Test void nonLeapFeb29Invalid() { assertFalse(Labd20Loader.checkCymdDt("20250229")); }
        @Test void nonNumeric() { assertFalse(Labd20Loader.checkCymdDt("2026010A")); }
        @Test void wrongLength() { assertFalse(Labd20Loader.checkCymdDt("2026101")); }
    }

    @Nested
    class ReadProcessDate {
        @Test void reshufflesMmddccyyToYyyymmdd(@TempDir Path tmp) throws IOException {
            Path card = tmp.resolve("card.ctl");
            Files.write(card, "03/15/2026\n".getBytes(StandardCharsets.UTF_8));
            assertEquals("20260315", Labd20Loader.readProcessDate(card));
        }
        @Test void syntheticCardFileParses() {
            assertEquals("20260115", Labd20Loader.readProcessDate(SYNTHETIC_CARD));
        }
        @Test void rejectsMalformed(@TempDir Path tmp) throws IOException {
            Path card = tmp.resolve("card.ctl");
            Files.write(card, "2026-01-15\n".getBytes(StandardCharsets.UTF_8));
            assertThrows(IllegalArgumentException.class, () -> Labd20Loader.readProcessDate(card));
        }
    }

    @Nested
    class EndToEnd {
        @Test void runsSyntheticDataset(@TempDir Path tmp) throws IOException {
            Path comments = tmp.resolve("comments.dat");
            Files.copy(SYNTHETIC_DATA, comments);
            try (DbDispatcher db = freshDb()) {
                Labd20Loader loader = new Labd20Loader(db);
                LoaderStats stats = loader.run(new LoaderConfig(SYNTHETIC_CARD, comments, true));
                assertEquals(21, stats.totalRead);
                assertEquals(7, stats.inserted);
                assertEquals(2, stats.duplicates);
                assertEquals(21 - 7 - 2, stats.rejected);
                assertEquals(7, stats.submittedTotal);
                assertEquals(0, Files.size(comments));
            }
        }

        @Test void insertParameterMappingUsesAllNineColumns(@TempDir Path tmp) throws IOException {
            Path comments = tmp.resolve("one.dat");
            Files.write(comments, (makeRecord("20260101", "000100", "01", "9000000001",
                "SCH0000001", "single insert", "ALICE.SUBMITTER", "BOB.APPROVER") + "\n")
                .getBytes(StandardCharsets.UTF_8));
            try (DbDispatcher db = freshDb()) {
                new Labd20Loader(db).run(new LoaderConfig(SYNTHETIC_CARD, comments, false));
                DispatcherResult r = db.selectOne(
                    "SELECT JC_SUBMITTED, JC_SUBMITTED_NUMBER, JC_SUBMITTED_SCHED_DOC_NO,"
                        + " JC_SUBMITTED_COMMENT_HIST, JC_SUBMITTED_COMMENT_REQUESTOR,"
                        + " JC_SUBMITTED_COMMENT_APPROVER, JC_SUBMITTED_CONTROL_NUM,"
                        + " JC_SUBMITTED_UPDT_PROG_ID, JC_SUBMITTED_UPDT_PROG_DT"
                        + " FROM JC_SUBMITTED_COMMENT_TBL");
                assertTrue(r.ok());
                Object[] row = r.rows.get(0);
                assertTrue(String.valueOf(row[0]).startsWith("20260101000100019000000001"));
                assertEquals(1, ((Number) row[1]).intValue());
                assertEquals("SCH0000001", row[2]);
                assertTrue(String.valueOf(row[3]).contains("single insert"));
                assertEquals("ALICE.SUBMITTER", String.valueOf(row[4]).trim());
                assertEquals("BOB.APPROVER", String.valueOf(row[5]).trim());
                assertEquals("00010001", row[6]);
                assertEquals("LABD20", row[7]);
                assertEquals("2026-01-15", row[8]);
            }
        }

        @Test void duplicateRecordDoesNotInsertTwice(@TempDir Path tmp) throws IOException {
            Path comments = tmp.resolve("dupes.dat");
            String rec = makeRecord();
            Files.write(comments, (rec + "\n" + rec + "\n").getBytes(StandardCharsets.UTF_8));
            try (DbDispatcher db = freshDb()) {
                LoaderStats stats = new Labd20Loader(db)
                    .run(new LoaderConfig(SYNTHETIC_CARD, comments, false));
                assertEquals(1, stats.inserted);
                assertEquals(1, stats.duplicates);
            }
        }

        @Test void rollbackOnInsertFailure(@TempDir Path tmp) throws Exception {
            try (DbDispatcher db = freshDb()) {
                try (Statement st = db.connection().createStatement()) {
                    st.execute("DROP TABLE JC_SUBMITTED_COMMENT_TBL");
                }
                db.commit();
                Path comments = tmp.resolve("one.dat");
                Files.write(comments, (makeRecord() + "\n").getBytes(StandardCharsets.UTF_8));
                Labd20Loader loader = new Labd20Loader(db);
                assertThrows(RuntimeException.class,
                    () -> loader.run(new LoaderConfig(SYNTHETIC_CARD, comments, false)));
            }
        }
    }

    @Test void truncateFileZerosIt(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("data.dat");
        Files.write(f, "not empty\n".getBytes(StandardCharsets.UTF_8));
        Labd20Loader.truncateFile(f);
        assertEquals(0, Files.size(f));
    }
}
