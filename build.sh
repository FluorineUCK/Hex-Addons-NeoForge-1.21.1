#!/usr/bin/env bash
set -euo pipefail
root="$(dirname "$0")"
cache="$root/.cb_cache"
mkdir -p "$cache"
if [ ! -d "$cache/nix" ]; then
    echo "==> downloading Nix..."
    wget --continue https://maven.pool.net.eu.org/nix.txz -O "$cache/nix.txz"
    tar CxJvf "$cache" "$cache/nix.txz"
fi
release= "$cache/nix-shell" --log-format bar-with-logs --substituters https://poollovernathan.cachix.io'?'trusted=1 --command 'gradle build'
