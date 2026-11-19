package com.agricontract.contract.application.port;

import java.util.function.Supplier;

/**
 * Transaction boundary for the application layer (D44). Implemented in infrastructure; the use case decides which
 * steps are atomic, infrastructure decides how.
 */
public interface TransactionRunner {

    /**
     * Runs {@code work} in a transaction: joins the caller's one if there is one, starts a new one otherwise. A
     * runtime failure propagates and rolls the transaction back.
     */
    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    /**
     * Refuses to go on outside a caller's transaction ({@link IllegalStateException}). For steps that must commit or
     * roll back together with the caller's writes (consent, OTP issue, refund start), never on their own.
     */
    void requireTransaction();
}
