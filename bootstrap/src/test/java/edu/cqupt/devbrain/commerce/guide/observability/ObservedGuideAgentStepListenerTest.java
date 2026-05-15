package edu.cqupt.devbrain.commerce.guide.observability;

import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEvent;
import edu.cqupt.devbrain.commerce.guide.stream.GuideSseEventType;
import edu.cqupt.devbrain.commerce.guide.stream.GuideStreamEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ObservedGuideAgentStepListenerTest {

    @Test
    void timeoutIsTerminalAndPreventsLaterCancelOrFinishEvents() {
        GuideAgentObservationService observationService = mock(GuideAgentObservationService.class);
        RecordingPublisher publisher = new RecordingPublisher();
        ObservedGuideAgentStepListener listener = new ObservedGuideAgentStepListener(observationService, publisher);
        GuideAgentRunContext context = context(listener);

        listener.onTimeout(context);
        listener.onCancel(context);
        listener.onFinish(context, null, 1, "final_answer");

        verify(observationService, times(1)).timeoutRun(context);
        verify(observationService, times(0)).cancelRun(context);
        verify(observationService, times(0)).completeRun(context, null, 1, "final_answer");
        assertEquals(List.of(GuideSseEventType.AGENT_FINISH), publisher.events.stream().map(GuideSseEvent::type).toList());
        assertEquals(GuideAgentRunStatus.TIMEOUT.value(),
                ((edu.cqupt.devbrain.commerce.guide.stream.GuideAgentFinishPayload) publisher.events.get(0).payload()).status());
    }

    private GuideAgentRunContext context(GuideAgentStepListener listener) {
        return new GuideAgentRunContext(
                "run1",
                "task1",
                "s1",
                "c1",
                "u1",
                "commerce_guide",
                CancellationToken.none(),
                listener
        );
    }

    private static final class RecordingPublisher implements GuideStreamEventPublisher {
        private final List<GuideSseEvent> events = new ArrayList<>();

        @Override
        public void emit(GuideSseEvent event) {
            events.add(event);
        }

        @Override
        public void emitAnswerDelta(String sessionId, String delta) {
        }

        @Override
        public void complete(String sessionId) {
        }

        @Override
        public void error(String sessionId, Throwable throwable) {
        }
    }
}
