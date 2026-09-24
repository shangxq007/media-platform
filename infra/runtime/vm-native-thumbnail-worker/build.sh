#!/usr/bin/env bash
set -euo pipefail
IMAGE="${1:?image tag required}"
OUT="${2:?output dir required}"
ROOT=$(mktemp -d); CID=""
trap 'if [ -n "$CID" ]; then podman rm "$CID" >/dev/null 2>&1 || true; fi; rm -rf "$ROOT"' EXIT
mkdir -p "$OUT" "$ROOT/payload/usr/local/libexec/thumbnail-worker/bin" "$ROOT/payload/usr/local/lib/thumbnail-worker" "$ROOT/payload/opt/thumbnail-worker/jre" "$ROOT/payload/app" "$ROOT/payload/usr/share/ffmpeg"
CID=$(podman create "$IMAGE")
podman cp "$CID:/app/platform-thumbnail-worker.jar" "$ROOT/payload/app/platform-thumbnail-worker.jar"
podman cp "$CID:/opt/java/openjdk/." "$ROOT/payload/opt/thumbnail-worker/jre/"
for t in bwrap ffmpeg ffprobe; do podman cp "$CID:/usr/bin/$t" "$ROOT/payload/usr/local/libexec/thumbnail-worker/bin/$t.real"; done
podman cp "$CID:/usr/share/ffmpeg/." "$ROOT/payload/usr/share/ffmpeg/" >/dev/null 2>&1 || true
# Stream image package libraries without extracting the whole image or touching host linker paths.
podman run --rm --entrypoint /bin/sh "$IMAGE" -c 'cd /usr/lib/x86_64-linux-gnu && find . -maxdepth 1 -type f -name "lib*.so*" ! -name "libc.so*" ! -name "libm.so*" ! -name "libpthread.so*" ! -name "libdl.so*" ! -name "librt.so*" ! -name "libgcc_s.so*" ! -name "libstdc++.so*" ! -name "libz.so*" ! -name "libcrypto.so*" ! -name "libssl.so*" ! -name "libselinux.so*" ! -name "libpcre2*" ! -name "libcap.so*" -print0 | tar --null -T - -cf -' | tar -xf - -C "$ROOT/payload/usr/local/lib/thumbnail-worker"
podman cp "$CID:/usr/lib/x86_64-linux-gnu/pulseaudio/libpulsecommon-15.99.so" "$ROOT/payload/usr/local/lib/thumbnail-worker/libpulsecommon-15.99.so" >/dev/null 2>&1 || true
podman cp "$CID:/usr/lib/x86_64-linux-gnu/blas/libblas.so.3.10.0" "$ROOT/payload/usr/local/lib/thumbnail-worker/libblas.so.3.10.0" >/dev/null 2>&1 || true
podman cp "$CID:/usr/lib/x86_64-linux-gnu/lapack/liblapack.so.3.10.0" "$ROOT/payload/usr/local/lib/thumbnail-worker/liblapack.so.3.10.0" >/dev/null 2>&1 || true
for lib in "$ROOT/payload/usr/local/lib/thumbnail-worker"/*.so.*.*; do
  [ -f "$lib" ] || continue
  soname=$(readelf -d "$lib" 2>/dev/null | sed -n 's/.*SONAME.*\[\([^]]*\)\].*/\1/p' | head -1)
  [ -n "$soname" ] && [ "$soname" != "$(basename "$lib")" ] && ln -sfn "$(basename "$lib")" "$ROOT/payload/usr/local/lib/thumbnail-worker/$soname"
done
for t in bwrap ffmpeg ffprobe; do
cat > "$ROOT/payload/usr/local/libexec/thumbnail-worker/bin/$t" <<EOF_WRAP
#!/bin/sh
set -eu
[ -x /usr/local/libexec/thumbnail-worker/bin/$t.real ] || exit 78
exec env LD_LIBRARY_PATH=/usr/local/lib/thumbnail-worker:${LD_LIBRARY_PATH:-} /usr/local/libexec/thumbnail-worker/bin/$t.real "\$@"
EOF_WRAP
chmod 0755 "$ROOT/payload/usr/local/libexec/thumbnail-worker/bin/$t"
done
mkdir -p "$ROOT/payload/etc/systemd/system" "$ROOT/payload/usr/local/sbin"
cat > "$ROOT/payload/etc/systemd/system/thumbnail-worker.service" <<'UNIT'
[Unit]
Description=Media thumbnail Temporal worker
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=thumbnail-worker
Group=thumbnail-worker
WorkingDirectory=/var/lib/thumbnail-worker
EnvironmentFile=-/etc/thumbnail-worker/worker.env
Environment=JAVA_HOME=/opt/thumbnail-worker/jre
Environment=PATH=/opt/thumbnail-worker/jre/bin:/usr/local/libexec/thumbnail-worker/bin:/usr/bin:/bin
Environment=THUMBNAIL_BWRAP_PATH=/usr/local/libexec/thumbnail-worker/bin/bwrap
Environment=THUMBNAIL_FFMPEG_PATH=/usr/local/libexec/thumbnail-worker/bin/ffmpeg
Environment=THUMBNAIL_FFPROBE_PATH=/usr/local/libexec/thumbnail-worker/bin/ffprobe
Environment=SPRING_MAIN_WEB_APPLICATION_TYPE=none
Environment=SPRING_PROFILES_ACTIVE=dev,temporal,thumbnail-worker
Environment=PLATFORM_RUNTIME_ROLE=WORKER
Environment=APP_TEMPORAL_TASK_QUEUE=media-platform-tasks
ExecStart=/opt/thumbnail-worker/jre/bin/java -jar /opt/thumbnail-worker/platform-thumbnail-worker.jar
Restart=on-failure
NoNewPrivileges=yes
CapabilityBoundingSet=
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/var/lib/thumbnail-worker /var/cache/thumbnail-worker
[Install]
WantedBy=multi-user.target
UNIT
cat > "$ROOT/payload/usr/local/sbin/install-thumbnail-worker-native" <<'INST'
#!/usr/bin/env bash
set -euo pipefail
id thumbnail-worker >/dev/null 2>&1 || useradd --system --uid 10001 --user-group --home-dir /var/lib/thumbnail-worker --shell /usr/sbin/nologin thumbnail-worker
install -d -o thumbnail-worker -g thumbnail-worker -m 0750 /var/lib/thumbnail-worker /var/cache/thumbnail-worker /etc/thumbnail-worker
cp -a "${ARTIFACT_ROOT:-/opt/thumbnail-worker-artifact}/payload/." /
install -d /opt/thumbnail-worker
install -m 0644 /app/platform-thumbnail-worker.jar /opt/thumbnail-worker/platform-thumbnail-worker.jar
chown -R root:root /opt/thumbnail-worker /usr/local/libexec/thumbnail-worker /usr/local/lib/thumbnail-worker /usr/share/ffmpeg /etc/systemd/system/thumbnail-worker.service
chmod 0755 /usr/local/libexec/thumbnail-worker/bin/* /usr/local/sbin/install-thumbnail-worker-native
systemctl daemon-reload
INST
chmod 0755 "$ROOT/payload/usr/local/sbin/install-thumbnail-worker-native"
ID=$(podman image inspect "$IMAGE" --format '{{.Id}}'); DIGEST=$(podman image inspect "$IMAGE" --format '{{.Digest}}')
JAR=$(sha256sum "$ROOT/payload/app/platform-thumbnail-worker.jar" | awk '{print $1}')
cat > "$ROOT/PROVENANCE.json" <<JSON
{"source_commit":"3f253b5009f0c4b552a59e8980486725649f40e3","source_tree":"0929a4ea15244af1886514f7a4ba81200cc0e7f2","image":"$IMAGE","image_id":"$ID","image_digest":"$DIGEST","image_config_id":"sha256:e900a1115da78419a2522834b6129f0e631b5e52d6b53a9c16b47f71155156c3","worker_jar_sha256":"$JAR","queue":"media-platform-tasks","namespace":"media-platform-dev","artifact_mode":"vm-native-direct-process","source_date_epoch":"${SOURCE_DATE_EPOCH:-0}"}
JSON
rm -f "$OUT/thumbnail-worker-vm-native.tar.zst" "$OUT/thumbnail-worker-vm-native.tar.zst.sha256"
tar --format=ustar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner --no-xattrs --no-acls -C "$ROOT" -cf - payload PROVENANCE.json | zstd -19 -T1 -o "$OUT/thumbnail-worker-vm-native.tar.zst" >/dev/null
sha256sum "$OUT/thumbnail-worker-vm-native.tar.zst" > "$OUT/thumbnail-worker-vm-native.tar.zst.sha256"
cp "$ROOT/PROVENANCE.json" "$OUT/PROVENANCE.json"
