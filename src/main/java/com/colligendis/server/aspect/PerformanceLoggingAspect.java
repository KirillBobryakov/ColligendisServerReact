package com.colligendis.server.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class PerformanceLoggingAspect {

    @Value("${performance.monitoring.threshold-ms:1000}")
    private long thresholdMs;

    @Around("@annotation(LogExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("Method {} started execution", methodName);

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            if (executionTime > thresholdMs) {
                log.warn("Method {} executed in {} ms (exceeds threshold of {} ms)",
                        methodName, executionTime, thresholdMs);
            } else {
                log.debug("Method {} executed in {} ms", methodName, executionTime);
            }

            return result;
        } catch (Throwable throwable) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Method {} failed after {} ms with exception: {}",
                    methodName, executionTime, throwable.getMessage());
            throw throwable;
        }
    }

    // @Around("execution(* com.colligendis.server.service..*(..)) && execution(*
    // com.colligendis.server.parser.numista.init_parser..*(..))")
    @Around("execution(* com.colligendis..*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().toShortString();

        log.debug("Service method {} invoked with args: {}", methodName, joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            if (executionTime > thresholdMs) {
                log.warn("Service method {} executed in {} ms (exceeds threshold of {} ms)",
                        methodName, executionTime, thresholdMs);
            } else {
                log.debug("Service method {} executed in {} ms", methodName, executionTime);
            }

            return result;
        } catch (Throwable throwable) {
            log.error("Service method {} failed: {}", methodName, throwable.getMessage());
            throw throwable;
        }
    }
}
