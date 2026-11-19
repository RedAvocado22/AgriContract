package com.agricontract.user.application.usecase;

public interface PurgeUnverifiedAccountsUseCase {
    // Returns how many accounts were deleted.
    int execute();
}
