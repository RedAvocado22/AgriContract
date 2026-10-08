package com.agricontract.inspection.application.port;

import com.agricontract.inspection.domain.model.InspectorEligibility;

public interface InspectorEligibilityPort {
    InspectorEligibility load(String authenticatedUserId);
}
