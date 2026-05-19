<script setup lang="ts">
interface BreadcrumbItem {
  label: string
  icon?: string
  path?: string
}

defineProps<{
  items: BreadcrumbItem[]
  separator?: string
}>()

defineEmits<{
  navigate: [item: BreadcrumbItem, index: number]
}>()
</script>

<template>
  <nav class="c-breadcrumb" aria-label="Breadcrumb">
    <template v-for="(item, index) in items" :key="index">
      <router-link
        v-if="item.path && index < items.length - 1"
        :to="item.path"
        class="theme-text-link hover:underline focus-visible:underline"
        :aria-label="`Go to ${item.label}`"
      >
        <span v-if="item.icon" class="mr-xs" aria-hidden="true">{{ item.icon }}</span>
        {{ item.label }}
      </router-link>
      <span
        v-else
        class="c-breadcrumb-current"
        :aria-current="index === items.length - 1 ? 'page' : undefined"
      >
        <span v-if="item.icon" class="mr-xs" aria-hidden="true">{{ item.icon }}</span>
        {{ item.label }}
      </span>
      <span
        v-if="index < items.length - 1"
        class="c-breadcrumb-separator"
        aria-hidden="true"
      >
        <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
      </span>
    </template>
  </nav>
</template>
