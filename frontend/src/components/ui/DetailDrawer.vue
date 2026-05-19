<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  width?: string
  showClose?: boolean
}>(), {
  title: '',
  width: '420px',
  showClose: true,
})

const emit = defineEmits<{
  close: []
}>()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.open) {
    emit('close')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="open">
        <div class="c-drawer-overlay" @click="$emit('close')" />
        <aside class="c-drawer" :style="{ width }">
          <div v-if="title || showClose" class="c-drawer-header">
            <h3 class="text-lg font-semibold text-text-primary truncate-text">{{ title }}</h3>
            <button
              v-if="showClose"
              class="theme-btn theme-btn-ghost theme-btn-sm flex-shrink-0"
              @click="$emit('close')"
            >
              ✕
            </button>
          </div>
          <div class="c-drawer-body theme-scrollbar">
            <slot />
          </div>
          <div v-if="$slots.footer" class="c-drawer-footer">
            <slot name="footer" />
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity var(--duration-normal);
}
.drawer-enter-active .c-drawer,
.drawer-leave-active .c-drawer {
  transition: transform var(--duration-normal);
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
.drawer-enter-from .c-drawer,
.drawer-leave-to .c-drawer {
  transform: translateX(100%);
}
</style>
