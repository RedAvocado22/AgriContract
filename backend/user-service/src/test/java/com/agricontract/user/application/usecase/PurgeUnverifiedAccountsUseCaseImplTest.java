package com.agricontract.user.application.usecase;

import com.agricontract.user.application.exception.IdentityProviderUnavailableException;
import com.agricontract.user.application.port.IdentityAccounts;
import com.agricontract.user.domain.model.UnverifiedAccountPolicy;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurgeUnverifiedAccountsUseCaseImplTest {
    private static final Instant NOW = Instant.parse("2026-11-20T08:00:00Z");

    @Mock private IdentityAccounts accounts;
    @Mock private UserProfileRepository profiles;
    @Mock private EmailVerificationChallengeRepository challenges;

    private PurgeUnverifiedAccountsUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new PurgeUnverifiedAccountsUseCaseImpl(accounts, profiles, challenges,
                new UnverifiedAccountPolicy(Duration.ofHours(24)), Clock.fixed(NOW, ZoneOffset.UTC),
                new DirectTransactionRunner());
    }

    @Test
    void asksForAccountsCreatedMoreThan24HoursAgo() {
        useCase.execute();

        verify(accounts).unverifiedSelfRegisteredCreatedBefore(NOW.minus(Duration.ofHours(24)));
    }

    @Test
    void keycloakUnavailable_deletesNothing() {
        when(accounts.unverifiedSelfRegisteredCreatedBefore(any()))
                .thenThrow(new IdentityProviderUnavailableException("down", null));

        assertThat(useCase.execute()).isZero();
        verify(profiles, never()).deleteById(any());
    }

    @Test
    void profileRowsGoBeforeTheKeycloakAccount() {
        // Keycloak last: if it fails, the transaction rolls the profile delete back and the next run retries.
        when(accounts.unverifiedSelfRegisteredCreatedBefore(any())).thenReturn(List.of("u1"));
        when(profiles.findByIdForUpdate(new UserId("u1"))).thenReturn(Optional.of(profile("u1")));

        assertThat(useCase.execute()).isEqualTo(1);

        InOrder order = inOrder(profiles, challenges, accounts);
        order.verify(profiles).findByIdForUpdate(new UserId("u1"));
        order.verify(challenges).deleteByUserId("u1");
        order.verify(profiles).deleteById(new UserId("u1"));
        order.verify(accounts).delete("u1");
    }

    @Test
    void oneFailure_doesNotStopTheOthers() {
        when(accounts.unverifiedSelfRegisteredCreatedBefore(any())).thenReturn(List.of("u1", "u2"));
        when(profiles.findByIdForUpdate(any())).thenReturn(Optional.empty());
        doThrow(new IdentityProviderUnavailableException("down", null)).when(accounts).delete("u1");

        assertThat(useCase.execute()).isEqualTo(1);
        verify(accounts).delete("u2");
    }

    @Test
    void verifiedHere_isKept_andKeycloakIsToldAgain() {
        UserProfile verified = profile("u1");
        verified.markEmailVerified("u1@example.test", NOW.minusSeconds(60));
        when(accounts.unverifiedSelfRegisteredCreatedBefore(any())).thenReturn(List.of("u1"));
        when(profiles.findByIdForUpdate(new UserId("u1"))).thenReturn(Optional.of(verified));

        assertThat(useCase.execute()).isZero();

        verify(accounts).markEmailVerified("u1");
        verify(accounts, never()).delete(any());
        verify(profiles, never()).deleteById(any());
    }

    private static UserProfile profile(String userId) {
        return UserProfile.create(new UserId(userId), "HTX Ca Phe", Role.BUYER,
                new ContactInfo(userId + "@example.test", null, null));
    }
}
