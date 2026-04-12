package org.example;

import com.alibaba.dashscope.app.ApplicationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TokenDebugger {
    public static void main(String[] args) throws Exception {
        AskingAgent agent = new AskingAgent(DataManager.apiKey(), DataManager.mqlAppId(), DataManager.sqlAppId());
        ApplicationResult result = agent.getMqlResult("测试");
        
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("Result JSON:");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }
}
