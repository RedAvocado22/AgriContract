package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.exception.SelfRoleChangeException;
import com.agricontract.user.domain.model.RoleChangeAuditEntry;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.RoleChangeAuditRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignUserRoleUseCaseImplTest {

    private static final String TARGET_ID = "staff-1";
    private static final String ACTOR_ID = "admin-1";
    private static final Instant NOW = Instant.parse("2026-10-31T03:00:00.123456789Z");

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private RoleChangeAuditRepository roleChangeAuditRepository;

    private AssignUserRoleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AssignUserRoleUseCaseImpl(userProfileRepository, roleChangeAuditRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserProfile profileWithRole(String userId, Role role) {
        return UserProfile.create(new UserId(userId), "AgriContract Ops",
                role, new ContactInfo("staff@agricontract.test", null, null));
    }

    @Test
    void execute_userNotFound_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AssignUserRoleCommand(TARGET_ID, ACTOR_ID, Role.OPERATOR)))
                .isInstanceOf(NoSuchElementException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(roleChangeAuditRepository);
    }

    @Test
    void execute_newRole_persistsChangeAndRecordsActor() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profileWithRole(TARGET_ID, Role.BUYER)));
        when(userProfileRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = useCase.execute(new AssignUserRoleCommand(TARGET_ID, ACTOR_ID, Role.INSPECTOR));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).update(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.INSPECTOR);
        assertThat(response.role()).isEqualTo(Role.INSPECTOR);
        verify(roleChangeAuditRepository).append(new RoleChangeAuditEntry(ACTOR_ID, TARGET_ID,
                Role.BUYER, Role.INSPECTOR, Instant.parse("2026-10-31T03:00:00.123456Z")));
    }

    @Test
    void execute_sameRole_returnsWithoutUpdateOrAudit() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profileWithRole(TARGET_ID, Role.OPERATOR)));

        AdminUserResponse response = useCase.execute(new AssignUserRoleCommand(TARGET_ID, ACTOR_ID, Role.OPERATOR));

        assertThat(response.role()).isEqualTo(Role.OPERATOR);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(roleChangeAuditRepository);
    }

    @Test
    void execute_operatorToArbitrator_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profileWithRole(TARGET_ID, Role.OPERATOR)));

        assertThatThrownBy(() -> useCase.execute(new AssignUserRoleCommand(TARGET_ID, ACTOR_ID, Role.ARBITRATOR)))
                .isInstanceOf(RoleSeparationViolationException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(roleChangeAuditRepository);
    }

    @Test
    void execute_adminChangesOwnRole_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(ACTOR_ID))).thenReturn(Optional.of(profileWithRole(ACTOR_ID, Role.ADMIN)));

        assertThatThrownBy(() -> useCase.execute(new AssignUserRoleCommand(ACTOR_ID, ACTOR_ID, Role.ARBITRATOR)))
                .isInstanceOf(SelfRoleChangeException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(roleChangeAuditRepository);
    }

    @Test
    void execute_adminReassignsOwnCurrentRole_throwsInsteadOfNoOp() {
        when(userProfileRepository.findById(new UserId(ACTOR_ID))).thenReturn(Optional.of(profileWithRole(ACTOR_ID, Role.ADMIN)));

        assertThatThrownBy(() -> useCase.execute(new AssignUserRoleCommand(ACTOR_ID, ACTOR_ID, Role.ADMIN)))
                .isInstanceOf(SelfRoleChangeException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(roleChangeAuditRepository);
    }
}
