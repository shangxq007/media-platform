# Effect Migration Mapping - Current System to Taxonomy v1

## Migration Principles

1. **Preserve Compatibility**: Keep existing `effectKey` and `category` fields
2. **Add New Field**: Introduce `taxonomyCategory` for new classification
3. **Gradual Migration**: Allow UI to use new classification while maintaining backward compatibility
4. **Clear Separation**: Distinguish effects from operations

## Current System Effects (27 total)

### Video Transitions → temporal (7 effects)

| effectKey | oldCategory | newCategory | isEffect | Notes |
|-----------|-------------|-------------|----------|-------|
| `video.fade_in` | transition | temporal | ✅ | Time-based fade, not clip transition |
| `video.fade_out` | transition | temporal | ✅ | Time-based fade, not clip transition |
| `video.cross_dissolve` | transition | transition | ✅ | Requires adjacent clips |
| `video.dissolve` | transition | transition | ✅ | Requires adjacent clips |
| `video.wipe` | transition | transition | ✅ | Requires adjacent clips |
| `video.slide` | transition | transition | ✅ | Requires adjacent clips |
| `video.zoom` | transition | transition | ✅ | Clip-to-zoom transition |

### Video Filters → filter/color (12 effects)

| effectKey | oldCategory | newCategory | isEffect | Notes |
|-----------|-------------|-------------|----------|-------|
| `video.blur` | video | filter | ✅ | Pixel-level blur filter |
| `video.sharpen` | video | filter | ✅ | Pixel-level sharpening |
| `video.vignette` | video | filter | ✅ | Radial falloff filter |
| `video.natron_vignette` | video | filter | ✅ | Advanced vignette filter |
| `video.natron_color_grade` | video | color | ✅ | Color grading |
| `video.chromatic` | video | filter | ✅ | Chromatic aberration |
| `video.brightness` | video | color | ✅ | Color brightness adjustment |
| `video.contrast` | video | color | ✅ | Color contrast adjustment |
| `video.grayscale` | video | color | ✅ | Color space conversion |
| `video.sepia` | video | color | ✅ | Color tone effect |
| `video.particle_overlay` | video | vfx | ✅ | Generated particle effects |
| `video.watermark` | video | composite | ✅ | Overlay compositing |

### Compositing Operations → composite (2 effects)

| effectKey | oldCategory | newCategory | isEffect | Notes |
|-----------|-------------|-------------|----------|-------|
| `video.overlay` | compositor | composite | ✅ | Multi-layer overlay |
| `video.pip` | compositor | composite | ✅ | Picture-in-picture compositing |

### Text Effects → text (2 effects)

| effectKey | oldCategory | newCategory | isEffect | Notes |
|-----------|-------------|-------------|----------|-------|
| `text.subtitle_burn_in` | text | text | ✅ | Text rendering |
| `text.overlay` | text | text | ✅ | Text overlay |

### Audio Effects → audio (1 effect)

| effectKey | oldCategory | newCategory | isEffect | Notes |
|-----------|-------------|-------------|----------|-------|
| `audio.volume` | audio | audio | ✅ | Audio volume adjustment |

### Non-Effect Operations → operation (5 effects)

| effectKey | oldCategory | newCategory | isEffect | Notes |
|-----------|-------------|-------------|----------|-------|
| `video.dash_drm` | video | operation | ❌ | DRM packaging operation |
| `video.shotstack_template` | video | operation | ❌ | Cloud rendering operation |
| `video.remotion_template` | video | operation | ❌ | Cloud rendering operation |
| `video.blender_scene` | video | operation | ❌ | Cloud rendering operation |
| `video.particle_overlay` | video | vfx | ✅ | Generated visual effects |

## Summary Statistics

### Effect Categories
- **temporal**: 2 effects (fade_in, fade_out)
- **transition**: 5 effects (cross_dissolve, dissolve, wipe, slide, zoom)
- **filter**: 4 effects (blur, sharpen, vignette, chromatic)
- **color**: 4 effects (natron_color_grade, brightness, contrast, grayscale, sepia)
- **vfx**: 1 effect (particle_overlay)
- **composite**: 2 effects (overlay, pip)
- **text**: 2 effects (subtitle_burn_in, overlay)
- **audio**: 1 effect (volume)

### Non-Effect Operations
- **operation**: 4 effects (dash_drm, shotstack_template, remotion_template, blender_scene)

### Migration Impact
- **Actual Effects**: 22 effects (reclassified from 27)
- **Non-Effects**: 5 operations (removed from effect category)
- **Net Reduction**: 5 effects reclassified as operations

## Missing Effects in Current System

Based on Taxonomy v1, the following key effect categories are missing:

### Transform Effects (Missing)
- `video.scale` - Scaling
- `video.rotate` - Rotation
- `video.flip_horizontal` - Horizontal flip
- `video.flip_vertical` - Vertical flip
- `video.translate` - Translation

### Keying Effects (Missing)
- `video.chroma_key` - Chroma keying
- `video.luma_key` - Luma keying
- `video.background_removal` - AI background removal

### Deform Effects (Missing)
- `video.perspective` - Perspective correction
- `video.corner_pin` - Corner pin mapping
- `video.fisheye` - Fisheye effect

### Enhanced Audio Effects (Missing)
- `audio.equalizer` - Audio equalization
- `audio.compression` - Dynamic range compression
- `audio.reverb` - Reverb effect

## Implementation Strategy

### Phase 1: Taxonomy Integration
1. Add `taxonomyCategory` field to `EffectDescriptor`
2. Update frontend to use taxonomy-based categorization
3. Maintain backward compatibility with existing `category` field
4. Create mapping functions for legacy effects

### Phase 2: UI Updates
1. Update EffectsPanel to show 12 categories instead of 4
2. Add "Operations" section for non-effect operations
3. Implement tier-based filtering per category
4. Add visual indicators for effect types

### Phase 3: Missing Effects
1. Implement missing transform effects
2. Add keying effects
3. Implement deform effects
4. Enhance audio effects

### Phase 4: Advanced Features
1. Add crop effect with coordinate system
2. Implement complex compositing operations
3. Add temporal effects with time remapping
4. Implement VFX with particle systems

## Provider Compatibility

### Existing Providers
- **ffmpeg**: Supports most basic effects (blur, brightness, contrast, etc.)
- **javacv**: Supports advanced effects (vignette, watermark, etc.)
- **gstreamer**: Supports compositing operations
- **mlt**: Supports complex effect chains
- **natron**: Supports color grading effects
- **shotstack**: Supports cloud rendering operations
- **remotion**: Supports cloud rendering operations
- **blender**: Supports 3D rendering operations

### New Provider Requirements
- **transform effects**: Require scale, rotate, flip support
- **keying effects**: Require chroma/luma key support
- **deform effects**: Require perspective/warp support
- **crop effect**: Requires crop filter support

## Testing Strategy

### Unit Tests
1. **EffectTaxonomyMappingTest**: Verify correct category mapping
2. **EffectDescriptorTest**: Validate taxonomy field integration
3. **LegacyCompatibilityTest**: Ensure backward compatibility

### Integration Tests
1. **EffectChainTest**: Test effect chains with new categories
2. **ProviderSelectionTest**: Verify provider routing for new effects
3. **UIRenderingTest**: Test frontend categorization display

### Performance Tests
1. **EffectPerformanceTest**: Benchmark new effect performance
2. **MemoryUsageTest**: Monitor memory usage for complex effects
3. **ProviderComparisonTest**: Compare provider performance

## Migration Rollout Plan

### Week 1: Infrastructure
1. Add `taxonomyCategory` field to database schema
2. Update EffectDescriptor model
3. Create migration scripts for existing data
4. Update effect registry service

### Week 2: Backend Updates
1. Implement taxonomy mapping service
2. Update effect catalog service
3. Add tests for new taxonomy
4. Update API documentation

### Week 3: Frontend Updates
1. Update EffectsPanel component
2. Implement taxonomy-based categorization
3. Add operations section
4. Update effect store and API calls

### Week 4: Validation
1. Run full test suite
2. Verify UI rendering
3. Test backward compatibility
4. Performance validation

### Week 5: Missing Effects
1. Implement missing transform effects
2. Add keying and deform effects
3. Enhance audio effects
4. Add crop effect implementation

This migration plan ensures smooth transition to Effect Taxonomy v1 while maintaining full backward compatibility and enabling future enhancements.