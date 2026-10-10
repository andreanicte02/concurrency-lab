package io.github.andreanicte02.concurrency.bank;

/** A bank account that several ATM threads withdraw from at once. */
public interface Account {

    /** Withdraws {@code amount} if the balance covers it. Returns whether the money was handed out. */
    boolean withdraw(int amount);

    int balance();

    String name();

    /**
     * Check-then-act without a lock: two threads can both see enough money, and both withdraw.
     * {@link Thread#yield()} stands in for the real work between the check and the update
     * (a database round trip, printing a receipt...) and makes the race easy to reproduce.
     */
    static Account unsafe(int initialBalance) {
        return new Account() {
            private volatile int balance = initialBalance;

            @Override
            public boolean withdraw(int amount) {
                if (balance >= amount) {
                    Thread.yield();
                    balance = balance - amount;
                    return true;
                }
                return false;
            }

            @Override public int balance() { return balance; }
            @Override public String name() { return "unsafe"; }
        };
    }

    /** The check and the update happen atomically under the account's lock. */
    static Account synchronizedAccount(int initialBalance) {
        return new Account() {
            private int balance = initialBalance;

            @Override
            public synchronized boolean withdraw(int amount) {
                if (balance >= amount) {
                    Thread.yield();
                    balance = balance - amount;
                    return true;
                }
                return false;
            }

            @Override public synchronized int balance() { return balance; }
            @Override public String name() { return "synchronized"; }
        };
    }
}
