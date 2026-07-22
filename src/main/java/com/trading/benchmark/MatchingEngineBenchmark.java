package com.trading.benchmark;

import com.trading.engine.OrderMatchingEngine;
import com.trading.model.Client;
import com.trading.model.Instrument;
import com.trading.model.Order;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dependency-free, reproducible end-to-end throughput benchmark.
 *
 * <p>This is deliberately not a microbenchmark. It measures one complete call to
 * {@link OrderMatchingEngine#processOrders(List)} with a fresh engine and a deterministic,
 * balanced stream of crossing limit orders.</p>
 */
public final class MatchingEngineBenchmark {
    private static final String INSTRUMENT_ID = "BENCH";
    private static final BigDecimal PRICE = new BigDecimal("100.00");
    private static final LocalTime ORDER_TIME = LocalTime.of(10, 0);

    private MatchingEngineBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        Result result = run(config);
        String json = result.toJson();
        System.out.println(result.summary());
        System.out.println(json);
        if (config.output() != null) {
            write(config.output(), json);
            System.out.println("Result written to " + config.output());
        }
    }

    public static Result run(Config config) {
        if (config.orders() < 2 || config.orders() % 2 != 0) {
            throw new IllegalArgumentException("--orders must be an even number greater than zero");
        }
        if (config.warmup() < 0 || config.iterations() < 1) {
            throw new IllegalArgumentException("warmup must be >= 0 and iterations must be >= 1");
        }

        for (int iteration = 0; iteration < config.warmup(); iteration++) {
            measure(config.orders(), "warmup-" + iteration);
        }

        long[] durations = new long[config.iterations()];
        for (int iteration = 0; iteration < config.iterations(); iteration++) {
            durations[iteration] = measure(config.orders(), "measure-" + iteration);
        }

        long[] sorted = durations.clone();
        Arrays.sort(sorted);
        long totalNanos = Arrays.stream(durations).sum();
        double throughput = (double) config.orders() * config.iterations()
            / (totalNanos / 1_000_000_000.0);

        return new Result(
            config,
            throughput,
            percentile(sorted, 0.50),
            percentile(sorted, 0.95),
            percentile(sorted, 0.99),
            System.getProperty("java.version"),
            System.getProperty("os.name") + " " + System.getProperty("os.arch"),
            Runtime.getRuntime().availableProcessors(),
            Instant.now().toString()
        );
    }

    private static long measure(int orderCount, String runId) {
        Client buyer = new Client("BUYER", Set.of("USD"), false, 1);
        Client seller = new Client("SELLER", Set.of("USD"), false, 1);
        Instrument instrument = new Instrument(INSTRUMENT_ID, "USD", 1);
        OrderMatchingEngine engine = new OrderMatchingEngine(
            Map.of(buyer.getClientId(), buyer, seller.getClientId(), seller),
            Map.of(instrument.getInstrumentId(), instrument)
        );
        List<Order> orders = createOrders(orderCount, runId);

        long start = System.nanoTime();
        engine.processOrders(orders);
        long duration = System.nanoTime() - start;

        int expectedExecutions = orderCount / 2;
        if (engine.getTransactions().size() != expectedExecutions || !engine.getRejections().isEmpty()) {
            engine.shutdown();
            throw new IllegalStateException("Benchmark workload did not produce the expected result");
        }
        engine.shutdown();
        return duration;
    }

    private static List<Order> createOrders(int orderCount, String runId) {
        List<Order> orders = new ArrayList<>(orderCount);
        for (int index = 0; index < orderCount / 2; index++) {
            orders.add(Order.limit(
                runId + "-S-" + index,
                "SELLER",
                INSTRUMENT_ID,
                1,
                PRICE,
                Order.Side.SELL,
                ORDER_TIME
            ));
            orders.add(Order.limit(
                runId + "-B-" + index,
                "BUYER",
                INSTRUMENT_ID,
                1,
                PRICE,
                Order.Side.BUY,
                ORDER_TIME
            ));
        }
        return orders;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    private static void write(Path output, String value) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(output, value + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public record Config(int orders, int warmup, int iterations, Path output) {
        static Config parse(String[] args) {
            int orders = 20_000;
            int warmup = 3;
            int iterations = 7;
            Path output = null;
            for (int index = 0; index < args.length; index++) {
                switch (args[index]) {
                    case "--orders" -> orders = Integer.parseInt(next(args, ++index, "--orders"));
                    case "--warmup" -> warmup = Integer.parseInt(next(args, ++index, "--warmup"));
                    case "--iterations" -> iterations = Integer.parseInt(next(args, ++index, "--iterations"));
                    case "--output" -> output = Path.of(next(args, ++index, "--output"));
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
                }
            }
            return new Config(orders, warmup, iterations, output);
        }

        private static String next(String[] args, int index, String flag) {
            if (index >= args.length) throw new IllegalArgumentException("Missing value for " + flag);
            return args[index];
        }
    }

    public record Result(
        Config config,
        double ordersPerSecond,
        long p50BatchNanos,
        long p95BatchNanos,
        long p99BatchNanos,
        String javaVersion,
        String platform,
        int processors,
        String measuredAt
    ) {
        String summary() {
            return String.format(
                Locale.ROOT,
                "%,.0f orders/s | batch p50 %.2f ms | p95 %.2f ms | p99 %.2f ms",
                ordersPerSecond,
                millis(p50BatchNanos),
                millis(p95BatchNanos),
                millis(p99BatchNanos)
            );
        }

        String toJson() {
            return String.format(
                Locale.ROOT,
                """
                {
                  "benchmark": "end-to-end-crossing-limit-orders",
                  "ordersPerIteration": %d,
                  "warmupIterations": %d,
                  "measuredIterations": %d,
                  "ordersPerSecond": %.2f,
                  "batchDurationMs": { "p50": %.3f, "p95": %.3f, "p99": %.3f },
                  "environment": {
                    "java": "%s",
                    "platform": "%s",
                    "availableProcessors": %d
                  },
                  "measuredAt": "%s"
                }""",
                config.orders(),
                config.warmup(),
                config.iterations(),
                ordersPerSecond,
                millis(p50BatchNanos),
                millis(p95BatchNanos),
                millis(p99BatchNanos),
                escape(javaVersion),
                escape(platform),
                processors,
                escape(measuredAt)
            );
        }

        private static double millis(long nanos) {
            return nanos / 1_000_000.0;
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
