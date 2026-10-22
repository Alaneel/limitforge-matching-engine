#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

# Package an already verified build. Never silently build with skipped tests.
shopt -s nullglob
jars=(target/limitforge-engine-*.jar)
if [[ ${#jars[@]} -ne 1 ]]; then
  echo 'Expected one executable JAR; run mvn clean verify first.' >&2
  exit 1
fi
jar_path="${jars[0]}"
version="$(unzip -p "$jar_path" META-INF/maven/com.trading/limitforge-engine/pom.properties | sed -n 's/^version=//p' | tr -d '\r')"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$ ]]; then
  echo "Unsupported release version: $version" >&2
  exit 1
fi
if [[ -n "${1:-}" && "$1" != "v$version" ]]; then
  echo "Release tag $1 does not match built version v$version." >&2
  exit 1
fi

# Preserve the original project's license in every standalone JAR download.
unzip -p "$jar_path" META-INF/limitforge/LICENSE | cmp - LICENSE
unzip -p "$jar_path" META-INF/limitforge/NOTICE | cmp - NOTICE
while IFS= read -r license_file; do
  unzip -p "$jar_path" "META-INF/limitforge/third-party/${license_file#licenses/}" | cmp - "$license_file"
done < <(find licenses -type f)

staging_root="$(mktemp -d "$repo_root/target/release-staging.XXXXXX")"
trap 'rm -r -- "$staging_root"' EXIT
bundle_name="limitforge-$version"
bundle_dir="$staging_root/$bundle_name"
mkdir -p "$bundle_dir/examples" target/release
cp "$jar_path" "$bundle_dir/limitforge.jar"
cp input_clients.csv input_instruments.csv input_orders.csv LICENSE NOTICE "$bundle_dir/"
cp -R licenses "$bundle_dir/"
cp docs/DISTRIBUTION.md "$bundle_dir/README.md"
cp examples/simulation-request.json "$bundle_dir/examples/"
cp "$jar_path" "target/release/limitforge-engine-$version.jar"
tar -czf "target/release/$bundle_name.tar.gz" -C "$staging_root" "$bundle_name"

(
  cd target/release
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "limitforge-engine-$version.jar" "$bundle_name.tar.gz" > SHA256SUMS
  else
    shasum -a 256 "limitforge-engine-$version.jar" "$bundle_name.tar.gz" > SHA256SUMS
  fi
)
echo "Release files written to $repo_root/target/release"
