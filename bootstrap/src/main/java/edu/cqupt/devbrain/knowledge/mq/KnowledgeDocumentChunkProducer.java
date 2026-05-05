package edu.cqupt.devbrain.knowledge.mq;

import edu.cqupt.devbrain.framework.exception.ClientException;
import edu.cqupt.devbrain.framework.mq.producer.MessageQueueProducer;
import edu.cqupt.devbrain.knowledge.dao.entity.KnowledgeDocumentChunkLogDO;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentChunkLogMapper;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 知识库文档分块事件生产者，负责用事务消息触发异步分块任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentChunkProducer {

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

    /**
     * 文档处理完成状态，完成后不允许再次触发分块。
     */
    private static final String STATUS_COMPLETED = "completed";

    /**
     * 分块日志运行中状态。
     */
    private static final String LOG_STATUS_PROCESSING = "processing";

    /**
     * 兼容旧链路中的运行中状态。
     */
    private static final String LOG_STATUS_RUNNING = "RUNNING";

    private final ObjectProvider<MessageQueueProducer> messageQueueProducerProvider;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeDocumentChunkLogMapper chunkLogMapper;

    @Value("${rocketmq.topic.knowledge-document-chunk:knowledge-document-chunk_topic}")
    private String knowledgeDocumentChunkTopic;

    /**
     * 发送事务消息并在本地事务中原子更新文档状态。
     *
     * @param docId    文档 ID
     * @param kbId     知识库 ID
     * @param operator 操作人用户 ID
     * @return true 表示消息和本地事务均已提交
     */
    public boolean startChunk(String docId, String kbId, String operator) {
        MessageQueueProducer messageQueueProducer = messageQueueProducerProvider.getIfAvailable();
        if (messageQueueProducer == null) {
            log.warn("RocketMQ 生产者未初始化，无法触发知识库文档分块，docId={}, kbId={}", docId, kbId);
            return false;
        }

        KnowledgeDocumentChunkEvent event = new KnowledgeDocumentChunkEvent(docId, kbId, operator);
        try {
            TransactionSendResult sendResult = messageQueueProducer.sendInTransaction(
                    knowledgeDocumentChunkTopic,
                    docId,
                    "知识库文档异步分块",
                    event,
                    ignored -> executeLocalTransaction(docId, operator)
            );
            boolean committed = SendStatus.SEND_OK.equals(sendResult.getSendStatus())
                    && LocalTransactionState.COMMIT_MESSAGE.equals(sendResult.getLocalTransactionState());
            if (!committed) {
                log.warn("知识库文档分块事务消息未提交，docId={}, sendStatus={}, localTransactionState={}",
                        docId, sendResult.getSendStatus(), sendResult.getLocalTransactionState());
            }
            return committed;
        } catch (Exception e) {
            log.error("知识库文档分块事务消息发送失败，docId={}, kbId={}, operator={}", docId, kbId, operator, e);
            return false;
        }
    }

    /**
     * 兼容旧调用点的普通触发方法，内部统一改走事务消息。
     *
     * @param docId    文档 ID
     * @param kbId     知识库 ID
     * @param operator 操作人用户 ID
     */
    public void sendChunkEvent(String docId, String kbId, String operator) {
        startChunk(docId, kbId, operator);
    }

    /**
     * 本地事务：只有 pending/failed 文档可以切换到 processing。
     *
     * @param docId    文档 ID
     * @param operator 操作人用户 ID
     */
    private void executeLocalTransaction(String docId, String operator) {
        String currentStatus = knowledgeDocumentMapper.selectStatusById(docId);
        if (currentStatus == null) {
            throw new ClientException("文档不存在或已删除");
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(currentStatus) && hasRunningChunkLog(docId)) {
            throw new ClientException("文档正在分块处理中");
        }
        if (STATUS_COMPLETED.equalsIgnoreCase(currentStatus)) {
            throw new ClientException("文档已分块成功，不允许重复分块");
        }
        if (!STATUS_PENDING.equalsIgnoreCase(currentStatus)
                && !STATUS_FAILED.equalsIgnoreCase(currentStatus)) {
            throw new ClientException("当前文档状态不允许分块: " + currentStatus);
        }

        int updated = knowledgeDocumentMapper.updateStartableToProcessing(docId, operator);
        if (updated != 1) {
            throw new ClientException("文档状态更新失败，请稍后重试");
        }
        log.info("知识库文档分块本地事务提交，docId={}, operator={}", docId, operator);
    }

    /**
     * 判断是否存在未结束的分块日志，用于避免重复消费或重复点击造成并发分块。
     *
     * @param docId 文档 ID
     * @return 存在运行中日志返回 true
     */
    private boolean hasRunningChunkLog(String docId) {
        KnowledgeDocumentChunkLogDO latestLog = chunkLogMapper.selectLatestByDocId(docId);
        return latestLog != null
                && latestLog.getEndTime() == null
                && (LOG_STATUS_PROCESSING.equalsIgnoreCase(latestLog.getStatus())
                || LOG_STATUS_RUNNING.equalsIgnoreCase(latestLog.getStatus()));
    }
}
