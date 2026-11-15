package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.application.port.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate required;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate mandatory;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        required = new TransactionTemplate(transactionManager);
        readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        mandatory = new TransactionTemplate(transactionManager);
        mandatory.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return required.execute(status -> work.get());
    }

    @Override
    public void inTransaction(Runnable work) {
        required.executeWithoutResult(status -> work.run());
    }

    @Override
    public <T> T inReadOnlyTransaction(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }

    @Override
    public void inExistingTransaction(Runnable work) {
        mandatory.executeWithoutResult(status -> work.run());
    }
}
