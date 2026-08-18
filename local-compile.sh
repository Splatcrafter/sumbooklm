#!/usr/bin/env bash
set -euo pipefail

# Builds every module and packages the executable artifact.
# Arguments are forwarded to Maven, so -Dfrontend.skip=true builds the backend only.

cd "$(dirname "$0")"

readonly REQUIRED_JAVA_MAJOR=25
readonly ARTIFACT="sumbooklm-app/target/sumbooklm.jar"

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is not on the PATH. This project carries no Maven wrapper, so mvn has to be installed." >&2
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Java is not on the PATH. JDK ${REQUIRED_JAVA_MAJOR} or newer is required." >&2
    exit 1
fi

java_version="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([^"]+)".*/\1/')"
java_major="${java_version%%[.+_-]*}"
if [[ "${java_major}" =~ ^[0-9]+$ ]] && [ "${java_major}" -lt "${REQUIRED_JAVA_MAJOR}" ]; then
    echo "Java ${java_version} found, but JDK ${REQUIRED_JAVA_MAJOR} or newer is required." >&2
    exit 1
fi

mvn clean install "$@"

echo
echo "Artifact: ${ARTIFACT}"
echo "Run ./local-start.sh to start it, or ./local-dev-server.sh for the frontend dev server."
