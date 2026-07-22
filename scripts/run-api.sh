#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

mvn --batch-mode --no-transfer-progress package -DskipTests
java -cp target/limitforge-engine-1.0.0.jar com.trading.api.SimulationServer "${1:-8080}"
