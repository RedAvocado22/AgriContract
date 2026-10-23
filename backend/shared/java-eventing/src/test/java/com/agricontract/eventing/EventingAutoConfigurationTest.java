package com.agricontract.eventing;

import com.agricontract.eventing.outbox.OutboxPoller;
import com.agricontract.eventing.outbox.OutboxWriter;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventingAutoConfigurationTest {

    // Long interval: the first scheduled run fires on startup against mocks and returns, nothing after that.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EventingAutoConfiguration.class))
            .withPropertyValues("agricontract.eventing.outbox.poll-interval=PT1H");

    @Configuration(proxyBeanMethods = false)
    static class Infra {
        static boolean confirms = true;

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
            ConnectionFactory factory = mock(ConnectionFactory.class);
            when(factory.isPublisherConfirms()).thenReturn(confirms);
            when(factory.isPublisherReturns()).thenReturn(confirms);
            return factory;
        }
    }

    @Test
    void defaults_writerAndCodecRegistered_pollerOff() {
        runner.withUserConfiguration(Infra.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OutboxWriter.class);
            assertThat(context).hasSingleBean(EventEnvelopeCodec.class);
            assertThat(context).doesNotHaveBean(OutboxPoller.class);
        });
    }

    @Test
    void noJdbcTemplate_noWriter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxWriter.class);
        });
    }

    @Test
    void enabledWithExchangeAndConfirms_pollerRegistered() {
        Infra.confirms = true;
        runner.withUserConfiguration(Infra.class)
                .withPropertyValues("agricontract.eventing.outbox.enabled=true",
                        "agricontract.eventing.outbox.exchange=agricontract.contracts")
                .run(context -> assertThat(context).hasSingleBean(OutboxPoller.class));
    }

    @Test
    void enabledWithDefaultPollInterval_startsWithoutOverride() {
        Infra.confirms = true;
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EventingAutoConfiguration.class))
                .withUserConfiguration(Infra.class)
                .withPropertyValues("agricontract.eventing.outbox.enabled=true",
                        "agricontract.eventing.outbox.exchange=agricontract.contracts")
                .run(context -> assertThat(context).hasSingleBean(OutboxPoller.class));
    }

    @Test
    void enabledWithoutExchange_failsFast() {
        Infra.confirms = true;
        runner.withUserConfiguration(Infra.class)
                .withPropertyValues("agricontract.eventing.outbox.enabled=true")
                .run(context -> assertThat(context).getFailure()
                        .hasRootCauseMessage("agricontract.eventing.outbox.exchange must be set when the outbox poller is enabled"));
    }

    @Test
    void enabledWithoutPublisherConfirms_failsFast() {
        Infra.confirms = false;
        try {
            runner.withUserConfiguration(Infra.class)
                    .withPropertyValues("agricontract.eventing.outbox.enabled=true",
                            "agricontract.eventing.outbox.exchange=agricontract.contracts")
                    .run(context -> assertThat(context).getFailure()
                            .rootCause().hasMessageContaining("publisher-confirm-type=correlated"));
        } finally {
            Infra.confirms = true;
        }
    }
}
