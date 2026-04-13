package org.example.service;

import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import org.example.AskingAgent;
import org.example.DataManager;
import org.example.SqlQuery;
import org.example.dto.QueryResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class QueryService {

    public QueryResponse query(String question) {
        if (question == null || question.isBlank()) {
            return new QueryResponse(false, "问题不能为空", "", "", "", Collections.emptyList(), Collections.emptyList(), 0, 0, 0, 0, 0);
        }

        if (DataManager.apiKey().isBlank() || DataManager.mqlAppId().isBlank() || DataManager.schemaLinkAppId().isBlank() || DataManager.sqlAppId().isBlank()) {
            return new QueryResponse(false, "缺少 DashScope 配置，请在 application.properties 或环境变量中配置 API Key 和 App ID", "", "", "",
                    Collections.emptyList(), Collections.emptyList(), 0, 0, 0, 0, 0);
        }

        long totalStart = System.currentTimeMillis();
        long mqlMs = 0, linkMs = 0, sqlMs = 0;
        String mql = "", subSchema = "", sql = "";

        try {
            AskingAgent agent = new AskingAgent(DataManager.apiKey(), DataManager.mqlAppId(), DataManager.sqlAppId(), DataManager.schemaLinkAppId());
            SqlQuery sqlQuery = new SqlQuery();

            // 1. NL2MQL
            long mqlStart = System.currentTimeMillis();
            mql = agent.getMQL(question.trim());
            mqlMs = System.currentTimeMillis() - mqlStart;

            // 2. Schema Linking
            long linkStart = System.currentTimeMillis();
            String linkInput = "【原始业务问题查询】\n" + question.trim() + "\n\n【NL2MQL 提取的 IR JSON】\n" + mql;
            subSchema = agent.getSchemaLink(linkInput);
            linkMs = System.currentTimeMillis() - linkStart;

            // 3. MQL2SQL
            long sqlStart = System.currentTimeMillis();
            String combinedInput = "【原始业务问题查询】\n" + question.trim() + "\n\n【NL2MQL 提取的 IR JSON】\n" + mql + "\n\n【过滤后的子 Schema (DDL)】\n" + subSchema;
            sql = agent.getSQL(combinedInput);
            sqlMs = System.currentTimeMillis() - sqlStart;

            // 4. Execute SQL
            SqlQuery.QueryResult result = sqlQuery.execute(sql);
            long totalMs = System.currentTimeMillis() - totalStart;

            if (!result.isSuccess()) {
                return new QueryResponse(false, result.errorMsg, mql, subSchema, sql,
                        result.columns, result.rows, mqlMs, linkMs, sqlMs, result.queryMs, totalMs);
            }

            return new QueryResponse(true, "查询成功", mql, subSchema, sql,
                    result.columns, result.rows, mqlMs, linkMs, sqlMs, result.queryMs, totalMs);
        } catch (ApiException | NoApiKeyException | InputRequiredException ex) {
            long totalMs = System.currentTimeMillis() - totalStart;
            return new QueryResponse(false, "AI 处理失败: " + ex.getMessage(), mql, subSchema, sql,
                    Collections.emptyList(), Collections.emptyList(), mqlMs, linkMs, sqlMs, 0, totalMs);
        } catch (Exception ex) {
            long totalMs = System.currentTimeMillis() - totalStart;
            return new QueryResponse(false, "系统异常: " + ex.getMessage(), mql, subSchema, sql,
                    Collections.emptyList(), Collections.emptyList(), mqlMs, linkMs, sqlMs, 0, totalMs);
        }
    }
}
