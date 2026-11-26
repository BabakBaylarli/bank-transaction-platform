package com.bankplatform.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Correlation id helpers. The id travels as the {@value #HEADER} HTTP header, as a Kafka record
 * header with the same name, and lives in the SLF4J MDC under {@value #MDC_KEY}, so all log lines
 * of one business flow - across every service - can be found with a single query.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrNew() {
        String id = current();
        return id != null ? id : newId();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static void set(String id) {
        MDC.put(MDC_KEY, id);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
