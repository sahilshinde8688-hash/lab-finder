package com.sakec.smartspace.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Small JDBC helper. Every statement is a PreparedStatement and every connection is closed. */
public final class Db {
    @FunctionalInterface
    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private Db() { }

    public static Connection open() {
        try {
            Connection c = DriverManager.getConnection(Config.get("db.url", "jdbc:sqlite:smartspace.db"));
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys = ON");
                s.execute("PRAGMA busy_timeout = 5000");
            }
            return c;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database", e);
        }
    }

    // ---- queries on a fresh connection ------------------------------------------------------

    public static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = open()) {
            return query(c, sql, mapper, params);
        } catch (SQLException e) {
            throw new DataAccessException("Query failed", e);
        }
    }

    public static <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(String sql, Object... params) {
        try (Connection c = open()) {
            return update(c, sql, params);
        } catch (SQLException e) {
            throw new DataAccessException("Update failed", e);
        }
    }

    public static long insert(String sql, Object... params) {
        try (Connection c = open()) {
            return insert(c, sql, params);
        } catch (SQLException e) {
            throw new DataAccessException("Insert failed", e);
        }
    }

    // ---- statements on an existing connection (used for transactions) ----------------------

    public static <T> List<T> query(Connection c, String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(mapper.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Query failed", e);
        }
    }

    public static int update(Connection c, String sql, Object... params) {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Update failed", e);
        }
    }

    public static long insert(Connection c, String sql, Object... params) {
        update(c, sql, params);
        Long id = query(c, "SELECT last_insert_rowid()", rs -> rs.getLong(1)).get(0);
        return id;
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            int idx = i + 1;
            if (v == null) ps.setNull(idx, Types.NULL);
            else if (v instanceof Boolean b) ps.setInt(idx, b ? 1 : 0);
            else if (v instanceof LocalDateTime t) ps.setString(idx, Dt.toDb(t));
            else if (v instanceof LocalDate d) ps.setString(idx, Dt.toDb(d));
            else if (v instanceof Enum<?> e) ps.setString(idx, e.name());
            else ps.setObject(idx, v);
        }
    }

    /** Runs work in one transaction: commit on success, rollback on any exception. */
    public static <T> T inTransaction(java.util.function.Function<Connection, T> work) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Transaction failed", e);
        }
    }

    // ---- schema / scripts --------------------------------------------------------------------

    public static void initSchema() {
        runScript("/database/schema.sql");
        runScript("/database/inventory.sql");
    }

    public static void runScript(String resource) {
        String text;
        try (InputStream in = Db.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + resource, e);
        }
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\n")) {
            if (!line.trim().startsWith("--")) cleaned.append(line).append('\n');
        }
        try (Connection c = open(); Statement st = c.createStatement()) {
            for (String stmt : cleaned.toString().split(";")) {
                if (!stmt.isBlank()) st.execute(stmt);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Script failed: " + resource, e);
        }
    }
}
