package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycDecisionResponse;
import com.agricontract.user.application.dto.RejectUserCommand;
import com.agricontract.user.application.port.KycResultPublisher;
import com.agricontract.user.domain.exception.ConcurrentProfileUpdateException;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.model.vo.VerificationStatus;
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
class RejectUserUseCaseImplTest {

    private static final String TARGET_ID = "buyer-1";
    private static final String ACTOR_ID = "admin-1";
    private static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z");

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private KycResultPublisher kycResultPublisher;

    private RejectUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RejectUserUseCaseImpl(userProfileRepository, kycResultPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserProfile pendingProfile() {
        return UserProfile.create(new UserId(TARGET_ID), "Cong ty Xuat Khau",
                Role.BUYER, new ContactInfo("buyer@agricontract.test", null, null));
    }

    @Test
    void execute_userNotFound_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "Thieu giay phep")))
                .isInstanceOf(NoSuchElementException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(kycResultPublisher);
    }

    @Test
    void execute_pending_rejectsWithReasonServerTimeAndActor() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));
        when(userProfileRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        KycDecisionResponse response = useCase.execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "Thieu giay phep"));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).update(captor.capture());
        UserProfile updated = captor.getValue();
        assertThat(updated.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(updated.getVerifiedByActorId()).isEqualTo(ACTOR_ID);
        assertThat(updated.getVerifiedAt()).isEqualTo(NOW);
        assertThat(updated.getRejectionReason()).isEqualTo("Thieu giay phep");
        verify(kycResultPublisher).decided(updated);

        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Thieu giay phep");
    }

    @Test
    void execute_staleVersion_propagatesConflictAndDoesNotPublish() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));
        when(userProfileRepository.update(any())).thenThrow(new ConcurrentProfileUpdateException(TARGET_ID));

        assertThatThrownBy(() -> useCase.execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "Thieu giay phep")))
                .isInstanceOf(ConcurrentProfileUpdateException.class);
        verifyNoInteractions(kycResultPublisher);
    }

    @Test
    void execute_blankReason_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));

        assertThatThrownBy(() -> useCase.execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "  ")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(kycResultPublisher);
    }

    @Test
    void execute_alreadyRejected_throwsConflictAndDoesNotUpdate() {
        UserProfile profile = pendingProfile();
        profile.reject("admin-0", NOW.minusSeconds(60), "Lan 1");
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> useCase.execute(new RejectUserCommand(TARGET_ID, ACTOR_ID, "Lan 2")))
                .isInstanceOf(InvalidVerificationTransitionException.class);
        verify(userProfileRepository, never()).update(any());
        verifyNoInteractions(kycResultPublisher);
    }
}
