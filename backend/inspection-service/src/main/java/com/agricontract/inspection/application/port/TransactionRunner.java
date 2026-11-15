package com.agricontract.inspection.application.port;

import java.util.function.Supplier;

/** Transaction boundaries supplied by infrastructure, without framework dependencies. */
public interface TransactionRunner {
    /** Join the caller's transaction, or start one if none exists. */
    <T> T inTransaction(Supplier<T> work);

    void inTransaction(Runnable work);

    /** Join the caller's transaction, or start a read-only transaction if none exists. */
    <T> T inReadOnlyTransaction(Supplier<T> work);

    /** Require and participate in the caller's transaction; never start an independent transaction. */
    void inExistingTransaction(Runnable work);
}
