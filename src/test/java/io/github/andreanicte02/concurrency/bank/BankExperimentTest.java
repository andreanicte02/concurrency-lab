package io.github.andreanicte02.concurrency.bank;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankExperimentTest {

    @RepeatedTest(10)
    void synchronizedAccountHandsOutExactlyTheBalance() throws InterruptedException {
        BankExperiment.Result r = BankExperiment.run(Account::synchronizedAccount, 1_000, 8, 100, 10);
        assertEquals(1_000, r.withdrawn());
        assertEquals(0, r.finalBalance());
        assertTrue(r.correct(), r.toString());
    }

    @Test
    void withdrawalIsRefusedWhenBalanceIsTooLow() {
        Account account = Account.synchronizedAccount(15);
        assertTrue(account.withdraw(10));
        assertFalse(account.withdraw(10));
        assertEquals(5, account.balance());
    }

    @Test
    void resultFlagsOverdraft() {
        var overdrawn = new BankExperiment.Result("x", 2, 100, 10, 12, -20);
        assertTrue(overdrawn.overdrawn());
        assertFalse(overdrawn.correct());

        var lostUpdate = new BankExperiment.Result("x", 2, 100, 10, 10, 30);
        assertFalse(lostUpdate.overdrawn());
        assertTrue(lostUpdate.inconsistent());
    }
}
