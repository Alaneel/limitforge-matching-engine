#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
container_id="$(docker run --detach --rm \
  --read-only --tmpfs /tmp:size=64m,mode=1777 \
  --cap-drop ALL --security-opt no-new-privileges \
  --publish 127.0.0.1::8080 "${1:-limitforge-api:ci}")"
trap 'docker stop --time 2 "$container_id" >/dev/null' EXIT
address="$(docker port "$container_id" 8080/tcp)"
for attempt in {1..20}; do
  if curl --fail --silent "http://$address/health" >/dev/null; then
    test "$(docker inspect --format '{{.Config.User}}' "$container_id")" = limitforge
    docker exec "$container_id" java -cp /app/limitforge.jar com.trading.api.ApiHealthProbe
    curl --fail --silent --show-error \
      --header 'Content-Type: application/json' \
      --data-binary @examples/simulation-request.json \
      "http://$address/api/v1/simulations" \
      | jq --exit-status '
          .executions == [{time:"10:00:01", instrument:"SIA", price:32.10,
            quantity:100, seller:"SELLER", buyer:"BUYER"}]
          and .rejections == []
          and .positions == [{clientId:"BUYER", instrument:"SIA", netPosition:100},
            {clientId:"SELLER", instrument:"SIA", netPosition:-100}]'
    echo 'Hardened container, Java health probe, and simulation response verified.'
    exit 0
  fi
  sleep 1
done
docker logs "$container_id"
exit 1
