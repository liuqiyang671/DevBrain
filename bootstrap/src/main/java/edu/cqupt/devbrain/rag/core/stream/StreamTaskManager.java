package edu.cqupt.devbrain.rag.core.stream;

import edu.cqupt.devbrain.infra.ai.llm.StreamCancellationHandle;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 流式任务管理器，维护本地取消句柄并通过 Redis Pub/Sub 广播取消信号。
 * <p>
 * 多实例部署时，取消请求会通过 Redis Topic 广播到所有节点，确保任务在任意节点都能被取消。
 */
@Slf4j
@Service
public class StreamTaskManager {

    public static final String CANCEL_CHANNEL = "ragent:stream:cancel";
    public static final String CANCEL_KEY_PREFIX = "ragent:stream:cancel:";

    private final RedissonClient redissonClient;
    private final Executor executor;
    private final Map<String, StreamCancellationHandle> localHandles = new ConcurrentHashMap<>();

    public StreamTaskManager() {
        this(null, Runnable::run);
    }

    @Autowired
    public StreamTaskManager(RedissonClient redissonClient,
                             @Qualifier("streamTaskExecutor") Executor executor) {
        this.redissonClient = redissonClient;
        this.executor = executor == null ? Runnable::run : executor;
    }

    /**
     * 绑定 LLM 流式取消句柄，同一 taskId 多次绑定会合并为组合句柄。
     */
    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        if (!StringUtils.hasText(taskId) || handle == null) {
            return;
        }
        localHandles.merge(taskId, handle, this::combine);
    }

    /**
     * 注册取消回调，通常在 SSE 事件处理器构造时调用。
     */
    public void register(String taskId, Runnable cancelAction) {
        if (cancelAction == null) {
            return;
        }
        bindHandle(taskId, cancelAction::run);
    }

    /**
     * 取消指定任务，通过 Redis 广播取消信号到所有节点。
     *
     * @return 本地是否存在该任务的取消句柄
     */
    public boolean cancel(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return false;
        }
        boolean localPresent = localHandles.containsKey(taskId);
        if (redissonClient != null) {
            try {
                // 在 Redis 中设置取消标记（1 小时过期），用于跨节点状态同步
                redissonClient.getBucket(CANCEL_KEY_PREFIX + taskId).set("1", Duration.ofHours(1));
                // 通过 Redis Topic 广播取消信号，所有订阅节点都会收到
                redissonClient.getTopic(CANCEL_CHANNEL).publish(taskId);
            } catch (Throwable ex) {
                // Redis 不可用时降级为本地取消
                log.warn("Publish stream cancel signal failed, taskId={}", taskId, ex);
                cancelLocal(taskId);
            }
        } else {
            // 无 Redis 时直接本地取消
            cancelLocal(taskId);
        }
        return localPresent;
    }

    /** 任务完成时注销取消句柄。 */
    public void unregister(String taskId) {
        if (StringUtils.hasText(taskId)) {
            localHandles.remove(taskId);
        }
    }

    public boolean contains(String taskId) {
        return localHandles.containsKey(taskId);
    }

    public Set<String> activeTaskIds() {
        return Set.copyOf(localHandles.keySet());
    }

    /** 启动时订阅 Redis 取消频道，接收跨节点取消信号。 */
    @PostConstruct
    void subscribeCancelChannel() {
        if (redissonClient == null) {
            return;
        }
        redissonClient.getTopic(CANCEL_CHANNEL).addListener(String.class, (channel, taskId) -> {
            if (StringUtils.hasText(taskId)) {
                executor.execute(() -> cancelLocal(taskId));
            }
        });
    }

    /** 执行本地取消：移除句柄并调用 cancel()。 */
    private void cancelLocal(String taskId) {
        StreamCancellationHandle handle = localHandles.remove(taskId);
        if (handle == null) {
            return;
        }
        try {
            handle.cancel();
        } catch (Throwable ex) {
            log.warn("Cancel local stream task failed, taskId={}", taskId, ex);
        }
    }

    /** 合并两个取消句柄为组合句柄，取消时两者都会被调用。 */
    private StreamCancellationHandle combine(StreamCancellationHandle first, StreamCancellationHandle second) {
        return () -> {
            first.cancel();
            second.cancel();
        };
    }
}
