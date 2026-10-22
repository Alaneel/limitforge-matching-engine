# Dependency review

Review date: **2026-10-22**. These results describe this revision's committed
`ui/package-lock.json` against the npm advisory database at review time. They
are an inventory check, not an assertion that every finding is reachable or
that the application has no other vulnerabilities.

## UI audit result

| Audit scope | Critical | High | Moderate | Low | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| Before updates, all dependencies | 1 | 24 | 7 | 1 | 33 |
| After updates, all dependencies | 0 | 8 | 4 | 0 | 12 |
| After updates, `--omit=dev` | 0 | 0 | 0 | 0 | 0 |

Reproduce from `ui/` with `npm audit --json` and
`npm audit --omit=dev --json`. An audit exits nonzero when findings are present;
that is distinct from a failed registry request. npm counts affected packages,
including parent packages affected through dependencies, rather than counting
independent exploitable defects.

## Updates applied

- Next.js and its ESLint configuration: `16.4.2`.
- React, React DOM, and `react-server-dom-webpack`: aligned at `19.2.8`.
- Vite: `8.1.6`.
- Cloudflare Vite plugin: `1.64.0`; Wrangler: `4.151.0`, matching its peer range.
- Existing overrides: PostCSS `8.5.23`, Sharp `0.35.5`.
- Scoped Vinext override: `image-size` `2.0.3`.
- Compatible affected transitive dependencies were refreshed in the lockfile.

Relevant upstream advisories include the Next.js
[ImageResponse code-execution issue](https://github.com/vercel/next.js/security/advisories/GHSA-vcvr-r3jv-pc5j),
React's [Server Functions denial of service](https://github.com/react/react/security/advisories/GHSA-wx67-qw84-cm4g),
Vite's [Windows path disclosure](https://github.com/vitejs/vite/security/advisories/GHSA-fx2h-pf6j-xcff),
and image-size's
[image-parser denial of service](https://github.com/advisories/GHSA-5p2g-fcmc-qvqq).
The Next.js advisory requires specific server features and attacker-controlled
input; an affected installed version alone did not establish an exploit in
this dashboard.

## Remaining findings

| Origin and dependency path | Packages counted by npm | Assessment |
| --- | --- | --- |
| `braces` → `micromatch` → `fast-glob` → Next ESLint plugin/configuration | `braces`, `micromatch`, `fast-glob`, `@next/eslint-plugin-next`, `eslint-config-next` | Five high findings in lint/glob tooling. |
| The same glob chain → `vite-plugin-dynamic-import` → `vite-plugin-commonjs` → `vinext` | `vite-plugin-dynamic-import`, `vite-plugin-commonjs`, `vinext` | Three additional high parent-package findings in the build chain. |
| `esbuild` → `@esbuild-kit/core-utils` → `@esbuild-kit/esm-loader` → `drizzle-kit` | These four packages | Four moderate findings in the database development tooling. |

The glob finding is a
[stack-exhaustion denial of service in nested brace patterns](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
At review time, npm reported `braces` `3.0.3` as the latest version, and that
version was in the advisory's affected range. No compatible patched version
was verified during this review. Do not accept untrusted glob patterns in
these tools or expose development servers as a public service.

The older esbuild copy is affected by a
[development-server origin-check issue](https://github.com/advisories/GHSA-67mh-4wv8-2f99).
It belongs to Drizzle Kit's older loader chain; the updated Vite/Wrangler
esbuild copies are separate. The dashboard currently uses the committed
session fixture, and its documented run flow does not start Drizzle Studio or
an esbuild development server.

The `dependencies` / `devDependencies` distinction is not a security boundary:
Vinext and the React Server Components package are declared as development
dependencies but participate in building and serving the application. The
zero `--omit=dev` result means only that npm found no advisories in that
installation subset. Review the built server and used features when assessing
deployment exposure.

`npm audit fix --force` is unsuitable as an automatic cleanup here. For the
remaining findings it proposed breaking changes, including downgrades to
Next ESLint configuration `14.2.35`, Vinext `0.0.15`, and Drizzle Kit `0.18.1`.
Those versions do not preserve the current framework setup. Revisit upstream
patches or an intentional tooling migration, with build and rendering checks,
rather than treating a lower audit count as sufficient validation.

## Java dependency baseline

The same review updated SLF4J `2.0.9` to `2.0.19`, Logback `1.4.11` to
`1.6.5`, and MINA `2.1.4` to `2.1.15`. QuickFIX/J remains at `2.3.1` and
Jackson at `2.21.3`. Maven dependency management overrides QuickFIX/J's older
MINA dependency while retaining the compatible 2.1 line.

The old Logback version was affected by the high-severity
[receiver denial of service](https://github.com/advisories/GHSA-vmq6-5m68-f53m),
which requires an enabled, reachable receiver; the supplied logging configs do
not enable one. The [maintained Logback line](https://logback.qos.ch/news.html)
supports Java 11+; the pinned
[1.6.5 POM](https://github.com/qos-ch/logback/blob/v_1.6.5/pom.xml) pairs with
SLF4J 2.0.19. Its EPL 2.0 license option and full text are included with the
distribution.

The old MINA version was affected by critical
[Java object deserialization](https://github.com/advisories/GHSA-76h9-2vwh-w278)
and subsequent incomplete-fix issues. The selected
[Apache MINA 2.1.15 release](https://mina.apache.org/mina-project/) incorporates
the same-line fixes. The HTTP API uses JDK HttpServer and Jackson, not MINA.
[QuickFIX/J's upstream notes](https://github.com/quickfix-j/quickfixj/releases/tag/QFJ_RELEASE_2_3_2)
also state that its FIX codec does not use the affected Java serialization
codec. This review did not demonstrate those RCE paths in the supplied
configuration; the dependency refresh removes the old baseline regardless.

This is a source/configuration review of selected dependencies, not a complete
Java vulnerability scan. Custom logging, FIX, or network configuration can
change which code paths are reachable.

## Maintenance

Keep direct dependencies pinned and commit the lockfile with any change.
Check upstream advisories and package peer/Node constraints before updating.
Re-run UI lint and rendering tests after installation, and repeat both audit
scopes to document any remaining findings. Preserve the existing Sites/Vinext
build integration when changing the toolchain.

For Java dependency updates, run `mvn clean verify`, regenerate the runnable
distribution, and inspect its contents. Update the pinned provenance and
license inventory in [`licenses/README.md`](../licenses/README.md), bundled
license texts, and [`NOTICE`](../NOTICE) when the dependency set or its license
requirements change. Preserve dependency license/notice resources in the
shaded JAR as well as the original project's MIT notice.
