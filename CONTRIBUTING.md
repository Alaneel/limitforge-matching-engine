# Contributing to LimitForge

LimitForge is an educational Java exchange simulator with a batch simulation
API and a dashboard backed by exported engine output. Contributions that make
matching behavior easier to verify, reproduce, and explain are especially useful.
Read the [architecture](docs/ARCHITECTURE.md) and [API contract](docs/API.md)
before changing order processing or request semantics.

## Set up a working copy

Use Java 17+, Maven 3.6+, and Git. Dashboard work also needs Node.js 22.13+;
Docker with Compose is optional for container checks.

```bash
git clone https://github.com/Alaneel/limitforge-matching-engine.git
cd limitforge-matching-engine
git switch -c fix/describe-the-change
mvn --batch-mode --no-transfer-progress clean verify
```

For dashboard work, run these commands from `ui/`:

```bash
npm ci
npm run lint
npm run typecheck
npm test
```

`npm test` builds the dashboard and checks the rendered HTML. Start the local
development server with `npm run dev` when a change needs visual inspection.
Use `npm ci` with the committed lockfile; include intentional dependency changes
and their lockfile update in the same pull request.

After building the Java JAR and dashboard, run `npm run test:integration` from
`ui/` to exercise the built Worker bridge against the real Java API. The test
starts and stops its own loopback server on an ephemeral port. See the
[Simulation Lab guide](docs/SIMULATION_LAB.md) for the complete local workflow.

## Make a focused change

- Describe the observed behavior, expected behavior, and a minimal example.
  For a bug, add a regression test that fails without the fix.
- Keep matching rules in the engine and domain model. CSV, FIX, and HTTP
  adapters should translate input and output rather than implement a second
  matching policy.
- Preserve deterministic results for the same input. Explain any change to
  price priority, tie-breaking, timestamps, phase transitions, or risk checks.
- Keep API documentation and `src/main/resources/openapi.json` aligned with
  changes to request validation, response fields, or errors.
- Use synthetic clients and orders in examples. Do not commit credentials,
  real account details, private trading data, logs, or generated reports.

For design changes such as incremental order submission or persistence,
describe the proposed semantics in a draft pull request before building a large
feature. See the [roadmap](docs/ROADMAP.md) for the intended sequence.

## Verify engine output and fixtures

From the repository root, build and test the Java code:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

The dashboard fixture is generated from the committed CSV inputs. If a change
intentionally affects their output, regenerate it and review the JSON diff:

```bash
java -Dsession.report=ui/public/session.json -jar target/limitforge-engine-1.0.0.jar
git diff -- ui/public/session.json
```

Include the fixture update with the behavior change. The CI fixture check
compares fresh engine output with `ui/public/session.json`; do not edit the
fixture by hand to make that check pass. Running the application also writes
ignored CSV reports in the repository root.

For API or container changes, use the [API example](docs/API.md#run-the-example)
and confirm the health endpoint and a simulation request work. Container checks
should include the read-only, non-root configuration in `compose.yaml`.
To run the same container check as CI (requires Docker, `curl`, and `jq`):

```bash
docker build --tag limitforge-api:local .
./scripts/smoke-test-container.sh limitforge-api:local
```

Performance changes should include the workload, environment, and correctness
checks described in [BENCHMARKS.md](docs/BENCHMARKS.md), with before/after
measurements on the same machine.

## Open a pull request

Keep a pull request centered on one problem. Explain what changes for a caller
or user, list the checks you ran, and identify any limitations or compatibility
changes. For UI changes, include a useful screenshot or short description of
the visual verification. CI checks the Java build and tests, dashboard lint and
rendering, the generated fixture, benchmark smoke run, and container startup
and simulation.

The original engine was authored by EricFan2002 under MIT. Preserve the
[license](LICENSE), original copyright notice, and attribution when modifying
or redistributing that work. New contributions are submitted under the
repository's MIT license; authorship remains visible in Git history.

Report suspected vulnerabilities through the private process in
[SECURITY.md](SECURITY.md), rather than a public pull request or issue.
