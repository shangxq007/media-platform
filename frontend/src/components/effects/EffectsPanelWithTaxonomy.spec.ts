import { describe, it, expect } from 'vitest';
import { 
  mapEffectKeyToCategory,
  isNonEffectOperation,
  getEffectDisplayCategory,
  EFFECT_MIGRATION_MAPPING
} from '@/types/effect-taxonomy';

describe('EffectsPanelWithTaxonomy - Taxonomy Mapping Tests', () => {
  describe('Effect Category Mapping', () => {
    it('maps fade_in to temporal category', () => {
      expect(mapEffectKeyToCategory('video.fade_in')).toBe('temporal');
    });

    it('maps blur to filter category', () => {
      expect(mapEffectKeyToCategory('video.blur')).toBe('filter');
    });

    it('maps brightness to color category', () => {
      expect(mapEffectKeyToCategory('video.brightness')).toBe('color');
    });

    it('maps watermark to composite category', () => {
      expect(mapEffectKeyToCategory('video.watermark')).toBe('composite');
    });

    it('maps dash_drm to packaging (non-effect operation)', () => {
      expect(isNonEffectOperation('video.dash_drm')).toBe(true);
      expect(mapEffectKeyToCategory('video.dash_drm')).toBe('packaging');
    });
  });

  describe('Unknown Effect Handling', () => {
    it('handles unknown effect without crashing', () => {
      expect(() => {
        mapEffectKeyToCategory('unknown.effect');
        isNonEffectOperation('unknown.effect');
        getEffectDisplayCategory('unknown.effect');
      }).not.toThrow();
    });

    it('displays unknown effect as unsupported', () => {
      expect(getEffectDisplayCategory('unknown.effect')).toBe('unsupported');
    });
  });

  describe('Legacy Category Fallback', () => {
    it('returns unsupported for unmapped effect even with legacy video', () => {
      const legacyCategory = 'video';
      expect(mapEffectKeyToCategory('legacy.effect', legacyCategory)).toBe('unsupported');
    });

    it('returns unsupported when no legacy category provided', () => {
      expect(mapEffectKeyToCategory('unknown.effect')).toBe('unsupported');
    });
  });

  describe('Effect Migration Mapping', () => {
    it('contains mappings for all required effects', () => {
      const requiredEffects = [
        'video.fade_in',
        'video.blur',
        'video.brightness',
        'video.watermark',
        'video.dash_drm'
      ];

      requiredEffects.forEach(effectKey => {
        expect(EFFECT_MIGRATION_MAPPING).toHaveProperty(effectKey);
      });
    });

    it('correctly classifies effects vs operations', () => {
      // Check effects
      expect(EFFECT_MIGRATION_MAPPING['video.blur'].isEffect).toBe(true);
      expect(EFFECT_MIGRATION_MAPPING['video.brightness'].isEffect).toBe(true);
      expect(EFFECT_MIGRATION_MAPPING['video.watermark'].isEffect).toBe(true);

      // Check operations
      expect(EFFECT_MIGRATION_MAPPING['video.dash_drm'].isEffect).toBe(false);
    });

    it('has correct taxonomy categories', () => {
      expect(EFFECT_MIGRATION_MAPPING['video.fade_in'].taxonomyCategory).toBe('temporal');
      expect(EFFECT_MIGRATION_MAPPING['video.blur'].taxonomyCategory).toBe('filter');
      expect(EFFECT_MIGRATION_MAPPING['video.brightness'].taxonomyCategory).toBe('color');
      expect(EFFECT_MIGRATION_MAPPING['video.watermark'].taxonomyCategory).toBe('composite');
      expect(EFFECT_MIGRATION_MAPPING['video.dash_drm'].taxonomyCategory).toBe('packaging');
    });
  });

  describe('Non-Effect Operations', () => {
    it('identifies dash_drm as non-effect operation', () => {
      expect(isNonEffectOperation('video.dash_drm')).toBe(true);
    });

    it('identifies regular effects as effects', () => {
      expect(isNonEffectOperation('video.blur')).toBe(false);
      expect(isNonEffectOperation('video.brightness')).toBe(false);
      expect(isNonEffectOperation('video.watermark')).toBe(false);
    });
  });

  describe('Display Categories', () => {
    it('returns correct display categories for mapped effects', () => {
      expect(getEffectDisplayCategory('video.blur')).toBe('filter');
      expect(getEffectDisplayCategory('video.brightness')).toBe('color');
      expect(getEffectDisplayCategory('video.watermark')).toBe('composite');
    });

    it('returns unsupported for unmapped effects even with legacy video', () => {
      expect(getEffectDisplayCategory('unknown.effect', 'video')).toBe('unsupported');
    });

    it('returns unsupported for completely unknown effects', () => {
      expect(getEffectDisplayCategory('completely.unknown.effect')).toBe('unsupported');
    });
  });
});