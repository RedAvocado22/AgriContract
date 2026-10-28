package com.agricontract.escrow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** V7 removes every Phase 1 table, both on a fresh database and on one that already holds Phase 1 rows. */
class Phase1CleanupMigrationTest {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42");

    @BeforeAll
    static void start() {
        MYSQL.start(); // No disabled-without-Docker condition: missing integration infrastructure is a failure.
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void freshDatabaseEndsWithNoPhase1Tables() {
        JdbcTemplate db = database("escrow_fresh");
        flyway("escrow_fresh", null).migrate();

        assertThat(phase1Tables(db)).isEmpty();
    }

    @Test
    void existingPhase1DatabaseWithRowsIsCleanedUp() {
        JdbcTemplate db = database("escrow_phase1");
        flyway("escrow_phase1", "6").migrate();
        db.update("INSERT INTO escrow_accounts (escrow_id, contract_id, buyer_user_id, seller_user_id, buyer_email,"
                + " seller_email, total_amount, currency, status)"
                + " VALUES ('e-1', 'c-1', 'b-1', 's-1', 'b@x.test', 's@x.test', 1000.00, 'VND', 'LOCKED')");
        db.update("INSERT INTO escrow_transactions (transaction_id, escrow_account_id, escrow_id, transaction_type,"
                + " amount, currency) SELECT 't-1', id, 'e-1', 'LOCK', 1000.00, 'VND' FROM escrow_accounts");
        assertThat(phase1Tables(db)).hasSize(3);

        flyway("escrow_phase1", null).migrate();

        assertThat(phase1Tables(db)).isEmpty();
    }

    private static List<String> phase1Tables(JdbcTemplate db) {
        return db.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()"
                + " AND table_name IN ('escrow_accounts', 'escrow_transactions', 'escrow_domain_events')", String.class);
    }

    private static JdbcTemplate database(String name) {
        new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()))
                .execute("CREATE DATABASE " + name);
        return new JdbcTemplate(new DriverManagerDataSource(url(name), "root", MYSQL.getPassword()));
    }

    private static Flyway flyway(String name, String target) {
        var config = Flyway.configure().dataSource(url(name), "root", MYSQL.getPassword())
                .locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    private static String url(String name) {
        return MYSQL.getJdbcUrl().replace("/" + MYSQL.getDatabaseName(), "/" + name);
    }
}
