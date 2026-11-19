package com.agricontract.bank.application.port;

import java.util.function.Supplier;

/**
 * Transaction boundary for the application layer (D44). Implemented in infrastructure; the use case decides which
 * steps are atomic (a ledger entry and its result in the outbox, D22), infrastructure decides how.
 */
public interface TransactionRunner {

    /** Runs {@code work} in a transaction; a runtime failure propagates and rolls it back. */
    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
