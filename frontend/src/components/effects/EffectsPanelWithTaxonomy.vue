<template>
  <div class="effects-panel">
    <!-- Category Tabs -->
    <div class="category-tabs">
      <div 
        v-for="category in sortedEffectCategories" 
        :key="category"
        :class="['category-tab', { active: selectedCategory === category }]"
        @click="selectedCategory = category"
      >
        <i :class="['category-icon', EFFECT_CATEGORY_ICONS[category]]"></i>
        <span class="category-label">{{ EFFECT_CATEGORY_LABELS[category] }}</span>
        <span class="effect-count">{{ getEffectCountByCategory(category) }}</span>
      </div>
      
      <!-- Operations Tab -->
      <div 
        v-for="operation in nonEffectOperations" 
        :key="operation"
        :class="['operation-tab', { active: selectedOperation === operation }]"
        @click="selectedOperation = operation"
      >
        <i class="operation-icon">settings</i>
        <span class="operation-label">{{ NON_EFFECT_OPERATION_LABELS[operation] }}</span>
        <span class="effect-count">{{ getOperationCountByCategory(operation) }}</span>
      </div>
    </div>

    <!-- Tier Filter -->
    <div class="tier-filter">
      <select v-model="selectedTier" @change="filterEffects">
        <option value="all">All Tiers</option>
        <option value="FREE">FREE</option>
        <option value="PRO">PRO</option>
        <option value="TEAM">TEAM</option>
        <option value="ENTERPRISE">ENTERPRISE</option>
      </select>
    </div>

    <!-- Effects List -->
    <div class="effects-list">
      <!-- Regular Effects -->
      <div v-if="selectedCategory && !selectedOperation" class="regular-effects">
        <div 
          v-for="effect in filteredEffects" 
          :key="effect.effectKey"
          :class="['effect-item', { 
            'applied': isEffectApplied(effect),
            'disabled': !isEffectAvailable(effect),
            'premium': effect.allowedTiers && effect.allowedTiers.includes('PRO'),
            'team': effect.allowedTiers && effect.allowedTiers.includes('TEAM'),
            'enterprise': effect.allowedTiers && effect.allowedTiers.includes('ENTERPRISE')
          }]"
          @click="handleEffectClick(effect)"
          draggable="true"
          @dragstart="(e) => handleDragStart(effect, e)"
          @dragend="handleDragEnd"
        >
          <div class="effect-header">
            <i class="effect-icon">{{ getEffectIcon(effect) }}</i>
            <div class="effect-info">
              <h4 class="effect-name">{{ effect.displayName }}</h4>
              <p class="effect-description">{{ effect.description }}</p>
              <span class="effect-category">{{ getEffectDisplayCategory(effect) }}</span>
            </div>
          </div>
          
          <div class="effect-footer">
            <div class="tier-badge" v-if="effect.allowedTiers">
              <span v-if="effect.allowedTiers.includes('FREE')" class="tier-free">FREE</span>
              <span v-if="effect.allowedTiers.includes('PRO')" class="tier-pro">PRO</span>
              <span v-if="effect.allowedTiers.includes('TEAM')" class="tier-team">TEAM</span>
              <span v-if="effect.allowedTiers.includes('ENTERPRISE')" class="tier-enterprise">ENTERPRISE</span>
            </div>
            
            <div class="effect-actions">
              <button 
                v-if="isEffectApplied(effect)"
                @click.stop="removeEffect(effect)"
                class="remove-btn"
              >
                Remove
              </button>
              <button 
                v-else
                @click.stop="applyEffect(effect)"
                class="apply-btn"
                :disabled="!isEffectAvailable(effect)"
              >
                Apply
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Operations -->
      <div v-if="selectedOperation" class="operations-list">
        <div 
          v-for="effect in filteredOperations" 
          :key="effect.effectKey"
          :class="['operation-item', { 
            'applied': isEffectApplied(effect),
            'disabled': !isEffectAvailable(effect)
          }]"
          @click="handleOperationClick(effect)"
        >
          <div class="operation-header">
            <i class="operation-icon">settings</i>
            <div class="operation-info">
              <h4 class="operation-name">{{ effect.displayName }}</h4>
              <p class="operation-description">{{ effect.description }}</p>
            </div>
          </div>
          
          <div class="operation-footer">
            <div class="operation-actions">
              <button 
                v-if="isEffectApplied(effect)"
                @click.stop="removeEffect(effect)"
                class="remove-btn"
              >
                Remove
              </button>
              <button 
                v-else
                @click.stop="applyEffect(effect)"
                class="apply-btn"
                :disabled="!isEffectAvailable(effect)"
              >
                Configure
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Effect Configuration Modal -->
    <div v-if="selectedEffectForConfig" class="effect-config-modal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ selectedEffectForConfig.displayName }}</h3>
          <button @click="closeConfig" class="close-btn">×</button>
        </div>
        
        <div class="modal-body">
          <div class="effect-description">
            {{ selectedEffectForConfig.description }}
          </div>
          
          <div class="effect-parameters">
            <h4>Parameters</h4>
            <div v-for="(param, idx) in selectedEffectForConfig.paramSchemas" :key="idx">
              <label>{{ param.description || 'Parameter ' + idx }}</label>
              <input 
                v-if="param.type === 'int' || param.type === 'float'"
                type="number" 
                :value="effectParameters[param.description || ('param' + idx)]"
                @input="(e) => updateParameter(param.description || ('param' + idx), (e.target as HTMLInputElement).value)"
              />
              <input 
                v-if="param.type === 'string'"
                type="text" 
                :value="effectParameters[param.description || ('param' + idx)]"
                @input="(e) => updateParameter(param.description || ('param' + idx), (e.target as HTMLInputElement).value)"
              />
              <input 
                v-if="param.type === 'boolean'"
                type="checkbox"
                :checked="effectParameters[param.description || ('param' + idx)] === true"
                @change="(e) => updateParameter(param.description || ('param' + idx), (e.target as HTMLInputElement).checked)"
              />
              <input 
                v-if="param.type === 'color'"
                type="color"
                :value="effectParameters[param.description || ('param' + idx)] || '#ffffff'"
                @input="(e) => updateParameter(param.description || ('param' + idx), (e.target as HTMLInputElement).value)"
              />
            </div>
          </div>
        </div>
        
        <div class="modal-footer">
          <button @click="closeConfig" class="cancel-btn">Cancel</button>
          <button @click="saveEffectConfig" class="save-btn">Save</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { 
  EFFECT_CATEGORY_LABELS, 
  EFFECT_CATEGORY_ICONS,
  NON_EFFECT_OPERATION_LABELS,
  EFFECT_MIGRATION_MAPPING,
  getSortedEffectCategories,
  getNonEffectOperationCategories
} from '@/types/effect-taxonomy';
import type { EffectPackEffect, ClipEffect } from '@/types';
import type { EffectCategoryV1, NonEffectOperationCategory } from '@/types/effect-taxonomy';

// Type alias for component props (extends EffectPackEffect with taxonomy fields)
type EffectPackEffectDto = EffectPackEffect;

// Props
const props = defineProps<{
  effectPacks: EffectPackEffectDto[];
  appliedEffects: ClipEffect[];
  onEffectApply: (effect: EffectPackEffectDto, parameters?: Record<string, any>) => void;
  onEffectRemove: (effectId: string) => void;
}>();

// Emits
const emit = defineEmits<{
  effectApply: [effect: EffectPackEffectDto, parameters?: Record<string, any>];
  effectRemove: [effectId: string];
  effectDrag: [effect: EffectPackEffectDto, event: DragEvent];
}>();

// State
const selectedCategory = ref<EffectCategoryV1>('filter');
const selectedOperation = ref<NonEffectOperationCategory | null>(null);
const selectedTier = ref<string>('all');
const selectedEffectForConfig = ref<EffectPackEffectDto | null>(null);
const effectParameters = ref<Record<string, any>>({});

// Computed
const sortedEffectCategories = computed(() => getSortedEffectCategories());

const nonEffectOperations = computed(() => getNonEffectOperationCategories());

const filteredEffects = computed(() => {
  let effects = props.effectPacks;
  
  // Filter by category
  if (selectedCategory.value) {
    effects = effects.filter(effect => {
      // Use taxonomyCategory from the effect if available, otherwise fall back to mapping
      if (effect.taxonomyCategory) {
        return effect.taxonomyCategory === selectedCategory.value;
      }
      const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
      return mapping?.taxonomyCategory === selectedCategory.value;
    });
  }
  
  // Filter by tier
  if (selectedTier.value !== 'all') {
    effects = effects.filter(effect => {
      return !effect.allowedTiers || effect.allowedTiers.includes(selectedTier.value);
    });
  }
  
  // Filter by operation
  if (selectedOperation.value) {
    effects = effects.filter(effect => {
      const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
      return mapping?.taxonomyCategory === selectedOperation.value;
    });
  }
  
  return effects;
});

const filteredOperations = computed(() => {
  let effects = props.effectPacks;
  
  // Filter by operation category
  if (selectedOperation.value) {
    effects = effects.filter(effect => {
      const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
      return mapping?.taxonomyCategory === selectedOperation.value;
    });
  }
  
  // Filter by tier
  if (selectedTier.value !== 'all') {
    effects = effects.filter(effect => {
      return !effect.allowedTiers || effect.allowedTiers.includes(selectedTier.value);
    });
  }
  
  return effects;
});

// Methods
const getEffectCountByCategory = (category: EffectCategoryV1): number => {
  return props.effectPacks.filter(effect => {
    // Use taxonomyCategory from the effect if available, otherwise fall back to mapping
    if (effect.taxonomyCategory) {
      return effect.taxonomyCategory === category;
    }
    const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
    return mapping?.taxonomyCategory === category;
  }).length;
};

const getOperationCountByCategory = (category: NonEffectOperationCategory): number => {
  return props.effectPacks.filter(effect => {
    // Use taxonomyCategory from the effect if available, otherwise fall back to mapping
    if (effect.taxonomyCategory) {
      return effect.taxonomyCategory === category;
    }
    const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
    return mapping?.taxonomyCategory === category;
  }).length;
};

const isEffectApplied = (effect: EffectPackEffectDto): boolean => {
  return props.appliedEffects.some(applied => applied.effectKey === effect.effectKey);
};

const isEffectAvailable = (effect: EffectPackEffectDto): boolean => {
  // Check if effect is actually an effect (not an operation)
  if (effect.isEffect === false) {
    return false;
  }
  
  // Check if effect is available based on tier
  if (selectedTier.value !== 'all' && effect.allowedTiers) {
    return effect.allowedTiers.includes(selectedTier.value);
  }
  return true;
};

const getEffectIcon = (effect: EffectPackEffectDto): string => {
  // Use taxonomyCategory from the effect if available, otherwise fall back to mapping
  if (effect.taxonomyCategory) {
    return EFFECT_CATEGORY_ICONS[effect.taxonomyCategory as EffectCategoryV1] || 'sparkles';
  }
  
  const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
  if (mapping) {
    return EFFECT_CATEGORY_ICONS[mapping.taxonomyCategory];
  }
  // Fallback to legacy category
  switch (effect.category) {
    case 'transition': return 'arrows-right-left';
    case 'video': return 'sparkles';
    case 'audio': return 'volume';
    case 'text': return 'text';
    case 'compositor': return 'layers';
    default: return 'sparkles';
  }
};

const getEffectDisplayCategory = (effect: EffectPackEffectDto): string => {
  // Use taxonomyCategory from the effect if available, otherwise fall back to mapping
  if (effect.taxonomyCategory) {
    return EFFECT_CATEGORY_LABELS[effect.taxonomyCategory as EffectCategoryV1];
  }
  
  const mapping = EFFECT_MIGRATION_MAPPING[effect.effectKey];
  if (mapping) {
    return EFFECT_CATEGORY_LABELS[mapping.taxonomyCategory];
  }
  // Fallback to legacy category
  switch (effect.category) {
    case 'transition': return '转场';
    case 'video': return '滤镜';
    case 'audio': return '音频';
    case 'text': return '文本';
    case 'compositor': return '合成';
    default: return '其他';
  }
};

const handleEffectClick = (effect: EffectPackEffectDto) => {
  if (isEffectApplied(effect)) {
    removeEffect(effect);
  } else {
    applyEffect(effect);
  }
};

const handleOperationClick = (effect: EffectPackEffectDto) => {
  if (isEffectApplied(effect)) {
    removeEffect(effect);
  } else {
    openConfig(effect);
  }
};

const applyEffect = (effect: EffectPackEffectDto) => {
  const defaultParams = effect.defaultParams || {};
  emit('effectApply', effect, defaultParams);
};

const removeEffect = (effect: EffectPackEffectDto) => {
  const appliedEffect = props.appliedEffects.find(applied => applied.effectKey === effect.effectKey);
  if (appliedEffect) {
    emit('effectRemove', appliedEffect.id);
  }
};

const openConfig = (effect: EffectPackEffectDto) => {
  selectedEffectForConfig.value = effect;
  effectParameters.value = { ...effect.defaultParams };
};

const closeConfig = () => {
  selectedEffectForConfig.value = null;
  effectParameters.value = {};
};

const saveEffectConfig = () => {
  if (selectedEffectForConfig.value) {
    emit('effectApply', selectedEffectForConfig.value, effectParameters.value);
    closeConfig();
  }
};

const updateParameter = (name: string, value: any) => {
  effectParameters.value[name] = value;
};

const handleDragStart = (effect: EffectPackEffectDto, event: DragEvent) => {
  emit('effectDrag', effect, event);
};

const handleDragEnd = () => {
  // Handle drag end
};

const filterEffects = () => {
  // Filter effects based on selected tier
  // This will be automatically handled by the computed property
};

// Watch for category changes
watch(selectedCategory, () => {
  selectedOperation.value = null; // Clear operation when category changes
});

// Watch for operation changes
watch(selectedOperation, () => {
  selectedCategory.value = 'filter'; // Clear category when operation changes
});
</script>

<style scoped>
.effects-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f8f9fa;
  border-radius: 8px;
  overflow: hidden;
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: #ffffff;
  border-bottom: 1px solid #e9ecef;
}

.category-tab,
.operation-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.category-tab:hover,
.operation-tab:hover {
  background: #e9ecef;
}

.category-tab.active,
.operation-tab.active {
  background: #007bff;
  color: white;
  border-color: #007bff;
}

.category-icon,
.operation-icon {
  font-size: 16px;
}

.category-label,
.operation-label {
  font-weight: 500;
}

.effect-count {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 6px;
  border-radius: 12px;
  font-size: 12px;
}

.tier-filter {
  padding: 12px;
  background: #ffffff;
  border-bottom: 1px solid #e9ecef;
}

.tier-filter select {
  width: 100%;
  padding: 8px;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  background: #ffffff;
}

.effects-list,
.operations-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.effect-item,
.operation-item {
  background: #ffffff;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.effect-item:hover,
.operation-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.effect-item.applied,
.operation-item.applied {
  border-color: #28a745;
  background: #f8fff9;
}

.effect-item.disabled,
.operation-item.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.effect-header,
.operation-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.effect-icon,
.operation-icon {
  font-size: 20px;
  width: 24px;
  text-align: center;
}

.effect-info,
.operation-info {
  flex: 1;
}

.effect-name,
.operation-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.effect-description,
.operation-description {
  margin: 4px 0 0 0;
  font-size: 14px;
  color: #666;
  line-height: 1.4;
}

.effect-footer,
.operation-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.tier-badge {
  display: flex;
  gap: 4px;
}

.tier-free {
  background: #28a745;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.tier-pro {
  background: #007bff;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.tier-team {
  background: #6f42c1;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.tier-enterprise {
  background: #dc3545;
  color: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
}

.effect-actions,
.operation-actions {
  display: flex;
  gap: 8px;
}

.apply-btn,
.remove-btn,
.config-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
}

.apply-btn {
  background: #007bff;
  color: white;
}

.apply-btn:hover:not(:disabled) {
  background: #0056b3;
}

.apply-btn:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

.remove-btn {
  background: #dc3545;
  color: white;
}

.remove-btn:hover {
  background: #c82333;
}

.effect-config-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #dee2e6;
}

.modal-header h3 {
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

.close-btn:hover {
  background: #f8f9fa;
}

.modal-body {
  padding: 16px;
}

.effect-description {
  margin-bottom: 16px;
  color: #666;
}

.effect-parameters {
  margin-top: 16px;
}

.effect-parameters h4 {
  margin-bottom: 12px;
  color: #333;
}

.effect-parameters label {
  display: block;
  margin-bottom: 4px;
  font-weight: 500;
  color: #555;
}

.effect-parameters input,
.effect-parameters select {
  width: 100%;
  padding: 8px;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  margin-bottom: 12px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid #dee2e6;
}

.cancel-btn,
.save-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.cancel-btn {
  background: #6c757d;
  color: white;
}

.cancel-btn:hover {
  background: #5a6268;
}

.save-btn {
  background: #28a745;
  color: white;
}

.save-btn:hover {
  background: #218838;
}
</style>