// STATUS: Demo output — pending SME review
package gov.jv.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC wrapper — modernized analog of the DBIO dispatcher.
 * <p>
 * Source: source/procobol/DBIO.pco (Oracle connection/transaction dispatcher)
 *         source/procobol/CONTROL-RECORD-TABLE-IO.pco (CRUD for CONTROL_RECORD_TABLE)
 * <p>
 * The legacy DBIO.pco dispatches DB operations by string-concatenating the table name
 * with "-IO" to derive the subroutine name (DBIO.pco:228-260). This modern equivalent
 * exposes typed methods instead.
 * <p>
 * // RISK 3: Credentials managed via env vars / config, not from /tst/.oralogin.
 * // RISK 4: Dynamic dispatch replaced by static method calls.
 * // RISK 6: SQLCODE→DMS translation via DmsErrorCodes.translate()
 */
public class DbDispatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DbDispatcher.class);

    private final Connection connection;

    /**
     * Creates a dispatcher wrapping the given JDBC connection.
     * Source: DBIO.pco:188-215 (CONNECT-RTN)
     *
     * @param connection a live JDBC Connection
     */
    public DbDispatcher(Connection connection) {
        this.connection = connection;
    }

    /**
     * Executes a SELECT and returns the first row (or empty result).
     * <p>
     * Source: Used by LABA05.cbl:150-174 (FETCH-CTRL-REC) and LABD20 duplicate-check.
     *
     * @param sql    parameterized SELECT statement
     * @param params bind parameters
     * @return DispatcherResult with rows (may be empty)
     */
    public DispatcherResult selectOne(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Object[]> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                if (rs.next()) {
                    Object[] row = new Object[colCount];
                    for (int i = 0; i < colCount; i++) {
                        row[i] = rs.getObject(i + 1);
                    }
                    rows.add(row);
                }
                if (rows.isEmpty()) {
                    // Source: DBIO.pco:378-379 — SQLCODE 100 → NOT FOUND
                    String dms = DmsErrorCodes.translate(100, "", "FETCH");
                    return new DispatcherResult(dms, 100, "No row found", rows);
                }
                return new DispatcherResult(DmsErrorCodes.OK, 0, "OK", rows);
            }
        } catch (SQLException e) {
            LOG.error("selectOne failed: {}", e.getMessage());
            int sqlcode = e.getErrorCode() == 0 ? -1 : -e.getErrorCode();
            String dms = DmsErrorCodes.translate(sqlcode, "", "FETCH");
            return new DispatcherResult(dms, sqlcode, e.getMessage(), null);
        }
    }

    /**
     * Executes an UPDATE/INSERT statement.
     * <p>
     * Source: LABA05.cbl:176-205 (MODIFY-CTRL-REC), DBIO.pco UPDATE path.
     *
     * @param sql    parameterized DML statement
     * @param params bind parameters
     * @return DispatcherResult indicating success or error
     */
    public DispatcherResult update(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                // Source: DBIO.pco:378-379 — no row affected treated as NOT FOUND
                return new DispatcherResult(DmsErrorCodes.NOT_FOUND, 100,
                        "No rows affected", null);
            }
            return new DispatcherResult(DmsErrorCodes.OK, 0, "OK", null);
        } catch (SQLException e) {
            LOG.error("update failed: {}", e.getMessage());
            int sqlcode = e.getErrorCode() == 0 ? -1 : -e.getErrorCode();
            String dms = DmsErrorCodes.translate(sqlcode, "", "MODIFY");
            return new DispatcherResult(dms, sqlcode, e.getMessage(), null);
        }
    }

    /**
     * Executes an INSERT statement.
     * <p>
     * Source: LABD20.pco:342-389 (INSERT path), DBIO.pco INSERT path.
     *
     * @param sql    parameterized INSERT statement
     * @param params bind parameters
     * @return DispatcherResult indicating success or error
     */
    public DispatcherResult insert(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindParams(ps, params);
            ps.executeUpdate();
            return new DispatcherResult(DmsErrorCodes.OK, 0, "OK", null);
        } catch (SQLException e) {
            LOG.error("insert failed: {}", e.getMessage());
            int sqlcode = e.getErrorCode() == 0 ? -1 : -e.getErrorCode();
            String dms = DmsErrorCodes.translate(sqlcode, "", "STORE");
            return new DispatcherResult(dms, sqlcode, e.getMessage(), null);
        }
    }

    /**
     * Commits the current transaction.
     * <p>
     * Source: LABA05.cbl:120-140 (CLOSE-DEPART-DB paragraph)
     *         DBIO.pco — DB-COMMIT path
     *
     * @return DispatcherResult
     */
    public DispatcherResult commit() {
        try {
            connection.commit();
            return DispatcherResult.success("COMMIT OK");
        } catch (SQLException e) {
            LOG.error("commit failed: {}", e.getMessage());
            return DispatcherResult.error(-1, e.getMessage());
        }
    }

    /**
     * Rolls back the current transaction.
     * <p>
     * Source: LABA05.cbl:207-228 (DML-ROLLBACK-PARA)
     *         DBIO.pco — DB-ROLLBACK path
     *
     * @return DispatcherResult
     */
    public DispatcherResult rollback() {
        try {
            connection.rollback();
            return DispatcherResult.success("ROLLBACK OK");
        } catch (SQLException e) {
            LOG.error("rollback failed: {}", e.getMessage());
            return DispatcherResult.error(-1, e.getMessage());
        }
    }

    /**
     * Closes the underlying connection.
     * Source: DBIO.pco — DB-DONE path
     */
    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            LOG.warn("close failed: {}", e.getMessage());
        }
    }

    /**
     * Creates the CONTROL_RECORD_TABLE schema for testing with H2.
     * <p>
     * Source: database/descriptions/ — table DDL
     *         source/procobol/CONTROL-RECORD-TABLE-IO.pco:37-52
     *
     * @param conn a JDBC connection
     * @throws SQLException on DDL failure
     */
    public static void buildDemoSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS CONTROL_RECORD_TABLE (
                        CONTROL_RECORD_NAME   VARCHAR(30),
                        CONTROL_RECORD_NUMBER INT,
                        CONTROL_RECORD_DATA   CHAR(400)
                    )
                    """);
        }
    }

    /**
     * Seeds a JV-CONTROL-REC row into CONTROL_RECORD_TABLE for testing.
     * <p>
     * Source: test harness utility — mirrors python/db_dispatcher.seed_control_record()
     *
     * @param dispatcher the DbDispatcher to use
     * @param jvNumber   the initial JV-NUMBER value to seed
     */
    public static void seedControlRecord(DbDispatcher dispatcher, int jvNumber) {
        // Build a 400-byte blob with only JV-NUMBER populated at bytes 24-29
        var rec = new JvControlRecord(0, 0, 0, 0, jvNumber, "         ", 0, "          ");
        String data = rec.toControlRecordData();
        // Use insert() to seed — bypasses the "0 rows affected" check in update()
        dispatcher.insert(
                """
                INSERT INTO CONTROL_RECORD_TABLE (CONTROL_RECORD_NAME, CONTROL_RECORD_NUMBER, CONTROL_RECORD_DATA)
                VALUES (?, ?, ?)
                """,
                "JV-CONTROL-REC", 1, data);
    }

    private static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
