package com.agricontract.inspection.infrastructure.persistence;

import com.agricontract.inspection.application.port.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import java.util.function.Supplier;

@Component
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate required;
    private final TransactionTemplate readOnly;
    private final TransactionTemplate mandatory;
    private final TransactionTemplate readCommitted;
    private final DataSource dataSource;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager, DataSource dataSource) {
        this.dataSource = dataSource;
        required = new TransactionTemplate(transactionManager);
        readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        mandatory = new TransactionTemplate(transactionManager);
        mandatory.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        readCommitted = new TransactionTemplate(transactionManager);
        readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
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
    public void inReadCommittedTransaction(Runnable work) {
        readCommitted.executeWithoutResult(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED
                        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalTransactionStateException("Assignment transaction requires writable READ_COMMITTED isolation");
                }
                work.run();
            } catch (SQLException failure) {
                throw new IllegalTransactionStateException("Cannot verify assignment transaction isolation", failure);
            } finally {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        });
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
