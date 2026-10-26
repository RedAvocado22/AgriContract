package com.agricontract.bank.infrastructure.config;

import com.agricontract.bank.infrastructure.messaging.DlqMonitor;
import com.agricontract.bank.infrastructure.messaging.InvalidEventPayloadException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.Map;

/**
 * Commands arrive on escrow's exchange; results leave on bank's own exchange (published by the java-eventing outbox
 * poller, agricontract.eventing.outbox.exchange). bank-service has its own DLX, as every new consumer must.
 */
@Configuration
@EnableScheduling
public class RabbitConfiguration {

    public static final String ESCROW_EXCHANGE = "agricontract.escrow";
    public static final String BANK_EXCHANGE = "agricontract.bank";
    public static final String DLX = "agricontract.bank.dlx";
    public static final String LOCK_REQUEST_KEY = "bank.lock_requested";
    public static final String LOCK_REQUEST_QUEUE = "bank-svc." + LOCK_REQUEST_KEY;
    public static final String LOCK_REQUEST_DLQ = LOCK_REQUEST_QUEUE + ".dlq";
    public static final List<String> DLQS = List.of(LOCK_REQUEST_DLQ);

    @Bean
    Declarables bankTopology() {
        TopicExchange escrow = new TopicExchange(ESCROW_EXCHANGE, true, false);
        TopicExchange bank = new TopicExchange(BANK_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue lockRequests = QueueBuilder.durable(LOCK_REQUEST_QUEUE).deadLetterExchange(DLX).build();
        Queue lockRequestsDlq = QueueBuilder.durable(LOCK_REQUEST_DLQ).build();
        return new Declarables(escrow, bank, dlx, lockRequests, lockRequestsDlq,
                BindingBuilder.bind(lockRequests).to(escrow).with(LOCK_REQUEST_KEY),
                // Dead-lettered messages keep their original routing key.
                BindingBuilder.bind(lockRequestsDlq).to(dlx).with(LOCK_REQUEST_KEY));
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        // A broken payload fails the same way every time: straight to the DLQ. Other failures (database down) get
        // three attempts first.
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3,
                Map.of(InvalidEventPayloadException.class, false, AmqpRejectAndDontRequeueException.class, false),
                true, true);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(retryPolicy)
                .backOffOptions(1000, 2, 4000)
                .recoverer((message, cause) -> {
                    // The default recoverer logs the whole message, payload included.
                    throw new AmqpRejectAndDontRequeueException("bank command rejected to the DLQ", cause);
                })
                .build());
        return factory;
    }

    @Bean
    @ConditionalOnProperty(name = "bank.dlq-monitor.enabled", havingValue = "true", matchIfMissing = true)
    DlqMonitor bankDlqMonitor(CachingConnectionFactory connectionFactory,
                              @Value("${bank.dlq-monitor.timeout-ms:3000}") int timeoutMillis) {
        return new DlqMonitor(connectionFactory.getRabbitConnectionFactory(), DLQS, timeoutMillis);
    }
}
