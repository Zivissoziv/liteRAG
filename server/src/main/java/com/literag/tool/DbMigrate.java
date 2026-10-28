package com.literag.tool;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 旧版 H2 元数据库 → SQLite 的一次性搬运工具。
 *
 * 用法（在 server 目录下，脚本见 scripts/migrate-db.bat）：
 *   java -cp "target/classes;<依赖>" com.literag.tool.DbMigrate [h2库路径] [sqlite库路径] [--force]
 *
 * 默认：h2=./data/db/literag，sqlite=./data/db/literag.db
 * 不加 --force 时只做预检并打印各表行数，不会写任何数据。
 *
 * 前置条件：新库的表结构必须已经存在（先以 SQLite 配置启动一次服务，让 Hibernate 建表）。
 * 迁移为覆盖式：先清空目标表，再按原主键写入，因此可在重复执行。
 */
public final class DbMigrate {

    public static void main(String[] args) throws Exception {
        List<String> plain = new ArrayList<>();
        boolean force = false;
        for (String a : args) {
            if ("--force".equals(a)) {
                force = true;
            } else if (!a.isBlank()) {
                plain.add(a);
            }
        }
        String h2Path = plain.size() > 0 ? plain.get(0) : "./data/db/literag";
        String litePath = plain.size() > 1 ? plain.get(1) : "./data/db/literag.db";

        String h2Url = "jdbc:h2:file:" + abs(h2Path) + ";IFEXISTS=TRUE;MODE=MySQL";
        String liteUrl = "jdbc:sqlite:" + abs(litePath);

        System.out.println("源库  H2     : " + h2Url);
        System.out.println("目标库 SQLite : " + liteUrl);
        System.out.println("模式         : " + (force ? "写入（覆盖目标已有数据）" : "预检（不写入，加 --force 执行）"));

        try (Connection src = DriverManager.getConnection(h2Url, "sa", "");
             Connection dst = DriverManager.getConnection(liteUrl)) {

            List<String> tables = tables(src);
            if (tables.isEmpty()) {
                System.out.println("源库没有表，无需迁移。");
                return;
            }

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : tables) {
                counts.put(t, count(src, t));
            }

            List<String> missing = new ArrayList<>();
            for (String t : tables) {
                if (!exists(dst, t)) {
                    missing.add(t);
                }
            }
            if (!missing.isEmpty()) {
                System.out.println();
                System.out.println("目标库缺少这些表：" + missing);
                System.out.println("请先用 SQLite 配置启动一次服务（Hibernate 会自动建表），然后重跑本工具。");
                System.exit(2);
            }

            System.out.println();
            System.out.printf("%-24s %10s%n", "表", "行数");
            counts.forEach((t, c) -> System.out.printf("%-24s %10d%n", t, c));
            System.out.println();

            if (!force) {
                System.out.println("预检完成，未写入任何数据。确认无误后加 --force 再执行。");
                return;
            }

            dst.setAutoCommit(false);
            long total = 0;
            try {
                for (String t : tables) {
                    total += copy(src, dst, t);
                }
                dst.commit();
            } catch (Exception e) {
                dst.rollback();
                throw e;
            } finally {
                dst.setAutoCommit(true);
            }

            // 校准各表自增序列，避免迁移后新数据主键回退
            for (String t : tables) {
                bumpSequence(dst, t);
            }

            System.out.println();
            System.out.printf("迁移完成，共写入 %d 行。%n", total);
            System.out.printf("%-24s %10s %10s%n", "表", "源", "目标");
            for (String t : tables) {
                System.out.printf("%-24s %10d %10d%n", t, counts.get(t), count(dst, t));
            }
        }
    }

    private static long copy(Connection src, Connection dst, String table) throws Exception {
        List<String> srcCols = columns(src, table);
        List<String> dstCols = columns(dst, table);
        Set<String> lower = new LinkedHashSet<>();
        for (String c : dstCols) {
            lower.add(c.toLowerCase(Locale.ROOT));
        }
        List<String> common = new ArrayList<>();
        for (String c : srcCols) {
            if (lower.contains(c.toLowerCase(Locale.ROOT))) {
                common.add(c);
            }
        }
        if (common.isEmpty()) {
            return 0;
        }

        String cols = String.join(", ", common);
        String marks = String.join(", ", common.stream().map(c -> "?").toList());

        try (Statement st = dst.createStatement()) {
            st.executeUpdate("DELETE FROM " + quote(table));
        }

        long n = 0;
        try (Statement q = src.createStatement();
             ResultSet rs = q.executeQuery("SELECT " + cols + " FROM " + quote(table));
             PreparedStatement ps = dst.prepareStatement("INSERT INTO " + quote(table) + " (" + cols + ") VALUES (" + marks + ")")) {

            ResultSetMetaData md = rs.getMetaData();
            int batch = 0;
            while (rs.next()) {
                for (int i = 1; i <= common.size(); i++) {
                    bind(ps, i, md, rs);
                }
                ps.addBatch();
                n++;
                if (++batch % 200 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
        System.out.println("  " + table + " -> " + n + " 行");
        return n;
    }

    private static void bind(PreparedStatement ps, int i, ResultSetMetaData md, ResultSet rs) throws Exception {
        int type = md.getColumnType(i);
        switch (type) {
            case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> ps.setBytes(i, rs.getBytes(i));
            case Types.CLOB, Types.NCLOB -> ps.setString(i, rs.getString(i));
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                java.sql.Timestamp ts = rs.getTimestamp(i);
                if (ts == null) {
                    ps.setNull(i, Types.TIMESTAMP);
                } else {
                    ps.setTimestamp(i, ts);
                }
            }
            case Types.DATE -> {
                java.sql.Date d = rs.getDate(i);
                if (d == null) {
                    ps.setNull(i, Types.DATE);
                } else {
                    ps.setDate(i, d);
                }
            }
            case Types.BOOLEAN, Types.BIT -> {
                boolean b = rs.getBoolean(i);
                if (rs.wasNull()) {
                    ps.setNull(i, Types.BOOLEAN);
                } else {
                    ps.setBoolean(i, b);
                }
            }
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                int v = rs.getInt(i);
                if (rs.wasNull()) {
                    ps.setNull(i, type);
                } else {
                    ps.setInt(i, v);
                }
            }
            case Types.BIGINT -> {
                long v = rs.getLong(i);
                if (rs.wasNull()) {
                    ps.setNull(i, Types.BIGINT);
                } else {
                    ps.setLong(i, v);
                }
            }
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> {
                double v = rs.getDouble(i);
                if (rs.wasNull()) {
                    ps.setNull(i, type);
                } else {
                    ps.setDouble(i, v);
                }
            }
            default -> {
                Object v = rs.getObject(i);
                if (v == null) {
                    ps.setObject(i, null);
                } else if (v instanceof Character c) {
                    ps.setString(i, String.valueOf(c));
                } else {
                    ps.setObject(i, v);
                }
            }
        }
    }

    private static void bumpSequence(Connection dst, String table) {
        try (Statement st = dst.createStatement()) {
            st.executeUpdate("UPDATE sqlite_sequence SET seq = (SELECT IFNULL(MAX(id), 0) FROM " + quote(table)
                    + ") WHERE name = '" + table + "' AND seq < (SELECT IFNULL(MAX(id), 0) FROM " + quote(table) + ")");
        } catch (Exception ignored) {
            // 表没有自增列时会报错，忽略
        }
    }

    private static List<String> tables(Connection c) throws Exception {
        List<String> list = new ArrayList<>();
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String name = rs.getString("TABLE_NAME");
                if (schema != null && !"PUBLIC".equalsIgnoreCase(schema)) {
                    continue;
                }
                if (name.toUpperCase(Locale.ROOT).startsWith("SYS")) {
                    continue;
                }
                list.add(name);
            }
        }
        list.sort(String::compareTo);
        return list;
    }

    private static List<String> columns(Connection c, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        DatabaseMetaData md = c.getMetaData();
        try (ResultSet rs = md.getColumns(null, null, table, "%")) {
            while (rs.next()) {
                cols.add(rs.getString("COLUMN_NAME"));
            }
        }
        if (cols.isEmpty()) {
            // SQLite 的 DatabaseMetaData 对大小写敏感，退化用 PRAGMA
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
                while (rs.next()) {
                    cols.add(rs.getString("name"));
                }
            }
        }
        return cols;
    }

    private static boolean exists(Connection c, String table) throws Exception {
        return !columns(c, table).isEmpty();
    }

    private static long count(Connection c, String table) {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + quote(table))) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String quote(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private static String abs(String path) {
        return new File(path).getAbsolutePath().replace('\\', '/');
    }

    private DbMigrate() {
    }
}
