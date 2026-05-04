package edu.cqupt.devbrain.knowledge.mq;

import edu.cqupt.devbrain.framework.mq.MessageWrapper;
import edu.cqupt.devbrain.knowledge.mq.event.DocumentChunkEvent;
import edu.cqupt.devbrain.knowledge.service.DocumentParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 文档分块消息消费者，负责消费 RocketMQ 消息并执行真实解析流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = "${rocketmq.topic.document-chunk:devbrain-document-chunk}",
        consumerGroup = "${rocketmq.group.document-chunk:devbrain-document-chunk-group}"
)
public class DocumentChunkConsumer implements RocketMQListener<MessageWrapper<DocumentChunkEvent>> {

    private final DocumentParseService documentParseService;

    /**
     * 消费文档分块事件，异常会继续抛出以触发 RocketMQ 重试。
     *
     * @param message 消息包装体
     */
    @Override
    public void onMessage(MessageWrapper<DocumentChunkEvent> message) {
        DocumentChunkEvent event = message.getBody();
        if (event == null) {
            throw new IllegalArgumentException("文档解析消息体为空");
        }

        log.info("开始消费文档解析消息，docId={}, kbId={}, userId={}, msgKey={}",
                event.getDocId(), event.getKbId(), event.getUserId(), message.getKeys());
        try {
            documentParseService.parseAndChunk(event.getDocId());
            log.info("文档解析消息消费成功，docId={}", event.getDocId());
        } catch (Exception e) {
            log.error("文档解析消息消费失败，docId={}", event.getDocId(), e);
            throw e;
        }
    }
}
