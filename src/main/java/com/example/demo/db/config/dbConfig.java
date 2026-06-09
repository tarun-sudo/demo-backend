package com.example.demo.db.config;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class dbConfig {

    private static final String DB_URL = firstNonBlank(
            System.getenv("DB_URL"),
            System.getenv("SPRING_DATASOURCE_URL"),
            "jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    private static final String DB_USERNAME = firstNonBlank(
            System.getenv("DB_USERNAME"),
            System.getenv("SPRING_DATASOURCE_USERNAME"),
            "sa");
    private static final String DB_PASSWORD = firstNonBlank(
            System.getenv("DB_PASSWORD"),
            System.getenv("SPRING_DATASOURCE_PASSWORD"),
            "");

    public static void uploadFile(File file) throws IOException, SQLException {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("A valid CSV file is required");
        }
        uploadLines(file.getName(), Files.readAllLines(file.toPath()));
    }

    public static void uploadStream(String fileName, InputStream stream) throws IOException, SQLException {
        if (stream == null) {
            throw new IllegalArgumentException("A valid CSV file stream is required");
        }
        String safeFileName = (fileName == null || fileName.isBlank()) ? "uploaded_data.csv" : fileName;
        List<String> lines;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            lines = br.lines().toList();
        }
        uploadLines(safeFileName, lines);
    }

    private static void uploadLines(String sourceName, List<String> lines) throws SQLException {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String tableName = sanitizeIdentifier(removeExtension(sourceName), "uploaded_data");
        List<String> columns = sanitizeHeaders(parseCsvLine(lines.get(0)));
        List<String[]> dataRows = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                dataRows.add(parseCsvLine(lines.get(i)));
            }
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            conn.setAutoCommit(false);
            try {
                createTableIfMissing(conn, tableName, columns);
                insertRows(conn, tableName, columns, dataRows);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }

        System.out.println("Inserted " + dataRows.size() + " rows into table: " + tableName);
    }

    public static List<String> listTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
             ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String table = rs.getString("TABLE_NAME");
                if (isUserTable(schema, table)) {
                    tables.add(table);
                }
            }
        }
        Collections.sort(tables);
        return tables;
    }

    public static List<String> clearAllUserTables() throws SQLException {
        List<String> tables = listTables();
        if (tables.isEmpty()) {
            return tables;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(false);
            try {
                for (String table : tables) {
                    st.execute("DROP TABLE IF EXISTS " + quoteIdentifier(table));
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }

        return tables;
    }

    public static List<Map<String, Object>> getTableContentsForFile(String fileName) throws SQLException {
        String tableName = sanitizeIdentifier(removeExtension(fileName), "uploaded_data");
        return getTableContents(tableName);
    }

    public static List<Map<String, Object>> getTableContents(String tableName) throws SQLException {
        String safeTableName = sanitizeIdentifier(tableName, "uploaded_data");
        ensureRowIdColumnExists(safeTableName);
        String sql = "SELECT * FROM " + quoteIdentifier(safeTableName);
        List<Map<String, Object>> rows = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                addUiAliases(row, meta);
                rows.add(row);
            }
        }

        return rows;
    }

    public static boolean updateCellForFile(
            String fileName,
            long rowId,
            String columnName,
            String newValue
    ) throws SQLException {
        String tableName = sanitizeIdentifier(removeExtension(fileName), "uploaded_data");
        ensureRowIdColumnExists(tableName);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String resolvedColumn = resolveWritableColumn(conn, tableName, columnName);
            if (resolvedColumn == null) {
                String requested = sanitizeIdentifier(columnName, "col");
                throw new IllegalArgumentException("Column not found: " + requested);
            }
            String sql = "UPDATE " + quoteIdentifier(tableName)
                    + " SET " + quoteIdentifier(resolvedColumn) + " = ? WHERE \"_row_id\" = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, newValue);
                ps.setLong(2, rowId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    public static boolean deleteRowForFile(String fileName, long rowId) throws SQLException {
        String tableName = sanitizeIdentifier(removeExtension(fileName), "uploaded_data");
        ensureRowIdColumnExists(tableName);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            String sql = "DELETE FROM " + quoteIdentifier(tableName) + " WHERE \"_row_id\" = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, rowId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    private static void createTableIfMissing(Connection conn, String tableName, List<String> columns)
            throws SQLException {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(quoteIdentifier(tableName))
                .append(" (\"_row_id\" BIGINT GENERATED BY DEFAULT AS IDENTITY, ");

        for (String column : columns) {
            ddl.append(quoteIdentifier(column)).append(" TEXT, ");
        }

        ddl.setLength(ddl.length() - 2);
        ddl.append(")");

        try (Statement statement = conn.createStatement()) {
            statement.execute(ddl.toString());
        }
        ensureRowIdColumnExists(conn, tableName);
    }

    private static void insertRows(Connection conn, String tableName, List<String> columns, List<String[]> dataRows)
            throws SQLException {
        if (dataRows.isEmpty()) {
            return;
        }

        String columnNames = columns.stream()
                .map(dbConfig::quoteIdentifier)
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + quoteIdentifier(tableName) + " (" + columnNames + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String[] row : dataRows) {
                for (int i = 0; i < columns.size(); i++) {
                    String value = i < row.length ? row[i].trim() : null;
                    ps.setString(i + 1, value);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static String[] parseCsvLine(String line) {
        return line.split(",", -1);
    }

    private static List<String> sanitizeHeaders(String[] headers) {
        List<String> columns = new ArrayList<>();
        Set<String> usedNames = new LinkedHashSet<>();

        for (int i = 0; i < headers.length; i++) {
            String baseName = sanitizeIdentifier(headers[i], "column_" + (i + 1));
            String columnName = baseName;
            int suffix = 2;

            while (usedNames.contains(columnName)) {
                columnName = baseName + "_" + suffix;
                suffix++;
            }

            usedNames.add(columnName);
            columns.add(columnName);
        }

        if (columns.isEmpty()) {
            throw new IllegalArgumentException("File must contain at least one column");
        }

        return columns;
    }

    private static String sanitizeIdentifier(String value, String fallback) {
        String identifier = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        identifier = identifier.replaceAll("[^a-z0-9_]", "_");
        identifier = identifier.replaceAll("_+", "_");
        identifier = identifier.replaceAll("^_+|_+$", "");

        if (identifier.isBlank()) {
            identifier = fallback;
        }

        if (!Character.isLetter(identifier.charAt(0))) {
            identifier = "upload_" + identifier;
        }

        return identifier;
    }

    private static String removeExtension(String fileName) {
        return fileName.replaceFirst("\\.[^.]+$", "");
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static void ensureRowIdColumnExists(String tableName) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD)) {
            ensureRowIdColumnExists(conn, tableName);
        }
    }

    private static void ensureRowIdColumnExists(Connection conn, String tableName) throws SQLException {
        if (columnExists(conn, tableName, "_row_id")) {
            return;
        }
        String alter = "ALTER TABLE " + quoteIdentifier(tableName)
                + " ADD COLUMN \"_row_id\" BIGINT GENERATED BY DEFAULT AS IDENTITY";
        try (Statement st = conn.createStatement()) {
            st.execute(alter);
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private static List<String> getOrderedColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        String sql = "SELECT * FROM " + quoteIdentifier(tableName) + " WHERE 1 = 0";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
        }
        return columns;
    }

    private static String resolveWritableColumn(Connection conn, String tableName, String requestedColumn)
            throws SQLException {
        List<String> orderedColumns = getOrderedColumns(conn, tableName);
        if (orderedColumns.isEmpty()) {
            return null;
        }

        String requested = sanitizeIdentifier(requestedColumn, "col");
        if ("_row_id".equalsIgnoreCase(requested)) {
            throw new IllegalArgumentException("Cannot update internal row id column");
        }

        for (String column : orderedColumns) {
            if (column.equalsIgnoreCase(requestedColumn) || column.equalsIgnoreCase(requested)) {
                if ("_row_id".equalsIgnoreCase(column)) {
                    throw new IllegalArgumentException("Cannot update internal row id column");
                }
                return column;
            }
        }

        List<String> dataColumns = orderedColumns.stream()
                .filter(col -> !"_row_id".equalsIgnoreCase(col))
                .toList();
        if (dataColumns.isEmpty()) {
            return null;
        }

        if ("customer".equals(requested)) {
            return dataColumns.get(0);
        }
        if ("status".equals(requested) && dataColumns.size() >= 2) {
            return dataColumns.get(1);
        }
        if ("amount".equals(requested) && dataColumns.size() >= 3) {
            return dataColumns.get(2);
        }

        return null;
    }

    private static void addUiAliases(Map<String, Object> row, ResultSetMetaData meta) throws SQLException {
        if (row.containsKey("customer") && row.containsKey("status") && row.containsKey("amount")) {
            return;
        }

        List<String> orderedColumns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String name = meta.getColumnLabel(i);
            if (!"_row_id".equalsIgnoreCase(name)) {
                orderedColumns.add(name);
            }
        }
        if (orderedColumns.isEmpty()) {
            return;
        }

        if (!row.containsKey("customer")) {
            row.put("customer", row.get(orderedColumns.get(0)));
        }
        if (!row.containsKey("status") && orderedColumns.size() >= 2) {
            row.put("status", row.get(orderedColumns.get(1)));
        }
        if (!row.containsKey("amount") && orderedColumns.size() >= 3) {
            row.put("amount", row.get(orderedColumns.get(2)));
        }
    }

    private static boolean isUserTable(String schema, String table) {
        if (table == null || table.isBlank()) {
            return false;
        }
        if (schema == null || schema.isBlank()) {
            return true;
        }
        String normalized = schema.toLowerCase(Locale.ROOT);
        return !"information_schema".equals(normalized)
                && !"pg_catalog".equals(normalized)
                && !"sys".equals(normalized);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
