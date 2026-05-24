<script setup lang="ts">
const search = defineModel<string>('search', { default: '' })

withDefaults(defineProps<{
  searchPlaceholder?: string
  showSearch?: boolean
}>(), {
  searchPlaceholder: 'Search...',
  showSearch: true,
})

defineSlots<{
  filters?: () => unknown
  actions?: () => unknown
}>()
</script>

<template>
  <div class="c-filter-bar">
    <div v-if="showSearch" class="relative flex-1 min-w-48">
      <input
        v-model="search"
        type="text"
        :placeholder="searchPlaceholder"
        class="theme-input w-full pl-8"
      />
      <span class="absolute left-2.5 top-1/2 -translate-y-1/2 text-text-muted text-sm">search</span>
    </div>

    <slot name="filters" />

    <div class="flex items-center gap-sm ml-auto">
      <slot name="actions" />
    </div>
  </div>
</template>
