<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PortalPageHeader from '@/components/ui/PortalPageHeader.vue'

const props = defineProps<{
  title: string
  subtitle?: string
}>()

const route = useRoute()
const workspaceId = computed(() => String(route.params.workspaceId ?? ''))
const resolvedSubtitle = computed(() => props.subtitle ?? `Workspace ${workspaceId.value}`)
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PortalPageHeader :title="title" :subtitle="resolvedSubtitle">
      <template v-if="$slots.actions" #actions>
        <slot name="actions" />
      </template>
    </PortalPageHeader>
    <slot />
  </div>
</template>
