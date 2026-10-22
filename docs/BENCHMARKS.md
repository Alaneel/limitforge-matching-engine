# Performance benchmarks

LimitForge includes a dependency-free benchmark for repeatable, end-to-end
measurement of the matching path.

## Workload

- A fresh engine is created for every iteration.
- An even number of balanced, crossing limit orders is generated before timing.
- The input list alternates sell/buy at one price and produces one execution per
  pair. All orders have the same timestamp; the engine's time/ID ordering groups
  them by ID before processing. This is not an alternating live arrival stream.
- The timed region is one complete `processOrders` call.
- Order generation and engine shutdown are outside the timed region.
- Every run verifies the expected execution count and zero rejections.

This measures batch throughput and batch wall-clock duration. It is **not** a
claim about per-order latency, network latency, production capacity, or an SLA.

## Run it

```bash
./scripts/run-benchmark.sh
```

Defaults: 20,000 orders, 3 warmup iterations, and 7 measured iterations.

```bash
./scripts/run-benchmark.sh \
  --orders 100000 \
  --warmup 5 \
  --iterations 10 \
  --output benchmark-result.json
```

The output includes aggregate orders/second, p50/p95/p99 batch duration, Java
version, operating system, processor count, and measurement time. Always publish
the environment metadata alongside a result, and compare results only when the
workload and environment are equivalent.

## Reference run

A historical local reference run on 2026-07-22, before the chronological batch
ordering and 64-bit position fixes, produced the following result:

| Environment | Workload | Throughput | Batch p50 | Batch p95/p99 |
| --- | ---: | ---: | ---: | ---: |
| Java 17.0.14, macOS arm64, 12 available processors | 20,000 orders × 7 iterations | 822,392 orders/s | 24.872 ms | 28.294 ms |

This number is a reproducibility aid, not a production capacity claim. It was
measured in a local development environment, without network transport,
persistence, FIX parsing, or concurrent external clients.

The current timed path includes batch sorting. Do not present the historical
number as current performance or compare a run on an otherwise busy host as a
controlled regression measurement. Re-run the workload at the commit being
evaluated and retain its JSON output with the commit ID.
