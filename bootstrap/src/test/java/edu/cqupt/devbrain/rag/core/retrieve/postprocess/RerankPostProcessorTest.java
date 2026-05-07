package edu.cqupt.devbrain.rag.core.retrieve.postprocess;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.retrieve.rerank.RerankService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RerankPostProcessorTest {

    @Test
    void processShouldDelegateToRerankService() {
        RerankService rerankService = chunks -> {
            List<RetrievedChunk> reversed = new ArrayList<>(chunks);
            Collections.reverse(reversed);
            return reversed;
        };
        RerankPostProcessor processor = new RerankPostProcessor(rerankService);

        List<RetrievedChunk> result = processor.process(List.of(
                new RetrievedChunk("c1", "低分", 0.1f),
                new RetrievedChunk("c2", "高分", 0.9f)
        ));

        assertEquals(List.of("c2", "c1"), result.stream().map(RetrievedChunk::getId).toList());
    }
}
