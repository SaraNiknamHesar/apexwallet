package com.apexwallet.app.demo;

import org.springframework.stereotype.Service;

@Service
public class TransactionProbeClient {

    private final TransactionProbe probe;

    public TransactionProbeClient(TransactionProbe probe) {
        this.probe = probe;
    }

    public boolean throughProxy() {
        return probe.insideTransaction();
    }
}
