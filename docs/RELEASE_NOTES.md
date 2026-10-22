# LimitForge 1.0.0 candidate

An educational Java exchange simulator with an engine-backed React dashboard,
isolated HTTP batch simulations, benchmark tooling, and Docker delivery.

This project is independently maintained by Alan Wang / Alaneel and builds on
EricFan2002's MIT-licensed matching-engine project. The original copyright and
license are included in every download.

## Included

- Opening and closing auctions, continuous market/limit matching, and checked
  sell-position reservations.
- Deterministic CSV/JSON reports and dashboard execution replay.
- Batch HTTP API with OpenAPI contract, isolated requests, strict JSON
  validation, and bounded request size.
- Docker Compose runtime with non-root user, read-only filesystem, and health
  check.
- Java-only example bundle, executable JAR, and SHA-256 checksums.

## Download and try

Download `limitforge-1.0.0.tar.gz`, `limitforge-engine-1.0.0.jar`, and
`SHA256SUMS` into the same directory. Run `sha256sum --check SHA256SUMS`
(or `shasum -a 256 --check SHA256SUMS` on macOS). After verification,
extract the bundle and run `java -jar limitforge.jar` from the extracted
directory using Java 17+. The included README also covers the API and benchmark.

The dashboard remains a source-based Node.js application and renders an
exported sample session. FIX support is experimental and does not route fills
back to clients. There is no persistence, recovery, authentication, or live
market-data stream. See the repository architecture guide for scope.
