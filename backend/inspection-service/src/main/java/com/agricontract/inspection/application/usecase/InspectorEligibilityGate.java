package com.agricontract.inspection.application.usecase;

import com.agricontract.inspection.application.exception.EligibilityUnavailableException;
import com.agricontract.inspection.application.exception.InspectorNotEligibleException;
import com.agricontract.inspection.application.port.AuthenticatedInspectorPort;
import com.agricontract.inspection.application.port.InspectorEligibilityPort;

import org.springframework.stereotype.Service;

import java.time.Clock;

/** Eligibility only. A successful check is NOT commission authorization. */
@Service
public class InspectorEligibilityGate {
    private final AuthenticatedInspectorPort identity;
    private final InspectorEligibilityPort users;
    private final Clock clock;

    public InspectorEligibilityGate(AuthenticatedInspectorPort identity,
                                    InspectorEligibilityPort users, Clock clock) {
        this.identity = identity;
        this.users = users;
        this.clock = clock;
    }

    public String requireEligibleInspector() {
        String actorId = identity.requireInspectorId();
        var eligibility = users.load(actorId);
        if (eligibility == null || !actorId.equals(eligibility.userId())) {
            throw new EligibilityUnavailableException();
        }
        if (!eligibility.isEligibleAt(clock.instant())) {
            throw new InspectorNotEligibleException();
        }
        return actorId;
    }
}
