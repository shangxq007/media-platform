<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  feature?: string
  icon?: string
  variant?: 'banner' | 'card' | 'inline'
  dismissible?: boolean
}>(), {
  title: 'Upgrade available',
  description: 'Unlock this feature by upgrading your plan.',
  icon: '⚡',
  variant: 'card',
  dismissible: true,
})

defineEmits<{
  upgrade: []
  dismiss: []
}>()

defineSlots<{
  icon?: () => unknown
  action?: () => unknown
}>()
</script>

<template>
  <div
    v-if="variant === 'banner'"
    class="flex items-center justify-between px-lg py-sm bg-primary-500/10 border border-primary-200"
  >
    <div class="flex items-center gap-md">
      <span class="text-lg"><slot name="icon">{{ icon }}</slot></span>
      <div>
        <span class="text-sm font-medium text-text-primary">{{ title }}</span>
        <span v-if="feature" class="text-sm text-text-secondary ml-xs">{{ feature }}</span>
      </div>
    </div>
    <div class="flex items-center gap-sm">
      <slot name="action">
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="$emit('upgrade')">
          Upgrade
        </button>
      </slot>
      <button v-if="dismissible" class="theme-btn theme-btn-ghost theme-btn-sm" @click="$emit('dismiss')">
        Dismiss
      </button>
    </div>
  </div>

  <div
    v-else-if="variant === 'card'"
    class="c-upgrade-hint"
  >
    <span class="text-xl flex-shrink-0"><slot name="icon">{{ icon }}</slot></span>
    <div class="flex-1 min-w-0">
      <div class="text-sm font-medium text-text-primary">{{ title }}</div>
      <div class="text-xs text-text-secondary mt-xs">{{ description }}</div>
    </div>
    <slot name="action">
      <button class="theme-btn theme-btn-primary theme-btn-sm flex-shrink-0" @click="$emit('upgrade')">
        Upgrade
      </button>
    </slot>
    <button v-if="dismissible" class="theme-btn theme-btn-ghost theme-btn-sm flex-shrink-0" @click="$emit('dismiss')">
      ✕
    </button>
  </div>

  <div
    v-else
    class="inline-flex items-center gap-xs text-xs text-primary-500"
  >
    <span><slot name="icon">{{ icon }}</slot></span>
    <span>{{ title }}</span>
    <button class="theme-btn theme-btn-primary theme-btn-sm" @click="$emit('upgrade')">
      Upgrade
    </button>
  </div>
</template>
