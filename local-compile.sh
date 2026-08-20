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
