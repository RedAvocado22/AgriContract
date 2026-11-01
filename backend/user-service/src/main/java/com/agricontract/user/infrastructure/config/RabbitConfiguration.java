package com.agricontract.user.infrastructure.config;

import com.agricontract.user.application.exception.InvalidEventPayloadException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.Map;

/**
 * Commands leave on the user exchange through the java-eventing outbox poller. The exchange is declared from the
 * poller's own property, so an override cannot leave it publishing to an undeclared exchange.
 * reputation.locked/unlocked come in on agricontract.reputation; both routing keys share one queue so they are
 * consumed in publish order. The reputation exchange is declared here too (idempotent), so the binding exists
 * before reputation-service first publishes.
 */
@Configuration
public class RabbitConfiguration {

    public static final String REPUTATION_EXCHANGE = "agricontract.reputation";
    public static final String USER_DLX = "agricontract.user.dlx";
    public static final String REPUTATION_LOCK_QUEUE = "user-svc.reputation";
    public static final String REPUTATION_LOCK_DLQ = REPUTATION_LOCK_QUEUE + ".dlq";

    @Bean
    TopicExchange userExchange(@Value("${agricontract.eventing.outbox.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Declarables reputationLockDeclarables() {
        TopicExchange reputation = new TopicExchange(REPUTATION_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(USER_DLX, true, false);
        Queue queue = QueueBuilder.durable(REPUTATION_LOCK_QUEUE)
                .deadLetterExchange(USER_DLX)
                .deadLetterRoutingKey(REPUTATION_LOCK_QUEUE)
                .build();
        Queue dlq = QueueBuilder.durable(REPUTATION_LOCK_DLQ).build();
        return new Declarables(reputation, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(reputation).with("reputation.locked"),
                BindingBuilder.bind(queue).to(reputation).with("reputation.unlocked"),
                BindingBuilder.bind(dlq).to(dlx).with(REPUTATION_LOCK_QUEUE));
    }

    // Keeps Boot's spring.rabbitmq.listener.simple.* settings (tests turn auto-startup off) and adds the retry policy.
    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(listenerRetry());
        return factory;
    }

    // 3 attempts, 1s then 2s apart, then the DLQ. A schema violation goes to the DLQ at once.
    private static RetryOperationsInterceptor listenerRetry() {
        Map<Class<? extends Throwable>, Boolean> retryable = Map.of(InvalidEventPayloadException.class, false);
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, retryable, true, true))
                .backOffOptions(1000, 2, 4000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
