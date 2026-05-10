package org.example;

import java.util.*;
import java.util.regex.*;

public class SchemaDataInjector {

    public static String getSampleData(String subSchema, SqlQuery sqlQuery) {
        if (subSchema == null || subSchema.isBlank()) {
            return "";
        }

        String currentDb = "";
        List<String> fullTableNames = new ArrayList<>();

        Matcher matcher = Pattern.compile("(?i)(?:USE\\s+([\\w_]+)\\s*;)|(?:CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([`\\w\\.]+))").matcher(subSchema);

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                currentDb = matcher.group(1).replace("`", "");
            } else if (matcher.group(2) != null) {
                String tableName = matcher.group(2).replace("`", "");
                if (!tableName.contains(".") && !currentDb.isEmpty()) {
                    fullTableNames.add(currentDb + "." + tableName);
                } else {
                    fullTableNames.add(tableName);
                }
            }
        }

        if (fullTableNames.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String tableName : fullTableNames) {
            sb.append("表 ").append(tableName).append(" 样例数据 (LIMIT 3):\n");
            try {
                SqlQuery.QueryResult result = sqlQuery.execute("SELECT * FROM " + tableName + " LIMIT 3");
                if (result.isSuccess() && !result.columns.isEmpty()) {
                    sb.append("| ").append(String.join(" | ", result.columns)).append(" |\n");
                    sb.append("|");
                    for (int i = 0; i < result.columns.size(); i++) {
                        sb.append("---|");
                    }
                    sb.append("\n");

                    if (result.rows.isEmpty()) {
                        sb.append("(表内暂无数据)\n");
                    } else {
                        for (List<String> row : result.rows) {
                            List<String> safeRow = new ArrayList<>();
                            for (String val : row) {
                                if (val == null) {
                                    safeRow.add("NULL");
                                } else {
                                    safeRow.add(val.replace("\n", " ").replace("\r", "").replace("|", "\\|"));
                                }
                            }
                            sb.append("| ").append(String.join(" | ", safeRow)).append(" |\n");
                        }
                    }
                } else {
                    sb.append("(无法获取样例数据或表为空: ").append(result.errorMsg == null ? "" : result.errorMsg).append(")\n");
                }
            } catch (Exception e) {
                sb.append("(提取数据异常: ").append(e.getMessage()).append(")\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }
}
