package edu.cqupt.devbrain.framework.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 消息体包装器，用于统一封装发送到消息队列的业务载荷。
 * <p>
 * 职责：
 * <ul>
 *     <li>包装任意类型的业务对象，提供统一的消息结构</li>
 *     <li>自动生成唯一消息 ID（uuid），用于客户端幂等验证</li>
 *     <li>记录消息发送时间戳，便于消息追踪和超时判断</li>
 *     <li>支持业务 key（keys），可用于消息检索和幂等消费</li>
 * </ul>
 *
 * @param <T> 业务载荷类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageWrapper<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务 key
     */
    private String keys;

    /**
     * 业务载荷
     */
    private T body;

    /**
     * 唯一标识，用于客户端幂等验证
     */
    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    /**
     * 消息发送时间
     */
    @Builder.Default
    private Long timestamp = System.currentTimeMillis();
}
