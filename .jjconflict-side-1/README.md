<center>

<img height="200" src="https://codeberg.org/api/v1/repos/poollovernathan/hexic/raw/src/main/resources/assets/hexic/icon.png" title="hexic icon: a blue stringworm" width="200"/>

<h1 style="margin-top: 0">hexic</h1>

[![badge linking to documentation](https://img.shields.io/endpoint?url=https://hexxy.media/api/v0/badge/hexdoc/)](https://hexic.pool.net.eu.org/)
&nbsp; ![look at how much time i wasted](https://hackatime-badge.hackclub.com/U081HJSMURJ/hexic)

</center>

a small [hex casting][hexcasting] addon adding whatever features i find neat or that feel interesting, as well as some bugfixes.

*coming from siege? view [the summary page][siege] too*

## new features
* **mediaweave**, can be worn on shoulders and lets you easily cast up to two hexes
* **shardcasting**, letting echo shards work as single-use reusable casting devices for e.g. raycasts
* **stringworms**, little creatures to accompany you while hexxing
* **media pouches**, convenient & efficient media storage that recycles wasted media
* **greater reveal**, permanently displays a list of iotas in chat & deletes them when re-executed (for e.g. status indicators)
* **lani gambits**, allowing casting items to access your staffcasting stack
* **patchwork iotas**, allowing defining custom operations on a set of iotas
* **maps**, fast k→v iota storage (used by patchwork iotas)
* **nbt manipulation**, for thoroughly inspecting items & iotas
* adds `/gimmeiota` (push any iota to stack) for creative players or level 2 ops
* adds `/property` for level 4 ops

## foreign compat
* item stacks no longer get voided above 127
* i think i accidentally disabled phantoms
* hexical Hopper can be used with kinetic's Conduits; bypasses ambit

[hexcasting]: https://modrinth.com/mod/hex-casting
[siege]: https://hexic.pool.net.eu.org/siege.html

---

## development

put code in the following files:

* [`EarlyRiser.scala`](src/main/scala/org/eu/net/pool/hexic/EarlyRiser.scala) — something that needs to get loaded before launch, e.g. agents
* [`Utils.scala`](src/main/scala/org/eu/net/pool/hexic/Utils.scala) — utilities that aren't specifically related to the mod
* [`Hexic.scala`](src/main/scala/org/eu/net/pool/hexic/Hexic.scala) — anything else

## building

there are three major ways to build:

* **nix**: `nix-shell`, `gradle runDatagen`, `gradle build`
* **manual**: install aseprite, go, jujutsu, imagemagick, gradle 8.14, and gnu m4, then `gradle runDatagen; gradle build` as usual

### docker

isolated container environment gives you a guarantee of build reproducibility, if you don't care about build speed

* **simple local build**: `docker build . -f build.Dockerfile -o some/output/path/`
* **build without cloning**: `docker build https://codeberg.org/poollovernathan/hexic.git#main --build-arg BUILDKIT_CONTEXT_KEEP_GIT_DIR=1 -f build.Dockerfile -o some/output/path/`

you can optimize your builds by using the cache, which requires creating a build container: `docker buildx create --driver docker-container --name hexic-builder`

* **precached local build**: `docker build . -f build.Dockerfile -o some/output/path/ --builder hexic-builder --cache-from docker.pool.net.eu.org/hexic:cache`
* **precached build without cloning**: `docker build https://codeberg.org/poollovernathan/hexic.git#main --build-arg BUILDKIT_CONTEXT_KEEP_GIT_DIR=1 -f build.Dockerfile -o some/output/path/ --builder hexic-builder --cache-from docker.pool.net.eu.org/hexic:cache`