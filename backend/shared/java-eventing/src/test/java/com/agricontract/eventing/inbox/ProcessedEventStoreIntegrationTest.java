package com.agricontract.eventing.inbox;

import com.agricontract.eventing.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(ProcessedEventStoreIntegrationTest.Config.class)
class ProcessedEventStoreIntegrationTest {

    private static final String CONSUMER = "escrow.contract-signed";

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("db/eventing/V1__create_eventing_tables.sql");

    // A proxied bean, as in a service: MANDATORY is only enforced through the proxy.
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ProcessedEventStore processedEventStore(JdbcTemplate jdbcTemplate) {
            return new ProcessedEventStore(jdbcTemplate);
        }
    }

    @Autowired
    ProcessedEventStore store;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager txManager;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM processed_event");
    }

    private static EventEnvelope envelope() {
        return new EventEnvelope(UUID.randomUUID(), "contract.signed", 1, Instant.parse("2026-10-24T03:00:00Z"),
                "contract-service", "contract-1", "corr-1", null, null, Map.of("contractId", "contract-1"));
    }

    private boolean claim(String consumer, EventEnvelope envelope) {
        return new TransactionTemplate(txManager).execute(status -> store.markProcessed(consumer, envelope));
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM processed_event", Integer.class);
    }

    @Test
    void markProcessed_firstDelivery_returnsTrueAndRecordsRow() {
        EventEnvelope envelope = envelope();

        assertThat(claim(CONSUMER, envelope)).isTrue();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM processed_event");
        assertThat(row.get("consumer_name")).isEqualTo(CONSUMER);
        assertThat(row.get("event_id")).isEqualTo(envelope.eventId().toString());
        assertThat(row.get("event_type")).isEqualTo("contract.signed");
        assertThat(row.get("processed_at")).isNotNull();
    }

    @Test
    void markProcessed_redeliveryOfSameEventId_returnsFalse() {
        EventEnvelope envelope = envelope();
        claim(CONSUMER, envelope);

        assertThat(claim(CONSUMER, envelope)).isFalse();
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void markProcessed_handlerRolledBack_redeliveryIsProcessedAgain() {
        EventEnvelope envelope = envelope();
        assertThatThrownBy(() -> new TransactionTemplate(txManager).executeWithoutResult(status -> {
            store.markProcessed(CONSUMER, envelope);
            throw new IllegalStateException("handler failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowCount()).isZero();
        assertThat(claim(CONSUMER, envelope)).isTrue();
    }

    @Test
    void markProcessed_sameEventDifferentConsumers_bothProcess() {
        EventEnvelope envelope = envelope();

        assertThat(claim("escrow.lock-deposit", envelope)).isTrue();
        assertThat(claim("escrow.audit", envelope)).isTrue();
        assertThat(rowCount()).isEqualTo(2);
    }

    @Test
    void markProcessed_withoutTransaction_throwsAndRecordsNothing() {
        assertThatThrownBy(() -> store.markProcessed(CONSUMER, envelope()))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(rowCount()).isZero();
    }

    @Test
    void markProcessed_blankConsumerName_throws() {
        assertThatThrownBy(() -> claim(" ", envelope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consumerName");
    }

    @Test
    void markProcessed_consumerNameOverColumnLength_throwsInsteadOfTruncating() {
        assertThatThrownBy(() -> claim("c".repeat(101), envelope()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
        assertThat(rowCount()).isZero();
    }

    @Test
    void markProcessed_consumerNameAtColumnLength_isStoredWhole() {
        String name = "c".repeat(100);

        assertThat(claim(name, envelope())).isTrue();
        assertThat(jdbc.queryForObject("SELECT consumer_name FROM processed_event", String.class)).isEqualTo(name);
    }

    @Test
    void markProcessed_concurrentDuplicate_waitsForFirstThenSkipsAfterCommit() throws Exception {
        assertThat(concurrentSecondClaim(true)).isFalse();
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void markProcessed_concurrentDuplicate_processesWhenFirstRollsBack() throws Exception {
        assertThat(concurrentSecondClaim(false)).isTrue();
        assertThat(rowCount()).isEqualTo(1);
    }

    /**
     * Transaction A claims and holds its transaction open; B claims the same pair and must block on the primary
     * key. A then commits or rolls back; returns what B's claim answered.
     */
    private boolean concurrentSecondClaim(boolean firstCommits) throws Exception {
        EventEnvelope envelope = envelope();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> new TransactionTemplate(txManager).execute(status -> {
                boolean claimed = store.markProcessed(CONSUMER, envelope);
                firstClaimed.countDown();
                await(releaseFirst);
                if (!firstCommits) {
                    status.setRollbackOnly();
                }
                return claimed;
            }));
            assertThat(firstClaimed.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = pool.submit(() -> claim(CONSUMER, envelope));
            assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS))
                    .as("second claim must block while the first transaction is open")
                    .isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            return second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
