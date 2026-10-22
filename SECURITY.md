# Security policy

LimitForge is an educational exchange simulator. Its HTTP server runs isolated
in-memory batches; it has no built-in authentication, TLS, durable storage, or
rate limiting. The FIX adapter is experimental. Use synthetic data and keep
demonstration services on trusted local interfaces. See the
[API boundaries](docs/API.md#current-boundary) and
[architecture limitations](docs/ARCHITECTURE.md#deliberate-limitations).

## Supported code

Security fixes are maintained on the default `main` branch on a best-effort
basis. Older commits and release tags do not currently receive separate
backports. Use a current revision and review dependency updates before running
the project. There is no security response SLA or bug bounty program.

See the dated [dependency review](docs/DEPENDENCIES.md) for applied updates and
remaining tooling advisories. Audit counts describe the checked dependency
inventory, rather than certifying a deployment.

## Report a vulnerability privately

Open the repository's [Security page](https://github.com/Alaneel/limitforge-matching-engine/security)
and use **Report a vulnerability** if that option is available. Private
vulnerability reporting depends on the repository's GitHub settings.

If that option is unavailable, use a private contact method published on
the [maintainer's profile](https://github.com/Alaneel). If no private method is
listed, open a public issue only to request a private reporting channel; do not
include vulnerability details, reproduction payloads, credentials, or affected
user data. If Issues are disabled, wait for an available private channel rather
than posting those details in a public pull request or commit.

Include the affected commit or version, a small synthetic reproduction,
expected and actual behavior, and the impact you observed. Remove secrets and
personal data from logs and requests. Coordinate disclosure with the maintainer
so a fix and regression test can be prepared before publishing a working exploit.

## Running the demo safely

The Java launcher binds to loopback by default. The container listens on all
interfaces inside its network namespace, while the supplied Compose file
publishes port 8080 only on the host's loopback interface. Changing that mapping
or the server's bind address can make the API reachable from other machines.
Keep the supplied non-root user, read-only filesystem, dropped capabilities,
and `no-new-privileges` configuration when running the container.

The documented request limits reduce individual request size; they do not
provide admission control or make an unauthenticated public deployment safe.
Do not place real funds, credentials, or personal trading data in this demo.
