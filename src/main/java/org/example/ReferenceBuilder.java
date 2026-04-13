package org.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * ReferenceBuilder
 *
 * 读取 dataset/sqlReference.txt 中的 100 条 SQL（每条以 "-- Q{n}:" 注释行开始，紧跟一行真实 SQL），
 * 逐一在 PMS 数据库中执行，把结果写入 dataset/reference.txt。
 *
 * 输出格式：
 *   {n}|{question}|{result}
 * 其中 result 为查询返回的单行单列数值；若查询失败则写 "ERROR: {message}"。
 *
 * 使用方法：
 *   mvn compile exec:java -D"exec.mainClass"="org.example.ReferenceBuilder"
 */
public class ReferenceBuilder {

    // ---- 连接配置（从 application.properties 读取） ----
    private static final String DB_URL      = "jdbc:mysql://localhost:3306/pms" +
            "?useSSL=false&allowPublicKeyRetrieval=true&allowMultiQueries=true" +
            "&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String DB_USER     = "root";
    private static final String DB_PASSWORD = "12345678";

    private static final String SQL_REF_FILE  = "dataset/sqlReference.txt";
    private static final String QUESTIONS_FILE = "dataset/questions.txt";
    private static final String OUTPUT_FILE   = "dataset/reference.txt";

    public static void main(String[] args) throws Exception {

        // 1. 读取 questions.txt → Map<id, question>
        Map<Integer, String> questions = loadQuestions(QUESTIONS_FILE);
        System.out.println("[*] Loaded " + questions.size() + " questions.");

        // 2. 解析 sqlReference.txt → List<[id, sql]>
        List<int[]> idxList = new ArrayList<>();   // 存 id
        List<String> sqlList = new ArrayList<>();   // 存 SQL
        parseSqlReference(SQL_REF_FILE, idxList, sqlList);
        System.out.println("[*] Parsed " + sqlList.size() + " SQL statements.");

        // 3. 连接数据库，逐一执行
        System.out.println("[*] Connecting to " + DB_URL + " ...");
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                     new FileOutputStream(OUTPUT_FILE), StandardCharsets.UTF_8))) {

            System.out.println("[*] Connected. Starting execution...\n");

            for (int i = 0; i < sqlList.size(); i++) {
                int qId  = idxList.get(i)[0];
                String sql = sqlList.get(i);
                String question = questions.getOrDefault(qId, "（未找到题目）");
                String result;

                try (Statement stmt = conn.createStatement()) {
                    // 支持跨库查询，设置默认 catalog（不强制，因为 SQL 里已用 schema.table 格式）
                    ResultSet rs = stmt.executeQuery(sql);
                    if (rs.next()) {
                        Object val = rs.getObject(1);
                        result = (val == null) ? "0" : val.toString();
                    } else {
                        result = "0";
                    }
                    System.out.printf("  Q%3d ✅ → %s%n", qId, result);
                } catch (SQLException e) {
                    result = "ERROR: " + e.getMessage().replaceAll("[\r\n]+", " ");
                    System.out.printf("  Q%3d ❌ %s%n", qId, result);
                }

                pw.println(qId + "|" + question + "|" + result);
                pw.flush();
            }

            System.out.println("\n🎉 Done. Results written to " + OUTPUT_FILE);
        }
    }

    // -----------------------------------------------------------------------

    /**
     * 从 questions.txt 加载: "id|question" → Map<id, question>
     */
    private static Map<Integer, String> loadQuestions(String path) throws IOException {
        Map<Integer, String> map = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sep = line.indexOf('|');
                if (sep < 0) continue;
                try {
                    int id = Integer.parseInt(line.substring(0, sep).trim());
                    String q = line.substring(sep + 1).trim();
                    map.put(id, q);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /**
     * 解析 sqlReference.txt：
     *   格式：每个块以 "-- Q{n}: 问题文字" 开头，紧跟一行真实 SQL（不含注释）。
     *   空行、多行注释一律忽略。
     */
    private static void parseSqlReference(String path,
                                          List<int[]> idxOut,
                                          List<String> sqlOut) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            int pendingId = -1;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 识别注释行：-- Q{n}: ...
                if (line.startsWith("--")) {
                    // 尝试解析 Q 编号
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("--\\s*Q(\\d+):")
                            .matcher(line);
                    if (m.find()) {
                        pendingId = Integer.parseInt(m.group(1));
                    }
                    continue;  // 注释行本身不是 SQL
                }

                // 非注释行 = SQL
                if (pendingId > 0) {
                    idxOut.add(new int[]{pendingId});
                    sqlOut.add(line);
                    pendingId = -1;
                }
            }
        }
    }
}
