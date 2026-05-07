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
 * Streaming task manager with local cancellation handles and Redis broadcast support.
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

    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        if (!StringUtils.hasText(taskId) || handle == null) {
            return;
        }
        localHandles.merge(taskId, handle, this::combine);
    }

    public void register(String taskId, Runnable cancelAction) {
        if (cancelAction == null) {
            return;
        }
        bindHandle(taskId, cancelAction::run);
    }

    public boolean cancel(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return false;
        }
        boolean localPresent = localHandles.containsKey(taskId);
        if (redissonClient != null) {
            try {
                redissonClient.getBucket(CANCEL_KEY_PREFIX + taskId).set("1", Duration.ofHours(1));
                redissonClient.getTopic(CANCEL_CHANNEL).publish(taskId);
            } catch (Throwable ex) {
                log.warn("Publish stream cancel signal failed, taskId={}", taskId, ex);
                cancelLocal(taskId);
            }
        } else {
            cancelLocal(taskId);
        }
        return localPresent;
    }

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

    private StreamCancellationHandle combine(StreamCancellationHandle first, StreamCancellationHandle second) {
        return () -> {
            first.cancel();
            second.cancel();
        };
    }
}
