package edu.cqupt.devbrain.framework.web;

import org.slf4j.MDC;

/**
 * 请求 ID 线程上下文管理器。
 * <p>基于 SLF4J {@link MDC} 实现，提供当前线程的请求 ID 存取能力。
 * 核心用途：</p>
 * <ul>
 *   <li>在请求处理链路中传递 {@code requestId}，使日志自动关联</li>
 *   <li>提供 {@link Scope} 实现 try-with-resources 语义，确保请求结束后 MDC 正确清理</li>
 * </ul>
 *
 * @see RequestIdFilter
 */
public final class RequestIdContext {

    /** MDC 中存储请求 ID 的键名 */
    public static final String MDC_KEY = "requestId";
    /** HTTP 请求/响应头中的请求 ID 字段名 */
    public static final String HEADER_NAME = "X-Request-Id";

    private RequestIdContext() {
    }

    /**
     * 获取当前线程绑定的请求 ID。
     *
     * @return 当前请求 ID，若未设置则返回 {@code null}
     */
    public static String currentId() {
        return MDC.get(MDC_KEY);
    }

    /**
     * 打开一个新的请求 ID 作用域。
     * <p>将给定的 requestId 写入 MDC，并返回一个 {@link Scope} 对象。
     * 推荐配合 try-with-resources 使用，确保作用域结束后 MDC 自动恢复。</p>
     *
     * @param requestId 要绑定的请求 ID，为 {@code null} 或空白时清除当前值
     * @return 可自动关闭的作用域对象
     */
    public static Scope open(String requestId) {
        String previous = MDC.get(MDC_KEY);
        if (requestId == null || requestId.isBlank()) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, requestId);
        }
        return new Scope(previous);
    }

    /**
     * 请求 ID 作用域，实现 {@link AutoCloseable} 接口以支持 try-with-resources。
     * <p>关闭时会将 MDC 恢复到打开前的状态，避免线程复用导致的 ID 串扰。</p>
     */
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
