package com.agricontract.user.application.port;

import java.util.function.Supplier;

/**
 * Runs work atomically: everything inside commits together or not at all, and a runtime exception thrown by the
 * work rolls it back and propagates. Work that must happen after commit stays outside the lambda.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    void inTransaction(Runnable work);
}
