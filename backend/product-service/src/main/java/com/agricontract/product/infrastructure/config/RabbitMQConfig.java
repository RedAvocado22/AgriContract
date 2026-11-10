package com.agricontract.product.infrastructure.config;

import com.agricontract.product.application.exception.InvalidEventPayloadException;
import com.agricontract.product.infrastructure.messaging.DlqMonitor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * product-service publishes category.approved/category.rejected to agricontract.events and consumes contract.created,
 * contract.signed, contract.activated, contract.activation_failed and contract.withdrawn from agricontract.contracts,
 * one queue each (product-svc.&lt;routingKey&gt;; contract-service declares some ahead with the same arguments).
 * Processing failures are retried 3 times (1s/2s/4s); a rejected
 * (malformed) delivery is not retried. Exhausted or rejected deliveries go to the DLQ through the service-owned DLX,
 * bound with the original routing key, and DlqMonitor warns while a DLQ is non-empty (same as reputation-service).
 */
@Configuration
public class RabbitMQConfig {

    private static final String QUEUE_PREFIX = "product-svc.";
    public static final String CONTRACT_CREATED_QUEUE = QUEUE_PREFIX + "contract.created";
    public static final String CONTRACT_SIGNED_QUEUE = QUEUE_PREFIX + "contract.signed";
    public static final String CONTRACT_ACTIVATED_QUEUE = QUEUE_PREFIX + "contract.activated";
    public static final String CONTRACT_ACTIVATION_FAILED_QUEUE = QUEUE_PREFIX + "contract.activation_failed";
    public static final String CONTRACT_WITHDRAWN_QUEUE = QUEUE_PREFIX + "contract.withdrawn";
    /** The routing key is the queue name without the prefix. */
    static final List<String> CONTRACT_QUEUES = List.of(CONTRACT_CREATED_QUEUE, CONTRACT_SIGNED_QUEUE,
            CONTRACT_ACTIVATED_QUEUE, CONTRACT_ACTIVATION_FAILED_QUEUE, CONTRACT_WITHDRAWN_QUEUE);
    public static final List<String> DLQS = CONTRACT_QUEUES.stream().map(queue -> queue + ".dlq").toList();
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
        List<Declarable> declarables = new ArrayList<>(List.of(contracts, dlx));
        for (String queueName : CONTRACT_QUEUES) {
            String routingKey = queueName.substring(QUEUE_PREFIX.length());
            Queue queue = QueueBuilder.durable(queueName).deadLetterExchange(DLX).build();
            Queue dlq = QueueBuilder.durable(queueName + ".dlq").build();
            declarables.addAll(List.of(queue, dlq,
                    BindingBuilder.bind(queue).to(contracts).with(routingKey),
                    BindingBuilder.bind(dlq).to(dlx).with(routingKey)));
        }
        return new Declarables(declarables);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(listenerRetry(1000));
        return factory;
    }

    /**
     * traverseCauses: the container wraps the listener's InvalidEventPayloadException in a
     * ListenerExecutionFailedException, so without it a malformed event would still be retried.
     */
    public static MethodInterceptor listenerRetry(long initialIntervalMillis) {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, Map.of(
                        InvalidEventPayloadException.class, false,
                        AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(initialIntervalMillis, 2, initialIntervalMillis * 4)
                .recoverer((message, cause) -> {
                    // The default recoverer logs Message.toString(), which includes the payload.
                    throw new AmqpRejectAndDontRequeueException("contract event delivery rejected; inspect the DLQ");
                })
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "product.dlq-monitor.enabled", havingValue = "true", matchIfMissing = true)
    public DlqMonitor productDlqMonitor(CachingConnectionFactory connectionFactory,
                                        @Value("${product.dlq-monitor.timeout-ms:3000}") int timeout,
                                        @Value("${product.dlq-monitor.interval-ms:300000}") long interval) {
        return new DlqMonitor(connectionFactory.getRabbitConnectionFactory(), timeout, interval);
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
