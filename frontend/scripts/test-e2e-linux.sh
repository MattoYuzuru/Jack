#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "${script_dir}/../.." && pwd)"
npm_cache="$(npm config get cache)"
playwright_image="mcr.microsoft.com/playwright:v1.61.1-noble@sha256:5b8f294aff9041b7191c34a4bab3ac270157a28774d4b0660e9743297b697e48"

# Baseline создаётся только в том же Linux-образе, который использует CI.
# Анонимный volume не смешивает host node_modules с Linux, а npm переиспользует download cache.
docker run --rm --ipc=host \
  --env CI=1 \
  --mount "type=bind,source=${project_root},target=/work" \
  --mount type=volume,target=/work/frontend/node_modules \
  --mount "type=bind,source=${npm_cache},target=/root/.npm" \
  --workdir /work \
  "${playwright_image}" \
  bash -lc 'npm --prefix frontend ci && npm --prefix frontend run test:e2e -- "$@"' bash "$@"
