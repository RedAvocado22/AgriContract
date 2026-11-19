package com.agricontract.bank.infrastructure.persistence;

import com.agricontract.bank.application.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The only place in bank-service that knows about Spring transactions (D44). Default propagation REQUIRED. */
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
}
