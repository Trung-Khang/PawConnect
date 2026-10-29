import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

// Reads credentials from environment only. Backups must remain outside Git.
public class CouponDatabaseMaintenance {
    static String q(String value) { return "`" + value.replace("`", "``") + "`"; }
    static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) { byte[] b = new byte[65536]; for (int n; (n = in.read(b)) > 0;) digest.update(b, 0, n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String scalar(Connection db, String sql) throws Exception {
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
    static List<String> tables(Connection db) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")) { while (r.next()) names.add(r.getString(1)); }
        Collections.sort(names); return names;
    }
    static Properties load(Path p) throws Exception { Properties data = new Properties(); try (Reader r = Files.newBufferedReader(p)) { data.load(r); } return data; }
    static void backup(Connection db, Path folder) throws Exception {
        Files.createDirectories(folder);
        Path sql = folder.resolve("database.sql");
        if (Files.exists(sql) || Files.exists(folder.resolve("manifest.properties"))) throw new IllegalStateException("Backup already exists; choose a new folder");
        db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); db.setAutoCommit(false);
        db.setReadOnly(true);
        Properties manifest = new Properties(); long total = 0;
        try (BufferedWriter out = Files.newBufferedWriter(folder.resolve("database.partial"), StandardCharsets.UTF_8)) {
            out.write("-- Private PawConnect database snapshot; contains user data. Never commit.\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n");
            List<String> names = tables(db);
            for (String table : names) {
                try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SHOW CREATE TABLE " + q(table))) { r.next(); out.write(r.getString(2) + ";\n"); }
                long count = 0;
                try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM " + q(table))) {
                    ResultSetMetaData m = r.getMetaData(); int columns = m.getColumnCount();
                    List<String> columnNames = new ArrayList<>(); for (int i = 1; i <= columns; i++) columnNames.add(q(m.getColumnName(i)));
                    while (r.next()) {
                        List<String> values = new ArrayList<>();
                        for (int i = 1; i <= columns; i++) {
                            Object v = r.getObject(i);
                            if (v == null) values.add("NULL");
                            else if (v instanceof Number) values.add(v.toString());
                            else if (v instanceof Boolean b) values.add(b ? "1" : "0");
                            else {
                                int type = m.getColumnType(i);
                                boolean binary = type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB || type == Types.BIT;
                                String hex = HexFormat.of().formatHex(binary ? r.getBytes(i) : r.getString(i).getBytes(StandardCharsets.UTF_8));
                                values.add(binary ? "X'" + hex + "'" : "CONVERT(X'" + hex + "' USING utf8mb4)");
                            }
                        }
                        out.write("INSERT INTO " + q(table) + " (" + String.join(",", columnNames) + ") VALUES (" + String.join(",", values) + ");\n"); count++;
                    }
                }
                String queried = scalar(db, "SELECT COUNT(*) FROM " + q(table));
                if (!queried.equals(Long.toString(count))) throw new IllegalStateException("Snapshot row count mismatch");
                manifest.setProperty("rows." + table, Long.toString(count)); total += count;
            }
            manifest.setProperty("tableCount", Integer.toString(names.size()));
            manifest.setProperty("orderTotal", scalar(db, "SELECT COALESCE(SUM(total_amount),0) FROM orders"));
            out.write("SET FOREIGN_KEY_CHECKS=1;\n");
        } finally { db.rollback(); db.setReadOnly(false); db.setAutoCommit(true); }
        Files.move(folder.resolve("database.partial"), sql);
        manifest.setProperty("sha256", hash(sql)); manifest.setProperty("rows", Long.toString(total));
        try (Writer out = Files.newBufferedWriter(folder.resolve("manifest.properties"))) { manifest.store(out, "Private snapshot counts and integrity; not a restoration test"); }
        System.out.println("BACKUP_OK tables=" + manifest.getProperty("tableCount") + " rows=" + total + " sha256=" + manifest.getProperty("sha256"));
    }
    static void migrate(Connection db, Path folder) throws Exception {
        Properties manifest = load(folder.resolve("manifest.properties"));
        if (!hash(folder.resolve("database.sql")).equals(manifest.getProperty("sha256"))) throw new IllegalStateException("Backup checksum mismatch");
        String beforeCount = scalar(db, "SELECT COUNT(*) FROM orders");
        String beforeTotal = scalar(db, "SELECT COALESCE(SUM(total_amount),0) FROM orders");
        String delimiter = ";"; StringBuilder statement = new StringBuilder();
        Path migration = Path.of("data-pipeline/migrations/V4_9_0__discount_coupons_mysql.sql");
        for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;
            if (trimmed.startsWith("DELIMITER ")) { delimiter = trimmed.substring(10).trim(); continue; }
            statement.append(line).append('\n');
            String current = statement.toString().stripTrailing();
            if (current.endsWith(delimiter)) {
                try (Statement s = db.createStatement()) { s.execute(current.substring(0, current.length() - delimiter.length())); }
                statement.setLength(0);
            }
        }
        if (!statement.toString().isBlank()) throw new IllegalStateException("Incomplete migration statement");
        String columns = scalar(db, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name IN ('coupon_id','coupon_code','coupon_name','discount_amount','subtotal_amount')");
        if (!columns.equals("5") || !tables(db).contains("discount_coupons")) throw new IllegalStateException("Missing coupon schema");
        if (!beforeCount.equals(scalar(db, "SELECT COUNT(*) FROM orders")) || !beforeTotal.equals(scalar(db, "SELECT COALESCE(SUM(total_amount),0) FROM orders"))) throw new IllegalStateException("Order totals changed during migration; investigate concurrent activity");
        System.out.println("MIGRATION_OK columns=5 historicalOrderCountAndTotalsUnchanged=true");
    }
    static void restoreVerify(Connection db, Path folder) throws Exception {
        Properties source = load(folder.resolve("manifest.properties"));
        if (!hash(folder.resolve("database.sql")).equals(source.getProperty("sha256"))) throw new IllegalStateException("Backup checksum mismatch");
        Path marker = folder.resolve("restore.properties");
        if (Files.exists(marker)) throw new IllegalStateException("Restore marker already exists; inspect it before starting another restore");
        String catalog = "pawconnect_coupon_restore_" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString().replace("-", "") + "_" + UUID.randomUUID().toString().substring(0, 8);
        String original = db.getCatalog();
        try (Statement s = db.createStatement()) { s.execute("CREATE DATABASE " + q(catalog) + " CHARACTER SET utf8mb4"); }
        Properties record = new Properties(); record.setProperty("catalog", catalog); record.setProperty("backupHash", source.getProperty("sha256"));
        try (Writer out = Files.newBufferedWriter(marker)) { record.store(out, "Owned temporary restore schema; safe cleanup marker"); }
        db.setCatalog(catalog);
        StringBuilder statement = new StringBuilder();
        try {
            for (String line : Files.readAllLines(folder.resolve("database.sql"), StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("--")) continue;
                statement.append(line).append('\n');
                if (line.stripTrailing().endsWith(";")) {
                    String sql = statement.toString().stripTrailing();
                    try (Statement s = db.createStatement()) { s.execute(sql.substring(0, sql.length() - 1)); }
                    statement.setLength(0);
                }
            }
            for (String key : source.stringPropertyNames()) if (key.startsWith("rows.")) {
                if (!source.getProperty(key).equals(scalar(db, "SELECT COUNT(*) FROM " + q(key.substring(5))))) throw new IllegalStateException("Restored row count mismatch");
            }
            if (!source.getProperty("orderTotal").equals(scalar(db, "SELECT COALESCE(SUM(total_amount),0) FROM orders"))) throw new IllegalStateException("Restored order total mismatch");
            migrate(db, folder); migrate(db, folder);
            System.out.println("RESTORE_VERIFY_OK tables=" + source.getProperty("tableCount") + " rows=" + source.getProperty("rows") + " migrationRepeated=true");
        } finally { db.setCatalog(original); }
    }
    static void dropTest(Connection db, Path folder) throws Exception {
        Properties marker = load(folder.resolve("restore.properties"));
        String catalog = marker.getProperty("catalog");
        if (catalog == null || !catalog.matches("pawconnect_coupon_restore_[0-9]{8}_[a-f0-9]{8}") || catalog.equals(db.getCatalog())) throw new IllegalStateException("Not an owned temporary test schema");
        if (!marker.getProperty("backupHash").equals(load(folder.resolve("manifest.properties")).getProperty("sha256"))) throw new IllegalStateException("Temporary schema ownership marker mismatch");
        try (Statement s = db.createStatement()) { s.execute("DROP DATABASE " + q(catalog)); }
        System.out.println("TEMPORARY_TEST_SCHEMA_REMOVED productionDatabaseUntouched=true backupPreserved=true");
    }
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String host = System.getenv("MYSQL_HOST"), port = System.getenv("MYSQL_PORT");
            if (host == null || port == null || !port.matches("[0-9]+")) throw new IllegalArgumentException("Missing database configuration");
            String url = "jdbc:mysql://" + host + ":" + port + "/?sslMode=PREFERRED&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000&socketTimeout=120000";
            try (Connection db = DriverManager.getConnection(url, System.getenv("MYSQL_USER"), System.getenv("MYSQL_PASSWORD"))) {
                String catalog = System.getenv("MYSQL_DATABASE");
                if (catalog == null || catalog.isBlank()) {
                    List<String> candidates = new ArrayList<>();
                    try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT table_schema FROM information_schema.tables WHERE table_name IN ('orders','users','products','service_bookings') GROUP BY table_schema HAVING COUNT(*)=4")) { while (r.next()) if (!r.getString(1).startsWith("pawconnect_coupon_restore_")) candidates.add(r.getString(1)); }
                    if (candidates.size() != 1) throw new IllegalStateException("Ambiguous application database; set MYSQL_DATABASE explicitly");
                    catalog = candidates.get(0);
                }
                db.setCatalog(catalog);
                if (!tables(db).containsAll(List.of("orders", "users", "products", "service_bookings"))) throw new IllegalStateException("Not a PawConnect database");
                String mode = args[0].toLowerCase(Locale.ROOT);
                if (mode.equals("inspect")) System.out.println("CONNECTION_OK tables=" + tables(db).size() + " orders=" + scalar(db, "SELECT COUNT(*) FROM orders") + " couponSchemaPresent=" + tables(db).contains("discount_coupons") + " campaigns=" + (tables(db).contains("discount_coupons") ? scalar(db, "SELECT COUNT(*) FROM discount_coupons") : "0"));
                else if (mode.equals("backup")) backup(db, Path.of(args[1]));
                else if (mode.equals("migrate")) migrate(db, Path.of(args[1]));
                else if (mode.equals("restoreverify")) restoreVerify(db, Path.of(args[1]));
                else if (mode.equals("droptest")) dropTest(db, Path.of(args[1]));
                else throw new IllegalArgumentException("Unknown mode");
            }
        } catch (Exception error) {
            List<String> causes = new ArrayList<>();
            for (Throwable e = error; e != null && causes.size() < 8; e = e.getCause()) causes.add(e.getClass().getSimpleName());
            System.err.println("Database operation failed: " + String.join(" -> ", causes) + ". No credentials printed.");
            System.exit(1);
        }
    }
}
