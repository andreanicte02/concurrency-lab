package io.github.andreanicte02.concurrency.barber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The sleeping barber problem: one barber, one barber chair and a waiting room with a limited
 * number of chairs. Customers who find the waiting room full leave.
 *
 * <p>The waiting room is a bounded {@link BlockingQueue}, which removes the bugs of the naive
 * version: the barber sleeps by blocking on {@link BlockingQueue#take()} (no busy-waiting),
 * a customer cannot slip in between "barber checks the room" and "barber falls asleep" (no lost
 * wake-ups), and {@link BlockingQueue#offer} atomically answers "is there a free chair?".
 */
public final class BarberShop {

    public enum EventType { ARRIVE, SIT, LEAVE_FULL, SLEEP, WAKE, START_CUT, FINISH_CUT, CLOSE }

    /** {@code customer} is -1 for barber-only events; {@code waiting} is the queue size right after the event. */
    public record Event(long millis, EventType type, int customer, int waiting) {}

    public record Config(int customers, int chairs, int haircutMillis, int meanArrivalMillis, long seed) {
        public static Config defaults() {
            return new Config(12, 2, 200, 180, 5);
        }
    }

    public record Result(Config config, int served, int turnedAway, List<Event> events) {}

    private static final int CLOSED = -1; // poison pill telling the barber to go home

    private final Config config;
    private final BlockingQueue<Integer> waitingRoom;
    private final List<Event> events = Collections.synchronizedList(new ArrayList<>());
    private long startNanos;

    private BarberShop(Config config) {
        this.config = config;
        this.waitingRoom = new ArrayBlockingQueue<>(config.chairs());
    }

    public static Result simulate(Config config) throws InterruptedException {
        return new BarberShop(config).run();
    }

    private Result run() throws InterruptedException {
        startNanos = System.nanoTime();
        Thread barber = Thread.ofPlatform().name("barber").start(this::barberLoop);

        // Arrival times come from a seeded RNG so runs are comparable; scheduling is still real.
        Random rng = new Random(config.seed());
        List<Thread> customers = new ArrayList<>();
        for (int id = 1; id <= config.customers(); id++) {
            int customerId = id;
            customers.add(Thread.ofVirtual().name("customer-" + id).start(() -> customerArrives(customerId)));
            long gap = (long) (-Math.log(1 - rng.nextDouble()) * config.meanArrivalMillis());
            TimeUnit.MILLISECONDS.sleep(gap);
        }
        for (Thread customer : customers) {
            customer.join();
        }
        waitingRoom.put(CLOSED); // queued behind anyone still waiting, so they all get served
        barber.join();

        List<Event> log = List.copyOf(events);
        int served = (int) log.stream().filter(e -> e.type() == EventType.FINISH_CUT).count();
        int turnedAway = (int) log.stream().filter(e -> e.type() == EventType.LEAVE_FULL).count();
        return new Result(config, served, turnedAway, log);
    }

    private void customerArrives(int id) {
        record(EventType.ARRIVE, id);
        if (waitingRoom.offer(id)) {
            record(EventType.SIT, id);
        } else {
            record(EventType.LEAVE_FULL, id);
        }
    }

    private void barberLoop() {
        try {
            while (true) {
                Integer next = waitingRoom.poll();
                if (next == null) {
                    record(EventType.SLEEP, -1);
                    next = waitingRoom.take(); // blocks without burning CPU until someone sits down
                    record(EventType.WAKE, -1);
                }
                if (next == CLOSED) {
                    record(EventType.CLOSE, -1);
                    return;
                }
                record(EventType.START_CUT, next);
                TimeUnit.MILLISECONDS.sleep(config.haircutMillis());
                record(EventType.FINISH_CUT, next);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void record(EventType type, int customer) {
        // Size is read outside the queue's lock, so it is a snapshot; good enough for a timeline.
        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        events.add(new Event(millis, type, customer, waitingRoom.size()));
    }
}
