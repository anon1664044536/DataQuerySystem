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
            return new QueryResponse(false, "问题不能为空", "", "", Collections.emptyList(), Collections.emptyList(), 0, 0, 0, 0);
        }

        if (DataManager.apiKey().isBlank() || DataManager.mqlAppId().isBlank() || DataManager.sqlAppId().isBlank()) {
            return new QueryResponse(false, "缺少 DashScope 配置，请在 application.properties 或环境变量中配置 API Key 和 App ID", "", "",
                    Collections.emptyList(), Collections.emptyList(), 0, 0, 0, 0);
        }

        long totalStart = System.currentTimeMillis();
        long mqlMs = 0;
        long sqlMs = 0;

        try {
            AskingAgent agent = new AskingAgent(DataManager.apiKey(), DataManager.mqlAppId(), DataManager.sqlAppId());
            SqlQuery sqlQuery = new SqlQuery();

            long mqlStart = System.currentTimeMillis();
            String mql = agent.getMQL(question.trim());
            mqlMs = System.currentTimeMillis() - mqlStart;

            long sqlStart = System.currentTimeMillis();
            String sql = agent.getSQL(mql);
            sqlMs = System.currentTimeMillis() - sqlStart;

            SqlQuery.QueryResult result = sqlQuery.execute(sql);
            long totalMs = System.currentTimeMillis() - totalStart;

            if (!result.isSuccess()) {
                return new QueryResponse(false, result.errorMsg, mql, sql,
                        result.columns, result.rows, mqlMs, sqlMs, result.queryMs, totalMs);
            }

            return new QueryResponse(true, "查询成功", mql, sql,
                    result.columns, result.rows, mqlMs, sqlMs, result.queryMs, totalMs);
        } catch (ApiException | NoApiKeyException | InputRequiredException ex) {
            long totalMs = System.currentTimeMillis() - totalStart;
            return new QueryResponse(false, "AI 处理失败: " + ex.getMessage(), "", "",
                    Collections.emptyList(), Collections.emptyList(), mqlMs, sqlMs, 0, totalMs);
        } catch (Exception ex) {
            long totalMs = System.currentTimeMillis() - totalStart;
            return new QueryResponse(false, "系统异常: " + ex.getMessage(), "", "",
                    Collections.emptyList(), Collections.emptyList(), mqlMs, sqlMs, 0, totalMs);
        }
    }
}
