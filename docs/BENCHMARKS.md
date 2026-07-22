# Performance benchmarks

LimitForge includes a dependency-free benchmark for repeatable, end-to-end
measurement of the matching path.

## Workload

- A fresh engine is created for every iteration.
- An even number of balanced, crossing limit orders is generated before timing.
- Orders alternate sell/buy at one price and produce exactly one execution per pair.
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

A local reference run on 2026-07-22 produced the following result:

| Environment | Workload | Throughput | Batch p50 | Batch p95/p99 |
| --- | ---: | ---: | ---: | ---: |
| Java 17.0.14, macOS arm64, 12 available processors | 20,000 orders × 7 iterations | 822,392 orders/s | 24.872 ms | 28.294 ms |

This number is a reproducibility aid, not a production capacity claim. It was
measured in a local development environment, without network transport,
persistence, FIX parsing, or concurrent external clients.
