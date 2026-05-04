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
 * RocketMQ 消息队列自动装配配置
 */
@Configuration
@ConditionalOnClass(RocketMQTemplate.class)
public class RocketMQAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DelegatingTransactionListener delegatingTransactionListener() {
        return new DelegatingTransactionListener();
    }

    @Bean
    @ConditionalOnMissingBean(MessageQueueProducer.class)
    public MessageQueueProducer messageQueueProducer(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                                                     DelegatingTransactionListener transactionListener) {
        return new RocketMQProducerAdapter(rocketMQTemplateProvider, transactionListener);
    }
}
