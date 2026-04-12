package org.example;

import com.alibaba.dashscope.app.ApplicationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpiderEvaluator {

    // 配置抓取区间的题目数
    private static final int START_QID = 402;
    private static final int END_QID = 501; // 放开 100 题测试

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

        try {
            // 1. 加载 Spider 所有的 Database Schemas
            System.out.println("[*] Loading schemas from tables.json...");
            Map<String, String> schemas = loadSchemas(tablesJsonFile, mapper);

            // 2. 加载 dev.sql 中的测试区间
            System.out.println("[*] Loading questions from dev.sql...");
            List<TestCase> testCases = loadTestCases(devSqlFile);

            if (testCases.isEmpty()) {
                System.out.println("[!] No questions found in the specified range.");
                return;
            }
            System.out.println("[*] Loaded " + testCases.size() + " test cases.");

            List<String> metricsList = new ArrayList<>();
            metricsList.add(String.format("%-5s | %-15s | %-15s | %-20s | %-15s | %-20s | %-15s",
                    "Q_ID", "DB_ID", "NL2MQL_Time(ms)", "NL2MQL_Tokens", "MQL2SQL_Time(ms)", "MQL2SQL_Tokens", "Total_Time(ms)"));
            metricsList.add("-".repeat(120));

            // 初始化记录预测的流
            try (PrintWriter predWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(predictionsFile), "UTF-8"));
                 PrintWriter metricsWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(metricsFile), "UTF-8"))) {

                // 先写入头部
                for (String m : metricsList) {
                    metricsWriter.println(m);
                }

                // 3. 开始执行测试评估
                for (TestCase tc : testCases) {
                    System.out.println("\n---> Processing Q" + tc.id + " (DB: " + tc.dbId + "): " + tc.question);

                    String schemaText = schemas.getOrDefault(tc.dbId, "Unknown Schema");

                    // 巧妙注入 Schema：由于百炼配置固定了 Prompt，我们需要把 Schema 信息拼接到用户的提问文本中
                    // 若您的百炼 Prompt 已经硬性附带了 {spider_schema_placeholder}，你可以不用在这里拼。这里是兜底注入。
                    String mqlInput = "【当前数据库 Schema】\n" + schemaText + 
                                      "\n\n【用户问题】\n" + tc.question;

                    long mqlStart = System.currentTimeMillis();
                    ApplicationResult mqlResult = null;
                    String mqlText = "";
                    int mqlTokens = 0;
                    try {
                        mqlResult = agent.getMqlResult(mqlInput);
                        mqlText = mqlResult.getOutput().getText();
                        mqlTokens = extractTokens(mapper, mqlResult);
                    } catch (Exception e) {
                        System.err.println("Error in NL2MQL: " + e.getMessage());
                    }
                    long mqlTime = System.currentTimeMillis() - mqlStart;
                    
                    // =====================================
                    // MQL -> SQL 执行
                    // =====================================
                    System.out.println("     ✅ MQL Done. MQL output:\n" + mqlText);
                    long sqlStart = System.currentTimeMillis();
                    ApplicationResult sqlResult = null;
                    String sqlText = "";
                    int sqlTokens = 0;

                    if (!mqlText.isEmpty()) {
                        try {
                            // 注入原始问题供 MQL2SQL 做语义兜底校验（优先级低于 IR，仅辅助判断冗余列和不必要子句）
                            String sqlInput = "【原始问题】\n" + tc.question +
                                              "\n\n【当前数据库 Schema】\n" + schemaText +
                                              "\n\n【IR JSON】\n" + mqlText;

                            sqlResult = agent.getSqlResult(sqlInput);
                            sqlText = sqlResult.getOutput().getText();
                            sqlTokens = extractTokens(mapper, sqlResult);
                        } catch (Exception e) {
                            System.err.println("Error in MQL2SQL: " + e.getMessage());
                        }
                    }
                    long sqlTime = System.currentTimeMillis() - sqlStart;

                    long totalTime = mqlTime + sqlTime;
                    int totalTokens = mqlTokens + sqlTokens;

                    // 收缩并净化 SQL 用于 Exact Match
                    String cleanSql = sqlText.replace("\n", " ").trim();
                    if (cleanSql.startsWith("```sql")) {
                        cleanSql = cleanSql.substring(6).trim();
                    }
                    if (cleanSql.endsWith("```")) {
                        cleanSql = cleanSql.substring(0, cleanSql.length() - 3).trim();
                    }
                    // 保底机制
                    if (cleanSql.isEmpty()) {
                        cleanSql = "SELECT 1;";
                    }

                    // 写入 predictions
                    predWriter.println(cleanSql);
                    predWriter.flush();

                    // 写入 Metrics
                    String metricLine = String.format("%-5d | %-15s | %-15d | %-20d | %-15d | %-20d | %-15d",
                            tc.id, tc.dbId, mqlTime, mqlTokens, sqlTime, sqlTokens, totalTime);
                    metricsWriter.println(metricLine);
                    metricsWriter.flush();

                    System.out.println("     ✅ Done. Total Time: " + totalTime + "ms | Tokens: " + totalTokens);
                }
                
                System.out.println("\n🎉 Evaluation fully completed. Check predictions.txt and metrics_log.txt.");

            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> loadSchemas(String tJsonPath, ObjectMapper mapper) throws Exception {
        Map<String, String> schemaMap = new HashMap<>();
        JsonNode root = mapper.readTree(new File(tJsonPath));

        for (JsonNode dbNode : root) {
            String dbId = dbNode.get("db_id").asText();
            JsonNode tableNames = dbNode.get("table_names_original");
            JsonNode columnNames = dbNode.get("column_names_original");
            JsonNode foreignKeys = dbNode.get("foreign_keys");

            StringBuilder schemaStr = new StringBuilder();
            Map<Integer, List<String>> tableCols = new HashMap<>();

            for (JsonNode colNode : columnNames) {
                int tblIdx = colNode.get(0).asInt();
                String cName = colNode.get(1).asText();
                if (tblIdx >= 0) {
                    tableCols.computeIfAbsent(tblIdx, k -> new ArrayList<>()).add(cName);
                }
            }

            for (int i = 0; i < tableNames.size(); i++) {
                schemaStr.append("Table: ").append(tableNames.get(i).asText()).append(" (");
                List<String> cols = tableCols.get(i);
                if (cols != null) {
                    schemaStr.append(String.join(", ", cols));
                }
                schemaStr.append(")\n");
            }

            if (foreignKeys.size() > 0) {
                schemaStr.append("Foreign Keys:\n");
                for (JsonNode fkNode : foreignKeys) {
                    int c1Idx = fkNode.get(0).asInt();
                    int c2Idx = fkNode.get(1).asInt();

                    // Resolve first column ref
                    int t1Idx = columnNames.get(c1Idx).get(0).asInt();
                    String t1Name = tableNames.get(t1Idx).asText();
                    String c1Name = columnNames.get(c1Idx).get(1).asText();

                    // Resolve second column ref
                    int t2Idx = columnNames.get(c2Idx).get(0).asInt();
                    String t2Name = tableNames.get(t2Idx).asText();
                    String c2Name = columnNames.get(c2Idx).get(1).asText();

                    schemaStr.append(t1Name).append(".").append(c1Name).append(" = ")
                             .append(t2Name).append(".").append(c2Name).append("\n");
                }
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
                line = line.trim();
                if (line.startsWith("Question ")) {
                    Matcher m = pattern.matcher(line);
                    if (m.find()) {
                        int qId = Integer.parseInt(m.group(1));
                        String qText = m.group(2).trim();
                        String dbId = m.group(3).trim();

                        if (qId >= START_QID && qId <= END_QID) {
                            cases.add(new TestCase(qId, qText, dbId));
                        }
                    }
                }
            }
        }
        return cases;
    }

    private static int extractTokens(ObjectMapper mapper, ApplicationResult result) {
        if (result == null) return 0;
        try {
            String json = mapper.writeValueAsString(result);
            JsonNode rootNode = mapper.readTree(json);
            if (rootNode.has("usage") && rootNode.get("usage").has("models")) {
                JsonNode modelsNode = rootNode.get("usage").get("models");
                if (modelsNode.isArray() && modelsNode.size() > 0) {
                    JsonNode modelUsage = modelsNode.get(0);
                    int inTokens = modelUsage.has("inputTokens") ? modelUsage.get("inputTokens").asInt() : 0;
                    int outTokens = modelUsage.has("outputTokens") ? modelUsage.get("outputTokens").asInt() : 0;
                    return inTokens + outTokens;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse token usage: " + e.getMessage());
        }
        return 0;
    }

    static class TestCase {
        int id;
        String question;
        String dbId;

        TestCase(int id, String question, String dbId) {
            this.id = id;
            this.question = question;
            this.dbId = dbId;
        }
    }
}
