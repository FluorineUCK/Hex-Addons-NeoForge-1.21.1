FROM nixos/nix
WORKDIR /mnt
ADD minimal.nix minimal.nix
VOLUME /nix/store
RUN nix-build minimal.nix --extra-substituters https://poollovernathan.cachix.org?trusted=1
ADD . .
RUN nix-shell minimal.nix --command 'gradle runDatagen'
RUN nix-shell minimal.nix --command 'gradle build'

FROM scratch
COPY --from=0 /mnt/build2001/libs/ /