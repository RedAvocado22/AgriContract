package com.agricontract.reputation.infrastructure.persistence;

import com.agricontract.reputation.application.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate repeatableReadOnly;
    private final TransactionTemplate lockLifecycle;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        repeatableReadOnly = new TransactionTemplate(transactionManager);
        repeatableReadOnly.setReadOnly(true);
        repeatableReadOnly.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        repeatableReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        lockLifecycle = new TransactionTemplate(transactionManager);
        lockLifecycle.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T inRepeatableReadOnlyTransaction(Supplier<T> work) {
        return repeatableReadOnly.execute(status -> work.get());
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        // Joining a pre-existing RR snapshot could recompute stale ledger data after waiting on the mutex.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && (TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED).equals(
                        TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()))) {
            throw new IllegalStateException("Lock lifecycle requires a writable READ_COMMITTED transaction");
        }
        return lockLifecycle.execute(status -> work.get());
    }
}
