# Interactive simulation lab

The dashboard keeps the committed sample session available for replay and adds
a **Simulation Lab** for editable, isolated batches. The lab sends the scenario
to the Java engine and displays its returned executions, rejections, positions,
and auction prices. It does not implement another matching engine in JavaScript.

## Run locally

Start the Java API from the repository root:

```bash
./scripts/run-api.sh
```

In another terminal:

```bash
cd ui
npm ci
npm run dev
```

Open the printed local dashboard URL, select **Simulation Lab**, choose a
synthetic scenario, and run the batch. Edit the JSON to change clients,
instruments, holdings, or orders. Import and export JSON to reproduce a run.
The Java [API contract](API.md) is the authoritative input format.

The development server connects to `http://127.0.0.1:8080` by default. To use
a Java API on another local port, configure its origin when starting the UI:

```bash
SIMULATION_API_URL=http://127.0.0.1:8081 npm run dev
```

The browser calls the same-origin `/api/simulations` bridge. The bridge uses
only its server-side configured target, limits request size to 1 MiB, requires
JSON, checks the request Origin, forwards no browser credentials, and times
out unavailable engines. Responses are bounded to 8 MiB. The Java server retains its loopback default and
does not need cross-origin browser access.

## Understand the results

- Each request creates a fresh engine; earlier runs do not change holdings or
  order IDs in later runs.
- Executions and rejections are different from HTTP validation errors. A
  syntactically valid batch can complete with rejected orders or zero fills.
- Editing a scenario invalidates the in-flight run, preventing an older
  response from being presented as the result of newer input.
- Opening and closing prices are returned only when the corresponding auction
  establishes a price. A missing value is not zero.
- The sample overview's executed-price trajectory uses the exported executions.
  Its depth illustration is labelled separately because the API does not
  export a live order book.

## Hosted runtime

Production builds do not embed the local development target. A hosted Worker
requires a reachable Java service and an explicit `SIMULATION_API_URL` runtime
binding. Without that binding, the lab reports that its backend is not
configured; sample browsing and scenario editing still work.

Sites hosts the web Worker, not the JVM. A visitor's localhost is not the
hosted Worker's localhost. Do not configure a hosted bridge to loopback or
assume that opening the dashboard also starts Java.

The bridge is intended for trusted demonstration environments. Exposing a
shared backend publicly requires an authentication and admission-control
design, as described in [SECURITY.md](../SECURITY.md). The existing Site's
access policy must remain unchanged when publishing an update.

## Verify the full path

```bash
mvn --batch-mode --no-transfer-progress clean verify
npm ci --prefix ui
npm run lint --prefix ui
npm run typecheck --prefix ui
npm test --prefix ui
npm run test:integration --prefix ui
```

The integration check starts the real Java API on an ephemeral loopback port,
calls it through the built dashboard Worker, checks the known example and
request isolation, and stops the Java process. It also verifies empty results,
Java validation errors, cross-origin rejection, and unconfigured-backend
feedback. UI utility and proxy tests run with `npm test`.
