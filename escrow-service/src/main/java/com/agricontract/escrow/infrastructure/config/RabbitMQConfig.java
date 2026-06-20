package com.agricontract.escrow.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Declarables contractEventDeclarables() {
        TopicExchange exchangeContracts = new TopicExchange("agricontract.contracts", true, false);
        TopicExchange exchangeEscrow = new TopicExchange("agricontract.escrow", true, false);
        DirectExchange dlx = new DirectExchange("agricontract.contracts.dlx", true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(exchangeContracts, exchangeEscrow, dlx));

        for (String event : List.of("signed", "delivered", "cancelled")) {
            String routingKey = "contract." + event;
            Queue queue = QueueBuilder.durable("escrow-svc." + routingKey)
                    .withArgument("x-dead-letter-exchange", dlx.getName())
                    .build();
            Queue dlq = QueueBuilder.durable("escrow-svc." + routingKey + ".dlq").build();

            declarables.add(queue);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(queue).to(exchangeContracts).with(routingKey));
            declarables.add(BindingBuilder.bind(dlq).to(dlx).with(routingKey));
        }

        return new Declarables(declarables);
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

    // RECEIVE side (every @RabbitListener, e.g. ContractEventConsumer) — same converter parses JSON -> the param type declared in the method
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}
