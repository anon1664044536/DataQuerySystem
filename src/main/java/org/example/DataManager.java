package org.example;

import java.io.InputStream;
import java.util.Properties;

public class DataManager {
    private static final Properties APP_PROPS = new Properties();

    static {
        try (InputStream in = DataManager.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                APP_PROPS.load(in);
            }
        } catch (Exception ignored) {
            // Ignore config file errors and continue using defaults.
        }
    }

    private static String get(String envKey, String propKey, String defaultValue) {
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal;
        }

        String propVal = APP_PROPS.getProperty(propKey);
        if (propVal != null && !propVal.isBlank()) {
            return propVal;
        }

        return defaultValue;
    }

    public static String appName() {
        return get("APP_NAME", "app.name", "ACAI");
    }

    public static String apiKey() {
        return get("DASHSCOPE_API_KEY", "dashscope.api-key", "");
    }

    public static String mqlAppId() {
        return get("MQL_APP_ID", "dashscope.mql-app-id", "");
    }

    public static String sqlAppId() {
        return get("SQL_APP_ID", "dashscope.sql-app-id", "");
    }

    public static String mqlTestAppId() {
        return get("MQL_TEST_APP_ID", "dashscope.mqlTest-app-id", "");
    }

    public static String sqlTestAppId() {
        return get("SQL_TEST_APP_ID", "dashscope.sqlTest-app-id", "");
    }

    public static String mqlNatSQLAppId() {
        return get("MQL_NATSQL_APP_ID", "dashscope.mqlNatSQL-app-id", "");
    }

    public static String sqlNatSQLAppId() {
        return get("SQL_NATSQL_APP_ID", "dashscope.sqlNatSQL-app-id", "");
    }

    public static String dbHost() {
        return get("DB_HOST", "db.host", "localhost");
    }

    public static String dbPort() {
        return get("DB_PORT", "db.port", "3306");
    }

    public static String dbName() {
        return get("DB_NAME", "db.name", "pms");
    }

    public static String dbUser() {
        return get("DB_USER", "db.user", "root");
    }

    public static String dbPassword() {
        return get("DB_PASSWORD", "db.password", "");
    }
}
