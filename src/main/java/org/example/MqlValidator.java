package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MQL 验证器 (MqlValidator)
 * 对应论文 3.5 节：基于 JSON Schema 的抽象语法树校验机制
 * 负责在获取到大模型输出的 MQL 后，进行语法结构与核心字段的校验
 */
public class MqlValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 验证 MQL 字符串是否符合预定义的结构规范
     * @param mqlJson 大模型输出的原始 MQL 字符串
     * @throws Exception 如果校验失败，抛出对应的业务异常
     */
    public static void validate(String mqlJson) throws Exception {
        if (mqlJson == null || mqlJson.isBlank()) {
            throw new Exception("MQL 报文内容为空");
        }

        // 清理 Markdown 代码块包裹（如有）
        String cleanJson = mqlJson.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7).trim();
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3).trim();
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3).trim();
        }

        JsonNode root;
        try {
            root = mapper.readTree(cleanJson);
        } catch (Exception e) {
            throw new Exception("MQL JSON 语法解析错误，无法构建 AST 树: " + e.getMessage());
        }

        // 1. 校验 query_type 根节点
        if (!root.has("query_type") || root.get("query_type").isNull()) {
            throw new Exception("MQL 校验失败：缺少关键字段 query_type");
        }

        // 2. 校验 target/metrics 节点
        if (!root.has("target") || !root.get("target").has("metrics")) {
            throw new Exception("MQL 校验失败：缺少查询目标 metrics 节点");
        }

        JsonNode metrics = root.get("target").get("metrics");
        if (!metrics.isArray() || metrics.isEmpty()) {
            throw new Exception("MQL 校验失败：metrics 数组不能为空，系统无法确定查询目标");
        }

        // 3. 针对不同类型的查询进行业务规则深度校验 (论文中提到的边界异常阻断)
        String queryType = root.get("query_type").asText();
        if ("trend_query".equalsIgnoreCase(queryType)) {
            // 趋势查询必须包含时间维度
            if (!root.has("dimensions") || root.get("dimensions").isEmpty()) {
                throw new Exception("MQL 校验失败：趋势查询 (trend_query) 必须指定时间维度字段");
            }
        }

        // 4. 校验 filters 结构（如有）
        if (root.has("filters") && root.get("filters").isArray()) {
            for (JsonNode filter : root.get("filters")) {
                if (!filter.has("field") || !filter.has("operator") || !filter.has("value")) {
                    throw new Exception("MQL 校验失败：filters 过滤条件必须包含 field, operator 和 value 三要素");
                }
            }
        }
    }
}
