package com.agricontract.eventing;

import com.agricontract.eventing.inbox.ProcessedEventStore;
import com.agricontract.eventing.outbox.EventOutboxStaleMonitor;
import com.agricontract.eventing.outbox.OutboxPoller;
import com.agricontract.eventing.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventingAutoConfigurationTest {

    private static final String ENABLED = "agricontract.eventing.outbox.enabled=true";
    private static final String EXCHANGE = "agricontract.eventing.outbox.exchange=agricontract.contracts";

    // Long intervals: the first scheduled runs fire on startup against mocks and return, nothing after that.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventingAutoConfiguration.class))
            .withPropertyValues("agricontract.eventing.outbox.poll-interval=PT1H",
                    "agricontract.eventing.outbox.stale-check-interval=PT1H");

    private static ConnectionFactory connectionFactory(boolean confirms) {
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.isPublisherConfirms()).thenReturn(confirms);
        when(factory.isPublisherReturns()).thenReturn(confirms);
        return factory;
    }

    @Configuration(proxyBeanMethods = false)
    static class Infra {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        ConnectionFactory connectionFactory() {
            return EventingAutoConfigurationTest.connectionFactory(true);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InfraWithoutConfirms {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        ConnectionFactory connectionFactory() {
            return EventingAutoConfigurationTest.connectionFactory(false);
        }
    }

    /** Stand-in for the services' legacy @Component OutboxPoller, whose bean name is "outboxPoller". */
    @Configuration(proxyBeanMethods = false)
    static class LegacyPoller {
        @Bean
        Object outboxPoller() {
            return new Object();
        }
    }

    @Test
    void defaults_writerStoreAndCodecRegistered_pollerOff() {
        runner.withUserConfiguration(Infra.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxWriter.class);
            assertThat(context).hasSingleBean(ProcessedEventStore.class);
            assertThat(context).hasSingleBean(EventEnvelopeCodec.class);
            assertThat(context).doesNotHaveBean(OutboxPoller.class);
            assertThat(context).doesNotHaveBean(EventOutboxStaleMonitor.class);
        });
    }

    @Test
    void noJdbcTemplate_noWriterOrStore() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxWriter.class);
            assertThat(context).doesNotHaveBean(ProcessedEventStore.class);
        });
    }

    @Test
    void codecOnlyClasspath_withoutJdbcOrRabbit_startsWithCodecOnly() {
        runner.withClassLoader(new FilteredClassLoader(JdbcTemplate.class, RabbitTemplate.class))
                .withPropertyValues(ENABLED, EXCHANGE)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EventEnvelopeCodec.class);
                    assertThat(context).doesNotHaveBean(OutboxPoller.class);
                });
    }

    @Test
    void enabledWithExchangeAndConfirms_pollerAndStaleMonitorRegistered() {
        runner.withUserConfiguration(Infra.class)
                .withPropertyValues(ENABLED, EXCHANGE)
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxPoller.class);
                    assertThat(context).hasSingleBean(EventOutboxStaleMonitor.class);
                });
    }

    @Test
    void enabledAlongsideLegacyOutboxPollerBean_bothCoexist() {
        runner.withUserConfiguration(Infra.class, LegacyPoller.class)
                .withPropertyValues(ENABLED, EXCHANGE)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("outboxPoller");
                    assertThat(context).hasSingleBean(OutboxPoller.class);
                });
    }

    @Test
    void enabledWithDefaultIntervals_startsWithoutOverride() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EventingAutoConfiguration.class))
                .withUserConfiguration(Infra.class)
                .withPropertyValues(ENABLED, EXCHANGE)
                .run(context -> assertThat(context).hasSingleBean(OutboxPoller.class));
    }

    @Test
    void enabledWithBootStylePollInterval_failsFastNamingTheKey() {
        runner.withUserConfiguration(Infra.class)
                .withPropertyValues(ENABLED, EXCHANGE, "agricontract.eventing.outbox.poll-interval=2s")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("agricontract.eventing.outbox.poll-interval")
                        .hasMessageContaining("PT1S"));
    }

    @Test
    void enabledWithMillisecondPollInterval_isAccepted() {
        runner.withUserConfiguration(Infra.class)
                .withPropertyValues(ENABLED, EXCHANGE, "agricontract.eventing.outbox.poll-interval=2000")
                .run(context -> assertThat(context).hasSingleBean(OutboxPoller.class));
    }

    @Test
    void enabledWithoutExchange_failsFast() {
        runner.withUserConfiguration(Infra.class)
                .withPropertyValues(ENABLED)
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage("agricontract.eventing.outbox.exchange must be set when the outbox poller is enabled"));
    }

    @Test
    void enabledWithoutPublisherConfirms_failsFast() {
        runner.withUserConfiguration(InfraWithoutConfirms.class)
                .withPropertyValues(ENABLED, EXCHANGE)
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("publisher-confirm-type=correlated"));
    }
}
