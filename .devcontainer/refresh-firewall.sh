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

# Rebuild the egress-allowlist iptables ruleset from scratch.
#
# Why this exists: api.anthropic.com and most other allowed endpoints sit
# behind Cloudflare and rotate IPs frequently. The original post-create
# pinned IPs once at container-create time, so the allowlist drifted out
# of sync with reality after the next DNS rotation and Claude Code hung
# on "Retrying...". This script is idempotent — flushes OUTPUT and
# reapplies the rules from a fresh DNS lookup — and is called from both
# postCreate (initial setup) and postStart (every container start).
set -u

DOMAINS=(
    api.anthropic.com
    claude.ai
    github.com
    raw.githubusercontent.com
    maven.apache.org
    repo1.maven.org
    services.gradle.org
    gradle.org
    registry.npmjs.org
    registry.yarnpkg.com
    context7.com
    mcp.context7.com
)

sudo iptables -F OUTPUT
sudo iptables -P OUTPUT DROP
sudo iptables -A OUTPUT -o lo -j ACCEPT
sudo iptables -A OUTPUT -d 127.0.0.1 -j ACCEPT
sudo iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

for domain in "${DOMAINS[@]}"; do
    for ip in $(getent ahosts "$domain" | awk '{print $1}' | sort -u); do
        sudo iptables -A OUTPUT -d "$ip" -j ACCEPT
    done
done
