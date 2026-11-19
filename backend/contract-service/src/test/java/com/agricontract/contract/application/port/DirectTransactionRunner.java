package com.agricontract.contract.application.port;

import java.util.function.Supplier;

/** Unit tests: runs the work directly. Atomicity is proven by the MySQL integration tests. */
public class DirectTransactionRunner implements TransactionRunner {
    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    public void requireTransaction() {
    }
}
