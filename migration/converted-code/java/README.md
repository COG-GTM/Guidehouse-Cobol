# LABA05.cbl → Java 21 Migration

> **STATUS: Demo output — pending SME review**

Modernized analog of `source/cobol/LABA05.cbl` (285-line fiscal-year JV-NUMBER reset program), migrated to Java 21 with Maven, JUnit 5 tests, and full COBOL↔Java traceability.

## Overview

LABA05 is a batch utility that resets `JV-NUMBER` to `1` on the `JV-CONTROL-REC` row in `CONTROL_RECORD_TABLE` at the start of each fiscal year. This Java migration faithfully ports the COBOL logic using:

- **Java 21** features: records, switch expressions, text blocks
- **JDBC** for database access (H2 in-memory for tests, Oracle for production)
- **SLF4J + Logback** for logging (replaces `DISPLAY ... UPON PRINTER`)
- **JUnit 5** with 12 test cases ported from the Python reference implementation

## Build

```bash
mvn clean package
```

## Run tests

```bash
mvn test
```

## Run the program

```bash
# With the packaged JAR (requires JDBC connection setup):
java -jar target/laba05-reset-1.0.0-SNAPSHOT.jar

# Or via Maven exec:
mvn exec:java -Dexec.mainClass="gov.jv.migration.Laba05Reset"
```

> **Note:** The CLI entrypoint requires a live database connection. For demo purposes, use the test suite which exercises all logic against H2 in-memory.

## File inventory

| File | Description |
| --- | --- |
| `pom.xml` | Maven project — Java 21, JUnit 5, H2, SLF4J+Logback |
| `src/main/java/.../Laba05Reset.java` | Main program — ports LABA05.cbl logic |
| `src/main/java/.../JvControlRecord.java` | Record type — maps JV-CONTROL-REC.cpy |
| `src/main/java/.../DbDispatcher.java` | JDBC wrapper — ports DBIO.pco contract |
| `src/main/java/.../DispatcherResult.java` | Result record — DMS code + rows |
| `src/main/java/.../DmsErrorCodes.java` | SQLCODE→DMS translation — ports DBIO.pco:374-398 |
| `src/test/java/.../Laba05ResetTest.java` | 9 test cases — byte layout, end-to-end, round-trip |
| `src/test/java/.../JvControlRecordTest.java` | 5 test cases — record parsing/serialization |
| `migration-report.html` | Single-page HTML migration report with side-by-side traceability |

## Migration report

Open [`migration-report.html`](./migration-report.html) in a browser for the full side-by-side COBOL↔Java comparison, data structure mapping, risk register, and test coverage table.

## Key risks

| # | Risk | Status |
| --- | --- | --- |
| 2 | Binary↔display JV-NUMBER | Mitigated (demo uses zoned display form) |
| 3 | Credential files | Resolved (connection externalized) |
| 6 | SQLCODE→DMS translation | Resolved (verbatim port) |

See [`migration/RISKS-AND-GAPS.md`](../../RISKS-AND-GAPS.md) for the full register.

## Traceability

Every Java source file includes:
1. File-level Javadoc citing the COBOL source file and line range
2. Per-method Javadoc citing specific COBOL paragraphs
3. Inline `// Source:` comments for non-obvious mappings
4. `// RISK N:` comments where risk register items apply
5. `// ASSUMPTION A-N:` comments where assumptions apply
