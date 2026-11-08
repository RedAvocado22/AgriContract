package com.agricontract.escrow.application.usecase;

import java.util.List;

// Resends the bank.lock_requested of FAILED legs whose retry is due (#102, D39).
public interface RetryDepositLocksUseCase {

    /** Contracts with a retry due now. */
    List<String> contractsWithRetryDue();

    /** Resends every due leg of one contract; must run in a transaction. Returns how many were resent. */
    int retryDue(String contractId);
}
