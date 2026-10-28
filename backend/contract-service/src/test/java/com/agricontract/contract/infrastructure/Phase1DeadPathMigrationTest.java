package com.agricontract.contract.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

/** V5 leaves only contracts the Phase 2 status enum can load and drops the Phase 1 cancel columns. */
class Phase1DeadPathMigrationTest {
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
    void removesPhase1OnlyStatesAndCancelColumns() {
        JdbcTemplate db = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()));
        flyway("4").migrate();
        for (String status : List.of("OFFERED", "SIGNED", "ACTIVE", "SETTLED", "DELIVERED", "CANCELLED", "DISPUTED")) {
            db.update("INSERT INTO contracts (contract_id, listing_id, seller_id, buyer_id, product_name, buyer_org_name,"
                    + " seller_org_name, buyer_email, seller_email, quantity, quantity_unit, agreed_price, currency,"
                    + " delivery_deadline, status) VALUES (?, 'l-1', 's-1', 'b-1', 'Coffee', 'B', 'S', 'b@x.test',"
                    + " 's@x.test', 100.000, 'kg', 1000.00, 'VND', '2026-12-31', ?)", "c-" + status, status);
        }

        flyway(null).migrate();

        assertThat(db.queryForList("SELECT status FROM contracts ORDER BY status", String.class))
                .containsExactly("ACTIVE", "OFFERED", "SETTLED", "SIGNED");
        assertThat(db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = 'contracts' AND column_name IN ('cancel_reason', 'cancelled_by')", String.class))
                .isEmpty();
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())
                .locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }
}
