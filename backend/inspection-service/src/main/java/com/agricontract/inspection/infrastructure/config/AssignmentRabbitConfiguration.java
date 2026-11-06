package com.agricontract.inspection.infrastructure.config;

import java.util.Map;
import java.util.List;
import com.agricontract.inspection.application.exception.InvalidEventPayloadException;
import com.agricontract.inspection.infrastructure.messaging.DlqMonitor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.*;
import org.springframework.retry.policy.SimpleRetryPolicy;

@Configuration
public class AssignmentRabbitConfiguration {
    public static final String KEY = "inspection.level1_5_requested";
    public static final String QUEUE = "inspection-svc." + KEY;
    public static final String DLQ = QUEUE + ".dlq";
    public static final String DLX = "agricontract.inspection.dlx";
    public static final List<String> DLQS = List.of(DLQ);

    @Bean
    @ConditionalOnProperty(name = "inspection.dlq-monitor.enabled", havingValue = "true", matchIfMissing = true)
    DlqMonitor dlqMonitor(org.springframework.amqp.rabbit.connection.CachingConnectionFactory connection,
            @Value("${inspection.dlq-monitor.timeout-ms:3000}") int timeoutMillis) {
        return new DlqMonitor(connection.getRabbitConnectionFactory(), DLQS, timeoutMillis);
    }

    @Bean
    Declarables assignmentBindings() {
        var exchange = new TopicExchange("agricontract.contracts", true, false);
        var dlx = new DirectExchange(DLX, true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(dlx.getName()).build();
        var dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(exchange).with(KEY),
                BindingBuilder.bind(dlq).to(dlx).with(KEY));
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connection) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connection);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false,
                        InvalidEventPayloadException.class, false), true, true))
                .backOffOptions(1000, 2, 4000).recoverer((message, cause) -> {
                    throw new AmqpRejectAndDontRequeueException("Assignment delivery rejected; inspect the restricted DLQ");
                }).build());
        return factory;
    }
}
