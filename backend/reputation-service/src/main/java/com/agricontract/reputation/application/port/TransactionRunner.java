package com.agricontract.reputation.application.port;

import java.util.function.Supplier;

/** Transaction boundaries for consistent reads and lock lifecycle writes. */
public interface TransactionRunner {
    /**
     * Starts a read-only, repeatable-read transaction, or participates in the caller's
     * transaction with its existing settings. Runtime failures propagate and mark
     * the transaction for rollback.
     */
    <T> T inRepeatableReadOnlyTransaction(Supplier<T> work);

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
