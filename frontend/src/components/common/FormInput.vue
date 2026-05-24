<script setup lang="ts">
defineProps<{
  label: string
  modelValue: string | number
  type?: string
  placeholder?: string
  min?: string
  max?: string
  step?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <div class="space-y-1">
    <label class="text-xs text-text-secondary block">{{ label }}</label>
    <input
      :type="type || 'text'"
      :value="modelValue"
      :placeholder="placeholder"
      :min="min"
      :max="max"
      :step="step"
      class="w-full bg-surface-2 border border-border-default rounded px-2 py-1 text-xs text-white"
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
    />
  </div>
</template>
