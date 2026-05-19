<script setup lang="ts">
withDefaults(defineProps<{
  divided?: boolean
}>(), {
  divided: false,
})

defineSlots<{
  default: () => unknown
}>()
</script>

<template>
  <div class="c-toolbar">
    <slot />
  </div>
</template>
