package com.agricontract.user.testsupport;

import com.agricontract.user.application.port.TransactionRunner;

import java.util.function.Supplier;

/** Runs the work in place; atomicity itself is proven by the integration tests on real MySQL. */
public class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    public void inTransaction(Runnable work) {
        work.run();
    }
}
