<script setup lang="ts">
import PageHeader from '@/components/ui/PageHeader.vue'

defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <PageHeader :title="title" :subtitle="subtitle" :show-dashboard-back="true">
    <template #actions>
      <slot name="actions" />
    </template>
  </PageHeader>
</template>
