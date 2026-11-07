package com.agricontract.inspection.application.port;

import java.util.List;
import com.agricontract.inspection.domain.model.InspectorEligibility;

public interface InspectorEligibilityPort {
    InspectorEligibility load(String authenticatedUserId);
    List<String> loadEligibleInspectors();
}
