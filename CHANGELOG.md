# Changelog

## Unreleased — first independent release candidate

### Interactive batch lab

- Editable synthetic scenarios, import/export, validation feedback, and Java
  simulation results in the dashboard.
- Same-origin server bridge with explicit backend configuration, bounded
  request/response bodies, credential isolation, and request deadlines.
- Full-path integration check against the real Java API.
- Sample price chart derived from executions and explicit illustrative depth
  labels.

### Added

- Engine-generated session export and interactive dashboard replay.
- Isolated simulation HTTP API and OpenAPI 3.1 contract.
- Reproducible batch benchmark with documented measurement boundaries.
- Hardened Docker/Compose delivery with health checks and CI smoke tests.
- Downloadable Java-only bundle, embedded attribution, SHA-256 checksums, and
  tagged draft-release workflow.
- Contributor guide, security policy, roadmap, and dependency-update automation.

### Fixed

- Continuous matching processes batch orders chronologically, with deterministic
  ordering for equal timestamps.
- Opposing market orders no longer hide executable limit liquidity.
- Orders at or after the 16:10 close are rejected; fills require positive size.
- Cumulative positions and sell reservations use 64-bit integers, avoiding
  overflow when individually valid fills exceed the 32-bit range in aggregate.
- Malformed or ambiguous JSON returns client errors instead of changing input
  values through coercion or producing internal server errors.
- Updated Java logging/MINA and dashboard runtime/build dependencies; remaining
  npm tooling advisories are documented in the dependency review.
- Added official Cloudflare Worker types and a CI TypeScript check.

### Attribution

The original engine was authored by EricFan2002 under MIT. This independent
repository retains the original copyright notice, license, and commit history.
Alan Wang / Alaneel's extensions and subsequent fixes are recorded in Git.
