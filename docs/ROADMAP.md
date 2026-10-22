# Roadmap

LimitForge is being developed as an inspectable open-source exchange simulator
and portfolio project. The priority is reproducible behavior, clear boundaries,
and an easy path from clone to a working example. Milestones below describe an
implementation sequence, not committed dates or a production trading service.

## Current foundation

- Java matching engine with opening and closing auctions, continuous matching,
  price-time priority, validation, and position checks.
- CSV examples and reports, plus deterministic JSON session export.
- Dashboard for exploring the exported session, executions, and positions.
- HTTP API for isolated batch simulations with an OpenAPI contract.
- Reproducible batch benchmark and a hardened local container configuration.
- CI checks for Java, dashboard, fixture generation, benchmark, and container.

The original engine is EricFan2002's MIT-licensed work. LimitForge's extension
history and attribution are recorded in the [README](../README.md#attribution)
and [license](../LICENSE).

## 1. Reliable first release

Make the existing simulator easy for another developer to assess and run.

- [x] Expand regression coverage for phase boundaries, partial fills, market
  orders, auction ties, checked sell quantities, and invalid requests.
- [x] Verify request isolation and consistent API error responses.
- [x] Package a runnable Java artifact with sample inputs, documentation,
  license notices, and checksums.
- [x] Automate release verification and document the versioning process.
- [ ] Publish release notes that state the supported behavior and limitations.

Completion means a clean checkout passes CI, a release artifact runs the sample
without a development toolchain, and the documented API example produces the
expected execution and positions. Existing benchmark numbers remain tied to
their workload and environment.

## 2. Interactive batch simulation

Connect the dashboard to user-controlled simulation input while keeping the
batch contract explicit.

- [ ] Add editable synthetic scenarios and validation feedback in the UI.
- [ ] Run scenarios through the simulation API and render returned results.
- [ ] Make empty, invalid, loading, and failed-request states understandable.
- [ ] Export and re-import scenarios so demonstrations can be reproduced.

Completion means a user can change orders, run a complete session, and explain
the resulting trades and rejections from the displayed data. The existing
exported sample remains available for a static demonstration.

## 3. Stateful command and event boundary

Define order lifecycle semantics before exposing a live matching interface.

- [ ] Specify command ordering, session/phase transitions, idempotency, and
  single-writer ownership of each book.
- [ ] Introduce incremental submit/cancel commands and explicit order and trade
  events with deterministic sequence numbers.
- [ ] Test a command stream against a reference model, including rejected and
  duplicate commands.
- [ ] Add live UI updates and a complete FIX report lifecycle against the same
  event boundary.

Completion means replaying the same command stream produces the same events
and final state. Thread-safe collections alone are not the acceptance criterion
for concurrent correctness.

## 4. Recovery and measured performance

Build on the deterministic event model once its behavior is stable.

- [ ] Add a durable event journal, snapshots, and restart/replay verification.
- [ ] Test interrupted writes and recovery consistency.
- [ ] Measure throughput and latency with declared workloads, warmup,
  percentiles, environment, and correctness checks.
- [ ] Evaluate multi-instrument parallelism using evidence from those tests.

Exchange certification, real-money trading, brokerage integration, and a
commercial hosted service are outside the current roadmap. Authentication and
public deployment controls require a separate design before any shared service
is exposed.
