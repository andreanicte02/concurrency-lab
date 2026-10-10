package io.github.andreanicte02.concurrency.counter;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CounterExperimentTest {

    static Stream<Supplier<Counter>> threadSafeCounters() {
        return Stream.of(Counter::synchronizedCounter, Counter::lock, Counter::atomic);
    }

    @ParameterizedTest
    @MethodSource("threadSafeCounters")
    void threadSafeCountersNeverLoseUpdates(Supplier<Counter> counter) throws InterruptedException {
        for (int trial = 0; trial < 5; trial++) {
            CounterExperiment.Result r = CounterExperiment.run(counter.get(), 8, 50_000);
            assertEquals(400_000, r.actual(), r.counter() + " lost updates");
        }
    }

    @RepeatedTest(5)
    void unsafeCounterNeverOvercounts() throws InterruptedException {
        // A race can only lose increments, never invent them. Whether it loses any depends on
        // scheduling, so this test does not assert that it fails.
        CounterExperiment.Result r = CounterExperiment.run(Counter.unsafe(), 8, 50_000);
        assertTrue(r.actual() <= r.expected(), "counted more than possible: " + r);
    }

    @Test
    void singleThreadIsAlwaysExact() throws InterruptedException {
        assertEquals(100_000, CounterExperiment.run(Counter.unsafe(), 1, 100_000).actual());
    }
}
