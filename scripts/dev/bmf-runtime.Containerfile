FROM docker.io/library/debian@sha256:c0a2ad73611131275b0e9a2e7544cfe3725f4268d4085d8c6f61fdd55aef7917

RUN printf 'deb http://deb.debian.org/debian bullseye main\n' > /etc/apt/sources.list \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       python3=3.9.2-3 python3-numpy=1:1.19.5-1 \
       ffmpeg=7:4.3.7-0+deb11u1 libavdevice58=7:4.3.7-0+deb11u1 \
       libpython3.9=3.9.2-1 \
    && rm -rf /var/lib/apt/lists/*
