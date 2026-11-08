package com.agricontract.contract.application.service;

import com.agricontract.contract.application.dto.UserInfo;
import com.agricontract.contract.application.exception.SignerNotEligibleException;
import com.agricontract.contract.application.exception.UserServiceUnavailableException;
import com.agricontract.contract.application.port.UserPort;
import com.agricontract.contract.domain.exception.ContractConflictException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignerEligibilityTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    private final UserPort users = mock(UserPort.class);
    private final SignerEligibility eligibility = new SignerEligibility(users, Clock.fixed(NOW, ZoneOffset.UTC), 300);

    @Test
    void freshLogin_withinFiveMinutesPasses() {
        eligibility.requireFreshLogin(NOW.minusSeconds(300));
        eligibility.requireFreshLogin(NOW.plusSeconds(30));
    }

    @Test
    void staleMissingOrFutureLogin_asksForThePasswordAgain() {
        for (Instant authTime : new Instant[]{NOW.minusSeconds(301), null, NOW.plusSeconds(120)}) {
            assertThatThrownBy(() -> eligibility.requireFreshLogin(authTime))
                    .isInstanceOf(SignerNotEligibleException.class)
                    .extracting("code").isEqualTo(SignerNotEligibleException.STEP_UP_REQUIRED);
        }
    }

    @Test
    void verifiedUnlockedUserWithOpenAuthorization_isEligible() {
        UserInfo user = user("VERIFIED", null, null, "s@x.test");
        when(users.findUser("seller-1")).thenReturn(Optional.of(user));

        assertThat(eligibility.requireEligible("seller-1")).isEqualTo(user);
    }

    @Test
    void authorizationEndingLaterAndLockThatAlreadyEnded_areEligible() {
        when(users.findUser("seller-1")).thenReturn(Optional.of(
                user("VERIFIED", NOW.plusSeconds(1), NOW.minusSeconds(1), "s@x.test")));

        assertThat(eligibility.requireEligible("seller-1").userId()).isEqualTo("seller-1");
    }

    @Test
    void notVerified_isRefused() {
        refused(user("PENDING", null, null, "s@x.test"), SignerNotEligibleException.NOT_VERIFIED);
        refused(user("REJECTED", null, null, "s@x.test"), SignerNotEligibleException.NOT_VERIFIED);
    }

    @Test
    void authorizationEndedAtOrBeforeNow_isRefused() {
        refused(user("VERIFIED", NOW, null, "s@x.test"), SignerNotEligibleException.AUTHORIZATION_EXPIRED);
        refused(user("VERIFIED", NOW.minusSeconds(86400), null, "s@x.test"),
                SignerNotEligibleException.AUTHORIZATION_EXPIRED);
    }

    @Test
    void reputationLockStillRunning_isRefused() {
        refused(user("VERIFIED", null, NOW.plusSeconds(1), "s@x.test"), SignerNotEligibleException.LOCKED);
    }

    @Test
    void noEmailToSendTheCodeTo_isRefused() {
        refused(user("VERIFIED", null, null, " "), SignerNotEligibleException.NO_EMAIL);
        refused(user("VERIFIED", null, null, null), SignerNotEligibleException.NO_EMAIL);
    }

    @Test
    void unknownUser_isRefused() {
        when(users.findUser("seller-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eligibility.requireEligible("seller-1"))
                .extracting("code").isEqualTo(SignerNotEligibleException.UNKNOWN_USER);
    }

    @Test
    void userServiceDown_failsClosed() {
        when(users.findUser("seller-1")).thenThrow(new UserServiceUnavailableException("down", null));

        assertThatThrownBy(() -> eligibility.requireEligible("seller-1"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    @Test
    void counterpartyThatCanSign_passes_evenWithoutAnEmail() {
        when(users.findUser("seller-1")).thenReturn(Optional.of(user("VERIFIED", NOW.plusSeconds(1), NOW, null)));

        eligibility.requireCounterpartyEligible("seller-1");
    }

    @Test
    void counterpartyUnverifiedPastAuthorizationLockedOrUnknown_isAConflictWithoutTheReason() {
        for (UserInfo blocked : new UserInfo[]{
                user("PENDING", null, null, "s@x.test"),
                user("VERIFIED", NOW, null, "s@x.test"),
                user("VERIFIED", null, NOW.plusSeconds(1), "s@x.test"),
                null}) {
            when(users.findUser("seller-1")).thenReturn(Optional.ofNullable(blocked));

            assertThatThrownBy(() -> eligibility.requireCounterpartyEligible("seller-1"))
                    .isInstanceOf(ContractConflictException.class)
                    .hasMessageNotContaining("lock")
                    .extracting("code").isEqualTo(ContractConflictException.COUNTERPARTY_NOT_ELIGIBLE);
        }
    }

    @Test
    void counterpartyCheckWithUserServiceDown_failsClosed() {
        when(users.findUser("seller-1")).thenThrow(new UserServiceUnavailableException("down", null));

        assertThatThrownBy(() -> eligibility.requireCounterpartyEligible("seller-1"))
                .isInstanceOf(UserServiceUnavailableException.class);
    }

    private void refused(UserInfo user, String code) {
        when(users.findUser("seller-1")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> eligibility.requireEligible("seller-1"))
                .isInstanceOf(SignerNotEligibleException.class)
                .extracting("code").isEqualTo(code);
    }

    private static UserInfo user(String status, Instant authorizationEnds, Instant lockedUntil, String email) {
        return new UserInfo("seller-1", "HTX Bán", email, "SELLER", status, authorizationEnds, lockedUntil);
    }
}
