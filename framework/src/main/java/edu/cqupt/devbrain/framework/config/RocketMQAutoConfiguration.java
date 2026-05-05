package edu.cqupt.devbrain.framework.config;


import edu.cqupt.devbrain.framework.mq.producer.DelegatingTransactionListener;
import edu.cqupt.devbrain.framework.mq.producer.MessageQueueProducer;
import edu.cqupt.devbrain.framework.mq.producer.RocketMQProducerAdapter;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 消息队列自动装配配置类。
 * <p>
 * 职责：
 * <ul>
 *     <li>当 classpath 中存在 {@link RocketMQTemplate} 时自动生效</li>
 *     <li>注册 {@link DelegatingTransactionListener} 事务消息监听器</li>
 *     <li>注册 {@link MessageQueueProducer} 消息生产者适配器，供业务层直接注入使用</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(RocketMQTemplate.class)
public class RocketMQAutoConfiguration {

    /**
     * 注册通用事务消息监听器，处理事务消息的本地执行和回查。
     */
    @Bean
    @ConditionalOnMissingBean
    public DelegatingTransactionListener delegatingTransactionListener() {
        return new DelegatingTransactionListener();
    }

    /**
     * 注册消息生产者适配器，将 RocketMQTemplate 封装为统一的 MessageQueueProducer 接口。
     */
    @Bean
    @ConditionalOnMissingBean(MessageQueueProducer.class)
    public MessageQueueProducer messageQueueProducer(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                                                     DelegatingTransactionListener transactionListener) {
        return new RocketMQProducerAdapter(rocketMQTemplateProvider, transactionListener);
    }
}
