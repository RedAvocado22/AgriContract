package com.agricontract.eventing;

import com.agricontract.eventing.outbox.OutboxPoller;
import com.agricontract.eventing.outbox.OutboxProperties;
import com.agricontract.eventing.outbox.OutboxWriter;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Registered through META-INF/spring/...AutoConfiguration.imports, because services do not component-scan
 * com.agricontract.eventing. The writer is available wherever a JdbcTemplate is; the poller only when enabled.
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, RabbitAutoConfiguration.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class EventingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EventEnvelopeCodec eventEnvelopeCodec() {
        return new EventEnvelopeCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcTemplate.class)
    OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate, EventEnvelopeCodec codec) {
        return new OutboxWriter(jdbcTemplate, codec);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnProperty(prefix = "agricontract.eventing.outbox", name = "enabled", havingValue = "true")
    @EnableScheduling
    static class OutboxPollerConfiguration {

        @Bean
        OutboxPoller outboxPoller(JdbcTemplate jdbcTemplate, ConnectionFactory connectionFactory,
                                  PlatformTransactionManager transactionManager, OutboxProperties properties) {
            if (!StringUtils.hasText(properties.exchange())) {
                throw new IllegalStateException(
                        "agricontract.eventing.outbox.exchange must be set when the outbox poller is enabled");
            }
            // Without confirms and returns the poller cannot tell a delivered event from a dropped one.
            if (!connectionFactory.isPublisherConfirms() || !connectionFactory.isPublisherReturns()) {
                throw new IllegalStateException("Outbox poller requires spring.rabbitmq.publisher-confirm-type=correlated"
                        + " and spring.rabbitmq.publisher-returns=true");
            }
            // A private template, so mandatory=true does not change how the service's own RabbitTemplate behaves.
            RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
            rabbitTemplate.setMandatory(true);
            return new OutboxPoller(jdbcTemplate, rabbitTemplate, new TransactionTemplate(transactionManager),
                    properties.exchange(), properties.batchSize(), properties.confirmTimeout());
        }
    }
}
