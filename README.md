# LimitForge

[![CI](https://github.com/Alaneel/limitforge-matching-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/Alaneel/limitforge-matching-engine/actions/workflows/ci.yml)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**A Java order matching engine with auction and continuous trading.**

LimitForge is an educational exchange simulator that processes market and limit orders through opening auctions, price-time-priority continuous matching, and closing auctions. It also includes CSV workflows and experimental FIX 4.4 connectivity.

The repository also includes an interactive web dashboard for exploring a sample session, order-book depth, executions, and risk controls.

The dashboard is backed by a deterministic JSON snapshot produced by the Java engine itself. CI regenerates the snapshot and rejects changes when the UI fixture no longer matches engine output.

## 🚀 Features

- **Order Matching Engine**: Morning auction, real-time trading, and evening auction phases
- **FIX Protocol Support**: Full FIX 4.4 implementation for institutional connectivity
- **Advanced Concurrency**: Thread-safe operations using Java concurrent collections
- **Position Management**: Real-time position tracking with validation
- **CSV Processing**: Read orders from CSV files and generate comprehensive reports
- **Comprehensive Logging**: Full audit trail using SLF4J and Logback

## 📦 Quick Start

```bash
# Build the project
mvn clean package

# Run the trading system
java -jar target/limitforge-engine-1.0.0.jar

# Run with FIX protocol enabled
java -Dfix.enabled=true -jar target/limitforge-engine-1.0.0.jar

# Run the dashboard (in another terminal)
cd ui
npm ci
npm run dev
```

### Regenerate the dashboard session

```bash
mvn clean package
java -Dsession.report=ui/public/session.json -jar target/limitforge-engine-1.0.0.jar
```

The exported schema includes executions, rejected orders, client positions, open price, volume, VWAP, high, and low. The committed fixture makes the demo reproducible while keeping the Java engine as its source of truth.

## 📁 Project Structure

```
limitforge-matching-engine/
├── src/main/java/com/trading/
│   ├── model/              # Domain models (Client, Instrument, Order, Transaction)
│   ├── csv/                # CSV reading and writing utilities
│   ├── engine/             # Order matching engine with concurrency
│   ├── fix/                # FIX protocol server and handlers
│   └── TradingApplication  # Main application class
├── src/main/resources/
│   ├── logback.xml         # Logging configuration
│   └── fix-server.cfg      # FIX server configuration
├── ui/                     # Interactive Next.js/vinext dashboard
├── input_*.csv             # Input data files
├── output_*.csv            # Generated reports
├── pom.xml                 # Maven build configuration
├── README-JAVA.md          # Detailed Java documentation
└── QUICKSTART.md           # Quick reference guide
```

## 📊 Input Files

The system processes three CSV input files:

### input_clients.csv
Client information including currencies and position check settings
```csv
ClientID,Currencies,PositionCheck,Rating
A,"USD,SGD",Y,1
```

### input_instruments.csv
Tradable instruments with currency and lot size
```csv
InstrumentID,Currency,LotSize
SIA,SGD,100
```

### input_orders.csv
Order data with time, side, price, and quantity
```csv
Time,OrderID,Instrument,Quantity,Client,Price,Side
9:00:01,A1,SIA,1500,A,Market,Buy
```

## 📈 Output Reports

The system generates three comprehensive reports:

1. **output_exchange_report.csv** - Rejected orders with reasons
2. **output_client_report.csv** - Final positions for each client
3. **output_instrument_report.csv** - Trading statistics (VWAP, high/low, volume)
4. **output_session.json** - Machine-readable session snapshot for demos and integrations

## 🔧 Requirements

- Java 17 or higher
- Maven 3.6 or higher
- Node.js 22.13 or higher (dashboard only)

## 📚 Documentation

- **[README-JAVA.md](README-JAVA.md)** - Complete Java implementation documentation
- **[QUICKSTART.md](QUICKSTART.md)** - Quick reference and examples
- **[LICENSE](LICENSE)** - License information

## 🎯 Key Technologies

- **Java 17** - Modern Java features and APIs
- **QuickFIX/J 2.3.1** - FIX protocol implementation
- **Apache Commons CSV** - CSV parsing and generation
- **SLF4J + Logback** - Comprehensive logging
- **Maven** - Build and dependency management

## 🔥 Concurrency Features

- `ConcurrentHashMap` - Thread-safe client/instrument lookups
- `PriorityBlockingQueue` - Lock-free priority order books
- `ExecutorService` - Parallel processing (CPU-bound operations)
- `AtomicInteger` - Lock-free order quantity updates
- `ReentrantReadWriteLock` - Transaction consistency

## 📊 Trading Phases

### 1. Morning Auction (Before 9:30)
- Collects all orders
- Calculates optimal clearing price
- Executes matching orders at clearing price

### 2. Real-Time Trading (9:30 - 16:00)
- Continuous order matching
- Price-time priority
- Immediate execution when prices cross

### 3. Evening Auction (16:00 - 16:10)
- Final order collection
- Calculates closing price
- Executes remaining trades

## 🌐 FIX Protocol Integration

The system supports FIX 4.4 protocol for institutional order submission:

- **Port**: 9876 (configurable)
- **Message Types**: New Order Single (35=D)
- **Execution Reports**: Sent for all order state changes
- **Session Management**: Automatic heartbeats and reconnection

See [README-JAVA.md](README-JAVA.md) for detailed FIX configuration.

## 🐛 Troubleshooting

**Port conflict**: Change `SocketAcceptPort` in `src/main/resources/fix-server.cfg`

**Memory issues**: Run with more heap: `java -Xmx2g -jar target/limitforge-engine-1.0.0.jar`

**CSV not found**: Ensure input CSV files are in the project root directory

## 📝 License

See [LICENSE](LICENSE) file for details.

## 🔄 Version History

- **v2.0** (Current) - Java implementation with FIX protocol and concurrency
- **v1.0** - Original C++ implementation (deprecated)

---

For detailed implementation information, see **[README-JAVA.md](README-JAVA.md)**
