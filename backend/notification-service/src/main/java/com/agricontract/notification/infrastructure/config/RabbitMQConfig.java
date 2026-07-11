package com.agricontract.notification.infrastructure.config;

import com.agricontract.notification.application.exception.InvalidEventPayloadException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
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
@EnableRabbit
public class RabbitMQConfig {

    @Bean
    public Declarables notificationEventDeclarables() {
        TopicExchange contractsExchange = new TopicExchange("agricontract.contracts", true, false);
        TopicExchange escrowExchange = new TopicExchange("agricontract.escrow", true, false);
        TopicExchange productEventsExchange = new TopicExchange("agricontract.events", true, false);
        DirectExchange contractsDlx = new DirectExchange("notification-svc.contracts.dlx", true, false);
        DirectExchange escrowDlx = new DirectExchange("notification-svc.escrow.dlx", true, false);
        DirectExchange productEventsDlx = new DirectExchange("notification-svc.events.dlx", true, false);

        List<Declarable> declarables = new ArrayList<>(
                List.of(contractsExchange, escrowExchange, productEventsExchange, contractsDlx, escrowDlx, productEventsDlx));

        for (String routingKey : List.of("contract.signed", "contract.delivered", "contract.cancelled", "contract.disputed")) {
            Queue queue = QueueBuilder.durable("notification-svc." + routingKey)
                    .withArgument("x-dead-letter-exchange", contractsDlx.getName())
                    .build();
            Queue dlq = QueueBuilder.durable("notification-svc." + routingKey + ".dlq").build();

            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(queue).to(contractsExchange).with(routingKey));
            declarables.add(BindingBuilder.bind(dlq).to(contractsDlx).with(routingKey));
        }

        for (String routingKey : List.of("escrow.locked", "escrow.released", "escrow.penalized")) {
            Queue queue = QueueBuilder.durable("notification-svc." + routingKey)
                    .withArgument("x-dead-letter-exchange", escrowDlx.getName())
                    .build();
            Queue dlq = QueueBuilder.durable("notification-svc." + routingKey + ".dlq").build();

            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(queue).to(escrowExchange).with(routingKey));
            declarables.add(BindingBuilder.bind(dlq).to(escrowDlx).with(routingKey));
        }

        for (String routingKey : List.of("category.approved", "category.rejected")) {
            Queue queue = QueueBuilder.durable("notification-svc." + routingKey)
                    .withArgument("x-dead-letter-exchange", productEventsDlx.getName())
                    .build();
            Queue dlq = QueueBuilder.durable("notification-svc." + routingKey + ".dlq").build();

            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(queue).to(productEventsExchange).with(routingKey));
            declarables.add(BindingBuilder.bind(dlq).to(productEventsDlx).with(routingKey));
        }

        return new Declarables(declarables);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper amqpObjectMapper = new ObjectMapper();
        amqpObjectMapper.registerModule(new JavaTimeModule());
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

