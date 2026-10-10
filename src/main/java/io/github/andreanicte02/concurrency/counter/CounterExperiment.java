package io.github.andreanicte02.concurrency.counter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Runs N threads that each increment a shared {@link Counter} M times, then compares with N×M. */
public final class CounterExperiment {

    public record Result(String counter, int threads, int incrementsPerThread, long expected, long actual, long millis) {
        public long lost() { return expected - actual; }
        public boolean correct() { return lost() == 0; }
    }

    private CounterExperiment() {}

    public static Result run(Counter counter, int threads, int incrementsPerThread) throws InterruptedException {
        // A start gate makes every thread begin at the same moment, maximizing contention.
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < incrementsPerThread; i++) {
                    counter.increment();
                }
            }, "worker-" + t);
            workers.add(worker);
            worker.start();
        }

        long began = System.nanoTime();
        start.countDown();
        for (Thread worker : workers) {
            worker.join(); // wait for every worker before reading the total
        }
        long millis = (System.nanoTime() - began) / 1_000_000;

        long expected = (long) threads * incrementsPerThread;
        return new Result(counter.name(), threads, incrementsPerThread, expected, counter.get(), millis);
    }
}
