package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.ApplyLockChangeCommand;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.application.dto.LockChangeOutcome;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.application.port.KycResultPublisher;
import com.agricontract.user.application.port.LockChangePublisher;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.LockAction;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.RoleChangeAuditRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * Each use case owns its transaction boundary (through TransactionRunner): when the second write fails, the first one
 * rolls back with it on real MySQL, and a retry applies cleanly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UseCaseAtomicityIntegrationTest {

    private static final String ADMIN_ID = "admin-atomicity";

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @SpyBean private RoleChangeAuditRepository roleChangeAuditRepository;
    @SpyBean private KycResultPublisher kycResultPublisher;
    @SpyBean private LockChangePublisher lockChangePublisher;

    @Autowired private AssignUserRoleUseCase assignUserRoleUseCase;
    @Autowired private VerifyUserUseCase verifyUserUseCase;
    @Autowired private RejectUserUseCase rejectUserUseCase;
    @Autowired private ApplyLockChangeUseCase applyLockChangeUseCase;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void assignRole_auditWriteFails_roleUpdateRollsBack() {
        String userId = register(Role.BUYER);
        doThrow(new IllegalStateException("audit write failed")).when(roleChangeAuditRepository).append(any());

        assertThatThrownBy(() -> assignUserRoleUseCase.execute(new AssignUserRoleCommand(userId, ADMIN_ID, Role.ARBITRATOR)))
                .hasMessageContaining("audit write failed");

        assertThat(profileRow(userId)).containsEntry("role", "BUYER");
        assertThat(auditCount(userId)).isZero();
    }

    @Test
    void verify_commandWriteFails_decisionRollsBack_andRetryApplies() {
        String userId = register(Role.SELLER);
        Map<String, Object> before = profileRow(userId);
        doThrow(new IllegalStateException("outbox write failed")).when(kycResultPublisher).decided(any());

        assertThatThrownBy(() -> verifyUserUseCase.execute(new VerifyUserCommand(userId, "operator-a", null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(profileRow(userId)).isEqualTo(before);
        assertThat(outboxCount(userId)).isZero();

        reset(kycResultPublisher);
        verifyUserUseCase.execute(new VerifyUserCommand(userId, "operator-a", null));

        assertThat(profileRow(userId)).containsEntry("verification_status", "VERIFIED");
        assertThat(outboxCount(userId)).isEqualTo(1);
    }

    @Test
    void reject_commandWriteFails_decisionRollsBack() {
        String userId = register(Role.SELLER);
        Map<String, Object> before = profileRow(userId);
        doThrow(new IllegalStateException("outbox write failed")).when(kycResultPublisher).decided(any());

        assertThatThrownBy(() -> rejectUserUseCase.execute(new RejectUserCommand(userId, "operator-a", "Blurry ID scan")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(profileRow(userId)).isEqualTo(before);
        assertThat(outboxCount(userId)).isZero();
    }

    @Test
    void lock_commandWriteFails_projectionRollsBack_andRedeliveryApplies() {
        String userId = register(Role.SELLER);
        Instant until = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        ApplyLockChangeCommand lock = new ApplyLockChangeCommand(UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), userId, LockAction.LOCKED, until, "QUALITY", 1);
        doThrow(new IllegalStateException("outbox write failed")).when(lockChangePublisher).lockChanged(any(), any());

        assertThatThrownBy(() -> applyLockChangeUseCase.execute(lock)).isInstanceOf(IllegalStateException.class);

        assertThat(profileRow(userId)).containsEntry("last_lock_revision", 0L).containsEntry("locked_until", null);
        assertThat(outboxCount(userId)).isZero();

        reset(lockChangePublisher);
        assertThat(applyLockChangeUseCase.execute(lock)).isEqualTo(LockChangeOutcome.APPLIED);

        assertThat(profileRow(userId)).containsEntry("last_lock_revision", 1L);
        assertThat(outboxCount(userId)).isEqualTo(1);
    }

    private String register(Role role) {
        String userId = UUID.randomUUID().toString();
        userProfileRepository.save(UserProfile.create(new UserId(userId), "HTX Ca Phe", role,
                new ContactInfo("u-" + userId.substring(0, 8) + "@example.com", null, null)));
        return userId;
    }

    private Map<String, Object> profileRow(String userId) {
        return jdbc.queryForMap("SELECT role, verification_status, verified_by_actor_id, version, locked_until, "
                + "last_lock_revision FROM user_profiles WHERE user_id = ?", userId);
    }

    private long auditCount(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM role_change_audit WHERE target_user_id = ?", Long.class, userId);
    }

    private long outboxCount(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE aggregate_id = ?", Long.class, userId);
    }
}
