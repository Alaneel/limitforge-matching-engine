#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/target/release"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum --check SHA256SUMS
else
  shasum -a 256 --check SHA256SUMS
fi

shopt -s nullglob
archives=(limitforge-*.tar.gz)
if [[ ${#archives[@]} -ne 1 ]]; then
  echo 'Expected exactly one release archive.' >&2
  exit 1
fi
verify_root="$(mktemp -d "$repo_root/target/distribution-check.XXXXXX")"
trap 'rm -r -- "$verify_root"' EXIT
tar -xzf "${archives[0]}" -C "$verify_root"
bundles=("$verify_root"/limitforge-*)
cd "${bundles[0]}"
java -Dlogback.configurationFile=logback-benchmark.xml -jar limitforge.jar
for report in output_exchange_report.csv output_client_report.csv output_instrument_report.csv output_session.json; do
  test -s "$report"
done
cmp output_session.json "$repo_root/ui/public/session.json"
cmp LICENSE "$repo_root/LICENSE"
cmp NOTICE "$repo_root/NOTICE"
echo 'Downloaded-bundle workflow passed without source files or Maven.'
