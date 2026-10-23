package com.agricontract.eventing.outbox;

import com.agricontract.eventing.EventEnvelope;
import com.agricontract.eventing.EventEnvelopeCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DuplicateKeyException;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(OutboxWriterIntegrationTest.Config.class)
class OutboxWriterIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withInitScript("db/eventing/V1__create_eventing_tables.sql");

    // OutboxWriter is a proxied bean here, as in a service: MANDATORY is only enforced through the proxy.
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
        OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate) {
            return new OutboxWriter(jdbcTemplate, new EventEnvelopeCodec());
        }
    }

    @Autowired
    OutboxWriter writer;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager txManager;

    private final EventEnvelopeCodec codec = new EventEnvelopeCodec();

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM event_outbox");
    }

    private static EventEnvelope envelope(UUID eventId) {
        return new EventEnvelope(eventId, "contract.signed", 1, Instant.parse("2026-10-24T03:00:00Z"),
                "contract-service", "contract-1", "corr-1", null, null, Map.of("contractId", "contract-1"));
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(txManager).executeWithoutResult(status -> work.run());
    }

    private int rowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class);
    }

    @Test
    void append_insideTransaction_storesPendingRowWithFullEnvelope() {
        EventEnvelope envelope = envelope(UUID.randomUUID());

        inTransaction(() -> writer.append(envelope));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM event_outbox");
        assertThat(row.get("event_id")).isEqualTo(envelope.eventId().toString());
        assertThat(row.get("event_type")).isEqualTo("contract.signed");
        assertThat(row.get("aggregate_id")).isEqualTo("contract-1");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void append_storedEnvelope_parsesBackToSameEnvelopeAndEventId() {
        EventEnvelope envelope = envelope(UUID.randomUUID());

        inTransaction(() -> writer.append(envelope));

        String stored = jdbc.queryForObject("SELECT envelope FROM event_outbox", String.class);
        assertThat(codec.parse(stored.getBytes(StandardCharsets.UTF_8))).isEqualTo(envelope);
    }

    @Test
    void append_withoutTransaction_throwsAndWritesNothing() {
        assertThatThrownBy(() -> writer.append(envelope(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(rowCount()).isZero();
    }

    @Test
    void append_callerRollsBack_rowIsRolledBackToo() {
        assertThatThrownBy(() -> inTransaction(() -> {
            writer.append(envelope(UUID.randomUUID()));
            throw new IllegalStateException("aggregate save failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowCount()).isZero();
    }

    @Test
    void append_sameEventIdTwice_throwsDuplicateKey() {
        UUID eventId = UUID.randomUUID();
        inTransaction(() -> writer.append(envelope(eventId)));

        assertThatThrownBy(() -> inTransaction(() -> writer.append(envelope(eventId))))
                .isInstanceOf(DuplicateKeyException.class);

        assertThat(rowCount()).isEqualTo(1);
    }
}
