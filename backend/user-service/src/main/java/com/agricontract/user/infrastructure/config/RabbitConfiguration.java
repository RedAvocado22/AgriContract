package com.agricontract.user.infrastructure.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * user-service only publishes: commands leave on its own exchange through the java-eventing outbox poller. The
 * exchange is declared from the poller's own property, so an override cannot leave it publishing to an undeclared
 * exchange. Consumers declare and bind their own queues.
 */
@Configuration
public class RabbitConfiguration {

    @Bean
    TopicExchange userExchange(@Value("${agricontract.eventing.outbox.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }
}
