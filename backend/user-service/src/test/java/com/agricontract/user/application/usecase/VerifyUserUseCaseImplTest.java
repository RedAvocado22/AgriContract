package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.KycDecisionResponse;
import com.agricontract.user.application.dto.VerifyUserCommand;
import com.agricontract.user.domain.exception.InvalidVerificationTransitionException;
import com.agricontract.user.domain.exception.SelfKycDecisionException;
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
class VerifyUserUseCaseImplTest {

    private static final String TARGET_ID = "seller-1";
    private static final String ACTOR_ID = "operator-1";
    private static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2027-06-30T16:59:59.999Z");

    @Mock private UserProfileRepository userProfileRepository;

    private VerifyUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerifyUserUseCaseImpl(userProfileRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private UserProfile pendingProfile() {
        return UserProfile.create(new UserId(TARGET_ID), "HTX Ca Phe",
                Role.SELLER, new ContactInfo("seller@agricontract.test", "0901234567", "Dak Lak"));
    }

    @Test
    void execute_userNotFound_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(new VerifyUserCommand(TARGET_ID, ACTOR_ID, EXPIRES_AT)))
                .isInstanceOf(NoSuchElementException.class);
        verify(userProfileRepository, never()).update(any());
    }

    @Test
    void execute_pending_verifiesWithServerTimeAndActor() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));
        when(userProfileRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        KycDecisionResponse response = useCase.execute(new VerifyUserCommand(TARGET_ID, ACTOR_ID, EXPIRES_AT));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).update(captor.capture());
        UserProfile updated = captor.getValue();
        assertThat(updated.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(updated.getVerifiedByActorId()).isEqualTo(ACTOR_ID);
        assertThat(updated.getVerifiedAt()).isEqualTo(NOW);
        assertThat(updated.getAuthorizationExpiresAt()).isEqualTo(EXPIRES_AT);

        assertThat(response.userId()).isEqualTo(TARGET_ID);
        assertThat(response.verificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(response.verifiedByActorId()).isEqualTo(ACTOR_ID);
        assertThat(response.verifiedAt()).isEqualTo(NOW);
    }

    @Test
    void execute_truncatesDecisionTimeToDatabasePrecision() {
        useCase = new VerifyUserUseCaseImpl(userProfileRepository,
                Clock.fixed(Instant.parse("2026-10-07T03:00:00.123456789Z"), ZoneOffset.UTC));
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));
        when(userProfileRepository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        KycDecisionResponse response = useCase.execute(new VerifyUserCommand(TARGET_ID, ACTOR_ID, EXPIRES_AT));

        assertThat(response.verifiedAt()).isEqualTo(Instant.parse("2026-10-07T03:00:00.123456Z"));
    }

    @Test
    void execute_alreadyVerified_throwsConflictAndDoesNotUpdate() {
        UserProfile profile = pendingProfile();
        profile.verify("admin-0", NOW.minusSeconds(60), null);
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> useCase.execute(new VerifyUserCommand(TARGET_ID, ACTOR_ID, EXPIRES_AT)))
                .isInstanceOf(InvalidVerificationTransitionException.class);
        verify(userProfileRepository, never()).update(any());
    }

    @Test
    void execute_expiryNotAfterNow_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));

        assertThatThrownBy(() -> useCase.execute(new VerifyUserCommand(TARGET_ID, ACTOR_ID, NOW)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userProfileRepository, never()).update(any());
    }

    @Test
    void execute_selfVerification_throwsAndDoesNotUpdate() {
        when(userProfileRepository.findById(new UserId(TARGET_ID))).thenReturn(Optional.of(pendingProfile()));

        assertThatThrownBy(() -> useCase.execute(new VerifyUserCommand(TARGET_ID, TARGET_ID, EXPIRES_AT)))
                .isInstanceOf(SelfKycDecisionException.class);
        verify(userProfileRepository, never()).update(any());
    }
}
