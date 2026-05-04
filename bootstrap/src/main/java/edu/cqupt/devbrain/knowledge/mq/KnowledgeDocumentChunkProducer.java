package edu.cqupt.devbrain.knowledge.mq;

import edu.cqupt.devbrain.framework.mq.MessageWrapper;
import edu.cqupt.devbrain.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 知识库文档分块事件生产者，发送异步分块任务到 RocketMQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentChunkProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 发送文档分块事件。
     *
     * @param docId    文档 ID
     * @param kbId     知识库 ID
     * @param operator 操作人用户 ID
     */
    public void sendChunkEvent(String docId, String kbId, String operator) {
        KnowledgeDocumentChunkEvent event = new KnowledgeDocumentChunkEvent(docId, kbId, operator);
        MessageWrapper<KnowledgeDocumentChunkEvent> wrapper = MessageWrapper.<KnowledgeDocumentChunkEvent>builder()
                .keys(docId)
                .body(event)
                .build();

        try {
            Message<MessageWrapper<KnowledgeDocumentChunkEvent>> message = MessageBuilder
                    .withPayload(wrapper)
                    .setHeader("KEYS", docId)
                    .build();
            rocketMQTemplate.send("knowledge-document-chunk_topic", message);
            log.info("文档分块事件发送成功，docId={}, kbId={}, operator={}", docId, kbId, operator);
        } catch (Exception e) {
            log.error("文档分块事件发送失败，docId={}, kbId={}, operator={}", docId, kbId, operator, e);
        }
    }
}
