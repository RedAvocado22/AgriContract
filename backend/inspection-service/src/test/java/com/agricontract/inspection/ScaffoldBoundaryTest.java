package com.agricontract.inspection;

import com.agricontract.inspection.application.exception.InspectionAssignmentAccessDeniedException;
import com.agricontract.inspection.application.exception.InspectorNotEligibleException;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;
import com.agricontract.inspection.application.usecase.InspectionAuthorization;
import com.agricontract.inspection.domain.model.InspectorEligibility;
import com.agricontract.inspection.domain.repository.InspectionAssignmentRepository;
import com.agricontract.inspection.infrastructure.security.GatewayAuthenticationFilter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"SERVICE_INTERNAL_SECRET=test-service-secret", "GATEWAY_INTERNAL_SECRET=test-gateway-secret",
        "spring.rabbitmq.listener.simple.auto-startup=false", "management.health.rabbit.enabled=false",
        "inspection.assignment.scheduler-enabled=false", "inspection.assignment.holidays=2026-01-01"})
@AutoConfigureMockMvc
@Testcontainers
class ScaffoldBoundaryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.42")
            .withDatabaseName("inspection_db");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> MYSQL.getJdbcUrl()
                + (MYSQL.getJdbcUrl().contains("?") ? "&" : "?")
                + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
    }

    private static final String CONTRACT = UUID.randomUUID().toString();
    private static final String MILESTONE = UUID.randomUUID().toString();
    private static final String INSPECTOR_A = UUID.randomUUID().toString();
    private static final String INSPECTOR_B = UUID.randomUUID().toString();
    private static final Instant ASSIGNED = Instant.parse("2026-10-08T01:02:03.123456Z");
    @Autowired JdbcTemplate jdbc;
    @Autowired InspectionAssignmentRepository assignments;
    @Autowired InspectionAuthorization authorization;
    @MockBean InspectorEligibilityPort users;

    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM level1_5_inspection_assignment");
        when(users.load(INSPECTOR_A)).thenReturn(new InspectorEligibility(INSPECTOR_A, "INSPECTOR", "VERIFIED", null));
        when(users.load(INSPECTOR_B)).thenReturn(new InspectorEligibility(INSPECTOR_B, "INSPECTOR", "VERIFIED", null));
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @Autowired
    MockMvc mvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void healthIsAvailable() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void clientIdentityHeadersCannotOpenAnInspectionOperation() throws Exception {
        mvc.perform(post("/api/v1/inspections/contract/milestones/milestone/reports")
                        .header("X-User-Id", "forged-inspector")
                        .header("X-User-Role", "INSPECTOR")
                        .header("X-Gateway-Secret", "forged-secret")
                        .header("Idempotency-Key", "test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void scaffoldDoesNotExposeSpeculativeBusinessEndpoints() {
        assertThat(mappings.getHandlerMethods().values())
                .noneMatch(handler -> handler.getBeanType().getPackageName().startsWith("com.agricontract.inspection"));
    }

    @Test
    void evenAuthenticatedInspectorCannotInvokeDeferredReportEndpoint() throws Exception {
        mvc.perform(post("/api/v1/inspections/contract/milestones/milestone/reports")
                        .header("X-User-Id", INSPECTOR_A).header("X-User-Role", "INSPECTOR")
                        .header("X-Gateway-Secret", "test-gateway-secret"))
                .andExpect(status().isForbidden());
    }

    @Test
    void migrationRunsOnMySqlAndOwnershipProvenanceRoundTrips() {
        String id = UUID.randomUUID().toString();
        insert(id, CONTRACT, MILESTONE, INSPECTOR_A, null);
        var loaded = assignments.findByCommissionId(id).orElseThrow();
        assertThat(loaded.inspectorId()).isEqualTo(INSPECTOR_A);
        assertThat(loaded.contractId()).isEqualTo(CONTRACT);
        assertThat(loaded.milestoneId()).isEqualTo(MILESTONE);
        assertThat(loaded.assignedAt()).isEqualTo(ASSIGNED);
        assertThat(loaded.endedAt()).isNull();
        assertThat(loaded.assignedByService()).isEqualTo("test-fixture");
        assertThat(loaded.initiatedByActorId()).isNull();
        assertThat(assignments.findActive(CONTRACT, MILESTONE)).contains(loaded);
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.42");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints "
                + "WHERE constraint_schema = DATABASE()", Integer.class)).isZero();
    }

    @Test
    void activeUniquenessIsEnforcedByDatabase() {
        insert(UUID.randomUUID().toString(), CONTRACT, MILESTONE, INSPECTOR_A, null);
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), CONTRACT, MILESTONE, INSPECTOR_B, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insert(UUID.randomUUID().toString(), CONTRACT, UUID.randomUUID().toString(), INSPECTOR_B, null);
    }

    @Test
    void commissionPrimaryKeyCannotOverwriteInspector() {
        String id = UUID.randomUUID().toString();
        insert(id, CONTRACT, MILESTONE, INSPECTOR_A, null);
        assertThatThrownBy(() -> insert(id, CONTRACT, UUID.randomUUID().toString(), INSPECTOR_B, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(assignments.findByCommissionId(id).orElseThrow().inspectorId()).isEqualTo(INSPECTOR_A);
    }

    @Test
    void historyIsPreservedButCannotAuthorizeAccess() throws Exception {
        String historical = UUID.randomUUID().toString();
        insert(historical, CONTRACT, MILESTONE, INSPECTOR_A, ASSIGNED.plusSeconds(1));
        insert(UUID.randomUUID().toString(), CONTRACT, MILESTONE, INSPECTOR_A, ASSIGNED.plusSeconds(2));
        String current = UUID.randomUUID().toString();
        insert(current, CONTRACT, MILESTONE, INSPECTOR_B, null);
        assertThat(assignments.findActive(CONTRACT, MILESTONE).orElseThrow().commissionId()).isEqualTo(current);
        assertThat(authorize(historical, INSPECTOR_A, "INSPECTOR")).isEqualTo(403);
        assertThat(assignments.findByCommissionId(historical).orElseThrow().inspectorId()).isEqualTo(INSPECTOR_A);
    }

    @Test
    void invalidEndTimeAndMissingInspectorAreRejectedByMySql() {
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), CONTRACT, MILESTONE,
                INSPECTOR_A, ASSIGNED.minusSeconds(1)))
                .hasRootCauseInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("3819").hasMessageContaining("chk_l15_assignment_time");
        assertThatThrownBy(() -> insert(UUID.randomUUID().toString(), CONTRACT, MILESTONE,
                null, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentAssignmentInsertsCannotBothWin() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> racingInsert(start, INSPECTOR_A));
            var second = executor.submit(() -> racingInsert(start, INSPECTOR_B));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM level1_5_inspection_assignment", Integer.class)).isEqualTo(1);
    }

    @Test
    void authenticatedEligibleOwnerPassesButOtherInspectorAndMissingCommissionFail() throws Exception {
        String id = UUID.randomUUID().toString();
        insert(id, CONTRACT, MILESTONE, INSPECTOR_A, null);
        assertThat(authorize(id, INSPECTOR_A, "INSPECTOR")).isEqualTo(200);
        assertThat(authorize(id, INSPECTOR_B, "INSPECTOR")).isEqualTo(403);
        assertThat(authorize(UUID.randomUUID().toString(), INSPECTOR_A, "INSPECTOR")).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUYER", "SELLER", "ADMIN", "OPERATOR"})
    void ownershipDoesNotGrantInspectorRole(String role) throws Exception {
        String id = UUID.randomUUID().toString();
        insert(id, CONTRACT, MILESTONE, INSPECTOR_A, null);
        assertThat(authorize(id, INSPECTOR_A, role)).isEqualTo(403);
        verifyNoInteractions(users);
    }

    @Test
    void ownerWhoseKycIsNoLongerVerifiedIsDenied() throws Exception {
        String id = UUID.randomUUID().toString();
        insert(id, CONTRACT, MILESTONE, INSPECTOR_A, null);
        when(users.load(INSPECTOR_A)).thenReturn(new InspectorEligibility(INSPECTOR_A, "INSPECTOR", "REJECTED", null));
        assertThat(authorize(id, INSPECTOR_A, "INSPECTOR")).isEqualTo(403);
    }

    private int racingInsert(CountDownLatch start, String inspector) throws Exception {
        start.await();
        try {
            insert(UUID.randomUUID().toString(), CONTRACT, MILESTONE, inspector, null);
            return 1;
        } catch (DataIntegrityViolationException expected) {
            return 0;
        }
    }

    // Test fixture only: there is deliberately no production assignment creation API/repository method.
    private void insert(String id, String contract, String milestone, String inspector, Instant endedAt) {
        jdbc.update("INSERT INTO level1_5_inspection_assignment "
                        + "(commission_id, contract_id, milestone_id, inspector_id, assigned_at, ended_at, assigned_by_service, "
                        + "report_due_business_days, report_due_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, contract, milestone, inspector,
                Timestamp.from(ASSIGNED), endedAt == null ? null : Timestamp.from(endedAt), "test-fixture",
                1, Timestamp.from(ASSIGNED.plusSeconds(86400)));
    }

    // Exercise filter -> authenticated context -> eligibility -> real MySQL ownership, without a test-only endpoint.
    private int authorize(String commission, String actor, String role) throws Exception {
        var request = new MockHttpServletRequest();
        request.setServletPath("/api/v1/inspections/" + CONTRACT + "/milestones/" + MILESTONE + "/reports");
        request.addHeader("X-Gateway-Secret", "test-gateway-secret");
        request.addHeader("X-User-Id", actor);
        request.addHeader("X-User-Role", role);
        var response = new MockHttpServletResponse();
        new GatewayAuthenticationFilter("test-gateway-secret").doFilter(request, response, (req, res) -> {
            try {
                authorization.requireCommission(commission);
            } catch (AccessDeniedException | InspectorNotEligibleException | InspectionAssignmentAccessDeniedException denied) {
                response.setStatus(403);
            }
        });
        return response.getStatus();
    }
}
