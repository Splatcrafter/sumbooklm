#!/usr/bin/env bash
set -euo pipefail

# Starts the packaged application. Arguments are forwarded to Spring Boot, so a later
# --server.port overrides the port set here.

cd "$(dirname "$0")"

readonly ARTIFACT="sumbooklm-app/target/sumbooklm.jar"
readonly PORT=8080

if ! command -v java >/dev/null 2>&1; then
    echo "Java is not on the PATH." >&2
    exit 1
fi

if [ ! -f "${ARTIFACT}" ]; then
    echo "${ARTIFACT} does not exist. Run ./local-compile.sh first." >&2
    exit 1
fi

echo "Starting SumbookLM on http://localhost:${PORT}"
exec java -jar "${ARTIFACT}" --server.port="${PORT}" "$@"
