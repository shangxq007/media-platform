# Effect Taxonomy v1 - Platform Standard

## Core Principles

1. **Crop ≠ Transform**: Crop extracts a region from source frame, discarding remaining pixels. Transform scales, rotates, or displaces the entire frame.
2. **Industry Alignment**: Follows FFmpeg, DaVinci Resolve, Premiere Pro, Final Cut Pro, GStreamer, MLT, Blender, Natron conventions
3. **OTIO Neutrality**: OTIO handles timeline exchange only, effect classification defined by platform
4. **Execution Separation**: LLM outputs schema-validated plans, Render Worker executes deterministically

## 12 Primary Effect Categories

### 1. crop
**Description**: Extract rectangular region from source frame, discard remaining pixels
**Characteristics**: 
- Independent from transform operations
- Source frame coordinate space
- Canonical model: `sourceRegion` (x, y, width, height)
- Industry: FFmpeg `crop`, DaVinci Resolve "Crop", Premiere "Crop", FCP "Crop"

**Examples**:
- `video.crop` - Basic rectangular crop
- `video.smart_crop` - AI-guided crop detection
- `video.safe_area_crop` - Broadcast safe area extraction

### 2. transform
**Description**: Geometric operations on entire frame (scale, rotate, translate, flip)
**Characteristics**:
- Operates on complete frame
- Preserves all pixels (no discarding)
- Canvas coordinate space for positioning
- Industry: FFmpeg `scale`, `rotate`, `transpose`, `hflip`, `vflip`

**Examples**:
- `video.scale` - Resize frame
- `video.rotate` - Rotate frame
- `video.flip_horizontal` - Mirror horizontally
- `video.flip_vertical` - Mirror vertically
- `video.translate` - Position frame within canvas

### 3. color
**Description**: Color space and tone adjustments
**Characteristics**:
- RGB/YUV color space operations
- Brightness, contrast, saturation adjustments
- LUT and color grade operations
- Industry: FFmpeg `eq`, `hue`, `colorbalance`, `curves`

**Examples**:
- `video.brightness` - Lightness adjustment
- `video.contrast` - Contrast adjustment
- `video.saturation` - Color intensity
- `video.hue` - Color shift
- `video.white_balance` - Color temperature correction
- `video.lut` - Look-up table application
- `video.curves` - RGB/Luma curves

### 4. filter
**Description**: Pixel-level processing and convolution effects
**Characteristics**:
- Spatial domain processing
- Kernel-based operations
- Frequency domain effects
- Industry: FFmpeg `blur`, `sharpen`, `vignette`, `convolution`

**Examples**:
- `video.blur` - Gaussian blur
- `video.sharpen` - Sharpening filter
- `video.vignette` - Radial falloff
- `video.chromatic` - Chromatic aberration
- `video.grayscale` - Black and white conversion
- `video.sepia` - Sepia tone
- `video.denoise` - Noise reduction
- `video.edge_detect` - Edge detection

### 5. composite
**Description**: Layer blending and compositing operations
**Characteristics**:
- Multi-layer operations
- Blend modes and transparency
- Overlay and positioning
- Industry: FFmpeg `overlay`, `blend`, `colorchannelmixer`

**Examples**:
- `video.overlay` - Image/video overlay
- `video.watermark` - Watermark application
- `video.pip` - Picture-in-picture
- `video.blend_mode` - Alpha blending
- `video.mask` - Alpha masking
- `video.text_overlay` - Text rendering
- `video.opacity` - Transparency control

### 6. keying
**Description**: Background removal and keying operations
**Characteristics**:
- Chroma/luma key extraction
- Background replacement
- Matte generation
- Industry: FFmpeg `colorkey`, `lumakey`, `alphasplit`

**Examples**:
- `video.chroma_key` - Chroma keying
- `video.luma_key` - Luma keying
- `video.color_key` - Color-based keying
- `video.background_removal` - AI background removal
- `video.matte_generation` - Matte creation

### 7. deform
**Description**: Non-linear geometric transformations
**Characteristics**:
- Perspective and warping operations
- Non-linear geometry
- Lens correction
- Industry: FFmpeg `perspective`, `lenscorrection`, `warp`

**Examples**:
- `video.perspective` - Perspective correction
- `video.corner_pin` - Corner pin mapping
- `video.fisheye` - Fisheye effect
- `video.wave` - Wave distortion
- `video.lens_correction` - Lens distortion correction
- `video.warp` - Arbitrary warping

### 8. text
**Description**: Text rendering and subtitle operations
**Characteristics**:
- Text rendering and positioning
- Subtitle timing and styling
- Text animation
- Industry: FFmpeg `drawtext`, `subtitles`

**Examples**:
- `text.subtitle_burn_in` - Hardcoded subtitles
- `text.overlay` - Text overlay
- `text.title` - Title cards
- `text.lower_third` - Lower thirds
- `text.animation` - Animated text

### 9. vfx
**Description**: Visual effects and generated content
**Characteristics**:
- Particle systems
- Generated visual elements
- Special effects
- Industry: FFmpeg `frei0r`, `ladspa`, `neon`

**Examples**:
- `video.particle_overlay` - Particle effects
- `video.light_effects` - Light rays and glows
- `video.glitch` - Digital glitch effects
- `video.rain` - Rain effect
- `video.snow` - Snow effect
- `video.fire` - Fire simulation
- `video.smoke` - Smoke simulation

### 10. temporal
**Description**: Time-based operations and temporal effects
**Characteristics**:
- Frame rate changes
- Time manipulation
- Frame interpolation
- Industry: FFmpeg `fps`, `minterpolate`, `select`

**Examples**:
- `video.fade_in` - Fade in transition
- `video.fade_out` - Fade out transition
- `video.speed` - Speed change
- `video.reverse` - Reverse playback
- `video.frame_blend` - Frame interpolation
- `video.time_remap` - Time remapping
- `video.freeze_frame` - Freeze frame

### 11. transition
**Description**: Clip-to-clip transitions and effects
**Characteristics**:
- Requires adjacent or overlapping clips
- Timeline-based operations
- Cross-clip effects
- Industry: FFmpeg `xfade`, `blend`, `fade`

**Examples**:
- `video.cross_dissolve` - Cross dissolve
- `video.wipe` - Wipe transition
- `video.slide` - Slide transition
- `video.zoom` - Zoom transition
- `video.dissolve` - Dissolve transition

### 12. audio
**Description**: Audio processing and effects
**Characteristics**:
- Audio signal processing
- Volume and dynamics control
- Audio filtering
- Industry: FFmpeg `volume`, `afilter`, `dynaudnorm`

**Examples**:
- `audio.volume` - Volume adjustment
- `audio.fade_in` - Audio fade in
- `audio.fade_out` - Audio fade out
- `audio.equalizer` - Audio equalization
- `audio.compression` - Dynamic range compression
- `audio.denoise` - Audio noise reduction
- `audio.reverb` - Reverb effect
- `audio.ducking` - Audio ducking

## Non-Effect Operations

### RenderOperation / RenderStrategy / Packaging
These are not effects but operations that control rendering, packaging, or delivery.

#### Packaging Operations
- `video.dash_drm` - DASH packaging with DRM
- `video.hls_packaging` - HLS packaging
- `video.drm_packaging` - DRM protection
- `video.delivery` - Content delivery

#### Cloud Rendering Operations
- `video.shotstack_template` - Shotstack cloud rendering
- `video.remotion_template` - Remotion cloud rendering
- `video.blender_scene` - Blender cloud rendering
- `video.provider_render` - Generic provider rendering

#### Infrastructure Operations
- `video.cache` - Caching operations
- `video.webhook_callback` - Webhook notifications
- `video.metadata_extraction` - Metadata extraction

## Classification Rules

### Effect vs Operation Determination
1. **Effects**: Transform visual/audio content properties
2. **Operations**: Control rendering, packaging, or delivery

### Category Assignment Rules
1. **crop**: Always separate from transform
2. **transform**: Scale, rotate, translate, flip operations
3. **color**: Color space and tone adjustments
4. **filter**: Pixel-level processing
5. **composite**: Multi-layer operations
6. **keying**: Background removal
7. **deform**: Non-linear geometry
8. **text**: Text rendering
9. **vfx**: Generated visual effects
10. **temporal**: Time-based operations
11. **transition**: Clip-to-clip effects
12. **audio**: Audio processing

### Special Cases
- **fade_in/fade_out**: temporal (not transition)
- **overlay/pip**: composite (not video)
- **watermark**: composite (not video)
- **blur/vignette**: filter (not video)
- **brightness/contrast**: color (not video)

## Migration Guidelines

### Backward Compatibility
- Keep existing `effectKey` unchanged
- Maintain `category` field for legacy compatibility
- Add `taxonomyCategory` field for new classification
- UI should use taxonomyCategory with fallback to category

### Display Priority
1. Use `taxonomyCategory` if available
2. Fall back to `category` for legacy effects
3. Map legacy categories to taxonomy categories
4. Unknown effects show in "Other" category

### Provider Mapping
- Effect providers remain unchanged
- Provider selection based on effect capabilities
- New effects may require new provider implementations

## Industry References

### Supported Tools
- **FFmpeg**: crop, scale, rotate, blur, overlay, etc.
- **DaVinci Resolve**: Crop, Transform, Color, Fusion effects
- **Premiere Pro**: Video Effects, Transitions, Effects
- **Final Cut Pro**: Effects, Transitions, Filters
- **GStreamer**: videoconvert, videocrop, videoflip, etc.
- **MLT**: melt filter chain
- **Blender**: Compositor nodes
- **Natron**: OFX effects

### Standards Compliance
- No ISO/ITU standard exists for effect classification
- Industry converges to 8-12 primary categories
- Platform follows industry best practices
- OTIO remains neutral on effect classification