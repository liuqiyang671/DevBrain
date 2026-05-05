package edu.cqupt.devbrain.framework.mq.producer;

import cn.hutool.core.util.StrUtil;
import edu.cqupt.devbrain.framework.mq.MessageWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 基于 RocketMQ 的消息生产者适配器，实现 {@link MessageQueueProducer} 接口。
 * <p>
 * 职责：
 * <ul>
 *     <li>通过 {@link RocketMQTemplate} 发送同步消息和事务消息</li>
 *     <li>将业务载荷统一封装为 {@link MessageWrapper} 后发送</li>
 *     <li>配合 {@link DelegatingTransactionListener} 实现事务消息的本地事务执行</li>
 *     <li>提供统一的日志记录，便于消息发送过程的追踪排查</li>
 * </ul>
 */
@RequiredArgsConstructor
public class RocketMQProducerAdapter implements MessageQueueProducer {

    private static final Logger log = LoggerFactory.getLogger(RocketMQProducerAdapter.class);

    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final DelegatingTransactionListener transactionListener;

    /**
     * 发送同步消息到指定 topic。
     * 若 keys 为空则自动生成 UUID 作为消息唯一标识。
     */
    @Override
    public SendResult send(String topic, String keys, String bizDesc, Object body) {
        RocketMQTemplate rocketMQTemplate = requireRocketMQTemplate();
        keys = StrUtil.isEmpty(keys) ? UUID.randomUUID().toString() : keys;

        Message<MessageWrapper<Object>> message = MessageBuilder
                .withPayload(MessageWrapper.builder().keys(keys).body(body).build())
                .setHeader(MessageConst.PROPERTY_KEYS, keys)
                .build();

        SendResult sendResult;
        try {
            sendResult = rocketMQTemplate.syncSend(topic, message);
        } catch (Throwable ex) {
            log.error("[生产者] {} - 消息发送失败，topic: {}, keys: {}", bizDesc, topic, keys, ex);
            throw ex;
        }

        log.info("[生产者] {} - 发送结果: {}, 消息ID: {}, Keys: {}", bizDesc, sendResult.getSendStatus(), sendResult.getMsgId(), keys);
        return sendResult;
    }

    /**
     * 发送事务消息。
     * 先注册本地事务回调，发送 half 消息后由 Broker 触发本地事务执行。
     */
    @Override
    public TransactionSendResult sendInTransaction(String topic, String keys, String bizDesc, Object body,
                                                   Consumer<Object> localTransaction) {
        RocketMQTemplate rocketMQTemplate = requireRocketMQTemplate();
        keys = StrUtil.isEmpty(keys) ? UUID.randomUUID().toString() : keys;
        String txId = UUID.randomUUID().toString();

        transactionListener.registerLocalTransaction(txId, localTransaction);

        Message<MessageWrapper<Object>> message = MessageBuilder
                .withPayload(MessageWrapper.builder().keys(keys).body(body).build())
                .setHeader(MessageConst.PROPERTY_KEYS, keys)
                .setHeader(DelegatingTransactionListener.HEADER_TX_ID, txId)
                .setHeader(DelegatingTransactionListener.HEADER_TOPIC, topic)
                .build();

        TransactionSendResult sendResult;
        try {
            sendResult = rocketMQTemplate.sendMessageInTransaction(topic, message, null);
        } catch (Throwable ex) {
            log.error("[生产者] {} - 事务消息发送失败，topic: {}, keys: {}", bizDesc, topic, keys, ex);
            throw ex;
        }

        log.info("[生产者] {} - 事务消息发送结果: {}, 本地事务状态: {}, 消息ID: {}, Keys: {}",
                bizDesc, sendResult.getSendStatus(), sendResult.getLocalTransactionState(), sendResult.getMsgId(), keys);
        return sendResult;
    }

    /**
     * 获取 RocketMQTemplate 实例，若未初始化则抛出异常。
     */
    private RocketMQTemplate requireRocketMQTemplate() {
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            throw new IllegalStateException("RocketMQTemplate 未初始化，请检查 RocketMQ starter 和 rocketmq.name-server 配置");
        }
        return rocketMQTemplate;
    }
}
