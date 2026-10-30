package com.agricontract.user.infrastructure.persistence.repository;

import com.agricontract.user.application.dto.EligibleInspectorResponse;
import com.agricontract.user.application.usecase.ListEligibleInspectorsUseCaseImpl;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "service.internal-secret=service")
@AutoConfigureMockMvc
@Testcontainers
class EligibleInspectorQueryTest {
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.42");
    @Autowired private UserProfileJpaRepository query;
    @Autowired private UserProfileRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");

    @Test
    void authoritativePredicateIncludesOnlyVerifiedCurrentlyLicensedInspectors() {
        jdbc.update("DELETE FROM user_profiles");
        String unlimited = insert("INSPECTOR", "VERIFIED", null);
        String future = insert("INSPECTOR", "VERIFIED", NOW.plusSeconds(1));
        insert("INSPECTOR", "VERIFIED", NOW);
        insert("INSPECTOR", "VERIFIED", NOW.minusSeconds(1));
        insert("INSPECTOR", "PENDING", null);
        insert("INSPECTOR", "REJECTED", null);
        for (String role : new String[]{"BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR"}) {
            insert(role, "VERIFIED", null);
        }
        assertThat(query.findEligibleInspectorIds(NOW)).containsExactlyInAnyOrder(unlimited, future);
        var service = new ListEligibleInspectorsUseCaseImpl(users, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(service.execute()).extracting(EligibleInspectorResponse::userId).containsExactlyInAnyOrder(unlimited, future);
    }

    @Test
    void futureReputationLockDoesNotExcludeEligibleInspectorFromEndpoint() throws Exception {
        jdbc.update("DELETE FROM user_profiles");
        String inspector = insert("INSPECTOR", "VERIFIED", null);
        jdbc.update("UPDATE user_profiles SET locked_until = ? WHERE user_id = ?",
                LocalDateTime.ofInstant(Instant.now().plusSeconds(86400), ZoneOffset.UTC), inspector);

        mvc.perform(get("/internal/v1/users/inspectors/eligible").header("X-Internal-Secret", "service"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"userId\":\"" + inspector + "\"}]", true));
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
