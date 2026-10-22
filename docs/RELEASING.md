# Release guide

LimitForge has no published version yet. The `1.0.0` Maven version identifies
the first candidate; a version in `pom.xml` alone is not a published release.

## Verify a candidate locally

Use Java 17 for release builds, Maven 3.6+, Node.js 22.13+, Bash, `unzip`, `tar`,
and either `sha256sum` or `shasum`. From the repository root:

```bash
mvn --batch-mode --no-transfer-progress clean verify
npm ci --prefix ui
npm run lint --prefix ui
npm run typecheck --prefix ui
npm test --prefix ui
./scripts/package-release.sh v1.0.0
./scripts/verify-distribution.sh
```

The packaging script reads the version from the built JAR and rejects a
mismatched tag. It checks that the standalone JAR includes the original MIT
license and project notice before writing these files to `target/release/`:

- `limitforge-engine-1.0.0.jar`: executable Java application with dependencies.
- `limitforge-1.0.0.tar.gz`: JAR, sample CSV inputs, example API request, license,
  notice, third-party license texts, and Java-only instructions.
- `SHA256SUMS`: checksums for both downloads.

The distribution verification extracts the bundle into a temporary directory,
runs it without Maven or source files, checks its reports, and compares its
session JSON with the committed dashboard fixture. CI performs the same check
and retains the verified downloads as an Actions artifact.

## Build stability

Lifecycle plugin versions and the archive output timestamp are fixed in
`pom.xml`. This avoids Maven's implicit plugin-version drift and changing JAR
entry timestamps. Build with the same JDK major version, Maven version, and
dependency set when comparing binary hashes. The tarball is a convenience
bundle and does not promise identical hashes across hosts or builds.

Before a release, build the candidate twice and compare the JAR SHA-256 values.
For a version change, update the Maven version and timestamp together, plus
versioned JAR paths in the Dockerfile, launcher scripts, CI, and documentation.

## Prepare a GitHub draft

1. Merge the reviewed candidate with green CI.
2. Update `CHANGELOG.md` and `docs/RELEASE_NOTES.md` for the version being tagged.
3. Create and push an annotated tag matching `v` plus the Maven version:

   ```bash
   git tag -a v1.0.0 -m 'LimitForge v1.0.0'
   git push origin v1.0.0
   ```

4. The **Release candidate** workflow verifies Java and dashboard checks,
   validates the tag, packages the downloads, and creates a **draft** release.
5. Review the release text, download the assets, verify `SHA256SUMS`, and run the
   tarball example before publishing the draft in GitHub.

The workflow can also be run manually on a branch to produce an artifact. A
branch run does not create a tag or a GitHub release. Existing releases are not
overwritten. Container image publication is not part of this workflow.
