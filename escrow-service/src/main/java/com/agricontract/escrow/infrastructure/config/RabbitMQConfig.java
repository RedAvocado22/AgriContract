package com.agricontract.escrow.infrastructure.config;

import org.springframework.amqp.core.*;
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
}
