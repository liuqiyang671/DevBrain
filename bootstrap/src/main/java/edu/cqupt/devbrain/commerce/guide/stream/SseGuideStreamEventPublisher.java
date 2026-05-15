package edu.cqupt.devbrain.commerce.guide.stream;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 SSE 的导购流式事件发布器实现。
 * <p>
 * 通过 Spring 的 {@link SseEmitter} 将事件实时推送给前端，支持：
 * <ul>
 *   <li><b>事件推送</b> — emit() 发送结构化 SSE 事件</li>
 *   <li><b>流式回答</b> — emitAnswerDelta() 逐 token 推送回答内容</li>
 *   <li><b>生命周期管理</b> — complete() 正常结束、error() 异常结束</li>
 *   <li><b>连接状态</b> — 通过 closed 标志防止向已关闭的连接发送数据</li>
 * </ul>
 * <p>
 * 连接关闭触发：onCompletion、onTimeout、onError 回调都会设置 closed=true。
 *
 * @author liuqiyang
 * @since 2026-05-15
 * @see GuideStreamEventPublisher 事件发布器接口
 * @see GuideSseEvent SSE 事件
 */
@Slf4j
public class SseGuideStreamEventPublisher implements GuideStreamEventPublisher {

    /** JSON 序列化器（注册 JavaTimeModule 支持 Instant 序列化） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    /** SSE 发射器 */
    private final SseEmitter emitter;

    /** 连接已关闭标志 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SseGuideStreamEventPublisher(SseEmitter emitter) {
        this.emitter = emitter;
        this.emitter.onCompletion(() -> closed.set(true));
        this.emitter.onTimeout(() -> closed.set(true));
        this.emitter.onError(ignored -> closed.set(true));
    }

    /**
     * 发送 SSE 事件。
     * <p>
     * 事件格式：id=eventId, name=eventType, data=json(event)。
     */
        if (closed.get() || event == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .id(event.eventId())
                    .name(event.type().getValue())
                    .data(toJson(event)));
        } catch (IOException ex) {
            fail(ex);
        }
    }

    /** 发送流式回答增量（逐 token 推送） */
        emit(new GuideSseEvent(IdUtil.fastSimpleUUID(), sessionId, GuideSseEventType.ANSWER_DELTA,
                Instant.now(), delta == null ? "" : delta));
    }

    /** 正常完成（发送 DONE 事件后关闭连接） */
        emit(new GuideSseEvent(IdUtil.fastSimpleUUID(), sessionId, GuideSseEventType.DONE, Instant.now(), ""));
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    /** 异常结束（发送 ERROR 事件后关闭连接） */
        String message = throwable == null || throwable.getMessage() == null
                ? "导购服务暂时不可用，请稍后再试"
                : throwable.getMessage();
        emit(new GuideSseEvent(IdUtil.fastSimpleUUID(), sessionId, GuideSseEventType.ERROR, Instant.now(),
                new GuideErrorPayload(message)));
        complete(sessionId);
    }

    public boolean isClosed() {
        return closed.get();
    }

    private String toJson(Object value) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsString(value);
    }

    private void fail(Throwable throwable) {
        if (closed.compareAndSet(false, true)) {
            log.warn("导购 SSE 发送失败", throwable);
            try {
                emitter.complete();
            } catch (RuntimeException ex) {
                log.debug("导购 SSE 关闭时连接已结束", ex);
            }
        }
    }
}
