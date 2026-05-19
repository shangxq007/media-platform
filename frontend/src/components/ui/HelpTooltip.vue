<script setup lang="ts">
import { ref } from 'vue'

withDefaults(defineProps<{
  content: string
  icon?: string
  placement?: 'top' | 'bottom' | 'left' | 'right'
}>(), {
  icon: '?',
  placement: 'top',
})

const visible = ref(false)

const placementClasses = {
  top: 'bottom-full left-1/2 -translate-x-1/2 mb-1',
  bottom: 'top-full left-1/2 -translate-x-1/2 mt-1',
  left: 'right-full top-1/2 -translate-y-1/2 mr-1',
  right: 'left-full top-1/2 -translate-y-1/2 ml-1',
}
</script>

<template>
  <span class="c-tooltip" @mouseenter="visible = true" @mouseleave="visible = false">
    <span
      class="inline-flex items-center justify-center w-4 h-4 rounded-full bg-bg-surface border border-default text-[10px] text-text-muted cursor-help flex-shrink-0"
    >
      {{ icon }}
    </span>
    <Transition name="tooltip">
      <span
        v-if="visible"
        class="c-tooltip-content"
        :class="placementClasses[placement]"
      >
        {{ content }}
      </span>
    </Transition>
  </span>
</template>

<style scoped>
.tooltip-enter-active,
.tooltip-leave-active {
  transition: opacity var(--duration-fast);
}
.tooltip-enter-from,
.tooltip-leave-to {
  opacity: 0;
}
</style>
