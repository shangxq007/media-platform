#!/usr/bin/env bash
# test-assets/golden-render-project-v1/scripts/generate-assets.sh
# Generate synthetic assets for Golden Render Project v1.
#
# Usage: bash scripts/generate-assets.sh
#
# Requirements:
#   - ffmpeg (will error gracefully if missing)
#   - bash 4+
#
# This script is idempotent and network-free.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ASSETS_DIR="${PROJECT_DIR}/assets"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# Check ffmpeg
if ! command -v ffmpeg &>/dev/null; then
  log_error "ffmpeg is not installed. Install it and retry."
  exit 1
fi

FFMPEG="ffmpeg -y -hide_banner -loglevel warning"

# Common video params
FPS=30
VIDEO_CODEC="libx264"
AUDIO_CODEC="aac"
PIX_FMT="yuv420p"

mkdir -p "${ASSETS_DIR}/video" "${ASSETS_DIR}/image" "${ASSETS_DIR}/audio" "${ASSETS_DIR}/subtitle"

# ─── Video Assets ───

log_info "Generating color_bars_1080p.mp4..."
${FFMPEG} -f lavfi -i "smptebars=s=1920x1080:r=${FPS}:d=10" \
  -c:v ${VIDEO_CODEC} -pix_fmt ${PIX_FMT} -movflags +faststart \
  "${ASSETS_DIR}/video/color_bars_1080p.mp4"

log_info "Generating grid_motion_1080p.mp4..."
${FFMPEG} -f lavfi -i "testsrc2=s=1920x1080:r=${FPS}:d=10" \
  -vf "drawgrid=w=192:h=192:t=2:c=white@0.3, \
       drawbox=x='100+100*t':y=200:w=200:h=150:t=3:c=red@0.6" \
  -c:v ${VIDEO_CODEC} -pix_fmt ${PIX_FMT} -movflags +faststart \
  "${ASSETS_DIR}/video/grid_motion_1080p.mp4"

log_info "Generating moving_box_1080p.mp4..."
${FFMPEG} -f lavfi -i "color=c=black:s=1920x1080:r=${FPS}:d=10" \
  -vf "drawbox=x='mod(t*200,1720)':y=400:w=200:h=200:t=4:c=green@0.8" \
  -c:v ${VIDEO_CODEC} -pix_fmt ${PIX_FMT} -movflags +faststart \
  "${ASSETS_DIR}/video/moving_box_1080p.mp4"

log_info "Generating portrait_test_1080x1920.mp4..."
${FFMPEG} -f lavfi -i "testsrc2=s=1080x1920:r=${FPS}:d=10" \
  -vf "drawgrid=w=108:h=108:t=2:c=white@0.2" \
  -c:v ${VIDEO_CODEC} -pix_fmt ${PIX_FMT} -movflags +faststart \
  "${ASSETS_DIR}/video/portrait_test_1080x1920.mp4"

log_info "Generating square_test_1080x1080.mp4..."
${FFMPEG} -f lavfi -i "testsrc2=s=1080x1080:r=${FPS}:d=8" \
  -vf "drawgrid=w=108:h=108:t=2:c=white@0.2" \
  -c:v ${VIDEO_CODEC} -pix_fmt ${PIX_FMT} -movflags +faststart \
  "${ASSETS_DIR}/video/square_test_1080x1080.mp4"

log_info "Generating green_screen_test.mp4..."
${FFMPEG} -f lavfi -i "color=c=0x00FF00:s=1920x1080:r=${FPS}:d=6" \
  -vf "drawbox=x='mod(t*150,1600)':y=300:w=320:h=480:t=5:c=white@0.7" \
  -c:v ${VIDEO_CODEC} -pix_fmt ${PIX_FMT} -movflags +faststart \
  "${ASSETS_DIR}/video/green_screen_test.mp4"

# ─── Image Assets ───

log_info "Generating logo_transparent.png..."
${FFMPEG} -f lavfi -i "color=c=black@0:s=512x512" \
  -vf "drawbox=128:128:256:256:t=8:c=white@0.9, \
       drawtext=text='MP':fontsize=120:x=(w-text_w)/2:y=(h-text_h)/2:fontcolor=black@0.8:enable='1'" \
  -frames:v 1 \
  "${ASSETS_DIR}/image/logo_transparent.png" 2>/dev/null || \
  ${FFMPEG} -f lavfi -i "color=c=white@0:s=512x512,format=rgba" \
    -vf "drawbox=128:128:256:256:t=8:c=blue@0.8" \
    -frames:v 1 \
    "${ASSETS_DIR}/image/logo_transparent.png"

log_info "Generating product_card.png..."
${FFMPEG} -f lavfi -i "color=c=lightblue:s=1000x1000" \
  -vf "drawbox=100:100:800:800:t=4:c=white@0.5, \
       drawgrid=w=100:h=100:t=1:c=gray@0.2" \
  -frames:v 1 \
  "${ASSETS_DIR}/image/product_card.png"

log_info "Generating lower_third_bg.png..."
${FFMPEG} -f lavfi -i "color=c=navy@0.6:s=1920x300" \
  -frames:v 1 \
  "${ASSETS_DIR}/image/lower_third_bg.png"

log_info "Generating mask_shape.png..."
${FFMPEG} -f lavfi -i "color=c=black:s=512x512" \
  -vf "drawbox=64:64:384:384:t=fill:c=white" \
  -frames:v 1 \
  "${ASSETS_DIR}/image/mask_shape.png"

# ─── Audio Assets ───

log_info "Generating music_bgm.wav..."
${FFMPEG} -f lavfi -i "sine=frequency=440:duration=30:sample_rate=48000" \
  -f lavfi -i "sine=frequency=554:duration=30:sample_rate=48000" \
  -f lavfi -i "sine=frequency=659:duration=30:sample_rate=48000" \
  -filter_complex "[0][1][2]amix=inputs=3:duration=first,volume=0.3" \
  -ac 2 \
  "${ASSETS_DIR}/audio/music_bgm.wav"

log_info "Generating voiceover.wav..."
${FFMPEG} -f lavfi -i "sine=frequency=262:duration=15:sample_rate=48000" \
  -af "volume=0.5" \
  -ac 1 \
  "${ASSETS_DIR}/audio/voiceover.wav"

log_info "Generating sfx_click.wav..."
${FFMPEG} -f lavfi -i "sine=frequency=1000:duration=0.5:sample_rate=48000" \
  -af "volume=0.8" \
  -ac 1 \
  "${ASSETS_DIR}/audio/sfx_click.wav"

log_info "Generating sfx_whoosh.wav..."
${FFMPEG} -f lavfi -i "anoisesrc=color=pink:duration=1.5:sample_rate=48000" \
  -af "volume=0.4,highpass=f=500,lowpass=f=4000" \
  -ac 1 \
  "${ASSETS_DIR}/audio/sfx_whoosh.wav"

# ─── Subtitle Assets ───

log_info "Generating subtitles_zh.srt..."
cat > "${ASSETS_DIR}/subtitle/subtitles_zh.srt" << 'SRTEOF'
1
00:00:01,000 --> 00:00:04,000
欢迎使用媒体平台

2
00:00:05,000 --> 00:00:08,000
这是一个标准验收工程
用于验证渲染能力

3
00:00:10,000 --> 00:00:14,000
支持多轨道视频合成
包括转场、滤镜、字幕烧录

4
00:00:16,000 --> 00:00:20,000
音频轨道支持背景音乐
音效和旁白混合

5
00:00:22,000 --> 00:00:26,000
空间坐标系统支持
裁剪、变换、合成操作

6
00:00:27,000 --> 00:00:30,000
感谢使用媒体平台
SRTEOF

log_info "Generating subtitles_en.srt..."
cat > "${ASSETS_DIR}/subtitle/subtitles_en.srt" << 'SRTEOF'
1
00:00:01,000 --> 00:00:04,000
Welcome to the Media Platform

2
00:00:05,000 --> 00:00:08,000
This is a standard acceptance project
for validating render capabilities

3
00:00:10,000 --> 00:00:14,000
Supports multi-track video compositing
including transitions, filters, subtitle burn-in

4
00:00:16,000 --> 00:00:20,000
Audio tracks support background music
sound effects and voiceover mixing

5
00:00:22,000 --> 00:00:26,000
Spatial coordinate system supports
crop, transform, composite operations

6
00:00:27,000 --> 00:00:30,000
Thank you for using the Media Platform
SRTEOF

log_info "Generating subtitles_webvtt.vtt..."
cat > "${ASSETS_DIR}/subtitle/subtitles_webvtt.vtt" << 'SRTEOF'
WEBVTT

00:00:01.000 --> 00:00:04.000
欢迎使用媒体平台

00:00:05.000 --> 00:00:08.000
这是一个标准验收工程
用于验证渲染能力

00:00:10.000 --> 00:00:14.000
支持多轨道视频合成
包括转场、滤镜、字幕烧录

00:00:16.000 --> 00:00:20.000
音频轨道支持背景音乐
音效和旁白混合

00:00:22.000 --> 00:00:26.000
空间坐标系统支持
裁剪、变换、合成操作

00:00:27.000 --> 00:00:30.000
感谢使用媒体平台
SRTEOF

log_info "All synthetic assets generated successfully."
log_info "Assets directory: ${ASSETS_DIR}"
