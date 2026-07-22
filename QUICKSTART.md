# Quick Start Guide - LimitForge

## 🚀 Run the Application

### Basic Usage (CSV Mode)
```bash
# Build the project
mvn clean package

# Run the trading system
java -jar target/limitforge-engine-1.0.0.jar
```

### With FIX Protocol
```bash
# Enable FIX server on port 9876
java -Dfix.enabled=true -jar target/limitforge-engine-1.0.0.jar
```

### Local Simulation API

```bash
./scripts/run-api.sh
```

Submit `examples/simulation-request.json` as described in
[`docs/API.md`](docs/API.md).

## 📊 Expected Output

The system will:
1. Load clients, instruments, and orders from CSV files
2. Process morning auction (before 9:30)
3. Execute real-time trading (9:30 - 16:00)
4. Process evening auction (16:00 - 16:10)
5. Generate four report files

### Generated Reports
- `output_exchange_report.csv` - Rejected orders
- `output_client_report.csv` - Final client positions
- `output_instrument_report.csv` - Trading statistics
- `output_session.json` - Machine-readable session snapshot

## 🎯 Key Features Demonstrated

### Concurrency
- **Thread-safe order books** using `PriorityBlockingQueue`
- **Parallel price calculation** using `ExecutorService`
- **Atomic operations** for order quantity updates
- **Read-write locks** for transaction consistency

### FIX Protocol
- Experimental FIX 4.4 New Order Single adapter via QuickFIX/J
- Accept New Order Single messages
- Send immediate acceptance or conversion-level rejection reports
- Session management with heartbeats

### Order Matching
- Price-time priority
- Morning auction with optimal clearing price
- Real-time continuous matching
- Evening auction with a final clearing-price calculation

## 📈 Sample Run Results

```
Total transactions: 7
Total rejections: 3
Reports generated successfully
```

### Client Positions
| Client | Instrument | Position |
|--------|-----------|----------|
| A      | SIA       | +2300    |
| B      | SIA       | -5800    |
| C      | SIA       | +4300    |
| E      | SIA       | -800     |

### Instrument Statistics
- Open Price: 32.1
- Close Price: No closing execution in the sample
- Total Volume: 6800
- VWAP: 32.0721
- Day High: 32.2
- Day Low: 31.9

## 🔧 Configuration

### Logging
Edit `src/main/resources/logback.xml` to change log levels

### FIX Server
Edit `src/main/resources/fix-server.cfg` to:
- Change port (default: 9876)
- Modify session IDs
- Adjust heartbeat interval

## 🧪 Testing with Different Data

Replace the input CSV files:
- `input_clients.csv`
- `input_instruments.csv`
- `input_orders.csv`

Then run the application again.

## 📚 Architecture Highlights

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for component, lifecycle,
phase, and concurrency diagrams.

### Domain Models (src/main/java/com/trading/model/)
- `Client.java` - Client with currencies and positions
- `Instrument.java` - Tradable instrument
- `Order.java` - Order with atomic quantity updates
- `Transaction.java` - Completed trade

### CSV Utilities (src/main/java/com/trading/csv/)
- `CSVReader.java` - Parse input files
- `CSVWriter.java` - Generate reports

### Order Matching Engine (src/main/java/com/trading/engine/)
- `OrderMatchingEngine.java` - Core matching logic with concurrency

### FIX Protocol (src/main/java/com/trading/fix/)
- `FIXMessageHandler.java` - Handle FIX messages
- `FIXServer.java` - Accept FIX connections

## 🐛 Troubleshooting

**Problem**: Port 9876 already in use
**Solution**: Change `SocketAcceptPort` in fix-server.cfg

**Problem**: Out of memory
**Solution**: Run with more heap: `java -Xmx2g -jar ...`

**Problem**: CSV files not found
**Solution**: Ensure input_*.csv files are in project root

## 📦 Dependencies

- Java 17+
- QuickFIX/J 2.3.1 (FIX protocol)
- Apache Commons CSV 1.10.0 (CSV parsing)
- SLF4J + Logback (Logging)
- Jackson 2.21.3 (API JSON mapping)

All dependencies are bundled in the JAR file.
