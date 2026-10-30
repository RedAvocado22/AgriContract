package com.agricontract.product.application.usecase;

import java.time.Instant;

public interface ReapOrphanReservationsUseCase {
    /** @return how many orphan reservations were released */
    int execute(Instant createdBefore);
}
