package edu.cqupt.devbrain.rag.core.stream;

import edu.cqupt.devbrain.rag.enums.SSEEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 验证 {@link SseEmitterSender} 的状态机及错误终结行为。
 */
class SseEmitterSenderTest {

    private SseEmitter emitter;
    private SseEmitterSender sender;

    @BeforeEach
    void setUp() {
        emitter = spy(new SseEmitter(10_000L));
        sender = new SseEmitterSender(emitter);
    }

    @Test
    void completeShouldCloseEmitterOnce() {
        sender.complete();

        assertThat(sender.isClosed()).isTrue();
        verify(emitter, times(1)).complete();
    }

    @Test
    void failShouldCallCompleteNotCompleteWithError() {
        RuntimeException error = new RuntimeException("test error");

        sender.fail(error);

        assertThat(sender.isClosed()).isTrue();
        verify(emitter, times(1)).complete();
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void failWithNullThrowableShouldStillComplete() {
        sender.fail(null);

        assertThat(sender.isClosed()).isTrue();
        verify(emitter, times(1)).complete();
    }

    @Test
    void doubleFailShouldBeIdempotent() {
        sender.fail(new RuntimeException("first"));
        sender.fail(new RuntimeException("second"));

        verify(emitter, times(1)).complete();
    }

    @Test
    void sendEventAfterFailShouldBeIgnored() throws Exception {
        sender.fail(new RuntimeException("closed"));

        sender.sendEvent(SSEEventType.MESSAGE, "should-be-dropped");

        // complete() only called once from fail(); send() never called after close
        verify(emitter, times(1)).complete();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void failAfterCompleteShouldBeIdempotent() {
        sender.complete();
        sender.fail(new RuntimeException("after complete"));

        // complete() called only once (from the explicit complete() call)
        verify(emitter, times(1)).complete();
    }
}
