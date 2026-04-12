package org.example;

import java.sql.*;
import java.util.*;

public class SqlQuery {

    private final String url;
    private final String user;
    private final String password;

    public static class QueryResult {
        public final List<String>       columns;
        public final List<List<String>> rows;
        public final long               queryMs;
        public final String             errorMsg;

        //成功构造
        public QueryResult(List<String> columns, List<List<String>> rows, long queryMs) {
            this.columns  = columns;
            this.rows     = rows;
            this.queryMs  = queryMs;
            this.errorMsg = null;
        }

        //失败构造
        public QueryResult(String errorMsg) {
            this.columns  = Collections.emptyList();
            this.rows     = Collections.emptyList();
            this.queryMs  = 0;
            this.errorMsg = errorMsg;
        }

        public boolean isSuccess() { return errorMsg == null; }
    }

    public SqlQuery() {
        this.url      = String.format("jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                        DataManager.dbHost(), DataManager.dbPort(), DataManager.dbName());
        this.user     = DataManager.dbUser();
        this.password = DataManager.dbPassword();
    }

    public QueryResult execute(String sql) {
        if (sql == null || sql.isBlank()) {
            return new QueryResult("SQL 语句为空");
        }

        String cleanedSql = stripMarkdownCodeFence(sql);

        String normalizedSql = cleanedSql.trim()
                .replaceAll("(?i)\\bdw_power\\.", DataManager.dbName() + ".")
                .replaceAll("(?i)\\b`dw_power`\\.", "`" + DataManager.dbName() + "`.")
                .replaceAll("(?i)\\buse\\s+dw_power\\b", "USE " + DataManager.dbName());

        // AI 结果里可能带 `USE xxx;`，JDBC 连接已指定默认库，去掉前缀以避免非结果集语句导致异常。
        String executableSql = normalizedSql.replaceFirst("(?is)^\\s*USE\\s+[`\\w]+\\s*;\\s*", "").trim();
        if (executableSql.isBlank()) {
            return new QueryResult("SQL 语句为空");
        }

        long start = System.currentTimeMillis();

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement  stmt = conn.createStatement()) {

            stmt.setQueryTimeout(30);   // 30 秒超时
            boolean hasResultSet = stmt.execute(executableSql);
            if (!hasResultSet) {
                int affectedRows = stmt.getUpdateCount();
                long ms = System.currentTimeMillis() - start;
                return new QueryResult(
                        Collections.singletonList("affected_rows"),
                        Collections.singletonList(Collections.singletonList(String.valueOf(affectedRows))),
                        ms
                );
            }

            ResultSet rs = stmt.getResultSet();

            ResultSetMetaData meta    = rs.getMetaData();
            int               colCnt = meta.getColumnCount();

            //列名
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= colCnt; i++) {
                columns.add(meta.getColumnLabel(i));
            }

            //数据行
            List<List<String>> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= colCnt; i++) {
                    String val = rs.getString(i);
                    row.add(val != null ? val : "NULL");
                }
                rows.add(row);
            }

            long ms = System.currentTimeMillis() - start;
            return new QueryResult(columns, rows, ms);

        } catch (SQLException ex) {
            return new QueryResult("SQL 执行失败：" + ex.getMessage() + " (db=" + DataManager.dbName() + ")");
        }
    }

    private String stripMarkdownCodeFence(String sql) {
        String s = sql == null ? "" : sql.trim();
        // Handle forms like ```sqlSELECT ...``` or ```sql\nSELECT ...\n```
        s = s.replaceFirst("(?is)^\\s*```\\s*sql\\s*", "");
        s = s.replaceFirst("(?is)^\\s*```\\s*", "");
        s = s.replaceFirst("(?is)\\s*```\\s*$", "");
        return s.trim();
    }
}