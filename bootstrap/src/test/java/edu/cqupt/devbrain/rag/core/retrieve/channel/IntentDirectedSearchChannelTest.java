package edu.cqupt.devbrain.rag.core.retrieve.channel;

import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.rag.core.intent.IntentNode;
import edu.cqupt.devbrain.rag.core.intent.IntentProperties;
import edu.cqupt.devbrain.rag.core.intent.NodeScore;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieveRequest;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieverService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentDirectedSearchChannelTest {

    private final RetrieverService retrieverService = mock(RetrieverService.class);
    private final IntentParallelRetriever parallelRetriever =
            new IntentParallelRetriever(retrieverService, Runnable::run);
    private final IntentProperties properties = new IntentProperties();
    private final IntentDirectedSearchChannel channel =
            new IntentDirectedSearchChannel(parallelRetriever, properties);

    @Test
    void shouldEnableOnlyWhenKbIntentScoreReachesThreshold() {
        properties.setMinScore(0.35);

        assertFalse(channel.isEnabled(SearchChannelContext.builder()
                .query("后端部署")
                .topK(3)
                .kbIntents(List.of(score("kb-dev", "kb_dev", 0.2)))
                .build()));

        assertTrue(channel.isEnabled(SearchChannelContext.builder()
                .query("后端部署")
                .topK(3)
                .kbIntents(List.of(score("kb-dev", "kb_dev", 0.8)))
                .build()));
    }

    @Test
    void searchShouldRetrieveEachDistinctCollection() {
        when(retrieverService.retrieve(any(RetrieveRequest.class))).thenReturn(List.of(
                new RetrievedChunk("c1", "部署说明", 0.9f)
        ));

        List<RetrievedChunk> result = channel.search(SearchChannelContext.builder()
                .query("后端部署")
                .topK(5)
                .kbIntents(List.of(
                        score("kb-dev", "kb_dev", 0.92),
                        score("kb-dev-copy", "kb_dev", 0.7),
                        score("kb-hr", "kb_hr", 0.88)
                ))
                .build());

        assertEquals(2, result.size());
        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(retrieverService, times(2)).retrieve(captor.capture());
        assertEquals(List.of("kb_dev", "kb_hr"),
                captor.getAllValues().stream().map(RetrieveRequest::getCollectionName).toList());
        assertEquals(List.of(5, 5),
                captor.getAllValues().stream().map(RetrieveRequest::getTopK).toList());
    }

    private NodeScore score(String id, String collectionName, double value) {
        IntentNode node = new IntentNode();
        node.setId(id);
        node.setName(id);
        node.setKind("KB");
        node.setCollectionName(collectionName);
        return new NodeScore(node, value);
    }
}
