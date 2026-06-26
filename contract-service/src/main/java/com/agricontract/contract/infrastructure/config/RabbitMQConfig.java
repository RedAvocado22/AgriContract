package com.agricontract.contract.infrastructure.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Declarables escrowEventDeclarables() {
        TopicExchange exchangeContracts = new TopicExchange("agricontract.contracts", true, false);
        TopicExchange exchangeEscrow = new TopicExchange("agricontract.escrow", true, false);
        DirectExchange dlx = new DirectExchange("agricontract.escrow.dlx", true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(exchangeContracts, exchangeEscrow, dlx));

        for (String event : List.of("locked", "released")) {
            String routingKey = "escrow." + event;
            Queue queue = QueueBuilder.durable("contract-svc." + routingKey)
                    .withArgument("x-dead-letter-exchange", dlx.getName())
                    .build();
            Queue dlq = QueueBuilder.durable("contract-svc." + routingKey + ".dlq").build();

            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(queue).to(exchangeEscrow).with(routingKey));
            declarables.add(BindingBuilder.bind(dlq).to(dlx).with(routingKey));
        }

        return new Declarables(declarables);
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
                InvalidEventPayloadException.class, false
        );
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true, true);

        return RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(1000, 2, 4000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
