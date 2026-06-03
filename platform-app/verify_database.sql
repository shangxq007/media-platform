-- Database verification script for effect taxonomy fields
-- Connect to H2 database: jdbc:h2:mem:66196332-3a7e-4f3f-8327-55a8bcefe2fe

-- Check Flyway schema history
SELECT version, description, type, installed_on, execution_time 
FROM flyway_schema_history 
ORDER BY installed_rank DESC;

-- Check effect_pack_effect table structure
SELECT 
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns 
WHERE table_name = 'EFFECT_PACK_EFFECT'
ORDER BY ordinal_position;

-- Check sample data from effect_pack_effect
SELECT 
    effect_key,
    taxonomy_category,
    is_effect,
    display_name,
    category
FROM effect_pack_effect 
ORDER BY effect_key
LIMIT 10;

-- Count records by taxonomy_category
SELECT 
    taxonomy_category,
    COUNT(*) as count,
    COUNT(CASE WHEN is_effect = true THEN 1 END) as effects_count,
    COUNT(CASE WHEN is_effect = false THEN 1 END) as non_effects_count
FROM effect_pack_effect 
GROUP BY taxonomy_category
ORDER BY count DESC;

-- Count total effects vs non-effects
SELECT 
    is_effect,
    COUNT(*) as count
FROM effect_pack_effect 
GROUP BY is_effect;

-- Verify specific effect keys have correct taxonomy categories
SELECT 
    effect_key,
    taxonomy_category,
    is_effect,
    CASE 
        WHEN effect_key IN ('video.dash_drm', 'video.shotstack_template', 'video.remotion_template', 'video.blender_scene') 
        THEN 'Non-effect (should be false)' 
        ELSE 'Effect (should be true)' 
    END as expected_type
FROM effect_pack_effect 
WHERE effect_key IN (
    'video.fade_in', 'video.fade_out', 'video.cross_dissolve', 'video.dissolve', 'video.wipe', 'video.slide', 'video.zoom',
    'video.blur', 'video.sharpen', 'video.vignette', 'video.natron_vignette', 'video.chromatic', 'video.natron_color_grade', 
    'video.brightness', 'video.contrast', 'video.grayscale', 'video.sepia', 'video.particle_overlay',
    'video.overlay', 'video.pip', 'video.watermark',
    'text.subtitle_burn_in', 'text.overlay',
    'audio.volume',
    'video.dash_drm', 'video.shotstack_template', 'video.remotion_template', 'video.blender_scene'
)
ORDER BY effect_key;