package com.agricontract.contract.infrastructure.config;

import com.agricontract.contract.application.exception.ActivationRefusedException;
import com.agricontract.contract.application.exception.InvalidEventPayloadException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.List;
import java.util.Map;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    public static final String DEPOSIT_LOCKED_QUEUE = "contract-svc.escrow.deposit_locked";
    public static final String DEPOSIT_LOCK_FAILED_QUEUE = "contract-svc.escrow.deposit_lock_failed";
    public static final String ACTIVATION_REFUND_COMPLETED_QUEUE = "contract-svc.escrow.activation_refund_completed";

    // DlqMonitor reads the .dlq queues from this bean. agricontract.escrow.dlx is contract-service's own DLX
    // (kept from Phase 1, used by no other service).
    @Bean
    public Declarables contractDeclarables() {
        TopicExchange escrow = new TopicExchange("agricontract.escrow", true, false);
        DirectExchange dlx = new DirectExchange("agricontract.escrow.dlx", true, false);
        Queue depositLocked = QueueBuilder.durable(DEPOSIT_LOCKED_QUEUE).deadLetterExchange(dlx.getName()).build();
        Queue depositLockedDlq = QueueBuilder.durable(DEPOSIT_LOCKED_QUEUE + ".dlq").build();
        // Same name and DLX escrow-service declared ahead for it (#376), so both declarations agree.
        Queue lockFailed = QueueBuilder.durable(DEPOSIT_LOCK_FAILED_QUEUE).deadLetterExchange(dlx.getName()).build();
        Queue lockFailedDlq = QueueBuilder.durable(DEPOSIT_LOCK_FAILED_QUEUE + ".dlq").build();
        // Same name and DLX escrow-service declared ahead for it (#385).
        Queue refundCompleted = QueueBuilder.durable(ACTIVATION_REFUND_COMPLETED_QUEUE)
                .deadLetterExchange(dlx.getName()).build();
        Queue refundCompletedDlq = QueueBuilder.durable(ACTIVATION_REFUND_COMPLETED_QUEUE + ".dlq").build();
        return new Declarables(List.of(
                new TopicExchange("agricontract.contracts", true, false), escrow, dlx,
                depositLocked, depositLockedDlq,
                BindingBuilder.bind(depositLocked).to(escrow).with("escrow.deposit_locked"),
                BindingBuilder.bind(depositLockedDlq).to(dlx).with("escrow.deposit_locked"),
                lockFailed, lockFailedDlq,
                BindingBuilder.bind(lockFailed).to(escrow).with("escrow.deposit_lock_failed"),
                BindingBuilder.bind(lockFailedDlq).to(dlx).with("escrow.deposit_lock_failed"),
                refundCompleted, refundCompletedDlq,
                BindingBuilder.bind(refundCompleted).to(escrow).with("escrow.activation_refund_completed"),
                BindingBuilder.bind(refundCompletedDlq).to(dlx).with("escrow.activation_refund_completed")));
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        ObjectMapper amqpObjectMapper = objectMapper.copy();
        amqpObjectMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        return new Jackson2JsonMessageConverter(amqpObjectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                InvalidEventPayloadException.class, false,
                // A contract that cannot be activated now will not be on a retry either (D40).
                ActivationRefusedException.class, false
        );
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true, true);

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(1000, 2, 4000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
