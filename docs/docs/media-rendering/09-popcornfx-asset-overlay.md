# PopcornFX 粒子叠加（P4）

> **Module:** `render-module`, `effect-pack`  
> **Related:** [08-pipeline-tools-shotstack-natron-popcornfx-bento4.md](./08-pipeline-tools-shotstack-natron-popcornfx-bento4.md)

## 实现方式

平台**不**在服务端嵌入 PopcornFX 实时运行时，而是：

1. 在 PopcornFX Editor 中烘焙 **透明 WebM/MOV** 或图像序列；
2. 将资产 URI 写入时间线特效 `video.particle_overlay` 的参数 `assetPath`；
3. 通过 `javacv` / `ofx` / `ffmpeg` 合成路径叠加（`PopcornFxAssetResolver` 解析本地路径）。

## effectKey

| 参数 | 说明 |
|------|------|
| `assetPath` | 存储 URI 或本地路径（必填） |
| `opacity` | 0–1，默认 1.0 |
| `position` | `center` / `top` / `bottom`（由 OFX 合成器解释） |

## 权益

默认档位：**PRO / TEAM / ENTERPRISE**（见 `EffectMappingService`）。

## 后续

- OFX/JavaCV 合成器识别 `video.particle_overlay` 的双输入 FFmpeg `overlay` 滤镜；
- `EffectPack` 预置品牌粒子包元数据（无动态 `.pkfx` 上传）。
