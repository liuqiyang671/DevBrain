package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.retrieve.postprocess.SearchResultPostProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiChannelRetrievalEngineTest {

    @Test
    void retrieveKnowledgeChannelsShouldRunEnabledChannelsAndThenPostProcessors() {
        SearchChannel highPriority = new StubChannel("intent", 1, true,
                List.of(new RetrievedChunk("intent-1", "精准结果", 0.7f)));
        SearchChannel disabled = new StubChannel("disabled", 5, false,
                List.of(new RetrievedChunk("disabled-1", "禁用结果", 0.99f)));
        SearchChannel fallback = new StubChannel("global", 10, true,
                List.of(new RetrievedChunk("global-1", "兜底结果", 0.6f)));
        SearchResultPostProcessor boost = chunks -> chunks.stream()
                .map(chunk -> new RetrievedChunk(chunk.getId(), chunk.getText(), chunk.getScore() + 0.1f))
                .toList();

        MultiChannelRetrievalEngine engine = new MultiChannelRetrievalEngine(
                List.of(fallback, disabled, highPriority),
                List.of(boost),
                Runnable::run
        );

        List<RetrievedChunk> result = engine.retrieveKnowledgeChannels("后端部署", 5, List.of());

        assertEquals(List.of("intent-1", "global-1"), result.stream().map(RetrievedChunk::getId).toList());
        assertEquals(List.of(0.8f, 0.70000005f), result.stream().map(RetrievedChunk::getScore).toList());
    }

    private record StubChannel(String name,
                               int priority,
                               boolean enabled,
                               List<RetrievedChunk> chunks) implements SearchChannel {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public boolean isEnabled(SearchChannelContext ctx) {
            return enabled;
        }

        @Override
        public List<RetrievedChunk> search(SearchChannelContext ctx) {
            return chunks;
        }
    }
}
