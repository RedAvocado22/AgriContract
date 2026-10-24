package com.agricontract.eventing;

import com.agricontract.eventing.inbox.ProcessedEventStore;
import com.agricontract.eventing.outbox.EventOutboxStaleMonitor;
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
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Registered through META-INF/spring/...AutoConfiguration.imports, because services do not component-scan
 * com.agricontract.eventing. The codec is always available; the writer and the dedupe store wherever a JdbcTemplate
 * is; the poller only when enabled. spring-jdbc and spring-rabbit are optional dependencies, so each group sits in
 * its own class-guarded configuration and a service that only needs the codec does not get Rabbit auto-configured.
 * Bean names carry an "eventing" prefix: services still have their own legacy @Component OutboxPoller beans.
 */
@AutoConfiguration(after = {JdbcTemplateAutoConfiguration.class, RabbitAutoConfiguration.class})
@EnableConfigurationProperties(OutboxProperties.class)
public class EventingAutoConfiguration {

    private static final String PREFIX = "agricontract.eventing.outbox";
    // What @Scheduled's *String attributes accept on Spring 6.1: plain milliseconds or ISO-8601 (PT1S).
    private static final Pattern SCHEDULE_DURATION = Pattern.compile("\\d+|[Pp].+");

    @Bean
    @ConditionalOnMissingBean
    EventEnvelopeCodec eventEnvelopeCodec() {
        return new EventEnvelopeCodec();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(JdbcTemplate.class)
        OutboxWriter eventingOutboxWriter(JdbcTemplate jdbcTemplate, EventEnvelopeCodec codec) {
            return new OutboxWriter(jdbcTemplate, codec);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(JdbcTemplate.class)
        ProcessedEventStore eventingProcessedEventStore(JdbcTemplate jdbcTemplate) {
            return new ProcessedEventStore(jdbcTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JdbcTemplate.class, RabbitTemplate.class})
    @ConditionalOnProperty(prefix = PREFIX, name = "enabled", havingValue = "true")
    @EnableScheduling
    static class OutboxPollerConfiguration {

        @Bean
        OutboxPoller eventingOutboxPoller(JdbcTemplate jdbcTemplate, ConnectionFactory connectionFactory,
                                          PlatformTransactionManager transactionManager, OutboxProperties properties,
                                          Environment environment) {
            if (!StringUtils.hasText(properties.exchange())) {
                throw new IllegalStateException(PREFIX + ".exchange must be set when the outbox poller is enabled");
            }
            // Without confirms and returns the poller cannot tell a delivered event from a dropped one.
            if (!connectionFactory.isPublisherConfirms() || !connectionFactory.isPublisherReturns()) {
                throw new IllegalStateException("Outbox poller requires spring.rabbitmq.publisher-confirm-type=correlated"
                        + " and spring.rabbitmq.publisher-returns=true");
            }
            requireScheduleDuration(environment, PREFIX + ".poll-interval");
            // A private template, so mandatory=true does not change how the service's own RabbitTemplate behaves.
            RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
            rabbitTemplate.setMandatory(true);
            // Returns reach the poller through CorrelationData.getReturned(); this only stops the template from
            // warning "no callback available" on every unroutable publish.
            rabbitTemplate.setReturnsCallback(returned -> { });
            return new OutboxPoller(jdbcTemplate, rabbitTemplate, new TransactionTemplate(transactionManager),
                    properties.exchange(), properties.batchSize(), properties.confirmTimeout());
        }

        @Bean
        EventOutboxStaleMonitor eventingOutboxStaleMonitor(JdbcTemplate jdbcTemplate, OutboxProperties properties,
                                                           Environment environment) {
            requireScheduleDuration(environment, PREFIX + ".stale-check-interval");
            return new EventOutboxStaleMonitor(jdbcTemplate, properties.staleThreshold());
        }

        /**
         * Boot binds "2s" into the Duration fine, but the @Scheduled placeholder that reads the same key would then
         * fail with a bare NumberFormatException; fail here instead, naming the key and the accepted formats.
         */
        private static void requireScheduleDuration(Environment environment, String key) {
            String value = environment.getProperty(key);
            if (value != null && !SCHEDULE_DURATION.matcher(value.trim()).matches()) {
                throw new IllegalStateException(key + " must be milliseconds or ISO-8601 (e.g. PT1S), not '"
                        + value + "'");
            }
        }
    }
}
