package io.github.andreanicte02.concurrency;

import io.github.andreanicte02.concurrency.bank.Account;
import io.github.andreanicte02.concurrency.bank.BankExperiment;
import io.github.andreanicte02.concurrency.barber.BarberShop;
import io.github.andreanicte02.concurrency.counter.Counter;
import io.github.andreanicte02.concurrency.counter.CounterExperiment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Command-line entry point: {@code java -jar concurrency-lab.jar <experiment> [--key value ...] [--json]}. */
public final class Main {

    private static final String USAGE = """
            concurrency-lab - classic Java concurrency bugs, reproduced and fixed

            Usage:
              java -jar concurrency-lab.jar <experiment> [options] [--json]

            Experiments:
              counter   N threads increment one shared counter     --threads 4 --increments 100000 --trials 3
              bank      customers withdraw from one account        --customers 8 --balance 1000 --amount 10
              barber    the sleeping barber problem                --customers 12 --chairs 2 --haircut-ms 200
              all       run everything (used to build the website)

            --json prints machine-readable output instead of a table.
            """;

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help") || args[0].equals("help")) {
            System.out.print(USAGE);
            return;
        }
        Options opts;
        try {
            opts = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
            return;
        }

        switch (opts.command) {
            case "counter" -> print(opts, counter(opts), Main::printCounter);
            case "bank" -> print(opts, bank(opts), Main::printBank);
            case "barber" -> print(opts, barber(opts), Main::printBarber);
            case "all" -> {
                var counter = counter(opts);
                var bank = bank(opts);
                var barber = barber(opts);
                if (opts.json) {
                    Map<String, Object> report = new LinkedHashMap<>();
                    report.put("generatedAt", Instant.now().toString());
                    report.put("environment", Map.of(
                            "java", Runtime.version().toString(),
                            "os", System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                            "cpus", Runtime.getRuntime().availableProcessors()));
                    report.put("counter", counter);
                    report.put("bank", bank);
                    report.put("barber", barber);
                    System.out.println(Json.write(report));
                } else {
                    printCounter(counter);
                    printBank(bank);
                    printBarber(barber);
                }
            }
            default -> {
                System.err.println("error: unknown experiment '" + opts.command + "' (run with --help)");
                System.exit(2);
            }
        }
    }

    // --- experiments ---------------------------------------------------------------------------

    private static List<CounterExperiment.Result> counter(Options o) throws InterruptedException {
        int threads = o.integer("threads", 4);
        int increments = o.integer("increments", 100_000);
        int trials = o.integer("trials", 3);
        List<Supplier<Counter>> counters = List.of(
                Counter::unsafe, Counter::synchronizedCounter, Counter::lock, Counter::atomic);

        List<CounterExperiment.Result> results = new ArrayList<>();
        for (Supplier<Counter> counter : counters) {
            for (int t = 0; t < trials; t++) {
                results.add(CounterExperiment.run(counter.get(), threads, increments));
            }
        }
        return results;
    }

    private static List<BankExperiment.Result> bank(Options o) throws InterruptedException {
        int customers = o.integer("customers", 8);
        int balance = o.integer("balance", 1_000);
        int amount = o.integer("amount", 10);
        int trials = o.integer("trials", 3);
        // Every customer tries to take the whole balance, so the account is always drained.
        int attempts = balance / amount;

        List<BankExperiment.Result> results = new ArrayList<>();
        for (int t = 0; t < trials; t++) {
            results.add(BankExperiment.run(Account::unsafe, balance, customers, attempts, amount));
        }
        for (int t = 0; t < trials; t++) {
            results.add(BankExperiment.run(Account::synchronizedAccount, balance, customers, attempts, amount));
        }
        return results;
    }

    private static BarberShop.Result barber(Options o) throws InterruptedException {
        BarberShop.Config d = BarberShop.Config.defaults();
        return BarberShop.simulate(new BarberShop.Config(
                o.integer("customers", d.customers()),
                o.integer("chairs", d.chairs()),
                o.integer("haircut-ms", d.haircutMillis()),
                o.integer("arrival-ms", d.meanArrivalMillis()),
                o.integer("seed", (int) d.seed())));
    }

    // --- output --------------------------------------------------------------------------------

    private interface Printer<T> { void print(T value); }

    private static <T> void print(Options o, T value, Printer<T> table) {
        if (o.json) System.out.println(Json.write(value));
        else table.print(value);
    }

    private static void printCounter(List<CounterExperiment.Result> results) {
        CounterExperiment.Result first = results.getFirst();
        System.out.printf("%nCOUNTER  %d threads x %,d increments = %,d expected%n%n",
                first.threads(), first.incrementsPerThread(), first.expected());
        System.out.printf("  %-16s %12s %12s %8s%n", "implementation", "actual", "lost", "time");
        for (CounterExperiment.Result r : results) {
            System.out.printf("  %-16s %,12d %,12d %6dms  %s%n",
                    r.counter(), r.actual(), r.lost(), r.millis(), r.correct() ? "ok" : "RACE");
        }
    }

    private static void printBank(List<BankExperiment.Result> results) {
        BankExperiment.Result first = results.getFirst();
        System.out.printf("%nBANK  %d customers withdrawing %d at a time from a balance of %,d%n%n",
                first.customers(), first.amount(), first.initialBalance());
        System.out.printf("  %-14s %12s %14s%n", "account", "handed out", "final balance");
        for (BankExperiment.Result r : results) {
            System.out.printf("  %-14s %,12d %,14d  %s%n", r.account(), r.withdrawn(), r.finalBalance(),
                    r.correct() ? "ok" : r.overdrawn() ? "OVERDRAWN" : "LOST UPDATE");
        }
    }

    private static void printBarber(BarberShop.Result r) {
        System.out.printf("%nBARBER  %d customers, %d waiting chairs, %dms per haircut%n%n",
                r.config().customers(), r.config().chairs(), r.config().haircutMillis());
        for (BarberShop.Event e : r.events()) {
            String who = e.customer() < 0 ? "barber" : "customer " + e.customer();
            System.out.printf("  %5dms  %-12s %-11s waiting=%d%n", e.millis(), who, e.type(), e.waiting());
        }
        System.out.printf("%n  served %d, turned away %d%n", r.served(), r.turnedAway());
    }

    // --- argument parsing ----------------------------------------------------------------------

    private record Options(String command, Map<String, String> values, boolean json) {
        static Options parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            boolean json = false;
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--json")) {
                    json = true;
                } else if (arg.startsWith("--") && i + 1 < args.length) {
                    values.put(arg.substring(2), args[++i]);
                } else {
                    throw new IllegalArgumentException("unexpected argument '" + arg + "'");
                }
            }
            return new Options(args[0], values, json);
        }

        int integer(String key, int fallback) {
            String v = values.get(key);
            if (v == null) return fallback;
            try {
                int n = Integer.parseInt(v.replace("_", ""));
                if (n < 0) throw new NumberFormatException();
                return n;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + key + " must be a non-negative integer, got '" + v + "'");
            }
        }
    }
}
