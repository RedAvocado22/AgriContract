package com.agricontract.user.application.usecase;

import com.agricontract.user.application.dto.EligibleInspectorResponse;

import java.util.List;

public interface ListEligibleInspectorsUseCase {
    List<EligibleInspectorResponse> execute();
}
