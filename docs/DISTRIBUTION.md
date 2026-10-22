# LimitForge runnable distribution

This archive contains the Java exchange simulator and synthetic examples. You
need Java 17 or newer to run it; Maven, Node.js, and Docker are not required.
Check your Java installation with `java -version`.

Extract the archive and open a terminal in the extracted directory. It contains:

```text
limitforge.jar
input_clients.csv
input_instruments.csv
input_orders.csv
examples/simulation-request.json
LICENSE
NOTICE
licenses/
README.md
```

## Run the sample session

```bash
java -jar limitforge.jar
```

The application reads the three `input_*.csv` files from the current directory,
processes a complete trading session, and writes these reports there:

- `output_exchange_report.csv`: rejected orders and reasons.
- `output_client_report.csv`: final client positions.
- `output_instrument_report.csv`: instrument statistics.
- `output_session.json`: the machine-readable session snapshot.

Run the sample in a writable directory. Repeating it replaces reports with the
same filenames. Keep the three input files together when moving the JAR.

## Start the batch simulation API

```bash
java -Dlogback.configurationFile=logback-container.xml \
  -cp limitforge.jar com.trading.api.SimulationServer 8080
```

The logging configuration is bundled inside the JAR. The API listens on
`http://127.0.0.1:8080` by default. The final argument selects the port; it may be
omitted to use 8080. Keep the terminal open while using the API, and press
Ctrl+C to stop it.

In another terminal in the same extracted directory:

```bash
curl --fail --show-error http://127.0.0.1:8080/health

curl --fail-with-body \
  --header 'Content-Type: application/json' \
  --data-binary @examples/simulation-request.json \
  http://127.0.0.1:8080/api/v1/simulations
```

The example produces one execution for 100 SIA units at 32.10, with positions
of +100 for BUYER and -100 for SELLER. Each API request runs an independent
batch; it does not update the CSV session or share positions with other
requests. The OpenAPI contract is served at `/api/v1/openapi.json`.

The API is intended for local demonstrations and integration work. It has no
authentication, TLS, persistence, or rate limiting. Use synthetic input and
review the [API guide](https://github.com/Alaneel/limitforge-matching-engine/blob/main/docs/API.md)
before changing how it is exposed.

## Run a small benchmark

```bash
java -Dlogback.configurationFile=logback-benchmark.xml \
  -cp limitforge.jar com.trading.benchmark.MatchingEngineBenchmark \
  --orders 1000 --warmup 1 --iterations 2
```

This quick check verifies the expected executions while measuring batch
throughput and batch duration. It is not a per-order latency measurement. Use
the [benchmark method](https://github.com/Alaneel/limitforge-matching-engine/blob/main/docs/BENCHMARKS.md)
for meaningful comparisons and retain the environment metadata with results.

## Source and attribution

Source code, the companion dashboard, contribution guidance, and the full
documentation are available in the
[LimitForge repository](https://github.com/Alaneel/limitforge-matching-engine).

The original engine was authored by EricFan2002 under the MIT license.
Alan Wang / Alaneel extended it with the dashboard, deterministic session
export, simulation API, benchmark tooling, and container delivery. Read the
included `LICENSE` and `NOTICE` files, and preserve them when redistributing
the software.
