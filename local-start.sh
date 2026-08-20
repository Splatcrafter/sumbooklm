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
