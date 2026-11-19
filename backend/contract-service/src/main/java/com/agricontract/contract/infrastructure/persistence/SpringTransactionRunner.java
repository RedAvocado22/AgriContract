package com.agricontract.contract.infrastructure.persistence;

import com.agricontract.contract.application.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The only place in contract-service that knows about Spring transactions (D44). Default propagation REQUIRED. */
@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate template;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return template.execute(status -> work.get());
    }

    /** Same guarantee as {@code @Transactional(propagation = MANDATORY)} had. */
    @Override
    public void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Must run inside the caller's transaction");
        }
    }
}
