package edu.cqupt.devbrain.rag.core.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cqupt.devbrain.rag.enums.SSEEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 线程安全 SSE 发送器。
 */
@Slf4j
public class SseEmitterSender {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
        this.emitter.onCompletion(() -> closed.set(true));
        this.emitter.onTimeout(() -> closed.set(true));
        this.emitter.onError(ignored -> closed.set(true));
    }

    public void sendEvent(SSEEventType type, Object data) {
        sendEvent(type.getValue(), data);
    }

    public void sendEvent(String name, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(toData(data)));
        } catch (IOException ex) {
            fail(ex);
        }
    }

    public void complete() {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    /**
     * 关闭流，但不调用 {@link SseEmitter#completeWithError(Throwable)}。
     * <p>
     * 错误已通过 {@code event: error} 事件下发给前端，再调用 {@code completeWithError}
     * 会触发 Spring 在 SSE 响应上二次派发到 {@code @ExceptionHandler}，进而引发
     * {@code HttpMessageNotWritableException}（响应 Content-Type 已锁定为 text/event-stream）。
     */
    public void fail(Throwable throwable) {
        if (closed.compareAndSet(false, true)) {
            if (throwable != null) {
                log.warn("SSE 流终止，已通过 error 事件下发错误", throwable);
            }
            try {
                emitter.complete();
            } catch (RuntimeException ex) {
                log.debug("SSE emitter 完成时已被关闭", ex);
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    private String toData(Object data) throws JsonProcessingException {
        if (data == null) {
            return "";
        }
        if (data instanceof String string) {
            return string;
        }
        return OBJECT_MAPPER.writeValueAsString(data);
    }
}
