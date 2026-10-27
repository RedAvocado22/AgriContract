package com.agricontract.product.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * product-service publishes category.approved/category.rejected to agricontract.events and consumes
 * contract.created from agricontract.contracts. Listener retry (3 attempts, 1s/2s/4s) and
 * no-requeue-on-reject live in application.yml; exhausted or malformed deliveries go to the DLQ through
 * the service-owned DLX, bound with the original routing key (same convention as reputation-service).
 */
@Configuration
public class RabbitMQConfig {

    public static final String CONTRACT_CREATED_QUEUE = "product-svc.contract.created";
    private static final String CONTRACT_CREATED_KEY = "contract.created";
    private static final String DLX = "agricontract.product.dlx";

    @Bean
    public Declarables productEventDeclarables() {
        TopicExchange exchange = new TopicExchange("agricontract.events", true, false);
        return new Declarables(List.of(exchange));
    }

    @Bean
    public Declarables contractEventDeclarables() {
        TopicExchange contracts = new TopicExchange("agricontract.contracts", true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue queue = QueueBuilder.durable(CONTRACT_CREATED_QUEUE).deadLetterExchange(DLX).build();
        Queue dlq = QueueBuilder.durable(CONTRACT_CREATED_QUEUE + ".dlq").build();
        return new Declarables(List.of(contracts, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(contracts).with(CONTRACT_CREATED_KEY),
                BindingBuilder.bind(dlq).to(dlx).with(CONTRACT_CREATED_KEY)));
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
}
