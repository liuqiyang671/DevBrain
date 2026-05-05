package edu.cqupt.devbrain.framework.trace;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * RAG 链路追踪上下文，管理当前线程的 traceId、taskId 和节点调用栈。
 * <p>
 * 职责：
 * <ul>
 *     <li>使用 {@link TransmittableThreadLocal}（TTL）在异步线程池中透传 traceId 与节点栈</li>
 *     <li>维护 traceId 作为一次完整 RAG 请求的唯一标识</li>
 *     <li>维护 taskId 关联业务任务</li>
 *     <li>通过栈结构（Deque）管理节点嵌套关系，支持 push/pop 操作</li>
 * </ul>
 * <p>
 * 所有方法均为静态方法，通过私有构造器禁止实例化。
 */
public final class RagTraceContext {

    private static final TransmittableThreadLocal<String> TRACE_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> TASK_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Deque<String>> NODE_STACK = new TransmittableThreadLocal<>();

    private RagTraceContext() {
    }

    /** 获取当前线程的 traceId */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /** 开始一次新的 trace，自动生成 traceId */
    public static void begin() {
        begin(null);
    }

    /** 开始一次新的 trace，使用指定的 traceId（为空则自动生成），并重置 taskId 和节点栈 */
    public static void begin(String traceId) {
        setTraceId(traceId == null || traceId.isBlank() ? newTraceId() : traceId);
        TASK_ID.remove();
        NODE_STACK.remove();
    }

    /** 判断当前线程是否存在活跃的 trace */
    public static boolean hasTrace() {
        return TRACE_ID.get() != null;
    }

    /** 设置当前线程的 traceId，传入 null 或空白字符串则清除 */
    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            TRACE_ID.remove();
            return;
        }
        TRACE_ID.set(traceId);
    }

    /** 获取当前线程关联的任务 ID */
    public static String getTaskId() {
        return TASK_ID.get();
    }

    /** 设置当前线程关联的任务 ID，传入 null 或空白字符串则清除 */
    public static void setTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            TASK_ID.remove();
            return;
        }
        TASK_ID.set(taskId);
    }

    /** 获取当前节点栈深度，0 表示栈为空 */
    public static int depth() {
        Deque<String> stack = NODE_STACK.get();
        return stack == null ? 0 : stack.size();
    }

    /** 获取当前栈顶节点 ID，栈为空时返回 null */
    public static String currentNodeId() {
        Deque<String> stack = NODE_STACK.get();
        return stack == null ? null : stack.peek();
    }

    /** 将节点 ID 压入调用栈，栈不存在时自动创建 */
    public static void pushNode(String nodeId) {
        Deque<String> stack = NODE_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            NODE_STACK.set(stack);
        }
        stack.push(nodeId);
    }

    /** 弹出栈顶节点，栈为空时为空操作；弹出后若栈为空则自动清理 ThreadLocal */
    public static void popNode() {
        Deque<String> stack = NODE_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            NODE_STACK.remove();
        }
    }

    /** 清除当前线程的所有 trace 上下文（traceId、taskId、节点栈） */
    public static void clear() {
        TRACE_ID.remove();
        TASK_ID.remove();
        NODE_STACK.remove();
    }

    /** 结束当前 trace，等同于 {@link #clear()} */
    public static void end() {
        clear();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
