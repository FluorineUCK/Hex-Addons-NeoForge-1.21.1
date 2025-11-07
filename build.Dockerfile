FROM nixos/nix
WORKDIR /mnt
ADD minimal.nix minimal.nix
VOLUME /nix/store
RUN nix-build minimal.nix --extra-substituters https://poollovernathan.cachix.org?trusted=1
ADD . .
RUN nix-shell minimal.nix --command 'test -d .jj || { jj git init --colocate; jj edit --ignore-immutable @-; }'
RUN --mount=type=cache,dst=/mnt/build/ --mount=type=cache,dst=/mnt/build2001/ --mount=type=cache,dst=/root/.gradle/files/ nix-shell minimal.nix --command 'gradle -i runDatagen && gradle -i build && cp -r /mnt/build2001/libs /'

FROM scratch
COPY --from=0 /libs/ /
