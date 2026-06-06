-- V4__add_effect_taxonomy_fields.sql
-- Add taxonomy fields to effect_pack_effect table and backfill all 28 effect keys
--
-- Strategy:
--   1. Add taxonomy_category and is_effect columns
--   2. Backfill all 28 effect keys with correct taxonomy categories
--   3. Set is_effect = FALSE for non-effect operations (packaging, cloud rendering)
--   4. Set is_effect = TRUE for all actual effects
--   5. Create index for better query performance
--
-- This migration is idempotent for Flyway (runs once)

-- Step 1: Add taxonomy columns
ALTER TABLE effect_pack_effect ADD COLUMN taxonomy_category VARCHAR(50) NULL;
ALTER TABLE effect_pack_effect ADD COLUMN is_effect BOOLEAN DEFAULT TRUE;

-- Step 2: Backfill all 28 effect keys with correct taxonomy categories
UPDATE effect_pack_effect 
SET taxonomy_category = 
    CASE 
        -- Video Transitions -> temporal
        WHEN effect_key = 'video.fade_in' THEN 'temporal'
        WHEN effect_key = 'video.fade_out' THEN 'temporal'
        WHEN effect_key = 'video.cross_dissolve' THEN 'transition'
        WHEN effect_key = 'video.dissolve' THEN 'transition'
        WHEN effect_key = 'video.wipe' THEN 'transition'
        WHEN effect_key = 'video.slide' THEN 'transition'
        WHEN effect_key = 'video.zoom' THEN 'transition'
        
        -- Video Filters -> filter/color
        WHEN effect_key = 'video.blur' THEN 'filter'
        WHEN effect_key = 'video.sharpen' THEN 'filter'
        WHEN effect_key = 'video.vignette' THEN 'filter'
        WHEN effect_key = 'video.natron_vignette' THEN 'filter'
        WHEN effect_key = 'video.chromatic' THEN 'filter'
        WHEN effect_key = 'video.natron_color_grade' THEN 'color'
        WHEN effect_key = 'video.brightness' THEN 'color'
        WHEN effect_key = 'video.contrast' THEN 'color'
        WHEN effect_key = 'video.grayscale' THEN 'color'
        WHEN effect_key = 'video.sepia' THEN 'color'
        WHEN effect_key = 'video.particle_overlay' THEN 'vfx'
        
        -- Compositing Effects -> composite
        WHEN effect_key = 'video.overlay' THEN 'composite'
        WHEN effect_key = 'video.pip' THEN 'composite'
        WHEN effect_key = 'video.watermark' THEN 'composite'
        
        -- Text Effects -> text
        WHEN effect_key = 'text.subtitle_burn_in' THEN 'text'
        WHEN effect_key = 'text.overlay' THEN 'text'
        
        -- Audio Effects -> audio
        WHEN effect_key = 'audio.volume' THEN 'audio'
        
        -- Non-Effect Operations -> operation categories
        WHEN effect_key = 'video.dash_drm' THEN 'packaging'
        WHEN effect_key = 'video.shotstack_template' THEN 'cloud_rendering'
        WHEN effect_key = 'video.remotion_template' THEN 'cloud_rendering'
        WHEN effect_key = 'video.blender_scene' THEN 'cloud_rendering'
        
        -- Fallback for unknown effects
        ELSE 'filter'
    END
WHERE taxonomy_category IS NULL;

-- Step 3: Set is_effect flag correctly
-- Set is_effect = FALSE for non-effect operations
UPDATE effect_pack_effect 
SET is_effect = FALSE
WHERE effect_key IN (
    'video.dash_drm',
    'video.shotstack_template',
    'video.remotion_template',
    'video.blender_scene'
);

-- Set is_effect = TRUE for all remaining effects (this will include the default TRUE for new records)
UPDATE effect_pack_effect 
SET is_effect = TRUE
WHERE is_effect IS NULL OR is_effect = TRUE;

-- Step 4: Create index for taxonomy_category for better query performance
CREATE INDEX idx_effect_pack_effect_taxonomy_category ON effect_pack_effect(taxonomy_category);