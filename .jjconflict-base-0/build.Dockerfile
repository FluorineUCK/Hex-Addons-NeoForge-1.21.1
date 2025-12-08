FROM nixos/nix
WORKDIR /mnt
ADD minimal.nix minimal.nix
VOLUME /nix/store
RUN nix-build minimal.nix --extra-substituters https://poollovernathan.cachix.org?trusted=1
ADD build.gradle.kts settings.gradle.kts gradle.properties ./
ADD .git .git
RUN nix-shell minimal.nix --command 'test -d .jj || { jj git init --colocate; jj edit --ignore-immutable @-; }'
RUN --mount=type=cache,dst=/mnt/build/ --mount=type=cache,dst=/mnt/build2001/ --mount=type=cache,dst=/root/.gradle/caches/ nix-shell minimal.nix --command 'gradle -si'
ADD . .
RUN --mount=type=cache,dst=/mnt/build/ --mount=type=cache,dst=/mnt/build2001/ --mount=type=cache,dst=/root/.gradle/caches/ nix-shell minimal.nix --command 'gradle -si runDatagen && gradle -si build && cp -r /mnt/build2001/libs /'

FROM scratch
COPY --from=0 /libs/ /
