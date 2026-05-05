package edu.cqupt.devbrain.knowledge.mq;

import edu.cqupt.devbrain.framework.mq.producer.MessageQueueProducer;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentChunkProducerTest {

    private final MessageQueueProducer messageQueueProducer = mock(MessageQueueProducer.class);
    private final ObjectProvider<MessageQueueProducer> messageQueueProducerProvider = mock(ObjectProvider.class);
    private final KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper = mock(KnowledgeDocumentChunkLogMapper.class);

    private final KnowledgeDocumentChunkProducer producer = new KnowledgeDocumentChunkProducer(
            messageQueueProducerProvider, knowledgeDocumentMapper, chunkLogMapper
    );

    @Test
    void startChunkSendsTransactionMessageAndUpdatesStatus() {
        // given
        ReflectionTestUtils.setField(producer, "knowledgeDocumentChunkTopic", "knowledge-document-chunk_topic");
        when(messageQueueProducerProvider.getIfAvailable()).thenReturn(messageQueueProducer);
        when(knowledgeDocumentMapper.selectStatusById("doc-1")).thenReturn("pending");
        when(knowledgeDocumentMapper.updateStartableToProcessing("doc-1", "user-1")).thenReturn(1);
        when(messageQueueProducer.sendInTransaction(eq("knowledge-document-chunk_topic"), eq("doc-1"),
                eq("知识库文档异步分块"), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Object> localTransaction = invocation.getArgument(4);
            localTransaction.accept(null);
            TransactionSendResult result = new TransactionSendResult();
            result.setSendStatus(SendStatus.SEND_OK);
            result.setLocalTransactionState(LocalTransactionState.COMMIT_MESSAGE);
            return result;
        });

        // when
        boolean triggered = producer.startChunk("doc-1", "kb-1", "user-1");

        // then
        assertTrue(triggered);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messageQueueProducer).sendInTransaction(eq("knowledge-document-chunk_topic"), eq("doc-1"),
                eq("知识库文档异步分块"), eventCaptor.capture(), any());
        KnowledgeDocumentChunkEvent event = assertInstanceOf(KnowledgeDocumentChunkEvent.class, eventCaptor.getValue());
        assertEquals("doc-1", event.getDocId());
        assertEquals("kb-1", event.getKbId());
        assertEquals("user-1", event.getOperator());
        verify(knowledgeDocumentMapper).updateStartableToProcessing("doc-1", "user-1");
    }

    @Test
    void startChunkReturnsFalseWhenProducerMissing() {
        // given
        when(messageQueueProducerProvider.getIfAvailable()).thenReturn(null);

        // when
        boolean triggered = producer.startChunk("doc-1", "kb-1", "user-1");

        // then
        assertFalse(triggered);
        verify(messageQueueProducer, never()).sendInTransaction(anyString(), anyString(), anyString(), any(), any());
        verify(knowledgeDocumentMapper, never()).updateStartableToProcessing(anyString(), anyString());
    }

    @Test
    void startChunkRollsBackCompletedDocument() {
        // given
        ReflectionTestUtils.setField(producer, "knowledgeDocumentChunkTopic", "knowledge-document-chunk_topic");
        when(messageQueueProducerProvider.getIfAvailable()).thenReturn(messageQueueProducer);
        when(knowledgeDocumentMapper.selectStatusById("doc-1")).thenReturn("completed");
        when(messageQueueProducer.sendInTransaction(eq("knowledge-document-chunk_topic"), eq("doc-1"),
                eq("知识库文档异步分块"), any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<Object> localTransaction = invocation.getArgument(4);
            TransactionSendResult result = new TransactionSendResult();
            result.setSendStatus(SendStatus.SEND_OK);
            try {
                localTransaction.accept(null);
                result.setLocalTransactionState(LocalTransactionState.COMMIT_MESSAGE);
            } catch (Exception ignored) {
                result.setLocalTransactionState(LocalTransactionState.ROLLBACK_MESSAGE);
            }
            return result;
        });

        // when
        boolean triggered = producer.startChunk("doc-1", "kb-1", "user-1");

        // then
        assertFalse(triggered);
        verify(knowledgeDocumentMapper, never()).updateStartableToProcessing(anyString(), anyString());
    }
}
