package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.application.usecase.ListEligibleInspectors;
import com.agricontract.user.application.dto.EligibleInspectorResponse;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class EligibleInspectorQueryTest {
    @Container @ServiceConnection static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.42");
    @Autowired UserProfileJpaRepository query;
    @Autowired com.agricontract.user.domain.repository.UserProfileRepository users;
    @Autowired JdbcTemplate jdbc;
    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");

    @Test void authoritativePredicateIncludesOnlyVerifiedCurrentlyLicensedInspectors() {
        jdbc.update("DELETE FROM user_profiles");
        String unlimited = insert("INSPECTOR", "VERIFIED", null);
        String future = insert("INSPECTOR", "VERIFIED", NOW.plusSeconds(1));
        insert("INSPECTOR", "VERIFIED", NOW);
        insert("INSPECTOR", "VERIFIED", NOW.minusSeconds(1));
        insert("INSPECTOR", "PENDING", null);
        insert("INSPECTOR", "REJECTED", null);
        for (String role : new String[]{"BUYER", "SELLER", "ADMIN", "OPERATOR"}) insert(role, "VERIFIED", null);
        assertThat(query.findEligibleInspectorIds(NOW)).containsExactlyInAnyOrder(unlimited, future);
        var service = new ListEligibleInspectors(users, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.execute()).extracting(EligibleInspectorResponse::userId).containsExactlyInAnyOrder(unlimited, future);
    }

    private String insert(String role, String status, Instant expiry) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
            INSERT INTO user_profiles(user_id,organization_name,role,email,verification_status,authorization_expires_at,version)
            VALUES (?,'private organization',?,'private@example.test',?,?,0)
            """, id, role, status, expiry == null ? null : LocalDateTime.ofInstant(expiry, ZoneOffset.UTC));
        return id;
    }
}
