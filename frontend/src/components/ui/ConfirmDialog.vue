<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  open: boolean
  title?: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  variant?: 'danger' | 'warning' | 'info'
  loading?: boolean
}>(), {
  title: 'Confirm',
  confirmLabel: 'Confirm',
  cancelLabel: 'Cancel',
  variant: 'danger',
  loading: false,
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.open && !props.loading) {
    emit('cancel')
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

const confirmVariantClass = {
  danger: 'theme-btn-danger',
  warning: 'bg-warning-600 text-white hover:bg-warning-700',
  info: 'theme-btn-primary',
}[props.variant]
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="c-dialog-overlay" @click.self="!loading && $emit('cancel')">
        <div class="c-dialog">
          <div class="c-dialog-header">
            <h3 class="text-lg font-semibold text-text-primary">{{ title }}</h3>
          </div>
          <div class="c-dialog-body">
            <p class="text-sm text-text-secondary">{{ description }}</p>
            <slot />
          </div>
          <div class="c-dialog-footer">
            <button
              class="theme-btn theme-btn-secondary"
              :disabled="loading"
              @click="$emit('cancel')"
            >
              {{ cancelLabel }}
            </button>
            <button
              class="theme-btn"
              :class="confirmVariantClass"
              :disabled="loading"
              @click="$emit('confirm')"
            >
              <span v-if="loading" class="c-spinner c-spinner-sm mr-xs" />
              {{ confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--duration-fast);
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
