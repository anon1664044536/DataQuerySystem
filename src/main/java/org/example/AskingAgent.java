package org.example;

import com.alibaba.dashscope.app.*;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;

public class AskingAgent {
    String APIKey;
    String MQLAppID;
    String SQLAppID;

    public AskingAgent(String APIKey, String MQLAppID, String SQLAppID) {
        this.APIKey = APIKey;
        this.MQLAppID = MQLAppID;
        this.SQLAppID = SQLAppID;
    }

    public ApplicationResult getMqlResult(String NL) throws ApiException, NoApiKeyException, InputRequiredException {
        ApplicationParam param = ApplicationParam.builder()
                .apiKey(this.APIKey)
                .appId(this.MQLAppID)
                .prompt(NL)
                .build();
        Application application = new Application();
        return application.call(param);
    }
    
    public ApplicationResult getSqlResult(String MQL) throws ApiException, NoApiKeyException, InputRequiredException {
        ApplicationParam param = ApplicationParam.builder()
                .apiKey(this.APIKey)
                .appId(this.SQLAppID)
                .prompt(MQL)
                .build();
        Application application = new Application();
        return application.call(param);
    }

    public String getMQL(String NL)
            throws ApiException, NoApiKeyException, InputRequiredException {
        ApplicationResult result = getMqlResult(NL);
        return result.getOutput().getText();
    }

    public String getSQL(String MQL)
            throws ApiException, NoApiKeyException, InputRequiredException {
        ApplicationResult result = getSqlResult(MQL);
        return result.getOutput().getText();
    }
}
