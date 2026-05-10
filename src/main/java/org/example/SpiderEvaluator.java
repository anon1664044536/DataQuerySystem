package org.example;

import com.alibaba.dashscope.app.ApplicationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

lass SpiderEvaluator {

    private static final int START_QID = 402;
    private static final int END_QID = 501; 
    private static final int THREAD_COUNT = 8; // 对应论文中提到的核心线程数

    public static void main(String[] args) {
        String devSqlFile = "spider/evaluation_examples/dev.sql";
        String tablesJsonFile = "spider/evaluation_examples/examples/tables.json";
        String predictionsFile = "spider/predictions.txt";
        String metricsFile = "spider/metrics_log.txt";

        if (DataManager.apiKey().isBlank() || DataManager.mqlTestAppId().isBlank() || DataManager.sqlTestAppId().isBlank()) {
            System.err.println("Error: DashScope API key or Test App ID is missing.");
            return;
        }

        AskingAgent agent = new AskingAgent(DataManager.apiKey(), DataManager.mqlTestAppId(), DataManager.sqlTestAppId());
        ObjectMapper mapper = new ObjectMapper();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        try {
            System.out.println("[*] Loading schemas from tables.json...");
            Map<String, String> schemas = loadSchemas(tablesJsonFile, mapper);

            System.out.println("[*] Loading questions from dev.sql...");
            List<TestCase> testCases = loadTestCases(devSqlFile);

            if (testCases.isEmpty()) {
                System.out.println("[!] No questions found.");
                return;
            }
            System.out.println("[*] Loaded " + testCases.size() + " test cases. Starting concurrent evaluation...");

            // 提交并发任务
            List<Future<EvalResult>> futures = new ArrayList<>();
            for (TestCase tc : testCases) {
                futures.add(executor.submit(() -> evaluateCase(tc, schemas, agent, mapper)));
            }

            // 收集结果并按序写入文件
            try (PrintWriter predWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(predictionsFile), "UTF-8"));
                 PrintWriter metricsWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(metricsFile), "UTF-8"))) {

                metricsWriter.println(String.format("%-5s | %-15s | %-15s | %-20s | %-15s | %-20s | %-15s",
                        "Q_ID", "DB_ID", "NL2MQL_Time(ms)", "NL2MQL_Tokens", "MQL2SQL_Time(ms)", "MQL2SQL_Tokens", "Total_Time(ms)"));
                metricsWriter.println("-".repeat(120));

                for (Future<EvalResult> future : futures) {
                    EvalResult res = future.get();
                    predWriter.println(res.cleanSql);
                    metricsWriter.println(res.metricLine);
                    System.out.println("     ✅ Q" + res.id + " Done.");
                }
                
                System.out.println("\n🎉 Evaluation fully completed concurrently.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    private static EvalResult evaluateCase(TestCase tc, Map<String, String> schemas, AskingAgent agent, ObjectMapper mapper) {
        String schemaText = schemas.getOrDefault(tc.dbId, "Unknown Schema");
        String mqlInput = "【当前数据库 Schema】\n" + schemaText + "\n\n【用户问题】\n" + tc.question;

        long mqlStart = System.currentTimeMillis();
        String mqlText = "";
        int mqlTokens = 0;
        try {
            ApplicationResult mqlResult = agent.getMqlResult(mqlInput);
            mqlText = mqlResult.getOutput().getText();
            mqlTokens = extractTokens(mapper, mqlResult);
        } catch (Exception e) {
            mqlText = "";
        }
        long mqlTime = System.currentTimeMillis() - mqlStart;

        long sqlStart = System.currentTimeMillis();
        String sqlText = "";
        int sqlTokens = 0;
        if (!mqlText.isEmpty()) {
            try {
                String sqlInput = "【原始问题】\n" + tc.question + "\n\n【当前数据库 Schema】\n" + schemaText + "\n\n【IR JSON】\n" + mqlText;
                ApplicationResult sqlResult = agent.getSqlResult(sqlInput);
                sqlText = sqlResult.getOutput().getText();
                sqlTokens = extractTokens(mapper, sqlResult);
            } catch (Exception e) {
                sqlText = "SELECT 1;";
            }
        }
        long sqlTime = System.currentTimeMillis() - sqlStart;

        String cleanSql = sqlText.replace("\n", " ").trim();
        if (cleanSql.startsWith("```sql")) cleanSql = cleanSql.substring(6).trim();
        if (cleanSql.endsWith("```")) cleanSql = cleanSql.substring(0, cleanSql.length() - 3).trim();
        if (cleanSql.isEmpty()) cleanSql = "SELECT 1;";

        String metricLine = String.format("%-5d | %-15s | %-15d | %-20d | %-15d | %-20d | %-15d",
                tc.id, tc.dbId, mqlTime, mqlTokens, sqlTime, sqlTokens, (mqlTime + sqlTime));

        return new EvalResult(tc.id, cleanSql, metricLine);
    }

    private static Map<String, String> loadSchemas(String tJsonPath, ObjectMapper mapper) throws Exception {
        Map<String, String> schemaMap = new HashMap<>();
        JsonNode root = mapper.readTree(new File(tJsonPath));
        for (JsonNode dbNode : root) {
            String dbId = dbNode.get("db_id").asText();
            JsonNode tableNames = dbNode.get("table_names_original");
            JsonNode columnNames = dbNode.get("column_names_original");
            StringBuilder schemaStr = new StringBuilder();
            Map<Integer, List<String>> tableCols = new HashMap<>();
            for (JsonNode colNode : columnNames) {
                int tblIdx = colNode.get(0).asInt();
                if (tblIdx >= 0) tableCols.computeIfAbsent(tblIdx, k -> new ArrayList<>()).add(colNode.get(1).asText());
            }
            for (int i = 0; i < tableNames.size(); i++) {
                schemaStr.append("Table: ").append(tableNames.get(i).asText()).append(" (");
                List<String> cols = tableCols.get(i);
                if (cols != null) schemaStr.append(String.join(", ", cols));
                schemaStr.append(")\n");
            }
            schemaMap.put(dbId, schemaStr.toString());
        }
        return schemaMap;
    }

    private static List<TestCase> loadTestCases(String filePath) throws Exception {
        List<TestCase> cases = new ArrayList<>();
        Pattern pattern = Pattern.compile("Question\\s+(\\d+):\\s*(.*?)\\s*\\|\\|\\|\\s*(.*)");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().startsWith("Question ")) {
                    Matcher m = pattern.matcher(line);
                    if (m.find()) {
                        int qId = Integer.parseInt(m.group(1));
                        if (qId >= START_QID && qId <= END_QID) cases.add(new TestCase(qId, m.group(2).trim(), m.group(3).trim()));
                    }
                }
            }
        }
        return cases;
    }

    private static int extractTokens(ObjectMapper mapper, ApplicationResult result) {
        if (result == null) return 0;
        try {
            if (result.getUsage() != null) return result.getUsage().getTotalTokens();
        } catch (Exception ignored) {}
        return 0;
    }

    static class TestCase {
        int id; String question; String dbId;
        TestCase(int id, String question, String dbId) { this.id = id; this.question = question; this.dbId = dbId; }
    }

    static class EvalResult {
        int id; String cleanSql; String metricLine;
        EvalResult(int id, String cleanSql, String metricLine) { this.id = id; this.cleanSql = cleanSql; this.metricLine = metricLine; }
    }
}
