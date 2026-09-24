# Thumbnail Activity worker provenance

This manifest belongs to the worker-boundary candidate and is not a publication
record. The image is not deployed by this task.

| Input | Identity |
|---|---|
| Product source commit | `16e5f1397c02cf2479135d109babe7f98f254686` |
| Product source tree | `fd9c817ae787ef90b87761ce2d383108b62d21b8` |
| Base image repository digest | `docker.io/library/eclipse-temurin@sha256:abed22bb0186ab4554c339fa41e0a361daed337b938929c95a1f2e4a228c1935` (local amd64 manifest `sha256:299fe04f214ea4b27d38f5d82dfff2a02d8c329944c687947b37e2e6f9c131e9`) |
| Base image config ID | `sha256:5e70aa3ecf3bed71dd044336bbb408e506a76f9b09cd19b4427411802d926fb9` |
| FFmpeg package | `7:4.4.2-0ubuntu0.22.04.1`; `/usr/bin/ffmpeg` SHA-256 `36d94a605d612e4090d1b8aec889d0c0801c6eafb1593c90f5c0dfd2e2966a45` |
| FFprobe | `/usr/bin/ffprobe` SHA-256 `d4f3ef9c12be756793cad83dd2004d89f49c1c4094053bfbbe7e28925c8fa4fd` |
| Bubblewrap package | `0.6.1-1ubuntu0.3`; `/usr/bin/bwrap` SHA-256 `bf6cf3d4456665f5d80c14eb79a76e0ce87b39a0883e614a161658b4ec33492c` |
| Worker jar SHA-256 | `9a2dadae883553e5ffc6477182d7e4dc3449fc82caf15bb364f29cbe8df77c74` (two consecutive builds matched) |
| Final local image | `localhost/platform-thumbnail-activity-worker:16e5f139-imagefix` (rebuild tag `16e5f139-imagefix-r2`) |
| Final local image ID | `sha256:e900a1115da78419a2522834b6129f0e631b5e52d6b53a9c16b47f71155156c3` (both builds) |
| Final local image repository digest | `sha256:fc58b1d705e620b3142dbe7a538d1c5a9c36d1b7075459a28081061130914ac7` (both builds) |
| OCI worker metadata | `Config.ExposedPorts=null`; entrypoint `java -jar /app/platform-thumbnail-worker.jar`; user `spring:spring` (UID 10001) |

The image configuration was checked by `infra/runtime/verify-thumbnail-worker-image.sh`.
It has no API port metadata, drops all runtime capabilities in the verification
run, and uses no-new-privileges. The base layer is worker-specific and does not
inherit the API image's `8080/tcp` declaration.

The image above is local-only and was not transferred to PVE or published to a
registry. No deployment is authorized by this task.
