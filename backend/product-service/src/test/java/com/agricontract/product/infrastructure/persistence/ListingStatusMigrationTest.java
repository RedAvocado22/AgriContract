package com.agricontract.product.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V11 (#433) on real MySQL: Phase 1 ACTIVE/EXPIRED rows written at V10 map to the contract statuses from their
 * quantities, and delivery_deadline becomes optional. updated_at (the version token) is left alone.
 */
@Testcontainers(disabledWithoutDocker = true)
class ListingStatusMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static void insert(JdbcTemplate jdbc, String listingId, String status, String available,
                               String closeReason) {
        jdbc.update("""
                INSERT INTO listings (listing_id, seller_id, product_id, product_name, cover_image_url, quantity,
                    quantity_unit, quantity_available, price_floor, currency, delivery_deadline, status, close_reason,
                    created_at, updated_at)
                VALUES (?, 'seller-1', 'product-1', 'Ca phe Robusta', '', 100, 'kg', ?, 50000, 'VND', '2026-09-30',
                    ?, ?, NOW(), '2026-11-01 08:00:00.123456')""",
                listingId, available, status, closeReason);
    }

    @Test
    void phase1StatusesMapFromQuantities() {
        flyway("10").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(),
                mysql.getPassword()));
        insert(jdbc, "active-untouched", "ACTIVE", "100", null);
        insert(jdbc, "active-legacy-null", "ACTIVE", null, null);
        insert(jdbc, "active-partial", "ACTIVE", "60", null);
        insert(jdbc, "active-drained", "ACTIVE", "0", null);
        insert(jdbc, "closed-exhausted", "CLOSED", "0", "INVENTORY_EXHAUSTED");
        insert(jdbc, "closed-phase1", "CLOSED", null, null);
        insert(jdbc, "expired", "EXPIRED", "40", null);

        flyway("latest").migrate();

        assertThat(state(jdbc, "active-untouched")).isEqualTo("AVAILABLE/null");
        assertThat(state(jdbc, "active-legacy-null")).isEqualTo("AVAILABLE/null");
        assertThat(state(jdbc, "active-partial")).isEqualTo("PARTIALLY_COMMITTED/null");
        assertThat(state(jdbc, "active-drained")).isEqualTo("CLOSED/INVENTORY_EXHAUSTED");
        assertThat(state(jdbc, "closed-exhausted")).isEqualTo("CLOSED/INVENTORY_EXHAUSTED");
        assertThat(state(jdbc, "closed-phase1")).isEqualTo("CLOSED/null");
        assertThat(state(jdbc, "expired")).isEqualTo("CLOSED/LEGACY_EXPIRED");

        Map<String, Object> partial = jdbc.queryForMap(
                "SELECT quantity_available, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated_at "
                        + "FROM listings WHERE listing_id = 'active-partial'");
        assertThat(partial.get("updated_at")).isEqualTo("2026-11-01 08:00:00.123456");

        // the old values are gone from the column, and a new row needs no delivery deadline
        assertThatThrownBy(() -> jdbc.update("UPDATE listings SET status = 'ACTIVE' WHERE listing_id = 'expired'"))
                .hasMessageContaining("status");
        jdbc.update("""
                INSERT INTO listings (listing_id, seller_id, product_id, product_name, cover_image_url, quantity,
                    quantity_unit, price_floor, currency, status, created_at, updated_at)
                VALUES ('new-1', 'seller-1', 'product-1', 'Ca phe Robusta', '', 100, 'KG', 50000, 'VND', 'AVAILABLE',
                    NOW(), NOW(6))""");
        assertThat(jdbc.queryForObject("SELECT delivery_deadline FROM listings WHERE listing_id = 'new-1'",
                Object.class)).isNull();
    }

    private static String state(JdbcTemplate jdbc, String listingId) {
        return jdbc.queryForObject(
                "SELECT CONCAT(status, '/', COALESCE(close_reason, 'null')) FROM listings WHERE listing_id = ?",
                String.class, listingId);
    }
}
