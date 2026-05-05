package edu.cqupt.devbrain.knowledge.mq;

import edu.cqupt.devbrain.framework.context.LoginUser;
import edu.cqupt.devbrain.framework.context.UserContext;
import edu.cqupt.devbrain.framework.mq.MessageWrapper;
import edu.cqupt.devbrain.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import edu.cqupt.devbrain.knowledge.service.KnowledgeDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 知识库文档分块消费者，消费 RocketMQ 消息并调用文档分块服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${rocketmq.topic.knowledge-document-chunk:knowledge-document-chunk_topic}",
        consumerGroup = "knowledge-document-chunk_consumer"
)
public class KnowledgeDocumentChunkConsumer implements RocketMQListener<MessageWrapper<KnowledgeDocumentChunkEvent>> {

    private final KnowledgeDocumentService documentService;

    /**
     * 消费文档分块事件消息，设置用户上下文后调用文档分块服务执行分块。
     *
     * @param message 消息包装体
     */
    @Override
    public void onMessage(MessageWrapper<KnowledgeDocumentChunkEvent> message) {
        KnowledgeDocumentChunkEvent event = message.getBody();
        if (event == null) {
            log.warn("收到空消息体，跳过处理");
            return;
        }

        log.info("开始消费文档分块消息，docId={}, kbId={}, operator={}",
                event.getDocId(), event.getKbId(), event.getOperator());

        try {
            setupUserContext(event.getOperator());
            documentService.executeChunk(event.getDocId());
            log.info("文档分块消息消费成功，docId={}", event.getDocId());
        } catch (Exception e) {
            log.error("文档分块消息消费失败，docId={}", event.getDocId(), e);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 将操作人信息注入 UserContext，供下游 Service 获取当前用户。
     *
     * @param operator 操作人用户 ID
     */
    private void setupUserContext(String operator) {
        if (operator == null) {
            return;
        }
        LoginUser user = new LoginUser(
                operator, null, null, null, null, Set.of(), Set.of()
        );
        UserContext.set(user);
    }
}
