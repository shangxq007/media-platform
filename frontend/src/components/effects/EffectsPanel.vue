<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useTimelineStore } from '@/stores/timeline'
import { useEffectPackStore } from '@/stores/effectPack'
import { EntitlementAPI } from '@/api/index'
import type { ClipEffect, EffectPackEffect } from '@/types'
import EffectChain from './EffectChain.vue'
import EffectParameterEditor from './EffectParameterEditor.vue'
import AppIcon from '@/components/ui/AppIcon.vue'

const timelineStore = useTimelineStore()
const effectPackStore = useEffectPackStore()

const currentTier = ref<'FREE' | 'PRO' | 'TEAM' | 'ENTERPRISE'>('FREE')
const activeCategory = ref<'transition' | 'video' | 'audio' | 'text'>('transition')
const selectedEffect = ref<(EffectPackEffect & { packId: string; packVersion: string }) | null>(null)
const editingEffect = ref<ClipEffect | null>(null)
const showPackBrowser = ref(false)

const categories = ['transition', 'video', 'audio', 'text'] as const

const filteredEffects = computed(() => {
  return [...effectPackStore.allEffects.values()]
    .filter(e => e.category === activeCategory.value)
})

const availableEffects = computed(() =>
  filteredEffects.value.filter(e => isEffectAvailable(e))
)

const unavailableEffects = computed(() =>
  filteredEffects.value.filter(e => !isEffectAvailable(e))
)

const selectedClipEffects = computed(() => {
  const sel = timelineStore.selectedTrackClip
  if (!sel) return []
  return sel.trackClip.effects || []
})

function isEffectAvailable(effect: EffectPackEffect & { packId: string; packVersion: string }): boolean {
  if (!effect.allowedTiers.includes(currentTier.value)) {
    return false
  }
  const allowed = effectPackStore.allowedPackIds
  if (allowed.length === 0) {
    return true
  }
  return allowed.includes(effect.packId) || (effect.packId === 'builtin-core' && allowed.includes('basic'))
}

onMounted(async () => {
  await effectPackStore.loadFromApi()
  try {
    const policy = await EntitlementAPI.getCapabilities()
    if (policy?.tier) {
      currentTier.value = policy.tier as typeof currentTier.value
    }
    if (policy?.effectPacksAllowed?.length) {
      effectPackStore.setAllowedPackIds(policy.effectPacksAllowed)
    }
  } catch {
    // keep defaults when entitlement API unavailable
  }
})

function selectEffect(effect: EffectPackEffect & { packId: string; packVersion: string }) {
  if (!isEffectAvailable(effect)) return
  selectedEffect.value = { ...effect }
}

function applyEffectToSelectedClip(effect: EffectPackEffect & { packId: string; packVersion: string }) {
  const sel = timelineStore.selectedTrackClip
  if (!sel || !isEffectAvailable(effect)) return
  const clipEffect = effectPackStore.createClipEffect(effect.effectKey)
  clipEffect.packId = effect.packId
  clipEffect.packVersion = effect.packVersion

  if (!sel.trackClip.effects) sel.trackClip.effects = []
  sel.trackClip.effects.push({
    id: clipEffect.id,
    effectKey: clipEffect.effectKey,
    packId: clipEffect.packId,
    packVersion: clipEffect.packVersion,
    providerPreference: clipEffect.providerPreference,
    parameters: { ...clipEffect.parameters }
  })
}

function removeClipEffect(effectId: string) {
  const sel = timelineStore.selectedTrackClip
  if (!sel || !sel.trackClip.effects) return
  const idx = sel.trackClip.effects.findIndex(e => e.id === effectId)
  if (idx >= 0) sel.trackClip.effects.splice(idx, 1)
}

function updateEffectParameter(effectId: string, paramName: string, value: unknown) {
  const sel = timelineStore.selectedTrackClip
  if (!sel || !sel.trackClip.effects) return
  const ce = sel.trackClip.effects.find(e => e.id === effectId)
  if (ce) ce.parameters[paramName] = value
}

function handleEditEffect(effect: ClipEffect) {
  editingEffect.value = effect
}

function handleReorderEffects(effects: ClipEffect[]) {
  const sel = timelineStore.selectedTrackClip
  if (!sel) return
  sel.trackClip.effects = effects
}

function clearSelection() {
  selectedEffect.value = null
}

function onDragStart(e: DragEvent, effect: EffectPackEffect & { packId: string; packVersion: string }) {
  if (!isEffectAvailable(effect)) { e.preventDefault(); return }
  e.dataTransfer?.setData('effectKey', effect.effectKey)
  e.dataTransfer?.setData('packId', effect.packId)
}

function onClipDrop(e: DragEvent) {
  const effectKey = e.dataTransfer?.getData('effectKey')
  if (!effectKey) return
  const effect = effectPackStore.getEffect(effectKey)
  if (effect) {
    applyEffectToSelectedClip(effect)
  }
}
</script>

<template>
  <div class="effects-panel" @dragover.prevent @drop="onClipDrop">
    <div class="px-3 py-2 border-b border-default bg-bg-base/80">
      <div class="flex items-center gap-2">
        <AppIcon name="shield" :size="14" class="text-text-muted" />
        <span class="text-[10px] text-text-muted shrink-0 uppercase tracking-wide">Plan tier</span>
        <select
          v-model="currentTier"
          class="flex-1 bg-bg-surface border border-default rounded-md px-2 py-1 text-xs text-text-primary focus:outline-none focus:border-primary-400"
        >
          <option value="FREE">Free</option>
          <option value="PRO">Pro</option>
          <option value="TEAM">Team</option>
          <option value="ENTERPRISE">Enterprise</option>
        </select>
      </div>
    </div>

    <div class="effects-category-tabs">
      <button
        v-for="cat in categories"
        :key="cat"
        type="button"
        class="effects-category-tab"
        :class="{ 'is-active': activeCategory === cat }"
        @click="activeCategory = cat"
      >
        {{ cat }}
      </button>
      <button
        type="button"
        class="px-2 effects-category-tab"
        :class="{ 'is-active': showPackBrowser }"
        title="Browse effect packs"
        @click="showPackBrowser = !showPackBrowser"
      >
        <AppIcon name="package" :size="14" />
      </button>
    </div>

    <div v-if="showPackBrowser" class="p-3 border-b border-default bg-bg-base/60 max-h-36 overflow-y-auto theme-scrollbar">
      <div class="text-[10px] text-text-muted mb-2 uppercase tracking-wider font-semibold">Effect packs</div>
      <div
        v-for="pack in effectPackStore.allPacks"
        :key="pack.packId"
        class="text-xs text-text-primary py-1.5 flex items-center justify-between"
      >
        <span class="font-medium">{{ pack.name }}</span>
        <div class="flex items-center gap-2 text-text-muted">
          <span>v{{ pack.version }}</span>
          <span class="text-primary-400">{{ pack.effects.length }}</span>
        </div>
      </div>
    </div>

    <!-- Effects List -->
    <div class="flex-1 overflow-y-auto p-2 space-y-1.5 theme-scrollbar">
      <div
        v-for="effect in availableEffects"
        :key="effect.effectKey"
        class="effect-card"
        :class="{ 'is-selected': selectedEffect?.effectKey === effect.effectKey }"
        draggable="true"
        @click="selectEffect(effect)"
        @dragstart="onDragStart($event, effect)"
      >
        <div class="effect-card-icon">
          <AppIcon name="sparkles" :size="14" />
        </div>
        <span class="text-xs text-text-primary flex-1 font-medium">{{ effect.displayName }}</span>
        <span v-if="effect.providerMappings.includes('ofx')" class="text-[9px] text-info-500 px-1 rounded bg-info-500/10">OFX</span>
        <span v-if="effect.packId !== 'builtin-core'" class="text-[9px] text-purple-400 px-1 rounded bg-purple-500/10">PACK</span>
        <button
          v-if="timelineStore.selectedTrackClip"
          class="text-[10px] text-primary-400 hover:text-primary-300 px-1"
          title="Apply to selected clip"
          @click.stop="applyEffectToSelectedClip(effect)"
        >
          + Apply
        </button>
      </div>

      <!-- Unavailable Effects -->
      <div v-if="unavailableEffects.length" class="pt-1">
        <div class="text-[9px] text-gray-600 uppercase tracking-wider px-1 pb-1">Requires Upgrade</div>
        <div
          v-for="effect in unavailableEffects"
          :key="effect.effectKey"
          class="flex items-center gap-2 p-2 rounded border border-gray-800 opacity-40"
        >
          <span class="text-xs text-gray-500 flex-1">{{ effect.displayName }}</span>
          <span class="text-[9px] text-warning-500 px-1 rounded bg-warning-500/10">
            {{ effect.allowedTiers[effect.allowedTiers.length - 1] === 'FREE' ? 'PRO' : effect.allowedTiers.find(t => ['PRO','TEAM','ENTERPRISE'].indexOf(t) > ['PRO','TEAM','ENTERPRISE'].indexOf(currentTier)) || 'UPGRADE' }}
          </span>
        </div>
      </div>

      <div v-if="!filteredEffects.length" class="p-3 text-center text-xs text-gray-500">
        No effects in this category
      </div>
    </div>

    <!-- Selected Effect Config -->
    <div v-if="selectedEffect" class="p-2 border-t border-gray-700 space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-xs text-white font-medium">{{ selectedEffect.displayName }}</span>
        <span class="text-[9px] font-mono text-gray-500">{{ selectedEffect.effectKey }}</span>
      </div>
      <p v-if="selectedEffect.description" class="text-[10px] text-gray-500">{{ selectedEffect.description }}</p>

      <div v-if="Object.keys(selectedEffect.parameterSchema).length" class="space-y-2">
        <EffectParameterEditor
          v-for="(paramDef, paramName) in selectedEffect.parameterSchema"
          :key="paramName"
          :parameter-name="paramName"
          :definition="paramDef"
          :model-value="selectedEffect.defaultValues[paramName]"
          @update:model-value="selectedEffect!.defaultValues[paramName] = $event"
        />
      </div>

      <div class="flex gap-2">
        <button
          class="flex-1 py-1.5 bg-primary-500/20 text-primary-400 text-xs rounded hover:bg-primary-500/30 transition-colors"
          :class="!timelineStore.selectedTrackClip ? 'opacity-40 cursor-not-allowed' : ''"
          :disabled="!timelineStore.selectedTrackClip"
          @click="applyEffectToSelectedClip(selectedEffect)"
        >
          {{ timelineStore.selectedTrackClip ? 'Apply to Clip' : 'Select a Clip First' }}
        </button>
        <button
          class="py-1.5 px-3 bg-gray-700 text-gray-400 text-xs rounded hover:bg-gray-600 transition-colors"
          @click="clearSelection"
        >
          ✕
        </button>
      </div>
    </div>

    <!-- Effect Chain -->
    <div v-if="selectedClipEffects.length || timelineStore.selectedTrackClip" class="border-t border-gray-700">
      <EffectChain
        :effects="selectedClipEffects"
        @remove="removeClipEffect"
        @edit="handleEditEffect"
        @reorder="handleReorderEffects"
      />
    </div>

    <!-- Effect Parameter Editor (for applied effects) -->
    <div v-if="editingEffect" class="border-t border-gray-700 p-2 space-y-2">
      <div class="flex items-center justify-between">
        <span class="text-xs text-white font-medium">{{ editingEffect.effectKey }}</span>
        <button class="text-[10px] text-gray-500 hover:text-white" @click="editingEffect = null">✕</button>
      </div>
      <template v-for="(paramVal, paramName) in editingEffect.parameters" :key="paramName">
        <EffectParameterEditor
          :parameter-name="paramName"
          :definition="{ type: typeof paramVal === 'number' ? (Number.isInteger(paramVal) ? 'int' : 'float') : typeof paramVal === 'boolean' ? 'boolean' : 'string', defaultValue: paramVal, description: '' }"
          :model-value="paramVal"
          @update:model-value="updateEffectParameter(editingEffect.id, paramName, $event)"
        />
      </template>
    </div>

    <!-- No Clip Selected Hint -->
    <div v-if="!timelineStore.selectedTrackClip" class="p-3 border-t border-gray-700 text-center">
      <p class="text-[10px] text-gray-500">Select a clip on the timeline to apply effects</p>
    </div>
  </div>
</template>
