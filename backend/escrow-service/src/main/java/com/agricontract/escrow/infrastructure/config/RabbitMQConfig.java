package com.agricontract.escrow.infrastructure.config;

import com.agricontract.escrow.application.exception.InvalidEventPayloadException;
import com.agricontract.escrow.domain.exception.InvalidDepositLegException;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String CONTRACT_SIGNED_QUEUE = "escrow-svc.contract.signed";
    public static final String LOCK_COMPLETED_QUEUE = "escrow-svc.bank.lock_completed";
    public static final String LOCK_FAILED_QUEUE = "escrow-svc.bank.lock_failed";

    // One Declarables bean on purpose: DlqMonitor reads the .dlq queues from it.
    // The bank.lock_* queues have no listener until #102, but must exist now: bank's outbox poller stops its whole
    // batch on a result it cannot route (D23), so an unbound result would stall bank-service.
    @Bean
    public Declarables escrowDeclarables() {
        TopicExchange contracts = new TopicExchange("agricontract.contracts", true, false);
        TopicExchange bank = new TopicExchange("agricontract.bank", true, false);
        DirectExchange dlx = new DirectExchange("agricontract.contracts.dlx", true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(
                new TopicExchange("agricontract.escrow", true, false), contracts, bank, dlx));
        bindWithDlq(declarables, CONTRACT_SIGNED_QUEUE, contracts, "contract.signed", dlx);
        bindWithDlq(declarables, LOCK_COMPLETED_QUEUE, bank, "bank.lock_completed", dlx);
        bindWithDlq(declarables, LOCK_FAILED_QUEUE, bank, "bank.lock_failed", dlx);
        return new Declarables(declarables);
    }

    private static void bindWithDlq(List<Declarable> declarables, String queueName, TopicExchange exchange,
                                    String routingKey, DirectExchange dlx) {
        Queue queue = QueueBuilder.durable(queueName).deadLetterExchange(dlx.getName()).build();
        Queue dlq = QueueBuilder.durable(queueName + ".dlq").build();
        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
        declarables.add(BindingBuilder.bind(dlq).to(dlx).with(routingKey));
    }

    // JSON <-> Object converter, shared by both the sender (rabbitTemplate) and receiver (rabbitListenerContainerFactory) below
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        ObjectMapper amqpObjectMapper = objectMapper.copy();
        amqpObjectMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        return new Jackson2JsonMessageConverter(amqpObjectMapper);
    }

    // SEND side (e.g. OutboxPoller.publish...) — uses jsonMessageConverter to serialize object -> JSON
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // RECEIVE side (every @RabbitListener) — same converter parses JSON -> the param type declared in the method
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
                // A payload that passed the schema but breaks a deposit rule will not pass on a retry either.
                InvalidDepositLegException.class, false
        );
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true, true);

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(1000, 2, 4000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
