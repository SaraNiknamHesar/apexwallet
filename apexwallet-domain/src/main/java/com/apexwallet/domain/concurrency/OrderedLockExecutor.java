package com.apexwallet.domain.concurrency;

import com.apexwallet.domain.account.AccountId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locks two accounts in {@link AccountId} order so A-then-B and B-then-A cannot deadlock.
 * This protects one JVM only. A second process needs the same order in SQL {@code FOR UPDATE}.
 */
public final class OrderedLockExecutor {

    private final ConcurrentHashMap<AccountId, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void run(AccountId left, AccountId right, Runnable action) {
        if (left.equals(right)) {
            ReentrantLock only = lockFor(left);
            only.lock();
            try {
                action.run();
            } finally {
                only.unlock();
            }
            return;
        }
        AccountId first = left.compareTo(right) <= 0 ? left : right;
        AccountId second = first.equals(left) ? right : left;
        ReentrantLock firstLock = lockFor(first);
        ReentrantLock secondLock = lockFor(second);
        firstLock.lock();
        try {
            secondLock.lock();
            try {
                action.run();
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    private ReentrantLock lockFor(AccountId id) {
        return locks.computeIfAbsent(id, ignored -> new ReentrantLock());
    }
}
