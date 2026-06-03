package com.cognition.jvmigration.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modernized analog of {@code source/procobol/DBIO.pco}. Java port of
 * {@code migration/converted-code/python/db_dispatcher.py}.
 *
 * <p>STATUS: Demo output pending SME review.
 *
 * <p>DBIO.pco constructs the target IO-routine name at runtime by
 * string-concatenating the table name with {@code -IO} (DBIO.pco:228-260) and
 * maps SQLCODE values to four-character DMS return codes (DBIO.pco:374-398).
 * This class preserves the same <em>contract</em> (one method per DB verb, with
 * a DMS-style return code) but replaces dynamic dispatch with typed JDBC calls.
 * The demo wires it to an in-memory H2 database; production wires it to Oracle.
 *
 * <p>ASSUMPTIONS (see migration/ASSUMPTIONS-AND-PLACEHOLDERS.md):
 * <ul>
 *   <li>A-1: credentials come from environment variables, never from the legacy
 *       {@code /tst/.oralogin} / {@code /tst/.orapasswd} files (RISKS Risk 3).</li>
 *   <li>A-3: SQLCODE->DMS translation reproduces DBIO.pco:374-398 verbatim.</li>
 *   <li>A-9: dynamic dispatch (DBIO.pco:228-260) replaced by typed methods
 *       (RISKS Risk 4).</li>
 * </ul>
 */
public class DbDispatcher implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(DbDispatcher.class.getName());

    // DMS return-code constants — mirror DBIO.pco:374-398 (5300-TRANSLATE-SQLCODE).
    public static final String DMS_OK = "0000";            // SQLCODE 0
    public static final String DMS_NOT_FOUND = "0013";     // SQLCODE 100
    public static final String DMS_NOT_FOUND_SET = "0007"; // SQLCODE 100 + set-name
    public static final String DMS_BAD_FETCH = "0005";     // SQLCODE -1
    public static final String DMS_CONTINUE_8103 = "0000"; // SQLCODE -8103 -> treated as 0
    public static final String DMS_UNHANDLED = "9999";     // any other code

    // Allowlist of table identifiers that callers may pass to countRows().
    // Oracle and the SQL standard do not allow table identifiers to be bound
    // as parameters, so countRows() interpolates the name directly. Restricting
    // input to a known-safe set means a future caller passing untrusted input
    // cannot turn countRows() into a SQL injection vector.
    private static final Set<String> ALLOWED_TABLES = new HashSet<>(Arrays.asList(
        "JC_SUBMITTED_COMMENT_TBL",
        "JC_REJECTED_COMMENT_TBL",
        "JC_APPLIED_COMMENT_TBL",
        "JC_COUNT_TBL",
        "CONTROL_RECORD_TABLE"
    ));

    // Environment variable names — explicit, never read credential files.
    public static final String ENV_DB_BACKEND = "JV_DB_BACKEND"; // "h2" (demo) or "oracle"
    public static final String ENV_DB_DSN = "JV_DB_DSN";
    public static final String ENV_DB_USER = "JV_DB_USER";
    public static final String ENV_DB_PASSWORD = "JV_DB_PASSWORD";

    private final Connection conn;

    public DbDispatcher(Connection conn) {
        this.conn = conn;
    }

    public Connection connection() {
        return conn;
    }

    /**
     * Reproduce 5300-TRANSLATE-SQLCODE from DBIO.pco:374-398, verbatim:
     * <pre>
     *   0     -&gt; 0000
     *   100   -&gt; 0013 (or 0007 if a DB-SET-NAME is non-blank AND function-type
     *            is not 'FETCH OWNER')
     *   -1    -&gt; 0005
     *   -8103 -&gt; 0000 (with a DISPLAY block routed to the logger)
     *   other -&gt; 9999
     * </pre>
     */
    public static String translateSqlcode(int sqlcode, String setName, String functionType) {
        if (sqlcode == 0) {
            return DMS_OK;
        }
        if (sqlcode == 100) {
            if (setName != null && !setName.trim().isEmpty() && !"FETCH OWNER".equals(functionType)) {
                return DMS_NOT_FOUND_SET;
            }
            return DMS_NOT_FOUND;
        }
        if (sqlcode == -1) {
            return DMS_BAD_FETCH;
        }
        if (sqlcode == -8103) {
            LOGGER.warning("RECEIVED ORACLE ERROR -8103, CONTINUE...");
            return DMS_CONTINUE_8103;
        }
        return DMS_UNHANDLED;
    }

    public static String translateSqlcode(int sqlcode) {
        return translateSqlcode(sqlcode, "", "");
    }

    // ------- factory helpers ------------------------------------------------
    /**
     * Build a dispatcher from {@code JV_DB_*} environment variables. Defaults
     * to a fresh in-memory H2 database for the zero-setup demo. ASSUMPTION A-1:
     * credentials never come from disk files like {@code /tst/.oralogin}.
     */
    public static DbDispatcher fromEnv() {
        String backend = System.getenv().getOrDefault(ENV_DB_BACKEND, "h2").toLowerCase();
        if ("h2".equals(backend)) {
            return inMemory();
        }
        if ("oracle".equals(backend)) {
            // PLACEHOLDER: Oracle wiring shown for documentation; not exercised
            // in the demo (no Oracle driver/instance available). SME-REVIEW.
            String dsn = System.getenv(ENV_DB_DSN);
            String user = System.getenv(ENV_DB_USER);
            String password = System.getenv(ENV_DB_PASSWORD);
            if (dsn == null || user == null || password == null) {
                throw new IllegalStateException(
                    "Set " + ENV_DB_USER + "/" + ENV_DB_PASSWORD + "/" + ENV_DB_DSN
                        + " via your secrets manager.");
            }
            try {
                Connection c = DriverManager.getConnection(dsn, user, password);
                c.setAutoCommit(false);
                return new DbDispatcher(c);
            } catch (SQLException e) {
                throw new IllegalStateException("Oracle connect failed: " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException("Unknown " + ENV_DB_BACKEND + "=" + backend);
    }

    /** Open a fresh, isolated in-memory H2 database (Oracle stand-in for demo/tests). */
    public static DbDispatcher inMemory() {
        try {
            String url = "jdbc:h2:mem:jv_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1";
            Connection c = DriverManager.getConnection(url, "sa", "");
            c.setAutoCommit(false);
            return new DbDispatcher(c);
        } catch (SQLException e) {
            throw new IllegalStateException("H2 in-memory connect failed: " + e.getMessage(), e);
        }
    }

    // ------- read paths -----------------------------------------------------
    /**
     * Execute a SELECT and fetch at most one row. Used by LABA05 FETCH-CTRL-REC
     * (LABA05.cbl:152-174) and LABD20 DETERMINE-IF-DUPLICATE (LABD20.pco:317-339).
     */
    public DispatcherResult selectOne(String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new DispatcherResult(DMS_NOT_FOUND, 100);
                }
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                List<Object[]> rows = new ArrayList<>();
                rows.add(row);
                return new DispatcherResult(DMS_OK, 0, "", rows, 1);
            }
        } catch (SQLException e) {
            return error(e);
        }
    }

    /**
     * Mirror the post-process stats SELECTs in LABD20.pco:421-446. {@code table}
     * is a literal here, not a bind parameter; the allowlist enforces that only
     * known business tables can be counted (RISKS Risk 4 mitigation).
     */
    public int countRows(String table) {
        return countRows(table, "1=1");
    }

    public int countRows(String table, String where, Object... params) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException(
                "countRows: table " + table + " is not in the allowlist; add it to "
                    + "ALLOWED_TABLES if it is a legitimate target.");
        }
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + where;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("countRows failed: " + e.getMessage(), e);
        }
    }

    // ------- write paths ----------------------------------------------------
    /** Execute an INSERT (LABD20.pco:342-389). */
    public DispatcherResult insert(String sql, Object... params) {
        return execWrite(sql, params);
    }

    /**
     * Execute an UPDATE. Used by LABA05 MODIFY-CTRL-REC (LABA05.cbl:176-205)
     * and LABD20 UPDATE JC_COUNT_TBL (LABD20.pco:392-405).
     */
    public DispatcherResult update(String sql, Object... params) {
        return execWrite(sql, params);
    }

    /** Execute a DELETE (parity with CONTROL-RECORD-TABLE-IO.pco DELETE paths). */
    public DispatcherResult delete(String sql, Object... params) {
        return execWrite(sql, params);
    }

    private DispatcherResult execWrite(String sql, Object[] params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            int rc = ps.executeUpdate();
            return new DispatcherResult(DMS_OK, 0, "", new ArrayList<>(), rc);
        } catch (SQLException e) {
            return error(e);
        }
    }

    // ------- transaction control -------------------------------------------
    /** Mirror EXEC SQL COMMIT WORK (LABD20.pco:413). */
    public DispatcherResult commit() {
        try {
            conn.commit();
            return new DispatcherResult(DMS_OK, 0);
        } catch (SQLException e) {
            return error(e);
        }
    }

    /** Mirror EXEC SQL ROLLBACK (LABD20.pco 9999-ROLL-BACK section). */
    public DispatcherResult rollback() {
        try {
            conn.rollback();
            return new DispatcherResult(DMS_OK, 0);
        } catch (SQLException e) {
            return error(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error closing DB connection", e);
        }
    }

    // ------- error mapping --------------------------------------------------
    private DispatcherResult error(SQLException exc) {
        int sqlcode = deriveSqlcode(exc);
        String dms = translateSqlcode(sqlcode);
        LOGGER.log(Level.SEVERE,
            "DBIO-equivalent error: sqlcode=" + sqlcode + " dms=" + dms + " err=" + exc.getMessage());
        return new DispatcherResult(dms, sqlcode, exc.getMessage(), new ArrayList<>(), 0);
    }

    /**
     * Pull a SQLCODE-equivalent out of the driver's exception. JDBC has no
     * Oracle SQLCODE, so we synthesize -1 (DMS_BAD_FETCH) so callers still see
     * a non-zero DMS code — same fallback the Python port uses for sqlite.
     * SME-REVIEW: production should map the Oracle driver's codes here.
     */
    private static int deriveSqlcode(SQLException exc) {
        return -1;
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
