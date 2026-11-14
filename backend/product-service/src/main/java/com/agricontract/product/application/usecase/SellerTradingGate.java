package com.agricontract.product.application.usecase;

import com.agricontract.product.application.port.UserEligibilityPort;
import com.agricontract.product.common.exception.ActorNotEligibleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Fail-closed gate (user-service design §3, matrix 21f), before any read or write of a listing command: a
 * user-service outage (503) or an ineligible seller (403) never creates or edits a listing. Replays are gated too,
 * so no write path skips the check.
 */
@Component
@RequiredArgsConstructor
class SellerTradingGate {

    private final UserEligibilityPort userEligibilityPort;
    private final Clock clock;

    void assertEligible(String sellerId) {
        boolean eligible = userEligibilityPort.load(sellerId)
                .map(facts -> facts.allowsTradingAt(clock.instant()))
                .orElse(false);
        if (!eligible) {
            throw new ActorNotEligibleException();
        }
    }
}
