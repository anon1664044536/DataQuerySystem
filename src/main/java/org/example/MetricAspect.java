package org.example;

import com.alibaba.dashscope.app.ApplicationResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MetricAspect {

    private static final Logger logger = LoggerFactory.getLogger(MetricAspect.class);

    @Around("execution(* org.example.AskingAgent.*Result(..))")
    public Object auditMetric(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();

        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } finally {
            long duration = System.currentTimeMillis() - start;
            int tokens = 0;

            // 尝试解析 ApplicationResult 中的 Token 消耗
            if (result instanceof ApplicationResult) {
                ApplicationResult appResult = (ApplicationResult) result;
                try {
                    // 这里的解析逻辑可以根据具体的 SDK 结构微调
                    if (appResult.getUsage() != null) {
                        tokens = appResult.getUsage().getTotalTokens();
                    }
                } catch (Exception ignored) {
                }
            }

            // 输出审计日志，对应论文中所说的“异步持久化至独立的系统日志文件中”
            logger.info("[MetricAudit] Method: {} | Latency: {}ms | Tokens: {}",
                    methodName, duration, tokens);
        }
    }
}
