package com.cognition.jvmigration.db;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the SQLCODE→DMS translation table (DBIO.pco:374-398) and the
 * JDBC result/transaction wrapper. Mirrors the TestSQLCodeTranslation cases in
 * {@code migration/converted-code/python/tests/test_labd20_loader.py}.
 */
class DbDispatcherTest {

    @Nested
    class TranslateSqlcode {
        @Test void zeroIsOk() {
            assertEquals("0000", DbDispatcher.translateSqlcode(0));
        }
        @Test void hundredDefault() {
            assertEquals("0013", DbDispatcher.translateSqlcode(100));
        }
        @Test void hundredWithSetNameFind() {
            assertEquals("0007", DbDispatcher.translateSqlcode(100, "X", "FIND"));
        }
        @Test void hundredWithSetNameButFetchOwnerReturnsDefault() {
            assertEquals("0013", DbDispatcher.translateSqlcode(100, "X", "FETCH OWNER"));
        }
        @Test void minusOne() {
            assertEquals("0005", DbDispatcher.translateSqlcode(-1));
        }
        @Test void minus8103LoggedAsOk() {
            assertEquals("0000", DbDispatcher.translateSqlcode(-8103));
        }
        @Test void otherReturns9999() {
            assertEquals("9999", DbDispatcher.translateSqlcode(-42));
            assertEquals("9999", DbDispatcher.translateSqlcode(1000));
        }
    }

    @Nested
    class ResultHandling {
        @Test void insertThenSelectRoundTrips() {
            try (DbDispatcher db = DbDispatcher.inMemory()) {
                DemoSchema.buildDemoSchema(db);
                DispatcherResult ins = db.insert(
                    "INSERT INTO JC_COUNT_TBL (JC_SECTION, JC_COUNT_NUM) VALUES (?, ?)",
                    "MA", 5);
                assertTrue(ins.ok());
                db.commit();

                DispatcherResult sel = db.selectOne(
                    "SELECT JC_COUNT_NUM FROM JC_COUNT_TBL WHERE JC_SECTION = ?", "MA");
                assertTrue(sel.ok());
                assertEquals(5, ((Number) sel.rows.get(0)[0]).intValue());
            }
        }

        @Test void selectOneMissingRowReturnsNotFound() {
            try (DbDispatcher db = DbDispatcher.inMemory()) {
                DemoSchema.buildDemoSchema(db);
                DispatcherResult sel = db.selectOne(
                    "SELECT JC_COUNT_NUM FROM JC_COUNT_TBL WHERE JC_SECTION = ?", "ZZ");
                assertFalse(sel.ok());
                assertEquals(DbDispatcher.DMS_NOT_FOUND, sel.rtncodeDms);
            }
        }

        @Test void rollbackUndoesUncommittedInsert() {
            try (DbDispatcher db = DbDispatcher.inMemory()) {
                DemoSchema.buildDemoSchema(db);
                db.insert("INSERT INTO JC_COUNT_TBL (JC_SECTION, JC_COUNT_NUM) VALUES (?, ?)",
                    "MA", 9);
                db.rollback();
                assertEquals(0, db.countRows("JC_COUNT_TBL"));
            }
        }

        @Test void badSqlReturnsErrorResult() {
            try (DbDispatcher db = DbDispatcher.inMemory()) {
                DispatcherResult r = db.insert("INSERT INTO NO_SUCH_TABLE (X) VALUES (?)", 1);
                assertFalse(r.ok());
                assertNotEquals(DbDispatcher.DMS_OK, r.rtncodeDms);
            }
        }
    }
}
