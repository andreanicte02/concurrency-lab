package io.github.andreanicte02.concurrency.counter;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/** A counter shared by several threads. Implementations differ only in how they synchronize. */
public interface Counter {

    void increment();

    long get();

    /** Short label used in reports, e.g. {@code "unsafe"}. */
    String name();

    /** {@code count = count + 1} with no synchronization: a read-modify-write race. */
    static Counter unsafe() {
        return new Counter() {
            // volatile makes every read and write visible, but the read and the write are still
            // two separate steps, so two threads can read the same value and lose an update.
            private volatile long count;

            @Override public void increment() { count = count + 1; }
            @Override public long get() { return count; }
            @Override public String name() { return "unsafe"; }
        };
    }

    /** Intrinsic lock: only one thread at a time may run {@link #increment()}. */
    static Counter synchronizedCounter() {
        return new Counter() {
            private long count;

            @Override public synchronized void increment() { count = count + 1; }
            @Override public synchronized long get() { return count; }
            @Override public String name() { return "synchronized"; }
        };
    }

    /** Explicit {@link ReentrantLock}, the fix the original exercise had commented out. */
    static Counter lock() {
        return new Counter() {
            private final ReentrantLock lock = new ReentrantLock();
            private long count;

            @Override
            public void increment() {
                lock.lock();
                try {
                    count = count + 1;
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public long get() {
                lock.lock();
                try {
                    return count;
                } finally {
                    lock.unlock();
                }
            }

            @Override public String name() { return "reentrant-lock"; }
        };
    }

    /** Lock-free: a single atomic compare-and-swap per increment. */
    static Counter atomic() {
        return new Counter() {
            private final AtomicLong count = new AtomicLong();

            @Override public void increment() { count.incrementAndGet(); }
            @Override public long get() { return count.get(); }
            @Override public String name() { return "atomic"; }
        };
    }
}
