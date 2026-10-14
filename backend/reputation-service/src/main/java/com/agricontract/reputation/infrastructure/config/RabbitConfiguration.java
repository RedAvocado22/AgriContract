package com.agricontract.reputation.infrastructure.config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
@Configuration
public class RabbitConfiguration {
    @Bean
    Declarables reputationBindings() {
        var exchange = new TopicExchange("agricontract.contracts", true, false);
        var dlx = new DirectExchange("agricontract.contracts.dlx", true, false);
        List<Declarable> declarations = new ArrayList<>(List.of(exchange, dlx));
        for (String key : List.of("contract.settled", "milestone.dispute_resolved")) {
            String queueName = "reputation-svc." + key;
            var queue = QueueBuilder.durable(queueName).deadLetterExchange(dlx.getName())
                    .deadLetterRoutingKey(queueName).build();
            var dlq = QueueBuilder.durable(queue.getName() + ".dlq").build();
            declarations.addAll(List.of(queue, dlq, BindingBuilder.bind(queue).to(exchange).with(key),
                    BindingBuilder.bind(dlq).to(dlx).with(queueName)));
        }
        return new Declarables(declarations);
    }
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connection) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connection);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(1000, 2, 4000).recoverer((message, cause) -> {
                    // Default recoverer logs Message.toString(), which can include an invalid payload.
                    throw new AmqpRejectAndDontRequeueException("Reputation fact delivery rejected; inspect the restricted DLQ");
                }).build());
        return factory;
    }
}
