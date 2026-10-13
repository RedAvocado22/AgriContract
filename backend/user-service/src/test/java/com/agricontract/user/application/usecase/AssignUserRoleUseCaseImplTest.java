package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.AdminUserResponse;
import com.agricontract.user.application.dto.AssignUserRoleCommand;
import com.agricontract.user.domain.exception.RoleSeparationViolationException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignUserRoleUseCaseImplTest {

    private static final String TARGET_ID = "staff-1";

    @Mock private UserProfileRepository userProfileRepository;

    private AssignUserRoleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new AssignUserRoleUseCaseImpl(userProfileRepository);
    }

    private UserProfile profileWithRole(Role role) {
        return UserProfile.create(new UserId(TARGET_ID), "AgriContract Ops",
                role, new ContactInfo("staff@agricontract.test", null, null));
    }

    @Test
    void execute_userNotFound_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new AssignUserRoleCommand(TARGET_ID, Role.OPERATOR)))
                .isInstanceOf(NoSuchElementException.class);
        verify(userProfileRepository, never()).update(any());
    }

    @Test
    void execute_newRole_persistsChange() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profileWithRole(Role.BUYER)));
        when(userProfileRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminUserResponse response = useCase.execute(new AssignUserRoleCommand(TARGET_ID, Role.INSPECTOR));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).update(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.INSPECTOR);
        assertThat(response.role()).isEqualTo(Role.INSPECTOR);
    }

    @Test
    void execute_sameRole_returnsWithoutUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profileWithRole(Role.OPERATOR)));

        AdminUserResponse response = useCase.execute(new AssignUserRoleCommand(TARGET_ID, Role.OPERATOR));

        assertThat(response.role()).isEqualTo(Role.OPERATOR);
        verify(userProfileRepository, never()).update(any());
    }

    @Test
    void execute_operatorToArbitrator_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profileWithRole(Role.OPERATOR)));

        assertThatThrownBy(() -> useCase.execute(new AssignUserRoleCommand(TARGET_ID, Role.ARBITRATOR)))
                .isInstanceOf(RoleSeparationViolationException.class);
        verify(userProfileRepository, never()).update(any());
    }
}
