<script setup lang="ts">
withDefaults(defineProps<{
  message?: string
  size?: 'sm' | 'md' | 'lg'
}>(), {
  message: 'Loading...',
  size: 'md',
})
</script>

<template>
  <div class="c-loading-state">
    <div class="c-spinner" :class="{
      'c-spinner-sm': size === 'sm',
      'c-spinner-lg': size === 'lg',
    }" />
    <p v-if="message" class="mt-md text-sm text-text-secondary">{{ message }}</p>
  </div>
</template>
