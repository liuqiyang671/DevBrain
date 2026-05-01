package edu.cqupt.devbrain.framework.mq;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * 消息体包装器
 */
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
    private String uuid;

    /**
     * 消息发送时间
     */
    private Long timestamp;

    public MessageWrapper() {
        this.uuid = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public MessageWrapper(String keys, T body, String uuid, Long timestamp) {
        this.keys = keys;
        this.body = body;
        this.uuid = uuid;
        this.timestamp = timestamp;
    }

    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
    }

    public T getBody() {
        return body;
    }

    public void setBody(T body) {
        this.body = body;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public static <T> MessageWrapperBuilder<T> builder() {
        return new MessageWrapperBuilder<>();
    }

    public static class MessageWrapperBuilder<T> {
        private String keys;
        private T body;
        private String uuid = UUID.randomUUID().toString();
        private Long timestamp = System.currentTimeMillis();

        public MessageWrapperBuilder<T> keys(String keys) {
            this.keys = keys;
            return this;
        }

        public MessageWrapperBuilder<T> body(T body) {
            this.body = body;
            return this;
        }

        public MessageWrapperBuilder<T> uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public MessageWrapperBuilder<T> timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MessageWrapper<T> build() {
            return new MessageWrapper<>(keys, body, uuid, timestamp);
        }
    }
}
