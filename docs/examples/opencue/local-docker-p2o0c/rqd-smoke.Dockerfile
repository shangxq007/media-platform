# P2O.0c — Local Smoke-Only RQD Image
# Extends official OpenCue RQD with ffmpeg/ffprobe for smoke testing.
# Example-only. Operator-run. Not production. Not pushed.
#
# Build:
#   docker build -f docs/examples/opencue/local-docker-p2o0c/rqd-smoke.Dockerfile -t opencue-rqd-smoke:local .
#
# Base image opencue/rqd:1.19.1 is the Rust-based openrqd.
# Entrypoint: /app/openrqd
# Config: ~/.local/share/rqd.yaml (YAML format, read by openrqd)
# Key config field: grpc.cuebot_endpoints — defaults to ["localhost:8443"] if absent.

FROM opencue/rqd:1.19.1

# Install ffmpeg/ffprobe via apt
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Verify installation
RUN ffmpeg -version && ffprobe -version

# Create config pointing RQD to cuebot service on the Docker network
# openrqd reads ~/.local/share/rqd.yaml
# grpc.cuebot_endpoints is the key field for cuebot connection
RUN mkdir -p /root/.local/share && \
    printf 'grpc:\n  cuebot_endpoints:\n    - "cuebot:8443"\n' > /root/.local/share/rqd.yaml
