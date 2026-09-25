#!/usr/bin/env bash
# Builds the OCI cover-image worker image and the vm-native deployment artifact.
#
# Independent of the thumbnail build script: it never reads, writes or rebuilds thumbnail artifacts.
set -euo pipefail

IMAGE="${1:?image tag required}"
OUT="${2:?output dir required}"
ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
JAR="$ROOT/platform-app/build/libs/platform-cover-image-worker.jar"

[ -f "$JAR" ] || {
  echo "missing $JAR — run: ./gradlew :platform-app:coverImageWorkerBootJar" >&2
  exit 2
}

mkdir -p "$OUT"

# --- OCI image ---------------------------------------------------------------
podman build -f "$ROOT/infra/docker/Dockerfile.cover-image-worker" -t "$IMAGE" "$ROOT/infra/docker" \
  --build-context "jar=$JAR" 2>/dev/null || {
    # Plain build: stage the two build inputs next to the Dockerfile, build, then clean up.
    STAGE=$(mktemp -d)
    trap 'rm -rf "$STAGE"' EXIT
    cp "$JAR" "$STAGE/platform-cover-image-worker.jar"
    cp "$ROOT/infra/docker/cover-image-worker-override.yml" "$STAGE/cover-image-worker-override.yml"
    podman build -f "$ROOT/infra/docker/Dockerfile.cover-image-worker" -t "$IMAGE" "$STAGE"
  }

bash "$ROOT/infra/runtime/verify-cover-image-worker-image.sh" "$IMAGE"

# --- vm-native artifact ------------------------------------------------------
NATIVE=$(mktemp -d)
trap 'rm -rf "$NATIVE"' EXIT
mkdir -p "$NATIVE/payload/usr/local/libexec/cover-image-worker/bin" \
         "$NATIVE/payload/usr/local/lib/cover-image-worker" \
         "$NATIVE/payload/opt/cover-image-worker/jre" \
         "$NATIVE/payload/app"
CID=$(podman create "$IMAGE")
podman cp "$CID:/app/platform-cover-image-worker.jar" "$NATIVE/payload/app/platform-cover-image-worker.jar"
podman cp "$CID:/app/cover-image-worker-override.yml" "$NATIVE/payload/app/cover-image-worker-override.yml"
podman cp "$CID:/opt/java/openjdk/." "$NATIVE/payload/opt/cover-image-worker/jre/"
for tool in bwrap ffmpeg ffprobe; do
  podman cp "$CID:/usr/bin/$tool" "$NATIVE/payload/usr/local/libexec/cover-image-worker/bin/$tool.real"
done
podman run --rm --entrypoint /bin/sh "$IMAGE" -c \
  'cd /usr/lib/x86_64-linux-gnu && find . -maxdepth 1 -type f -name "lib*.so*" ! -name "libc.so*" ! -name "libm.so*" ! -name "libpthread.so*" ! -name "libdl.so*" ! -name "librt.so*" ! -name "libgcc_s.so*" ! -name "libstdc++.so*" ! -name "libz.so*" ! -name "libcrypto.so*" ! -name "libssl.so*" ! -name "libselinux.so*" ! -name "libpcre2*" ! -name "libcap.so*" -print0 | tar --null -T - -cf -' \
  | tar -xf - -C "$NATIVE/payload/usr/local/lib/cover-image-worker"
podman rm "$CID" >/dev/null
for tool in bwrap ffmpeg ffprobe; do
  cat > "$NATIVE/payload/usr/local/libexec/cover-image-worker/bin/$tool" <<EOF_WRAPPER
#!/bin/sh
set -eu
[ -x /usr/local/libexec/cover-image-worker/bin/$tool.real ] || exit 78
exec env LD_LIBRARY_PATH=/usr/local/lib/cover-image-worker:\${LD_LIBRARY_PATH:-} /usr/local/libexec/cover-image-worker/bin/$tool.real "\$@"
EOF_WRAPPER
  chmod 0755 "$NATIVE/payload/usr/local/libexec/cover-image-worker/bin/$tool"
done

mkdir -p "$NATIVE/payload/etc/systemd/system" "$NATIVE/payload/usr/local/sbin"
cat > "$NATIVE/payload/etc/systemd/system/cover-image-worker.service" <<'UNIT'
[Unit]
Description=Media cover-image Temporal worker
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=cover-image-worker
Group=cover-image-worker
WorkingDirectory=/var/lib/cover-image-worker
EnvironmentFile=-/etc/cover-image-worker/worker.env
Environment=JAVA_HOME=/opt/cover-image-worker/jre
Environment=PATH=/opt/cover-image-worker/jre/bin:/usr/local/libexec/cover-image-worker/bin:/usr/bin:/bin
Environment=SPRING_PROFILES_ACTIVE=dev,temporal,cover-image-worker
Environment=SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/opt/cover-image-worker/cover-image-worker-override.yml
Environment=SPRING_MAIN_WEB_APPLICATION_TYPE=none
Environment=PLATFORM_RUNTIME_ROLE=WORKER
Environment=APP_TEMPORAL_TASK_QUEUE=media-platform-tasks
Environment=COVER_IMAGE_BWRAP_PATH=/usr/local/libexec/cover-image-worker/bin/bwrap
Environment=COVER_IMAGE_FFMPEG_PATH=/usr/local/libexec/cover-image-worker/bin/ffmpeg
ExecStart=/opt/cover-image-worker/jre/bin/java -jar /opt/cover-image-worker/platform-cover-image-worker.jar
Restart=on-failure
NoNewPrivileges=yes
CapabilityBoundingSet=
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/var/lib/cover-image-worker
[Install]
WantedBy=multi-user.target
UNIT

cat > "$NATIVE/payload/usr/local/sbin/install-cover-image-worker-native" <<'INSTALL'
#!/usr/bin/env bash
set -euo pipefail
id cover-image-worker >/dev/null 2>&1 || useradd --system --uid 10001 --user-group \
  --home-dir /var/lib/cover-image-worker --shell /usr/sbin/nologin cover-image-worker
install -d -o cover-image-worker -g cover-image-worker -m 0750 \
  /var/lib/cover-image-worker /etc/cover-image-worker
cp -a "${ARTIFACT_ROOT:-/opt/cover-image-worker-artifact}/payload/." /
install -d /opt/cover-image-worker
install -m 0644 /app/platform-cover-image-worker.jar /opt/cover-image-worker/platform-cover-image-worker.jar
install -m 0644 /app/cover-image-worker-override.yml /opt/cover-image-worker/cover-image-worker-override.yml
chown -R root:root /opt/cover-image-worker /usr/local/libexec/cover-image-worker \
  /usr/local/lib/cover-image-worker /etc/systemd/system/cover-image-worker.service
chmod 0755 /usr/local/libexec/cover-image-worker/bin/* /usr/local/sbin/install-cover-image-worker-native
systemctl daemon-reload
INSTALL
chmod 0755 "$NATIVE/payload/usr/local/sbin/install-cover-image-worker-native"

IMAGE_ID=$(podman image inspect "$IMAGE" --format '{{.Id}}')
JAR_SHA=$(sha256sum "$NATIVE/payload/app/platform-cover-image-worker.jar" | awk '{print $1}')
cat > "$NATIVE/PROVENANCE.json" <<JSON
{"image":"$IMAGE","image_id":"$IMAGE_ID","worker_jar_sha256":"$JAR_SHA","queue":"media-platform-tasks","namespace":"media-platform-dev","artifact_mode":"vm-native-direct-process","source_date_epoch":"${SOURCE_DATE_EPOCH:-0}"}
JSON

rm -f "$OUT/cover-image-worker-vm-native.tar.zst" "$OUT/cover-image-worker-vm-native.tar.zst.sha256"
tar --format=ustar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner \
  --no-xattrs --no-acls -C "$NATIVE" -cf - payload PROVENANCE.json \
  | zstd -19 -T1 -o "$OUT/cover-image-worker-vm-native.tar.zst" >/dev/null
sha256sum "$OUT/cover-image-worker-vm-native.tar.zst" > "$OUT/cover-image-worker-vm-native.tar.zst.sha256"
cp "$NATIVE/PROVENANCE.json" "$OUT/PROVENANCE.json"
echo "cover-image worker image + vm-native artifact built: $IMAGE"
