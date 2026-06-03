package com.cognition.jvmigration.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the (DB-RTNCODE-DMS, DB-SQLCODE-NUM, DB-MESSAGE) tuple that
 * {@code DBIO.pco} writes back to its callers (cf. 9999-ERROR at
 * DBIO.pco:402-405). Java analog of {@code db_dispatcher.DispatcherResult}.
 */
public class DispatcherResult {
    public final String rtncodeDms;
    public final int sqlcode;
    public final String message;
    public final List<Object[]> rows;
    public final int rowcount;

    public DispatcherResult(String rtncodeDms, int sqlcode, String message,
                            List<Object[]> rows, int rowcount) {
        this.rtncodeDms = rtncodeDms;
        this.sqlcode = sqlcode;
        this.message = message;
        this.rows = rows != null ? rows : new ArrayList<>();
        this.rowcount = rowcount;
    }

    public DispatcherResult(String rtncodeDms, int sqlcode) {
        this(rtncodeDms, sqlcode, "", new ArrayList<>(), 0);
    }

    public boolean ok() {
        return DbDispatcher.DMS_OK.equals(rtncodeDms);
    }
}
