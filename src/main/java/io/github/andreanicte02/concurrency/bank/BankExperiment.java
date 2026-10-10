package io.github.andreanicte02.concurrency.bank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/** Several customers hit the same account from different ATMs until the money runs out. */
public final class BankExperiment {

    public record Result(String account, int customers, int initialBalance, int amount,
                         int successfulWithdrawals, int finalBalance) {
        /** Money handed out across all ATMs. */
        public int withdrawn() { return successfulWithdrawals * amount; }
        /** The bank paid out more than the account ever held. */
        public boolean overdrawn() { return withdrawn() > initialBalance; }
        /** The final balance does not match what was handed out (lost updates). */
        public boolean inconsistent() { return initialBalance - withdrawn() != finalBalance; }
        public boolean correct() { return !overdrawn() && !inconsistent(); }
    }

    private BankExperiment() {}

    public static Result run(IntFunction<Account> accountFactory, int initialBalance, int customers,
                             int attemptsPerCustomer, int amount) throws InterruptedException {
        Account account = accountFactory.apply(initialBalance);
        AtomicInteger successes = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> atms = new ArrayList<>();

        for (int c = 0; c < customers; c++) {
            Thread atm = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < attemptsPerCustomer; i++) {
                    if (account.withdraw(amount)) {
                        successes.incrementAndGet();
                    }
                }
            }, "customer-" + c);
            atms.add(atm);
            atm.start();
        }

        start.countDown();
        for (Thread atm : atms) {
            atm.join();
        }
        return new Result(account.name(), customers, initialBalance, amount, successes.get(), account.balance());
    }
}
