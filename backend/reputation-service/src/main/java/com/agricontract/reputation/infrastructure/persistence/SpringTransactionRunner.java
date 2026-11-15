package com.agricontract.reputation.infrastructure.persistence;

import com.agricontract.reputation.application.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate repeatableReadOnly;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        repeatableReadOnly = new TransactionTemplate(transactionManager);
        repeatableReadOnly.setReadOnly(true);
        repeatableReadOnly.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        repeatableReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public <T> T inRepeatableReadOnlyTransaction(Supplier<T> work) {
        return repeatableReadOnly.execute(status -> work.get());
    }
}
