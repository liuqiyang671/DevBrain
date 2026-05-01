package edu.cqupt.devbrain.framework.web;

import org.slf4j.MDC;

/**
 * 当前请求 ID 上下文。
 */
public final class RequestIdContext {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER_NAME = "X-Request-Id";

    private RequestIdContext() {
    }

    public static String currentId() {
        return MDC.get(MDC_KEY);
    }

    public static Scope open(String requestId) {
        String previous = MDC.get(MDC_KEY);
        if (requestId == null || requestId.isBlank()) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, requestId);
        }
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {

        private final String previous;
        private boolean closed;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
