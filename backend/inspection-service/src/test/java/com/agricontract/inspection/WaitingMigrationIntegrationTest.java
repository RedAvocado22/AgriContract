package com.agricontract.inspection;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Upgrade an isolated V4 database with existing history, without editing or replaying applied migrations. */
@Testcontainers
class WaitingMigrationIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42").withDatabaseName("inspection_db");

    @Test void v5IsAdditiveAndRetainsV4ChecksumsAndAssignmentHistory() {
        String url = MYSQL.getJdbcUrl();
        Flyway.configure().dataSource(url, MYSQL.getUsername(), MYSQL.getPassword())
                .target("4").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(url, MYSQL.getUsername(), MYSQL.getPassword()));
        var before = jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var ddl = jdbc.queryForMap("SHOW CREATE TABLE level1_5_inspection_assignment");
        Instant assigned = Instant.parse("2026-10-23T03:00:00Z");
        jdbc.update("""
                INSERT INTO level1_5_inspection_assignment
                (commission_id,contract_id,milestone_id,inspector_id,assigned_at,ended_at,assigned_by_service,
                 report_due_business_days,report_due_at)
                VALUES (?,?,?,?,?,?,'contract-service',2,?)
                """, UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), Timestamp.from(assigned), Timestamp.from(assigned.plusSeconds(3600)),
                Timestamp.from(assigned.plusSeconds(86400)));
        var history = jdbc.queryForList("SELECT * FROM level1_5_inspection_assignment");
        var flyway = Flyway.configure().dataSource(url, MYSQL.getUsername(), MYSQL.getPassword()).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        assertThat(jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE version IN ('1','2','3','4') ORDER BY installed_rank"))
                .isEqualTo(before);
        assertThat(jdbc.queryForList("SELECT * FROM level1_5_inspection_assignment")).isEqualTo(history);
        assertThat(jdbc.queryForMap("SHOW CREATE TABLE level1_5_inspection_assignment")).isEqualTo(ddl);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM level1_5_assignment_waiting", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT COLLATION_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='level1_5_assignment_waiting' AND COLUMN_NAME IN ('contract_id','milestone_id','initiated_by_actor_id')", String.class))
                .containsExactlyInAnyOrder("ascii_bin", "ascii_bin", "ascii_bin");
        String contract = UUID.randomUUID().toString();
        String milestone = UUID.randomUUID().toString();
        String insert = """
                INSERT INTO level1_5_assignment_waiting
                (contract_id, milestone_id, reason, report_due_business_days, assigned_by_service,
                 waiting_since, last_attempt_at) VALUES (?, ?, 'NO_ELIGIBLE', 1, 'contract-service', NOW(6), NOW(6))
                """;
        jdbc.update(insert, contract, milestone);
        assertThatThrownBy(() -> jdbc.update(insert, contract, milestone))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
