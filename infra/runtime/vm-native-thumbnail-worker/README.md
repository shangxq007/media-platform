# VM-native Thumbnail Worker

`build.sh` extracts only the approved worker JAR, Java runtime, FFmpeg/ffprobe/Bubblewrap and private runtime libraries from the approved local image. Libraries are installed below `/usr/local/lib/thumbnail-worker`; no `/lib`, `/usr/lib`, `/etc/ld.so.conf.d` or global linker cache is changed. Tool wrappers set `LD_LIBRARY_PATH` only for the worker subprocess.

Run twice with `SOURCE_DATE_EPOCH=0 podman unshare .../build.sh ...` and compare archive SHA-256 values. The artifact is not a deployment approval and does not contain credentials.
