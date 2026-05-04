package edu.cqupt.devbrain.knowledge.mq;

import edu.cqupt.devbrain.framework.mq.MessageWrapper;
import edu.cqupt.devbrain.framework.mq.producer.DelegatingTransactionListener;
import edu.cqupt.devbrain.framework.mq.producer.TransactionChecker;
import edu.cqupt.devbrain.knowledge.dao.mapper.KnowledgeDocumentMapper;
import edu.cqupt.devbrain.knowledge.mq.event.DocumentChunkEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 文档分块事务消息回查器，通过数据库状态判断本地事务是否已提交。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunkTransactionChecker implements TransactionChecker {

    /**
     * 文档处理中状态，表示本地事务已经提交并生效。
     */
    private static final String STATUS_PROCESSING = "processing";

    private final DelegatingTransactionListener transactionListener;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Value("${rocketmq.topic.document-chunk:devbrain-document-chunk}")
    private String documentChunkTopic;

    /**
     * 将当前回查器注册到通用事务消息监听器。
     */
    @PostConstruct
    public void registerChecker() {
        transactionListener.registerChecker(documentChunkTopic, this);
        log.info("文档分块事务回查器注册完成，topic={}", documentChunkTopic);
    }

    /**
     * 根据消息中的 docId 查询文档状态，processing 表示本地事务已提交。
     *
     * @param message 消息体包装器
     * @return true 表示提交消息，false 表示回滚消息
     */
    @Override
    public boolean check(MessageWrapper<?> message) {
        DocumentChunkEvent event = extractEvent(message);
        if (event == null || event.getDocId() == null) {
            log.warn("文档分块事务回查失败，消息体为空或 docId 为空");
            return false;
        }

        String status = knowledgeDocumentMapper.selectStatusById(event.getDocId());
        boolean committed = STATUS_PROCESSING.equalsIgnoreCase(status);
        log.info("文档分块事务回查完成，docId={}, status={}, committed={}",
                event.getDocId(), status, committed);
        return committed;
    }

    /**
     * 从通用消息包装体中提取文档分块事件。
     *
     * @param message 消息包装体
     * @return 文档分块事件，无法解析时返回 null
     */
    private DocumentChunkEvent extractEvent(MessageWrapper<?> message) {
        Object body = message.getBody();
        if (body instanceof DocumentChunkEvent event) {
            return event;
        }
        return null;
    }
}
