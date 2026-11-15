package com.agricontract.inspection.testsupport;

import com.agricontract.inspection.application.port.TransactionRunner;

import java.util.function.Supplier;

/** Executes business logic synchronously; real transaction guarantees belong to the MySQL tests. */
public class DirectTransactionRunner implements TransactionRunner {
    @Override
    public <T> T inTransaction(Supplier<T> work) { return work.get(); }

    @Override
    public void inTransaction(Runnable work) { work.run(); }

    @Override
    public <T> T inReadOnlyTransaction(Supplier<T> work) { return work.get(); }

    @Override
    public void inExistingTransaction(Runnable work) { work.run(); }
}
