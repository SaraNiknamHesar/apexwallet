package com.apexwallet.app.demo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TransactionProbe {

    @Transactional
    public boolean insideTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    public boolean selfInvocation() {
        return insideTransaction();
    }
}
