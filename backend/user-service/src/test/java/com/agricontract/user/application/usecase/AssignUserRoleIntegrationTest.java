package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.exception.SelfRoleChangeException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Role assignment against real MySQL: V4 accepts ARBITRATOR in the role ENUM, the update path
 * persists the new role with its V8 audit row, and refused changes (OPERATOR -> ARBITRATOR, own role)
 * leave the row untouched and write no audit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AssignUserRoleIntegrationTest {

    private static final String ADMIN_ID = "admin-1";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private AssignUserRoleUseCase assignUserRoleUseCase;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM role_change_audit");
        jdbcTemplate.update("DELETE FROM user_profiles");
    }

    @Test
    void assignArbitrator_persistsRoleAndAudit() {
        register("staff-1", Role.BUYER);

        assignUserRoleUseCase.execute(new AssignUserRoleCommand("staff-1", ADMIN_ID, Role.ARBITRATOR));

        assertThat(storedRole("staff-1")).isEqualTo("ARBITRATOR");
        assertThat(userProfileRepository.findById(new UserId("staff-1")).orElseThrow().getRole())
                .isEqualTo(Role.ARBITRATOR);
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT actor_id, target_user_id, old_role, new_role, changed_at FROM role_change_audit");
        assertThat(audit).containsEntry("actor_id", ADMIN_ID)
                .containsEntry("target_user_id", "staff-1")
                .containsEntry("old_role", "BUYER")
                .containsEntry("new_role", "ARBITRATOR");
        assertThat(audit.get("changed_at")).isNotNull();
    }

    @Test
    void sameRole_noAudit() {
        register("staff-3", Role.OPERATOR);

        assignUserRoleUseCase.execute(new AssignUserRoleCommand("staff-3", ADMIN_ID, Role.OPERATOR));

        assertThat(auditCount()).isZero();
    }

    @Test
    void operatorToArbitrator_refusedAndRowUnchanged() {
        register("staff-2", Role.OPERATOR);

        assertThatThrownBy(() -> assignUserRoleUseCase.execute(new AssignUserRoleCommand("staff-2", ADMIN_ID, Role.ARBITRATOR)))
                .isInstanceOf(RoleSeparationViolationException.class);

        assertThat(storedRole("staff-2")).isEqualTo("OPERATOR");
        assertThat(auditCount()).isZero();
    }

    @Test
    void adminChangesOwnRole_refusedAndRowUnchanged() {
        register(ADMIN_ID, Role.ADMIN);

        assertThatThrownBy(() -> assignUserRoleUseCase.execute(new AssignUserRoleCommand(ADMIN_ID, ADMIN_ID, Role.ARBITRATOR)))
                .isInstanceOf(SelfRoleChangeException.class);

        assertThat(storedRole(ADMIN_ID)).isEqualTo("ADMIN");
        assertThat(auditCount()).isZero();
    }

    private void register(String userId, Role role) {
        userProfileRepository.save(UserProfile.create(new UserId(userId), "AgriContract Ops", role,
                new ContactInfo(userId + "@agricontract.test", null, null)));
    }

    private String storedRole(String userId) {
        return jdbcTemplate.queryForObject("SELECT role FROM user_profiles WHERE user_id = ?", String.class, userId);
    }

    private long auditCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_change_audit", Long.class);
    }
}
