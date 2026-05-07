package edu.cqupt.devbrain.rag.core.retrieve.channel;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import edu.cqupt.devbrain.framework.convention.RetrievedChunk;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeBaseDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeBaseMapper;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieveRequest;
import edu.cqupt.devbrain.rag.core.retrieve.RetrieverService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionParallelRetrieverTest {

    private final KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
    private final RetrieverService retrieverService = mock(RetrieverService.class);
    private final CollectionParallelRetriever retriever =
            new CollectionParallelRetriever(knowledgeBaseMapper, retrieverService, Runnable::run);

    @Test
    void retrieveShouldSearchEachEnabledCollection() {
        KnowledgeBaseDO dev = knowledgeBase("kb_dev");
        KnowledgeBaseDO hr = knowledgeBase("kb_hr");
        when(knowledgeBaseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(dev, hr));
        when(retrieverService.retrieve(any(RetrieveRequest.class))).thenReturn(List.of(
                new RetrievedChunk("chunk-1", "命中文本", 0.9f)
        ));

        List<RetrievedChunk> result = retriever.retrieve("部署方式", 3);

        assertEquals(2, result.size());
        ArgumentCaptor<RetrieveRequest> captor = ArgumentCaptor.forClass(RetrieveRequest.class);
        verify(retrieverService, times(2)).retrieve(captor.capture());
        assertEquals(List.of("kb_dev", "kb_hr"),
                captor.getAllValues().stream().map(RetrieveRequest::getCollectionName).toList());
    }

    private KnowledgeBaseDO knowledgeBase(String collectionName) {
        KnowledgeBaseDO entity = new KnowledgeBaseDO();
        entity.setCollectionName(collectionName);
        entity.setStatus("enabled");
        return entity;
    }
}
