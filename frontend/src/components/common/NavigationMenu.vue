<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useNavigation } from '@/composables/useNavigation'
import DisabledRouteHint from './DisabledRouteHint.vue'

const { menuGroups, loading, fetchNavigation } = useNavigation()

const groupEntries = computed(() => Object.entries(menuGroups.value))

onMounted(() => {
  fetchNavigation()
})
</script>

<template>
  <nav v-if="!loading && groupEntries.length > 0" class="flex items-center gap-4">
    <template v-for="[group, items] in groupEntries" :key="group">
      <template v-for="item in items" :key="item.routeKey">
        <router-link
          v-if="item.visible && item.enabled"
          :to="item.path"
          class="text-sm text-info hover:text-info transition-colors"
        >
          {{ item.title }}
        </router-link>
        <DisabledRouteHint
          v-else-if="item.visible && !item.enabled"
          :route-decision="item"
          class="text-sm text-text-tertiary cursor-not-allowed"
        />
      </template>
    </template>
  </nav>
  <span v-else-if="loading" class="text-xs text-text-tertiary">Loading...</span>
</template>
