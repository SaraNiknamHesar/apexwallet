package com.apexwallet.domain.concurrency;

/**
 * A non-volatile flag can stay invisible to another thread. {@code volatile} adds visibility, not atomicity.
 */
public final class StopFlag {

    public boolean stop;
    public volatile boolean volatileStop;
}
