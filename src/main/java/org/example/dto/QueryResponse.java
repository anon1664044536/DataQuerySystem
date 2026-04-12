package org.example.dto;

import java.util.List;

public record QueryResponse(
        boolean success,
        String message,
        String mql,
        String sql,
        List<String> columns,
        List<List<String>> rows,
        long mqlMs,
        long sqlMs,
        long dbMs,
        long totalMs
) {
}
