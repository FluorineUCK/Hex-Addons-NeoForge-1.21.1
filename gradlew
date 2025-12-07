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
nix_args=(--extra-experimental-features 'flakes' --keep-going --extra-substituters https://poollovernathan.cachix.org'?'trusted=1)
if [ -n "${NO_CRASH:-}" ]; then
  "$cache/nix-shell" "${nix_args[@]}" --command "$(printf '%q ' gradle "$@")"
else
  #[ -d "$cache/_nix" ] || "$cache/nix" --extra-experimental-features 'nix-command flakes' build github:nixos/nixpkgs/fe51d34885f7b5e3e7b59572796e1bcb427eccb1#nix -o "$cache/_nix"
  [ -f "$shell" ] || "$cache/nix-build" "${nix_args[@]}" shell.nix -o "$shell" --log-format bar
  . <(tail +5 "$shell")
  exec gradle "$@"
fi
