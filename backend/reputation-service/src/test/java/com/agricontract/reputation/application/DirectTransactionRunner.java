package com.agricontract.reputation.application;

import com.agricontract.reputation.application.port.TransactionRunner;
import java.util.function.Supplier;

/** Executes application tests without a framework; MySQL tests prove transaction semantics. */
final class DirectTransactionRunner implements TransactionRunner {
    @Override
    public <T> T inRepeatableReadOnlyTransaction(Supplier<T> work) {
        return work.get();
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
