package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.UserProfileResponse;
import com.agricontract.user.application.port.VerificationCodes;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.exception.InvalidVerificationCodeException;
import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmEmailVerificationUseCaseImplTest {

    private static final String USER_ID = "user-1";
    private static final UserId ID = new UserId(USER_ID);
    private static final String EMAIL = "seller@example.com";
    private static final String HASH = "h".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EmailVerificationChallengeRepository challengeRepository;
    @Mock private VerificationCodes codes;

    private ConfirmEmailVerificationUseCaseImpl useCase;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmEmailVerificationUseCaseImpl(userProfileRepository, challengeRepository, codes,
                Clock.fixed(NOW, ZoneOffset.UTC));
        profile = UserProfile.create(ID, "HTX Ca Phe", Role.SELLER, new ContactInfo(EMAIL, null, null));
        when(userProfileRepository.findById(ID)).thenReturn(Optional.of(profile));
    }

    private void pending(String email, Instant expiresAt, int attemptsLeft) {
        when(challengeRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                new EmailVerificationChallenge(USER_ID, email, HASH, NOW.minusSeconds(60), expiresAt, attemptsLeft)));
    }

    private static String reasonOf(Throwable e) {
        return ((EmailVerificationConflictException) e).getReason();
    }

    @Test
    void rightCode_marksVerified_andDeletesChallenge() {
        pending(EMAIL, NOW.plusSeconds(300), 5);
        when(codes.matches("123456", HASH)).thenReturn(true);
        when(userProfileRepository.update(profile)).thenReturn(profile);

        UserProfileResponse response = useCase.execute(USER_ID, "123456");

        assertThat(response.emailVerified()).isTrue();
        assertThat(profile.verifiedContactEmail()).isEqualTo(EMAIL);
        assertThat(profile.getEmailVerifiedAt()).isEqualTo(NOW);
        verify(challengeRepository).deleteByUserId(USER_ID);
    }

    @Test
    void wrongCode_countsAttempt_andKeepsUnverified() {
        pending(EMAIL, NOW.plusSeconds(300), 2);
        when(codes.matches("000000", HASH)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(USER_ID, "000000"))
                .isInstanceOfSatisfying(InvalidVerificationCodeException.class,
                        e -> assertThat(e.getAttemptsLeft()).isEqualTo(1));
        verify(challengeRepository).save(any());
        verify(userProfileRepository, never()).update(any());
        assertThat(profile.isEmailVerified()).isFalse();
    }

    @Test
    void noAttemptsLeft_conflict_evenWithRightCode() {
        pending(EMAIL, NOW.plusSeconds(300), 0);

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(EmailVerificationConflictException.ATTEMPTS_EXHAUSTED));
        verify(codes, never()).matches(any(), any());
    }

    @Test
    void expiredCode_conflict() {
        pending(EMAIL, NOW, 5);

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(EmailVerificationConflictException.EXPIRED));
    }

    @Test
    void codeSentToEarlierAddress_treatedAsExpired() {
        pending("old@example.com", NOW.plusSeconds(300), 5);

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(EmailVerificationConflictException.EXPIRED));
        assertThat(profile.isEmailVerified()).isFalse();
    }

    @Test
    void noCodeRequested_conflict() {
        when(challengeRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(EmailVerificationConflictException.NOT_REQUESTED));
    }

    @Test
    void addressProvenByAnotherUserFirst_claimed() {
        pending(EMAIL, NOW.plusSeconds(300), 5);
        when(codes.matches("123456", HASH)).thenReturn(true);
        when(userProfileRepository.isEmailVerifiedByAnotherUser(EMAIL, ID)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456")).isInstanceOf(EmailAlreadyClaimedException.class);
        verify(userProfileRepository, never()).update(any());
        assertThat(profile.isEmailVerified()).isFalse();
    }
}
