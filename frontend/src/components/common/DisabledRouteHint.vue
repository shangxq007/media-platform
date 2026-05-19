<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RouteVisibilityDecision } from '@/types/routing'
import { getErrorMessage } from '@/utils/i18n'

const props = defineProps<{
  routeDecision: RouteVisibilityDecision
  showUpgrade?: boolean
}>()

const emit = defineEmits<{
  upgrade: []
}>()

const showTooltip = ref(false)
const triggerRef = ref<HTMLElement | null>(null)

const message = computed(() => {
  if (props.routeDecision.userFriendlyMessage) {
    return props.routeDecision.userFriendlyMessage
  }
  if (props.routeDecision.reasonCode) {
    return getErrorMessage(props.routeDecision.reasonCode)
  }
  return 'This feature is currently unavailable'
})

const hasUpgrade = computed(() =>
  props.routeDecision.requiredUpgrade != null ||
  (props.routeDecision.children != null && props.routeDecision.children.length > 0)
)

function handleKeyTrigger(e: KeyboardEvent) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    showTooltip.value = !showTooltip.value
  }
}
</script>

<template>
  <span
    ref="triggerRef"
    class="c-route-hint-wrapper"
    @mouseenter="showTooltip = true"
    @mouseleave="showTooltip = false"
    @focus="showTooltip = true"
    @blur="showTooltip = false"
  >
    <span
      class="c-route-hint-trigger"
      tabindex="0"
      role="button"
      :aria-describedby="showTooltip ? `tooltip-${routeDecision.routeKey}` : undefined"
      :aria-expanded="showTooltip"
      @keydown="handleKeyTrigger"
    >
      <span class="c-route-hint-label">{{ routeDecision.title }}</span>
      <svg class="w-3.5 h-3.5 text-text-muted flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
      </svg>
    </span>

    <transition name="tooltip-fade">
      <div
        v-if="showTooltip"
        :id="`tooltip-${routeDecision.routeKey}`"
        class="c-route-hint-tooltip"
        role="tooltip"
      >
        <div class="c-route-hint-tooltip-header">
          <span class="font-medium">{{ routeDecision.title }}</span>
        </div>
        <div class="c-route-hint-tooltip-message">{{ message }}</div>

        <div v-if="showUpgrade && hasUpgrade" class="c-route-hint-tooltip-upgrade">
          <div class="text-[10px] font-medium text-text-muted uppercase tracking-wider mb-xs">To unlock:</div>
          <div v-if="routeDecision.requiredUpgrade" class="c-route-hint-req-item">
            <svg class="w-3 h-3 text-primary-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
            </svg>
            <span>Upgrade to <span class="font-medium text-primary-400">{{ routeDecision.requiredUpgrade }}</span></span>
          </div>
          <div v-if="routeDecision.requiredPermission" class="c-route-hint-req-item">
            <svg class="w-3 h-3 text-warning-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
            <span>Permission: {{ routeDecision.requiredPermission }}</span>
          </div>
          <div v-if="routeDecision.requiredEntitlement" class="c-route-hint-req-item">
            <svg class="w-3 h-3 text-warning-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
            </svg>
            <span>Requires: {{ routeDecision.requiredEntitlement }}</span>
          </div>

          <button
            v-if="routeDecision.requiredUpgrade"
            class="theme-btn theme-btn-primary theme-btn-sm w-full mt-sm"
            @click="emit('upgrade')"
          >
            Upgrade Plan
          </button>
        </div>
      </div>
    </transition>
  </span>
</template>

<style scoped>
.c-route-hint-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.c-route-hint-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  cursor: not-allowed;
  opacity: 0.6;
  text-decoration: line-through;
  border-radius: var(--radius-sm);
}

.c-route-hint-trigger:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3);
}

.c-route-hint-label {
  text-decoration: line-through;
}

.c-route-hint-tooltip {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: var(--z-tooltip);
  background-color: var(--color-bg-surface-elevated);
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  min-width: 220px;
  max-width: 320px;
  box-shadow: var(--shadow-lg);
  text-align: left;
}

.c-route-hint-tooltip-header {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
}

.c-route-hint-tooltip-message {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  margin-bottom: var(--spacing-sm);
}

.c-route-hint-tooltip-upgrade {
  border-top: 1px solid var(--color-border-default);
  padding-top: var(--spacing-sm);
}

.c-route-hint-req-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  margin-bottom: 2px;
}

.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
