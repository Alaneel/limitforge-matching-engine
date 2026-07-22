# Architecture

LimitForge is a single-process educational exchange simulator. Its core is a
Java matching engine; CSV and FIX are input adapters, CSV/JSON reports are
output adapters, and the web dashboard renders a committed engine-generated
session snapshot.

## System context

```mermaid
flowchart LR
    CSV["CSV input files"] --> APP["TradingApplication"]
    FIX["FIX 4.4 client"] --> ADAPTER["QuickFIX/J adapter"]
    ADAPTER --> APP
    APP --> ENGINE["OrderMatchingEngine"]
    ENGINE --> BOOKS["Per-instrument order books"]
    ENGINE --> RISK["Validation and position checks"]
    ENGINE --> TRADES["Transactions and positions"]
    TRADES --> REPORTS["CSV reports"]
    TRADES --> SNAPSHOT["Deterministic session.json"]
    SNAPSHOT --> UI["Interactive dashboard"]
```

The matching engine does not depend on CSV, FIX, or the dashboard. Adapters
translate external data into domain objects and read results through explicit
engine accessors.

## Order lifecycle

```mermaid
sequenceDiagram
    participant Input as CSV or FIX adapter
    participant Engine as Matching engine
    participant Risk as Validation and position checks
    participant Book as Order book
    participant Ledger as Transaction ledger

    Input->>Engine: Submit market or limit order
    Engine->>Risk: Validate ID, instrument, client, currency, lot size
    alt invalid order
        Risk-->>Engine: Rejection reason
        Engine-->>Input: Rejection recorded
    else valid order
        Risk->>Risk: Reserve checked sell quantity
        Engine->>Book: Route by instrument, side, and phase
        alt prices cross
            Book->>Ledger: Execute at auction or resting-order price
            Ledger->>Risk: Update positions and release reservation
        else no match
            Book-->>Book: Rest with price-time priority
        end
    end
```

An accepted order moves from `NEW` to `PARTIALLY_FILLED` or `FILLED` as its
remaining quantity changes. Invalid or risk-failing orders become `REJECTED`.

## Trading phases

```mermaid
flowchart TD
    INPUT["Validated orders"] --> ROUTE{"Order time"}
    ROUTE -->|"before 09:30"| OPEN["Opening auction"]
    ROUTE -->|"09:30–16:00"| CONT["Continuous matching"]
    ROUTE -->|"16:00–16:10"| CLOSE["Closing auction"]
    OPEN --> CP1["Maximize executable volume"]
    CP1 --> CP2["Minimize imbalance"]
    CP2 --> CP3["Minimize reference-price distance"]
    CP3 --> EXEC1["Execute at one clearing price"]
    EXEC1 --> CONT
    CONT --> PT["Market priority, then price-time priority"]
    PT --> CLOSE
    CLOSE --> EXEC2["Calculate and execute closing price"]
```

Auction tie-breaking is deterministic: executable volume, imbalance,
reference-price distance, then the higher price. Continuous limit-limit trades
execute at the older resting order's price; market orders execute against the
available opposing limit price.

## Core invariants

- Order IDs are unique for the lifetime of an engine instance.
- Limit prices use `BigDecimal`; market orders carry an explicit order type.
- Quantities must be positive and conform to the instrument lot size.
- Price priority precedes time priority; order ID is the deterministic final tie-breaker.
- Position-checked sell orders reserve available quantity before entering a book.
- Each execution updates both counterparties and the transaction ledger under one write lock.
- Reports and the dashboard fixture are deterministic for a fixed input set.

## Concurrency model

- Per-instrument books use `PriorityBlockingQueue` for thread-safe queue access.
- Client, instrument, price, and reservation indexes use concurrent maps.
- Remaining order quantity uses `AtomicInteger`.
- Transaction and position mutation is coordinated with a write lock and synchronized client updates.
- Auction clearing-price calculations can run in parallel across instruments.

The public application currently submits a batch into one engine instance. The
concurrent data structures protect shared state and prepare the design for
multiple instruments, but this is not yet a horizontally scalable or
distributed matching architecture.

## Repository map

| Area | Responsibility |
| --- | --- |
| `model` | Orders, clients, instruments, and transactions |
| `engine` | Validation, auctions, continuous matching, positions |
| `csv` | Deterministic input and report adapters |
| `fix` | QuickFIX/J session adapter and New Order Single conversion |
| `report` | Machine-readable dashboard snapshot |
| `benchmark` | Reproducible end-to-end batch benchmark |
| `ui` | Read-only visualization and execution replay |

## Deliberate limitations

- No database, durable event log, recovery journal, or replication.
- No REST/WebSocket order-entry or live market-data API.
- FIX support is an experimental adapter for New Order Single messages with
  acceptance/rejection reports; it is not a complete exchange FIX gateway.
- The dashboard consumes a committed sample snapshot, not a live engine process.
- The benchmark excludes transport, persistence, FIX parsing, and external concurrency.

These boundaries keep the project useful as a transparent reference
implementation. The next architectural step is an event/API boundary between
the engine and external clients, followed by persistence and recovery.
