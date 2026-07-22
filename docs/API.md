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
Times determine whether an order enters the opening auction, continuous phase,
or closing auction.

## Request boundaries

- `Content-Type` must be `application/json`.
- Unknown JSON fields and trailing JSON values are rejected.
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

The server binds to loopback only and has no authentication, TLS, persistence,
rate limiting, or recovery journal. It is a local integration and demonstration
surface, not a production exchange gateway.
