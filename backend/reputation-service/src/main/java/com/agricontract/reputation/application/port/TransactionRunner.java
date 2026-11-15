package com.agricontract.reputation.application.port;

import java.util.function.Supplier;

/** Transaction boundary for reads that require a consistent snapshot. */
public interface TransactionRunner {
    /**
     * Starts a read-only, repeatable-read transaction, or participates in the caller's
     * transaction with its existing settings. Runtime failures propagate and mark
     * the transaction for rollback.
     */
    <T> T inRepeatableReadOnlyTransaction(Supplier<T> work);
}
