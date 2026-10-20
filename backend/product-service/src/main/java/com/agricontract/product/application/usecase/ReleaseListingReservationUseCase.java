package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ReleaseListingReservationRequest;

public interface ReleaseListingReservationUseCase {
    void execute(String contractId, ReleaseListingReservationRequest request);
}
