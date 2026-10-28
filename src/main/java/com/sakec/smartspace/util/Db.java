package com.sakec.smartspace.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small JDBC helper with connection pooling. Every statement is a PreparedStatement and every connection is closed. */
public final class Db {
    @FunctionalInterface
    public interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private record PooledItem(Connection connection, String url) { }

    private static final int MAX_POOL_SIZE = 10;
    private static final BlockingQueue<PooledItem> POOL = new LinkedBlockingQueue<>(MAX_POOL_SIZE);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Db::clearPool));
    }

    public static void clearPool() {
        PooledItem item;
        while ((item = POOL.poll()) != null) {
            try { item.connection().close(); } catch (Exception ignored) { }
        }
    }

    private Db() { }

    public static boolean isPostgres() {
        String url = Config.get("db.url", "");
        return url.startsWith("jdbc:postgresql:");
    }

    public static boolean isPostgres(Connection c) {
        try {
            return c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgresql");
        } catch (SQLException e) {
            return isPostgres();
        }
    }

    private static Connection createPhysicalConnection() {
        try {
            String url = Config.get("db.url", "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require");
            String user = Config.get("db.user", isPostgres() ? "postgres.ezcojvjsshecapesrsxs" : null);
            String pass = Config.get("db.password", null);
            if (isPostgres() && (pass == null || pass.isBlank() || pass.contains("YOUR_SUPABASE_DB_PASSWORD"))) {
                throw new DataAccessException("Supabase database password is not configured.\nPlease enter your Supabase database password to connect.");
            }
            Connection c;
            if (user != null && !user.isBlank()) {
                c = DriverManager.getConnection(url, user, pass);
            } else {
                c = DriverManager.getConnection(url);
            }
            if (!isPostgres(c)) {
                try (Statement s = c.createStatement()) {
                    s.execute("PRAGMA foreign_keys = ON");
                    s.execute("PRAGMA busy_timeout = 5000");
                    s.execute("PRAGMA journal_mode = WAL");
                }
            }
            return c;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database: " + e.getMessage(), e);
        }
    }

    public static Connection open() {
        String currentUrl = Config.get("db.url", "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require");
        Connection physical = null;
        while (!POOL.isEmpty()) {
            PooledItem candidate = POOL.poll();
            if (candidate != null) {
                if (!currentUrl.equals(candidate.url())) {
                    try { candidate.connection().close(); } catch (Exception ignored) { }
                    continue;
                }
                try {
                    if (!candidate.connection().isClosed() && candidate.connection().isValid(1)) {
                        physical = candidate.connection();
                        break;
                    } else {
                        try { candidate.connection().close(); } catch (Exception ignored) { }
                    }
                } catch (Exception e) {
                    try { candidate.connection().close(); } catch (Exception ignored) { }
                }
            }
        }
        if (physical == null) {
            physical = createPhysicalConnection();
        }
        return wrapPooled(physical, currentUrl);
    }

    private static Connection wrapPooled(Connection physical, String url) {
        AtomicBoolean closed = new AtomicBoolean(false);
        return (Connection) Proxy.newProxyInstance(
                Db.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (closed.compareAndSet(false, true)) {
                            try {
                                if (!physical.isClosed()) {
                                    if (!physical.getAutoCommit()) {
                                        physical.rollback();
                                        physical.setAutoCommit(true);
                                    }
                                    if (physical.isValid(1) && POOL.offer(new PooledItem(physical, url))) {
                                        return null;
                                    }
                                }
                            } catch (Exception ignored) { }
                            try { physical.close(); } catch (Exception ignored) { }
                        }
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return closed.get() || physical.isClosed();
                    }
                    if (closed.get()) {
                        throw new SQLException("Connection is closed");
                    }
                    if ("unwrap".equals(name) && args != null && args.length == 1 && ((Class<?>) args[0]).isInstance(physical)) {
                        return physical;
                    }
                    if ("isWrapperFor".equals(name) && args != null && args.length == 1) {
                        return ((Class<?>) args[0]).isInstance(physical);
                    }
                    try {
                        return method.invoke(physical, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );
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
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            if (isPostgres(c)) {
                Long id = query(c, "SELECT LASTVAL()", rs -> rs.getLong(1)).get(0);
                return id;
            } else {
                Long id = query(c, "SELECT last_insert_rowid()", rs -> rs.getLong(1)).get(0);
                return id;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Insert failed", e);
        }
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
        if (isPostgres()) {
            try (Connection c = open(); Statement s = c.createStatement()) {
                s.executeQuery("SELECT 1 FROM users LIMIT 1");
                return;
            } catch (SQLException ignored) {
                runScript("/database/schema-supabase.sql");
                runScript("/database/inventory.sql");
            }
            return;
        }
        runScript("/database/schema.sql");
        runScript("/database/inventory.sql");
        migrateV2EmailVerification();
    }

    /**
     * V2 migration: adds PENDING_VERIFICATION support to the users table.
     *
     * <p>SQLite does not support ALTER COLUMN, so we recreate the table preserving all data.
     * Foreign keys must be disabled during the rename/copy/drop cycle.
     */
    private static void migrateV2EmailVerification() {
        if (isPostgres()) return;
        try (Connection c = open()) {
            // Quick check: can we store PENDING_VERIFICATION? Use a savepoint probe.
            boolean needsMigration = false;
            c.setAutoCommit(false);
            try {
                Savepoint sp = c.setSavepoint("v2check");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO users(full_name,institutional_email,password_hash,role,"
                        + "account_status,must_change_password) "
                        + "VALUES('_v2check','_v2check@test.invalid','_hash','STUDENT',"
                        + "'PENDING_VERIFICATION',0)")) {
                    ps.executeUpdate();
                    c.rollback(sp); // constraint already allows it — no migration needed
                } catch (SQLException e) {
                    c.rollback(sp);
                    needsMigration = true;
                }
            } finally {
                c.setAutoCommit(true);
            }

            if (!needsMigration) return;

            Log.info("DB migration v2: adding PENDING_VERIFICATION to users.account_status …");

            // Disable FK enforcement for the duration of the table swap
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = OFF");
            }
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE users RENAME TO users_v1");
                st.execute("CREATE TABLE users ("
                        + "user_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "full_name TEXT NOT NULL CHECK (length(trim(full_name)) > 0),"
                        + "institutional_email TEXT NOT NULL UNIQUE COLLATE NOCASE,"
                        + "password_hash TEXT NOT NULL,"
                        + "role TEXT NOT NULL CHECK (role IN ('STUDENT','TEACHER','COORDINATOR')),"
                        + "department TEXT,"
                        + "account_status TEXT NOT NULL DEFAULT 'ACTIVE' "
                        + "CHECK (account_status IN ('ACTIVE','DISABLED','PENDING_VERIFICATION')),"
                        + "must_change_password INTEGER NOT NULL DEFAULT 0,"
                        + "failed_attempts INTEGER NOT NULL DEFAULT 0,"
                        + "locked_until TEXT,"
                        + "created_at TEXT NOT NULL DEFAULT "
                        + "(strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))"
                        + ")");
                st.execute("INSERT INTO users SELECT * FROM users_v1");
                st.execute("DROP TABLE users_v1");
                c.commit();
                Log.info("DB migration v2: complete.");
            } catch (SQLException e) {
                c.rollback();
                throw new DataAccessException("Migration v2 failed", e);
            } finally {
                c.setAutoCommit(true);
                // Always re-enable FK enforcement
                try (Statement st = c.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON");
                }
            }
        } catch (DataAccessException e) {
            throw e;
        } catch (SQLException e) {
            throw new DataAccessException("Migration v2 failed", e);
        }
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
        List<String> statements = splitStatements(cleaned.toString());
        try (Connection c = open(); Statement st = c.createStatement()) {
            for (String stmt : statements) {
                if (!stmt.isBlank()) st.execute(stmt);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Script failed: " + resource, e);
        }
    }

    private static List<String> splitStatements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDollarQuote = false;
        boolean inSingleQuote = false;
        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char ch = sql.charAt(i);
            if (ch == '\'' && !inDollarQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '$' && !inSingleQuote && i + 1 < len && sql.charAt(i + 1) == '$') {
                inDollarQuote = !inDollarQuote;
                current.append('$');
                i++;
                ch = '$';
            } else if (ch == ';' && !inSingleQuote && !inDollarQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) result.add(stmt);
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        String rem = current.toString().trim();
        if (!rem.isEmpty()) result.add(rem);
        return result;
    }
}
