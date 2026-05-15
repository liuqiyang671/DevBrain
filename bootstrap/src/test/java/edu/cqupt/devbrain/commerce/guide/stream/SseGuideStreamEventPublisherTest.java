package edu.cqupt.devbrain.commerce.guide.stream;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SseGuideStreamEventPublisherTest {

    @Test
    void publisherCanEmitErrorAndCompleteWithoutThrowing() {
        SseGuideStreamEventPublisher publisher = new SseGuideStreamEventPublisher(new SseEmitter(1000L));

        assertDoesNotThrow(() -> {
            publisher.emit(new GuideSseEvent("e1", "s1", GuideSseEventType.SESSION,
                    Instant.now(), new GuideSessionPayload("s1", "c1", "t1")));
            publisher.error("s1", new RuntimeException("测试错误"));
        });
    }
}
