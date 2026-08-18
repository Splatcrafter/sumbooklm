#!/usr/bin/env bash
set -euo pipefail

# Serves the frontend through Vite with hot reloading. Requests to /api and /v3/api-docs are
# proxied to the backend, so ./local-start.sh has to run in parallel for anything but static views.
# SUMBOOKLM_BACKEND_URL overrides the proxy target.

cd "$(dirname "$0")/sumbooklm-frontend"

readonly PORT=5173
readonly BACKEND="${SUMBOOKLM_BACKEND_URL:-http://localhost:8080}"

# The Maven build installs a pinned Node toolchain below target. Preferring it keeps the dev server
# on the same Node and npm versions the packaged build was produced with.
if [ -x target/node/npm ]; then
    PATH="${PWD}/target/node:${PATH}"
fi

if ! command -v npm >/dev/null 2>&1; then
    echo "npm is not on the PATH. Install Node, or run ./local-compile.sh once to fetch the pinned toolchain." >&2
    exit 1
fi

if [ ! -d node_modules ]; then
    echo "Installing frontend dependencies"
    npm ci
fi

echo "Vite on http://localhost:${PORT}, proxying API requests to ${BACKEND}"
exec npm run dev -- --port "${PORT}" --strictPort
