package com.agricontract.user.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service only publishes: commands leave on its own exchange through the java-eventing outbox poller
 * (agricontract.eventing.outbox.exchange). Consumers declare and bind their own queues.
 */
@Configuration
public class RabbitConfiguration {

    public static final String USER_EXCHANGE = "agricontract.user";

    @Bean
    TopicExchange userExchange() {
        return new TopicExchange(USER_EXCHANGE, true, false);
    }
}
