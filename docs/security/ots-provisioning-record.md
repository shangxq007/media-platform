# OTS Provisioning Record

- provisioned-at: 2026-10-05T05:53:57Z
- provisioned-by: agent (Owner-authorized)
- method: A2 (source build)
- install-status: STAGED — `sudo install` to /usr/bin PENDING (sudo requires a password)

## Binary (staged)
- staged-path: /tmp/ots-build/ots/build/ots-sanitize
- target-path: /usr/bin/ots-sanitize (not yet installed)
- sha256: d2fe31741858c39b4a7308a15b25e163befb6ba5fba3e88eb8f5d27e850be07d
- file: ELF 64-bit LSB executable, x86-64, version 1 (GNU/Linux), dynamically linked, interpreter /lib64/ld-linux-x86-64.so.2, BuildID[sha1]=c888f52ec7703fe7149f2ec744c1bac01fbff4f1, for GNU/Linux 4.3.0, with debug_info, not stripped
- size: 770168 bytes

## Version
ots 9.3.0

## Provenance
- source: https://github.com/khaledhosny/ots.git
- commit: 62634335137d86473c84b20a8de9b9d97645bbd6
- build-env: NAME="openSUSE Tumbleweed"
- meson: 1.12.1
- build dir: /tmp/ots-build/ots (retained for audit)

## Dependencies
- system: meson (installed by Owner), freetype2-devel (installed by Owner), ninja, gcc, pkg-config, zlib
- woff2: satisfied via meson wrap `subprojects/google-woff2.wrap` (woff2 1.0.2),
  because `libwoff2dec-devel` does not exist on openSUSE Tumbleweed
  (the distro package is `woff2-devel`); no extra system package was installed
- gtest: built from wrap subproject (tests only)

## Test Results
- valid TTF (/usr/share/fonts/truetype/Roboto-Light.ttf): exit=0
  - stdout: File sanitized successfully!
  - out file: /tmp/ots-valid-out.ttf (312996 bytes)
  - stderr: (empty)
- malformed (head -c 2000 of the TTF): exit=1
  - stderr: ERROR: GDEF: invalid table offset

## Remaining Owner Action
    sudo install -m 0755 /tmp/ots-build/ots/build/ots-sanitize /usr/bin/ots-sanitize

## Archive Note (added at Phase 5 archival — supersedes the staged status above)
- install-path: /usr/bin/ots-sanitize
- install-status: INSTALLED
- install-verified: 2026-10-05; `/usr/bin/ots-sanitize` sha256 = d2fe31741858c39b4a7308a15b25e163befb6ba5fba3e88eb8f5d27e850be07d (matches the staged binary)
- platform: Linux x86_64 (openSUSE Tumbleweed)
- sandbox: LocalSandboxProcess (bubblewrap), isolated file-in/file-out
