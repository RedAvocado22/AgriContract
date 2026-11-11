package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EmailVerificationSentResponse;
import com.agricontract.user.application.exception.VerificationEmailNotSentException;
import com.agricontract.user.application.port.VerificationCodes;
import com.agricontract.user.application.port.VerificationEmailSender;
import com.agricontract.user.domain.exception.EmailAlreadyClaimedException;
import com.agricontract.user.domain.exception.EmailVerificationConflictException;
import com.agricontract.user.domain.model.EmailVerificationChallenge;
import com.agricontract.user.domain.model.EmailVerificationPolicy;
import com.agricontract.user.domain.model.UserProfile;
import com.agricontract.user.domain.model.vo.ContactInfo;
import com.agricontract.user.domain.model.vo.Role;
import com.agricontract.user.domain.model.vo.UserId;
import com.agricontract.user.domain.repository.EmailVerificationChallengeRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestEmailVerificationUseCaseImplTest {

    private static final String USER_ID = "user-1";
    private static final String EMAIL = "seller@example.com";
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final EmailVerificationPolicy POLICY = new EmailVerificationPolicy(600, 5, 60);

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EmailVerificationChallengeRepository challengeRepository;
    @Mock private VerificationCodes codes;
    @Mock private VerificationEmailSender sender;

    private RequestEmailVerificationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestEmailVerificationUseCaseImpl(userProfileRepository, challengeRepository, codes, sender,
                POLICY, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserProfile profile() {
        return UserProfile.create(new UserId(USER_ID), "HTX Ca Phe", Role.SELLER, new ContactInfo(EMAIL, null, null));
    }

    @Test
    void sendsCodeToContactEmail_andStoresOnlyItsHash() {
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.of(profile()));
        when(challengeRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(codes.newCode()).thenReturn("123456");
        when(codes.hash("123456")).thenReturn("h".repeat(64));

        EmailVerificationSentResponse response = useCase.execute(USER_ID);

        ArgumentCaptor<EmailVerificationChallenge> saved = ArgumentCaptor.forClass(EmailVerificationChallenge.class);
        verify(challengeRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getCodeHash()).isEqualTo("h".repeat(64));
        assertThat(saved.getValue().getAttemptsLeft()).isEqualTo(5);
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(sender).send(anyString(), eq(USER_ID), eq(EMAIL), eq("123456"), eq(NOW.plusSeconds(600)));
    }

    @Test
    void alreadyVerified_conflict() {
        UserProfile profile = profile();
        profile.markEmailVerified(EMAIL, NOW.minusSeconds(3600));
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> useCase.execute(USER_ID))
                .isInstanceOfSatisfying(EmailVerificationConflictException.class,
                        e -> assertThat(e.getReason()).isEqualTo(EmailVerificationConflictException.ALREADY_VERIFIED));
        verifyNoInteractions(sender);
    }

    @Test
    void addressVerifiedByAnotherUser_noCodeSent() {
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.of(profile()));
        when(userProfileRepository.isEmailVerifiedByAnotherUser(EMAIL, new UserId(USER_ID))).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(USER_ID)).isInstanceOf(EmailAlreadyClaimedException.class);
        verify(challengeRepository, never()).save(any());
        verifyNoInteractions(sender);
    }

    @Test
    void resendWithinCooldown_conflict() {
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.of(profile()));
        when(challengeRepository.findByUserId(USER_ID)).thenReturn(Optional.of(
                new EmailVerificationChallenge(USER_ID, EMAIL, "h".repeat(64), NOW.minusSeconds(30),
                        NOW.plusSeconds(570), 5)));

        assertThatThrownBy(() -> useCase.execute(USER_ID))
                .isInstanceOfSatisfying(EmailVerificationConflictException.class,
                        e -> assertThat(e.getReason()).isEqualTo(EmailVerificationConflictException.RESEND_TOO_SOON));
        verifyNoInteractions(sender);
    }

    @Test
    void emailNotAccepted_propagates() {
        when(userProfileRepository.findById(new UserId(USER_ID))).thenReturn(Optional.of(profile()));
        when(challengeRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(codes.newCode()).thenReturn("123456");
        when(codes.hash("123456")).thenReturn("h".repeat(64));
        doThrow(new VerificationEmailNotSentException("down", null))
                .when(sender).send(anyString(), any(), any(), any(), any());

        assertThatThrownBy(() -> useCase.execute(USER_ID)).isInstanceOf(VerificationEmailNotSentException.class);
    }
}
