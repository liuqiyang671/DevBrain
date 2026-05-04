package edu.cqupt.devbrain.core.mq;

import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.mq.producer.MessageQueueProducer;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.mq.event.DocumentChunkEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文档分块事务消息生产者，用于异步触发文档解析任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunkProducer {

    /**
     * 文档待处理状态。
     */
    private static final String STATUS_PENDING = "pending";

    /**
     * 文档处理失败状态。
     */
    private static final String STATUS_FAILED = "failed";

    /**
     * 文档处理中状态。
     */
    private static final String STATUS_PROCESSING = "processing";

    private final ObjectProvider<MessageQueueProducer> messageQueueProducerProvider;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Value("${rocketmq.topic.document-chunk:devbrain-document-chunk}")
    private String documentChunkTopic;

    /**
     * 发送事务消息并在本地事务中原子更新文档状态。
     *
     * @param docId 文档 ID
     * @param userId 触发解析的用户 ID
     * @param kbId 知识库 ID
     * @return true 表示成功触发异步解析，false 表示发送或本地事务失败
     */
    public boolean startChunk(String docId, String userId, String kbId) {
        MessageQueueProducer messageQueueProducer = messageQueueProducerProvider.getIfAvailable();
        if (messageQueueProducer == null) {
            log.warn("RocketMQ 生产者未初始化，无法触发异步文档解析，docId={}, userId={}, kbId={}", docId, userId, kbId);
            return false;
        }

        DocumentChunkEvent event = new DocumentChunkEvent(docId, userId, kbId);
        try {
            TransactionSendResult sendResult = messageQueueProducer.sendInTransaction(
                    documentChunkTopic, docId, "文档异步解析", event,
                    ignored -> executeLocalTransaction(docId, userId));
            boolean triggered = SendStatus.SEND_OK.equals(sendResult.getSendStatus())
                    && LocalTransactionState.COMMIT_MESSAGE.equals(sendResult.getLocalTransactionState());
            if (!triggered) {
                log.warn("文档解析事务消息未提交，docId={}, sendStatus={}, localTransactionState={}",
                        docId, sendResult.getSendStatus(), sendResult.getLocalTransactionState());
            }
            return triggered;
        } catch (Exception e) {
            log.error("文档解析事务消息发送失败，docId={}, userId={}, kbId={}", docId, userId, kbId, e);
            return false;
        }
    }

    /**
     * 本地事务：仅允许 pending/failed 文档切换为 processing，其他状态回滚消息。
     *
     * @param docId 文档 ID
     * @param userId 触发解析的用户 ID
     */
    private void executeLocalTransaction(String docId, String userId) {
        String currentStatus = knowledgeDocumentMapper.selectStatusById(docId);
        if (currentStatus == null) {
            throw new ClientException("文档不存在或已删除");
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(currentStatus)) {
            throw new ClientException("文档正在解析中");
        }
        if (!STATUS_PENDING.equalsIgnoreCase(currentStatus) && !STATUS_FAILED.equalsIgnoreCase(currentStatus)) {
            throw new ClientException("当前文档状态不允许解析: " + currentStatus);
        }

        int updated = knowledgeDocumentMapper.updatePendingOrFailedToProcessing(docId, userId);
        if (updated != 1) {
            throw new ClientException("文档状态更新失败，请稍后重试");
        }
        log.info("文档解析本地事务提交，docId={}, userId={}", docId, userId);
    }
}
