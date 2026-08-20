#!/usr/bin/env bash
#
# Copyright (c) 2026 Erik Pförtner
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#

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
