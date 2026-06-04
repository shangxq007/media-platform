// Effect Taxonomy v1 Types

export type EffectCategoryV1 = 
  | 'crop'
  | 'transform' 
  | 'color'
  | 'filter'
  | 'composite'
  | 'keying'
  | 'deform'
  | 'text'
  | 'vfx'
  | 'temporal'
  | 'transition'
  | 'audio'
  | 'packaging'
  | 'cloud_rendering'
  | 'unsupported';

export type LegacyEffectCategory = 'transition' | 'video' | 'audio' | 'text' | 'compositor';

export type NonEffectOperationCategory = 'packaging' | 'cloud_rendering' | 'infrastructure';

export interface EffectTaxonomyEntry {
  effectKey: string;
  legacyCategory: LegacyEffectCategory;
  taxonomyCategory: EffectCategoryV1;
  isEffect: boolean;
  displayCategory: string;
  displayOrder: number;
}

export interface SpatialCoordinate {
  space: 'source' | 'clip' | 'canvas' | 'safe-area';
  unit: 'normalized_ppm' | 'absolute_px';
  origin: 'top-left' | 'bottom-left' | 'top-right' | 'bottom-right';
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CropParameters {
  sourceRegion?: SpatialCoordinate;
  edgeInsets?: {
    top: number;
    right: number;
    bottom: number;
    left: number;
  };
  clampToFrame: boolean;
  overflowPolicy: 'clip' | 'allow' | 'reject';
}

export interface TransformParameters {
  scale: number;
  position: SpatialCoordinate;
  anchor: SpatialCoordinate;
  rotation?: number;
  flipHorizontal?: boolean;
  flipVertical?: boolean;
}

export interface CompositeParameters {
  source: SpatialCoordinate;
  opacity: number;
  blendMode: 'normal' | 'multiply' | 'screen' | 'overlay' | 'soft-light' | 'hard-light';
  overflowPolicy: 'clip' | 'allow';
}

// Effect Category Labels
export const EFFECT_CATEGORY_LABELS: Record<EffectCategoryV1, string> = {
  crop: '裁剪',
  transform: '变换',
  color: '色彩',
  filter: '滤镜',
  composite: '合成',
  keying: '抠像',
  deform: '变形',
  text: '文本',
  vfx: '特效',
  temporal: '时间',
  transition: '转场',
  audio: '音频',
  packaging: '封装',
  cloud_rendering: '云渲染',
  unsupported: '未支持'
};

// Effect Category Order (display order)
export const EFFECT_CATEGORY_ORDER: EffectCategoryV1[] = [
  'crop',
  'transform',
  'color', 
  'filter',
  'composite',
  'keying',
  'deform',
  'text',
  'vfx',
  'temporal',
  'transition',
  'audio',
  'unsupported'
];

// Effect Category Icons
export const EFFECT_CATEGORY_ICONS: Record<EffectCategoryV1, string> = {
  crop: 'crop',
  transform: 'transform',
  color: 'palette',
  filter: 'filter',
  composite: 'layers',
  keying: 'key',
  deform: 'deform',
  text: 'text',
  vfx: 'sparkles',
  temporal: 'clock',
  transition: 'arrows-right-left',
  audio: 'volume',
  packaging: 'package',
  cloud_rendering: 'cloud',
  unsupported: 'question-mark-circle'
};

// Non-Effect Operation Labels
export const NON_EFFECT_OPERATION_LABELS: Record<NonEffectOperationCategory, string> = {
  infrastructure: '基础设施',
  packaging: '封装',
  cloud_rendering: '云渲染'
};

// Effect Migration Mapping
export const EFFECT_MIGRATION_MAPPING: Record<string, EffectTaxonomyEntry> = {
  // Video Transitions -> temporal
  'video.fade_in': {
    effectKey: 'video.fade_in',
    legacyCategory: 'transition',
    taxonomyCategory: 'temporal',
    isEffect: true,
    displayCategory: 'temporal',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('temporal')
  },
  'video.fade_out': {
    effectKey: 'video.fade_out',
    legacyCategory: 'transition',
    taxonomyCategory: 'temporal',
    isEffect: true,
    displayCategory: 'temporal',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('temporal')
  },
  'video.cross_dissolve': {
    effectKey: 'video.cross_dissolve',
    legacyCategory: 'transition',
    taxonomyCategory: 'transition',
    isEffect: true,
    displayCategory: 'transition',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('transition')
  },
  'video.dissolve': {
    effectKey: 'video.dissolve',
    legacyCategory: 'transition',
    taxonomyCategory: 'transition',
    isEffect: true,
    displayCategory: 'transition',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('transition')
  },
  'video.wipe': {
    effectKey: 'video.wipe',
    legacyCategory: 'transition',
    taxonomyCategory: 'transition',
    isEffect: true,
    displayCategory: 'transition',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('transition')
  },
  'video.slide': {
    effectKey: 'video.slide',
    legacyCategory: 'transition',
    taxonomyCategory: 'transition',
    isEffect: true,
    displayCategory: 'transition',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('transition')
  },
  'video.zoom': {
    effectKey: 'video.zoom',
    legacyCategory: 'transition',
    taxonomyCategory: 'transition',
    isEffect: true,
    displayCategory: 'transition',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('transition')
  },

  // Video Filters -> filter/color
  'video.blur': {
    effectKey: 'video.blur',
    legacyCategory: 'video',
    taxonomyCategory: 'filter',
    isEffect: true,
    displayCategory: 'filter',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('filter')
  },
  'video.sharpen': {
    effectKey: 'video.sharpen',
    legacyCategory: 'video',
    taxonomyCategory: 'filter',
    isEffect: true,
    displayCategory: 'filter',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('filter')
  },
  'video.vignette': {
    effectKey: 'video.vignette',
    legacyCategory: 'video',
    taxonomyCategory: 'filter',
    isEffect: true,
    displayCategory: 'filter',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('filter')
  },
  'video.natron_vignette': {
    effectKey: 'video.natron_vignette',
    legacyCategory: 'video',
    taxonomyCategory: 'filter',
    isEffect: true,
    displayCategory: 'filter',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('filter')
  },
  'video.chromatic': {
    effectKey: 'video.chromatic',
    legacyCategory: 'video',
    taxonomyCategory: 'filter',
    isEffect: true,
    displayCategory: 'filter',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('filter')
  },
  'video.natron_color_grade': {
    effectKey: 'video.natron_color_grade',
    legacyCategory: 'video',
    taxonomyCategory: 'color',
    isEffect: true,
    displayCategory: 'color',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('color')
  },
  'video.brightness': {
    effectKey: 'video.brightness',
    legacyCategory: 'video',
    taxonomyCategory: 'color',
    isEffect: true,
    displayCategory: 'color',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('color')
  },
  'video.contrast': {
    effectKey: 'video.contrast',
    legacyCategory: 'video',
    taxonomyCategory: 'color',
    isEffect: true,
    displayCategory: 'color',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('color')
  },
  'video.grayscale': {
    effectKey: 'video.grayscale',
    legacyCategory: 'video',
    taxonomyCategory: 'color',
    isEffect: true,
    displayCategory: 'color',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('color')
  },
  'video.sepia': {
    effectKey: 'video.sepia',
    legacyCategory: 'video',
    taxonomyCategory: 'color',
    isEffect: true,
    displayCategory: 'color',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('color')
  },
  'video.particle_overlay': {
    effectKey: 'video.particle_overlay',
    legacyCategory: 'video',
    taxonomyCategory: 'vfx',
    isEffect: true,
    displayCategory: 'vfx',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('vfx')
  },
  'video.watermark': {
    effectKey: 'video.watermark',
    legacyCategory: 'video',
    taxonomyCategory: 'composite',
    isEffect: true,
    displayCategory: 'composite',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('composite')
  },

  // Compositing Operations -> composite
  'video.overlay': {
    effectKey: 'video.overlay',
    legacyCategory: 'compositor',
    taxonomyCategory: 'composite',
    isEffect: true,
    displayCategory: 'composite',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('composite')
  },
  'video.pip': {
    effectKey: 'video.pip',
    legacyCategory: 'compositor',
    taxonomyCategory: 'composite',
    isEffect: true,
    displayCategory: 'composite',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('composite')
  },

  // Text Effects -> text
  'text.subtitle_burn_in': {
    effectKey: 'text.subtitle_burn_in',
    legacyCategory: 'text',
    taxonomyCategory: 'text',
    isEffect: true,
    displayCategory: 'text',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('text')
  },
  'text.overlay': {
    effectKey: 'text.overlay',
    legacyCategory: 'text',
    taxonomyCategory: 'text',
    isEffect: true,
    displayCategory: 'text',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('text')
  },

  // Audio Effects -> audio
  'audio.volume': {
    effectKey: 'audio.volume',
    legacyCategory: 'audio',
    taxonomyCategory: 'audio',
    isEffect: true,
    displayCategory: 'audio',
    displayOrder: EFFECT_CATEGORY_ORDER.indexOf('audio')
  },

  // Non-Effect Operations -> operation
  'video.dash_drm': {
    effectKey: 'video.dash_drm',
    legacyCategory: 'video',
    taxonomyCategory: 'packaging',
    isEffect: false,
    displayCategory: 'packaging',
    displayOrder: -1 // Not displayed in regular effects panel
  },
  'video.shotstack_template': {
    effectKey: 'video.shotstack_template',
    legacyCategory: 'video',
    taxonomyCategory: 'cloud_rendering',
    isEffect: false,
    displayCategory: 'cloud_rendering',
    displayOrder: -1 // Not displayed in regular effects panel
  },
  'video.remotion_template': {
    effectKey: 'video.remotion_template',
    legacyCategory: 'video',
    taxonomyCategory: 'cloud_rendering',
    isEffect: false,
    displayCategory: 'cloud_rendering',
    displayOrder: -1 // Not displayed in regular effects panel
  },
  'video.blender_scene': {
    effectKey: 'video.blender_scene',
    legacyCategory: 'video',
    taxonomyCategory: 'cloud_rendering',
    isEffect: false,
    displayCategory: 'cloud_rendering',
    displayOrder: -1 // Not displayed in regular effects panel
  }
};

// Utility Functions

/**
 * Map effectKey to taxonomy category.
 * Returns 'unsupported' for unknown keys — no silent fallback.
 */
export function mapEffectKeyToCategory(effectKey: string, legacyCategory?: LegacyEffectCategory): EffectCategoryV1 {
  const mapping = EFFECT_MIGRATION_MAPPING[effectKey];
  if (mapping) {
    return mapping.taxonomyCategory;
  }
  
  // For known legacy categories without explicit mapping, use the legacy mapping
  // but only for categories that have a clear 1:1 relationship
  if (legacyCategory) {
    switch (legacyCategory) {
      case 'transition': return 'transition';
      case 'audio': return 'audio';
      case 'text': return 'text';
      // 'video' and 'compositor' are too broad — no silent fallback
      default: return 'unsupported';
    }
  }
  
  return 'unsupported';
}

/**
 * Check if effectKey is a non-effect operation
 */
export function isNonEffectOperation(effectKey: string): boolean {
  const mapping = EFFECT_MIGRATION_MAPPING[effectKey];
  return mapping ? !mapping.isEffect : false;
}

/**
 * Get display category for an effect.
 * Returns 'unsupported' for unknown keys — no silent fallback.
 */
export function getEffectDisplayCategory(effectKey: string, legacyCategory?: LegacyEffectCategory): string {
  const mapping = EFFECT_MIGRATION_MAPPING[effectKey];
  if (mapping) {
    return mapping.displayCategory;
  }
  
  // For known legacy categories without explicit mapping, use simple mapping
  if (legacyCategory) {
    switch (legacyCategory) {
      case 'transition': return 'transition';
      case 'audio': return 'audio';
      case 'text': return 'text';
      // 'video' and 'compositor' are too broad — no silent fallback
      default: return 'unsupported';
    }
  }
  
  return 'unsupported';
}

/**
 * Get category order for sorting
 */
export function getCategoryOrder(category: EffectCategoryV1): number {
  return EFFECT_CATEGORY_ORDER.indexOf(category);
}

/**
 * Get all effect categories sorted by display order
 */
export function getSortedEffectCategories(): EffectCategoryV1[] {
  return [...EFFECT_CATEGORY_ORDER].sort((a, b) => {
    return getCategoryOrder(a) - getCategoryOrder(b);
  });
}

/**
 * Get all non-effect operation categories
 */
export function getNonEffectOperationCategories(): NonEffectOperationCategory[] {
  return ['packaging', 'cloud_rendering', 'infrastructure'];
}

/**
 * Validate spatial coordinate
 */
export function validateSpatialCoordinate(coord: SpatialCoordinate, width?: number, height?: number): string[] {
  const errors: string[] = [];
  
  if (coord.unit === 'absolute_px' && (width !== undefined && height !== undefined)) {
    if (coord.x < 0 || coord.y < 0) {
      errors.push('Coordinates cannot be negative');
    }
    if (coord.x + coord.width > width || coord.y + coord.height > height) {
      errors.push('Rectangle exceeds boundaries');
    }
  }
  
  if (coord.unit === 'normalized_ppm' && (width !== undefined && height !== undefined)) {
    if (coord.x < 0 || coord.y < 0 || coord.x > 1 || coord.y > 1) {
      errors.push('Normalized coordinates must be between 0 and 1');
    }
    if (coord.x + coord.width > 1 || coord.y + coord.height > 1) {
      errors.push('Normalized rectangle exceeds boundaries');
    }
  }
  
  if (coord.width <= 0 || coord.height <= 0) {
    errors.push('Width and height must be positive');
  }
  
  return errors;
}

/**
 * Convert normalized_ppm to percentage
 */
export function normalizedToPercent(normalized: number): number {
  return normalized / 10000; // 1000000 / 100 = 10000
}

/**
 * Convert percentage to normalized_ppm
 */
export function percentToNormalized(percent: number): number {
  return Math.round(percent * 10000);
}

/**
 * Convert normalized_ppm to pixels
 */
export function normalizedToPixels(normalized: number, dimension: number): number {
  return Math.round((normalized / 1000000) * dimension);
}

/**
 * Convert pixels to normalized_ppm
 */
export function pixelsToNormalized(pixels: number, dimension: number): number {
  return Math.round((pixels / dimension) * 1000000);
}

/**
 * Round normalized coordinates to pixels using edge-based rounding
 */
export function roundNormalizedToPixels(rect: SpatialCoordinate, width: number, height: number) {
  const scaleX = width / 1000000;
  const scaleY = height / 1000000;
  
  const left = Math.round(rect.x * scaleX);
  const top = Math.round(rect.y * scaleY);
  const right = Math.round((rect.x + rect.width) * scaleX);
  const bottom = Math.round((rect.y + rect.height) * scaleY);
  
  const pixelWidth = Math.max(1, right - left);
  const pixelHeight = Math.max(1, bottom - top);
  
  return {
    x: left,
    y: top,
    width: pixelWidth,
    height: pixelHeight
  };
}

/**
 * Convert source region to edge insets
 */
export function sourceRegionToEdgeInsets(region: SpatialCoordinate, width: number, height: number) {
  return {
    top: region.y,
    right: width - (region.x + region.width),
    bottom: height - (region.y + region.height),
    left: region.x
  };
}

/**
 * Convert edge insets to source region
 */
export function edgeInsetsToSourceRegion(insets: { top: number; right: number; bottom: number; left: number }, width: number, height: number) {
  return {
    x: insets.left,
    y: insets.top,
    width: width - insets.left - insets.right,
    height: height - insets.top - insets.bottom
  };
}