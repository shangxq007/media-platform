<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FeatureFlag } from '@/types'
import PageSection from '@/components/ui/PageSection.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import FeatureBadge from '@/components/ui/FeatureBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const props = defineProps<{
  loading?: boolean
  featureFlags?: FeatureFlag[]
}>()

const emit = defineEmits<{
  'toggle-beta': [flagKey: string, enabled: boolean]
}>()

const localFlags = ref<Record<string, boolean>>({})

function isEnabled(flag: FeatureFlag): boolean {
  if (flag.flagKey in localFlags.value) return localFlags.value[flag.flagKey]
  return flag.enabled
}

function toggleFlag(flag: FeatureFlag) {
  const newState = !isEnabled(flag)
  localFlags.value[flag.flagKey] = newState
  emit('toggle-beta', flag.flagKey, newState)
}

function riskLevel(flag: FeatureFlag): 'low' | 'medium' | 'high' {
  if (flag.flagKey.includes('experimental') || flag.flagKey.includes('unstable')) return 'high'
  if (flag.flagKey.includes('beta')) return 'medium'
  return 'low'
}

function riskLabel(flag: FeatureFlag): string {
  const level = riskLevel(flag)
  return level.charAt(0).toUpperCase() + level.slice(1) + ' Risk'
}

const availableFeatures = computed(() => (props.featureFlags || []).filter(f => f.targetTier !== 'INTERNAL'))
const enabledCount = computed(() => availableFeatures.value.filter(f => isEnabled(f)).length)
</script>

<template>
  <PageSection title="Beta Features" description="Opt-in to experimental features. These may be unstable.">
    <div v-if="loading" class="c-loading-state py-lg">
      <LoadingState size="sm" message="Loading features..." />
    </div>

    <EmptyState v-else-if="availableFeatures.length === 0" icon="⚗" title="No beta features" description="No beta features are currently available for your account." />

    <div v-else>
      <div class="flex items-center justify-between mb-md">
        <div class="text-sm text-text-secondary">
          {{ enabledCount }} of {{ availableFeatures.length }} enabled
        </div>
      </div>

      <div class="space-y-md">
        <div v-for="flag in availableFeatures" :key="flag.flagKey"
          class="c-card transition-colors"
          :class="isEnabled(flag) ? 'border-primary-200 bg-primary-500/5' : 'border-default'">
          <div class="c-card-body">
            <div class="flex items-start justify-between gap-md">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-sm flex-wrap mb-xs">
                  <span class="text-sm font-medium text-text-primary">{{ flag.displayName }}</span>
                  <FeatureBadge :feature="flag.targetTier" variant="beta" />
                  <RiskBadge :level="riskLevel(flag)" :label="riskLabel(flag)" />
                </div>
                <p class="text-xs text-text-secondary">{{ flag.description }}</p>
                <div class="flex items-center gap-sm mt-xs text-xs text-text-muted">
                  <span>Scope: {{ flag.scope }}</span>
                  <span>·</span>
                  <span>Tier: {{ flag.targetTier }}</span>
                </div>
              </div>
              <button
                class="theme-btn theme-btn-sm flex-shrink-0"
                :class="isEnabled(flag) ? 'theme-btn-primary' : 'theme-btn-secondary'"
                @click="toggleFlag(flag)">
                {{ isEnabled(flag) ? 'Enabled' : 'Enable' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="mt-md p-sm bg-info-500/10 border border-info-200 rounded-lg">
        <div class="flex items-start gap-sm">
          <span class="text-sm">ℹ️</span>
          <div class="text-xs text-text-secondary">
            Beta features are experimental and may change or be removed at any time.
            Enable at your own risk. Some features may require specific tier access.
          </div>
        </div>
      </div>
    </div>
  </PageSection>
</template>
