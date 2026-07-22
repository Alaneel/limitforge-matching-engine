# LimitForge - Java Implementation

A Java reference implementation of auction and continuous order matching with CSV workflows and an experimental FIX adapter.

## Features

- **Order Matching Engine**: Morning auction, continuous trading, and evening auction phases
- **FIX Adapter**: New Order Single conversion and acceptance/rejection reports using QuickFIX/J
- **Concurrency**: Thread-safe operations using Java's concurrent collections and ExecutorService
- **CSV Processing**: Read orders from CSV files and generate comprehensive reports
- **Position Management**: Real-time position tracking with position check validation
- **Logging**: Comprehensive logging using SLF4J and Logback
- **Simulation API**: Isolated batch simulations over a local HTTP interface

## Architecture

For component boundaries, order lifecycle, trading phases, invariants, and
explicit limitations, see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### Package Structure

```
com.trading
├── model/              # Domain models (Client, Instrument, Order, Transaction)
├── csv/                # CSV reading and writing utilities
├── engine/             # Order matching engine with concurrency
├── fix/                # FIX protocol server and message handlers
├── api/                # HTTP simulation adapter and OpenAPI contract
└── TradingApplication  # Main application class
```

### Concurrency Features

1. **ConcurrentHashMap**: Thread-safe client and instrument lookups
2. **PriorityBlockingQueue**: Thread-safe order books with priority ordering
3. **ExecutorService**: Parallel processing of best price calculations
4. **ReentrantReadWriteLock**: Coordinated access to transaction recording
5. **Atomic Operations**: Lock-free order quantity management

## Requirements

- Java 17 or higher
- Maven 3.6 or higher

## Building the Project

```bash
# Compile the project
mvn clean compile

# Run tests
mvn test

# Create executable JAR with dependencies
mvn clean package
```

This will create `target/limitforge-engine-1.0.0.jar` with all dependencies included.

## Running the Application

### CSV Mode (Default)

Process orders from CSV files:

```bash
java -jar target/limitforge-engine-1.0.0.jar
```

Or using Maven:

```bash
mvn exec:java -Dexec.mainClass="com.trading.TradingApplication"
```

### FIX Protocol Mode

Enable FIX protocol server:

```bash
java -Dfix.enabled=true -jar target/limitforge-engine-1.0.0.jar
```

The FIX server will listen on port 9876 (configurable in `fix-server.cfg`).

### HTTP Simulation API

Run an isolated batch simulation service:

```bash
./scripts/run-api.sh
```

See [`docs/API.md`](docs/API.md) for the OpenAPI endpoint, example request,
limits, and non-production security boundary.

## Input Files

The system expects the following CSV files in the project root:

### input_clients.csv
```csv
ClientID,Currencies,PositionCheck,Rating
A,"USD,SGD",Y,1
B,"USD,SGD,JPY",N,2
```

### input_instruments.csv
```csv
InstrumentID,Currency,LotSize
SIA,SGD,100
AAPL,USD,1
```

### input_orders.csv
```csv
Time,OrderID,Instrument,Quantity,Client,Price,Side
9:00:01,A1,SIA,1500,A,Market,Buy
9:02:00,B1,SIA,4500,B,32.1,Sell
```

## Output Files

The system generates four reports:

1. **output_exchange_report.csv**: Rejected orders with reasons
2. **output_client_report.csv**: Final positions for each client
3. **output_instrument_report.csv**: Trading statistics (VWAP, high/low, volume)
4. **output_session.json**: Deterministic machine-readable session snapshot

## FIX Protocol Integration

### Configuration

Edit `src/main/resources/fix-server.cfg` to customize:

- Port number (default: 9876)
- Session identifiers
- Heartbeat interval
- Log file locations

### FIX Message Format

Send New Order Single (35=D) messages:

```
8=FIX.4.4|9=XXX|35=D|49=CLIENT|56=LIMITFORGE|34=1|52=20250101-12:00:00|
11=ORDER123|55=SIA|54=1|38=100|40=2|44=32.5|1=CLIENT_A|10=XXX|
```

Fields:
- **11 (ClOrdID)**: Order ID
- **55 (Symbol)**: Instrument ID
- **54 (Side)**: 1=Buy, 2=Sell
- **38 (OrderQty)**: Quantity
- **40 (OrdType)**: 1=Market, 2=Limit
- **44 (Price)**: Limit price (for limit orders)
- **1 (Account)**: Client ID

### Testing FIX Connection

You can test the FIX server using QuickFIX/J executor or any FIX client:

```bash
# Start server with FIX enabled
java -Dfix.enabled=true -jar target/limitforge-engine-1.0.0.jar
```

## Trading Phases

### 1. Morning Auction (Before 9:30)
- Collects all orders
- Calculates optimal clearing price
- Executes all matching orders at clearing price
- Records opening price

### 2. Real-Time Trading (9:30 - 16:00)
- Continuous matching of orders
- Price-time priority
- Immediate execution when buy price ≥ sell price

### 3. Evening Auction (16:00 - 16:10)
- Collects remaining orders
- Calculates closing price
- Executes final trades
- Records closing price

## Order Validation

Orders are rejected if:
- **Instrument not found**: Invalid instrument ID
- **Currency mismatch**: Client cannot trade in instrument's currency
- **Invalid lot size**: Quantity not a multiple of instrument's lot size
- **Position check failed**: Sell order exceeds client's holdings (when position check is enabled)

## Concurrency Design

### Thread Safety

1. **Order Books**: Each instrument has separate order books with blocking queues
2. **Position Updates**: Synchronized updates to client positions
3. **Transaction Recording**: Write-locked to prevent race conditions
4. **Best Price Calculation**: Parallel execution per instrument using thread pool

Auction clearing-price calculations are parallelized across instruments. The
continuous matching path remains coordinated within one engine process; see
`docs/ARCHITECTURE.md` for the exact concurrency boundaries.

## Logging

Logs are written to:
- **Console**: INFO level and above
- **File**: `logs/trading-system.log` with daily rotation

Configure logging in `src/main/resources/logback.xml`.

## Development

### Project Structure

```
.
├── pom.xml                          # Maven configuration
├── src/
│   ├── main/
│   │   ├── java/com/trading/
│   │   │   ├── model/               # Domain models
│   │   │   ├── csv/                 # CSV utilities
│   │   │   ├── engine/              # Matching engine
│   │   │   ├── fix/                 # FIX protocol
│   │   │   └── TradingApplication.java
│   │   └── resources/
│   │       ├── logback.xml          # Logging configuration
│   │       └── fix-server.cfg       # FIX server configuration
│   └── test/
│       └── java/com/trading/        # Unit tests
├── input_*.csv                      # Input data files
└── output_*.csv                     # Generated reports
```

### Adding New Features

1. **Custom Order Types**: Extend `Order` class and update matching logic
2. **Additional Reports**: Add methods to `CSVWriter` class
3. **FIX Message Types**: Add handlers in `FIXMessageHandler`
4. **Trading Rules**: Modify `OrderMatchingEngine` matching algorithms

## Troubleshooting

### Issue: Port already in use
**Solution**: Change `SocketAcceptPort` in `fix-server.cfg`

### Issue: Out of memory
**Solution**: Increase JVM heap: `java -Xmx2g -jar ...`

### Issue: FIX connection timeout
**Solution**: Increase `maxWaitSeconds` in `TradingApplication.waitForFIXOrders()`

## Performance Benchmarks

See `docs/BENCHMARKS.md` for the reproducible workload, current reference
result, environment metadata, and explicit limitations.

## License

See LICENSE file for details.

## Differences from C++ Version

1. **Thread Safety**: Built-in concurrent collections vs manual locking
2. **Memory Management**: Automatic garbage collection vs manual memory management
3. **FIX Protocol**: QuickFIX/J library vs custom implementation
4. **Type Safety**: Compile-time type checking with generics
5. **Error Handling**: Exception-based vs return codes
6. **Performance**: Measured through the repository's reproducible benchmark harness

## Future Enhancements

- [ ] Add REST API for order submission
- [ ] Implement WebSocket for real-time updates
- [ ] Add database persistence for audit trail
- [ ] Support for more order types (stop-loss, iceberg, etc.)
- [ ] Market data distribution
- [ ] Risk management rules
- [ ] Multi-currency settlement
