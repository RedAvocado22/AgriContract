package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationConfirmation;
import com.agricontract.user.application.exception.IdentityProviderUnavailableException;
import com.agricontract.user.application.port.IdentityAccounts;
import com.agricontract.user.application.port.VerificationCodes;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
import com.agricontract.user.domain.repository.UserProfileRepository;
import com.agricontract.user.testsupport.DirectTransactionRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
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
    @Mock private IdentityAccounts identityAccounts;

    private ConfirmEmailVerificationUseCaseImpl useCase;
    private UserProfile profile;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmEmailVerificationUseCaseImpl(userProfileRepository, challengeRepository, codes,
                Clock.fixed(NOW, ZoneOffset.UTC), new DirectTransactionRunner(), identityAccounts);
        profile = UserProfile.create(ID, "HTX Ca Phe", Role.SELLER, new ContactInfo(EMAIL, null, null));
        when(userProfileRepository.findByIdForUpdate(ID)).thenReturn(Optional.of(profile));
    }

    private void pending(String email, Instant expiresAt, int attemptsLeft) {
        when(challengeRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(
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

        EmailVerificationConfirmation result = useCase.execute(USER_ID, "123456");

        assertThat(result).isInstanceOfSatisfying(EmailVerificationConfirmation.Verified.class,
                verified -> assertThat(verified.profile().emailVerified()).isTrue());
        assertThat(profile.verifiedContactEmail()).isEqualTo(EMAIL);
        assertThat(profile.getEmailVerifiedAt()).isEqualTo(NOW);
        verify(challengeRepository).deleteByUserId(USER_ID);
        verify(identityAccounts).markEmailVerified(USER_ID);
    }

    @Test
    void keycloakDown_stillVerified() {
        // D59: the record here is what counts; the purge job sets Keycloak's flag again before deleting anything.
        pending(EMAIL, NOW.plusSeconds(300), 5);
        when(codes.matches("123456", HASH)).thenReturn(true);
        when(userProfileRepository.update(profile)).thenReturn(profile);
        doThrow(new IdentityProviderUnavailableException("down", null))
                .when(identityAccounts).markEmailVerified(USER_ID);

        EmailVerificationConfirmation result = useCase.execute(USER_ID, "123456");

        assertThat(result).isInstanceOf(EmailVerificationConfirmation.Verified.class);
        assertThat(profile.isEmailVerified()).isTrue();
    }

    @Test
    void locksTheProfileBeforeTheChallenge() {
        pending(EMAIL, NOW.plusSeconds(300), 5);
        when(codes.matches("123456", HASH)).thenReturn(true);
        when(userProfileRepository.update(profile)).thenReturn(profile);

        useCase.execute(USER_ID, "123456");

        // Same order as the issuer; the opposite would deadlock a confirm against a resend.
        InOrder order = inOrder(challengeRepository, userProfileRepository);
        order.verify(userProfileRepository).findByIdForUpdate(ID);
        order.verify(challengeRepository).findByUserIdForUpdate(USER_ID);
    }

    @Test
    void wrongCode_isRejectedResult_withTheAttemptCounted() {
        pending(EMAIL, NOW.plusSeconds(300), 2);
        when(codes.matches("000000", HASH)).thenReturn(false);

        EmailVerificationConfirmation result = useCase.execute(USER_ID, "000000");

        assertThat(result).isEqualTo(new EmailVerificationConfirmation.Rejected(1));
        ArgumentCaptor<EmailVerificationChallenge> saved = ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(saved.capture());
        assertThat(saved.getValue().getAttemptsLeft()).isEqualTo(1);
        verify(userProfileRepository, never()).update(any());
        assertThat(profile.isEmailVerified()).isFalse();
        verify(identityAccounts, never()).markEmailVerified(any());
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
        when(challengeRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(EmailVerificationConflictException.NOT_REQUESTED));
    }

    @Test
    void alreadyVerified_conflict_evenWithoutChallenge() {
        // A parallel confirm with the right code verified the address and deleted the challenge while we waited.
        profile.markEmailVerified(EMAIL, NOW.minusSeconds(1));
        when(challengeRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(USER_ID, "123456"))
                .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(EmailVerificationConflictException.ALREADY_VERIFIED));
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
