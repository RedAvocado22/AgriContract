package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.ReservationResult;
import com.agricontract.product.application.dto.ReserveListingQuantityRequest;

public interface ReserveListingQuantityUseCase {
    ReservationResult execute(ReserveListingQuantityRequest request);
}
