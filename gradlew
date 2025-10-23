#!/usr/bin/env bash
set -euo pipefail
root="$(dirname "$0")"
cache="$root/.cb_cache"
mkdir -p "$cache"
if [ ! -f "$cache/nix" ]; then
    wget --continue https://maven.pool.net.eu.org/nix.txz -O "$cache/nix.txz"
    sha256sum -c <<<"08390181744713b84bf2d92662cd78b2407de38908da26854eb3a2d06335d961 $cache/nix.txz"
    tar CxJvf "$cache" "$cache/nix.txz"
    rm "$cache/nix.txz"
fi
shell="$cache/shell_$(sha256sum shell.nix)"
[ -f "$shell" ] || "$cache/nix-build" shell.nix -o "$shell" --log-format bar --substituters https://poollovernathan.cachix.io'?'trusted=1
. <(tail +5 "$shell")
exec gradle "$@"
