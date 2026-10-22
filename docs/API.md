# Simulation API

The HTTP API runs complete, isolated trading-day simulations. Every request
creates a fresh in-memory engine, so concurrent callers do not share clients,
positions, order books, or order IDs.

This is intentionally a batch API. It preserves the current engine's phase
semantics instead of presenting a misleading single-order endpoint as a live
exchange gateway.

## Start the server

```bash
./scripts/run-api.sh
```

The default address is `http://127.0.0.1:8080`. Pass a different port as the
first argument.

### Docker

```bash
docker compose up --build
```

Compose publishes the API only on the host loopback interface. The container
runs as a non-root user with a read-only filesystem, no Linux capabilities, and
`no-new-privileges` enabled.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Service readiness |
| `GET` | `/api/v1/openapi.json` | OpenAPI 3.1 contract |
| `POST` | `/api/v1/simulations` | Run one isolated simulation |

## Run the example

```bash
curl --fail-with-body \
  --header 'Content-Type: application/json' \
  --data @examples/simulation-request.json \
  http://127.0.0.1:8080/api/v1/simulations
```

The response contains executions, rejections, final non-zero positions, opening
prices, and closing prices. Market orders omit `price`; limit orders require it.
Order quantities and initial positions are 32-bit integers; aggregated response
positions are 64-bit integers so cumulative fills do not wrap at 2,147,483,647.
Times determine whether an order enters the opening auction, continuous phase,
or closing auction.

## Request boundaries

- `Content-Type` must be `application/json`.
- Unknown or duplicate JSON fields and trailing JSON values are rejected.
- Null request bodies and null entries in the input arrays are rejected with
  HTTP 400. Required fields, including `positionCheck` and `rating`, must be
  present; JSON `null` cannot stand in for a boolean or integer.
- JSON types are strict: quantities and positions must be integers, numeric
  strings are not converted, and fractional quantities are never truncated.
- Request bodies are limited to 1 MiB.
- A request may contain at most 100,000 orders.
- Client and instrument identifiers must be unique within a request.
- Domain validation still applies: client, instrument, currency, lot size,
  duplicate order ID, and checked sell-position rules.

Errors use a stable envelope:

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "At least one client is required"
  }
}
```

## Current boundary

The server binds to loopback by default. The container listens on its internal
network interface, while Compose publishes its port on host loopback only.
There is no authentication, TLS, persistence, rate limiting, or recovery journal.
It is a local integration and demonstration surface, not a production exchange
gateway.
