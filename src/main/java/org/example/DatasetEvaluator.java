package org.example;

import com.alibaba.dashscope.app.ApplicationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DatasetEvaluator {

    public static void main(String[] args) {
        String questionsFile = "dataset/questions.txt";
        String resultsFile = "dataset/results.txt";
        String usageFile = "dataset/usage.txt";

        if (DataManager.apiKey().isBlank() || DataManager.mqlAppId().isBlank() || DataManager.sqlAppId().isBlank()) {
            System.err.println("Error: DashScope API key or App ID is missing.");
            return;
        }

        AskingAgent agent = new AskingAgent(DataManager.apiKey(), DataManager.mqlAppId(), DataManager.sqlAppId());
        SqlQuery sqlQuery = new SqlQuery();
        ObjectMapper mapper = new ObjectMapper();

        List<String> resultsList = new ArrayList<>();
        List<String> usageList = new ArrayList<>();

        usageList.add(String.format("%-5s | %-15s | %-20s | %-15s | %-20s | %-10s | %-10s", 
                "ID", "NL2MQL_Time(ms)", "NL2MQL_Tokens(Total)", "MQL2SQL_Time(ms)", "MQL2SQL_Tokens(Total)", "Total_Time", "Total_Tokens"));
        usageList.add("-".repeat(110));

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(questionsFile), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\|", 2);
                if (parts.length < 2) continue;

                String qId = parts[0].trim();
                String question = parts[1].trim();
                System.out.println("Processing Question " + qId + ": " + question);

                long totalTimeStart = System.currentTimeMillis();

                // NL to MQL
                long mqlStart = System.currentTimeMillis();
                ApplicationResult mqlResult = null;
                String mqlText = "";
                int mqlTokens = 0;
                try {
                    mqlResult = agent.getMqlResult(question);
                    mqlText = mqlResult.getOutput().getText();
                    mqlTokens = extractTokens(mapper, mqlResult);
                } catch (Exception e) {
                    System.err.println("Error in NL2MQL: " + e.getMessage());
                }
                long mqlTime = System.currentTimeMillis() - mqlStart;

                // MQL to SQL
                long sqlStart = System.currentTimeMillis();
                ApplicationResult sqlResult = null;
                String sqlText = "";
                int sqlTokens = 0;
                if (!mqlText.isEmpty()) {
                    try {
                        sqlResult = agent.getSqlResult(mqlText);
                        sqlText = sqlResult.getOutput().getText();
                        sqlTokens = extractTokens(mapper, sqlResult);
                    } catch (Exception e) {
                        System.err.println("Error in MQL2SQL: " + e.getMessage());
                    }
                }
                long sqlTime = System.currentTimeMillis() - sqlStart;

                // Execute SQL
                String finalResult = "Error";
                if (!sqlText.isEmpty()) {
                    // Extract actual SQL if wrapped in ```sql ... ```
                    String cleanSql = sqlText;
                    if (cleanSql.startsWith("```sql")) {
                        cleanSql = cleanSql.substring(6);
                        if (cleanSql.endsWith("```")) {
                            cleanSql = cleanSql.substring(0, cleanSql.length() - 3);
                        }
                    } else if (cleanSql.startsWith("```")) {
                        cleanSql = cleanSql.substring(3);
                        if (cleanSql.endsWith("```")) {
                            cleanSql = cleanSql.substring(0, cleanSql.length() - 3);
                        }
                    }
                    cleanSql = cleanSql.trim();

                    SqlQuery.QueryResult queryResult = sqlQuery.execute(cleanSql);
                    if (queryResult.isSuccess()) {
                        if (queryResult.rows.isEmpty()) {
                            finalResult = "None";
                        } else {
                            // Assumes single value result for exact match evaluation
                            finalResult = queryResult.rows.get(0).get(0);
                        }
                    } else {
                        finalResult = "Error";
                    }
                }

                long totalTime = System.currentTimeMillis() - totalTimeStart;
                int totalTokens = mqlTokens + sqlTokens;

                resultsList.add(qId + "|" + finalResult);
                usageList.add(String.format("%-5s | %-15d | %-20d | %-15d | %-20d | %-10d | %-10d", 
                        qId, mqlTime, mqlTokens, sqlTime, sqlTokens, totalTime, totalTokens));
                
                System.out.println(" -> Result: " + finalResult + " (Total Time: " + totalTime + "ms, Tokens: " + totalTokens + ")");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Write results.txt
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(resultsFile), "UTF-8"))) {
            for (String res : resultsList) {
                pw.println(res);
            }
            System.out.println("Results saved to " + resultsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Write usage.txt
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(usageFile), "UTF-8"))) {
            for (String usage : usageList) {
                pw.println(usage);
            }
            System.out.println("Usage saved to " + usageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
}
