package com.agricontract.escrow.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** #247: when the activation deposits end in failure, and which leg failed. */
class ActivationDepositsTest {
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");
    private static final RetrySchedule ONE_RETRY = new RetrySchedule(List.of(Duration.ofMinutes(5)));

    @Test
    void lockedAndExhausted_isAFailedSeller() {
        DepositLeg buyer = leg(DepositLegParty.BUYER);
        buyer.markLocked(UUID.randomUUID().toString(), NOW);
        DepositLeg seller = exhausted(DepositLegParty.SELLER);

        ActivationDeposits deposits = new ActivationDeposits(List.of(buyer, seller));

        assertThat(deposits.lockFailed()).isTrue();
        assertThat(deposits.allLocked()).isFalse();
        assertThat(deposits.failedLeg()).isEqualTo(ActivationDeposits.FailedLeg.SELLER);
    }

    @Test
    void exhaustedWhileTheOtherIsStillInProgress_isNotFailedYet() {
        DepositLeg buyer = exhausted(DepositLegParty.BUYER);
        DepositLeg seller = leg(DepositLegParty.SELLER);                 // REQUESTED
        assertThat(new ActivationDeposits(List.of(buyer, seller)).lockFailed()).isFalse();

        seller.markFailed("bank down", NOW, ONE_RETRY);                 // retry due: still in progress
        assertThat(new ActivationDeposits(List.of(buyer, seller)).lockFailed()).isFalse();
    }

    @Test
    void bothExhausted_isBoth_andABuyerOnlyContractCanFail() {
        assertThat(new ActivationDeposits(List.of(exhausted(DepositLegParty.BUYER), exhausted(DepositLegParty.SELLER)))
                .failedLeg()).isEqualTo(ActivationDeposits.FailedLeg.BOTH);
        ActivationDeposits buyerOnly = new ActivationDeposits(List.of(exhausted(DepositLegParty.BUYER)));
        assertThat(buyerOnly.lockFailed()).isTrue();
        assertThat(buyerOnly.leg(DepositLegParty.SELLER)).isEmpty();
    }

    private static DepositLeg exhausted(DepositLegParty party) {
        DepositLeg leg = leg(party);
        leg.markFailed("bank down", NOW, ONE_RETRY);
        leg.retryRequested(NOW.plus(Duration.ofMinutes(5)));
        assertThat(leg.markFailed("still down", NOW, ONE_RETRY)).isEqualTo(DepositLeg.FailureOutcome.RETRIES_EXHAUSTED);
        return leg;
    }

    private static DepositLeg leg(DepositLegParty party) {
        return DepositLeg.request(UUID.randomUUID().toString(), party, UUID.randomUUID().toString(),
                new Money(new BigDecimal("1000000.00")), NOW);
    }
}
