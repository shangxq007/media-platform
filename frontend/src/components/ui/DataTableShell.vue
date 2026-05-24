<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  columns?: { key: string; label: string; width?: string; align?: 'left' | 'center' | 'right' }[]
  loading?: boolean
  rowCount?: number
  currentPage?: number
  pageSize?: number
  total?: number
  showPagination?: boolean
  empty?: boolean
  striped?: boolean
  bordered?: boolean
  hoverable?: boolean
}>(), {
  columns: () => [],
  loading: false,
  rowCount: 5,
  currentPage: 1,
  pageSize: 20,
  total: 0,
  showPagination: true,
  empty: false,
  striped: true,
  bordered: false,
  hoverable: true,
})

const emit = defineEmits<{
  'page-change': [page: number]
}>()

const totalPages = computed(() => {
  if (!props.total || !props.pageSize) return 1
  return Math.ceil(props.total / props.pageSize)
})

function goToPage(page: number) {
  if (page < 1 || page > totalPages.value) return
  emit('page-change', page)
}

defineSlots<{
  header?: () => unknown
  filters?: () => unknown
  empty?: () => unknown
  pagination?: () => unknown
  [key: string]: unknown
}>()
</script>

<template>
  <div class="c-table-shell" :class="{ 'border border-default': bordered }">
    <slot name="header" />
    <slot name="filters" />

    <div v-if="loading" class="c-loading-state">
      <div class="c-spinner" />
      <p class="mt-md text-sm text-text-secondary">Loading data...</p>
    </div>

    <div v-else-if="empty" class="c-empty-state">
      <slot name="empty">
        <div class="c-empty-state-icon">mail</div>
        <div class="c-empty-state-title">No data</div>
        <div class="c-empty-state-description">No records found matching your criteria.</div>
      </slot>
    </div>

    <div v-else class="overflow-x-auto theme-scrollbar">
      <table class="c-table">
        <thead>
          <tr>
            <th
              v-for="col in columns"
              :key="col.key"
              :style="{ width: col.width, textAlign: col.align || 'left' }"
            >
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(_, rowIndex) in rowCount" :key="rowIndex">
            <td v-for="col in columns" :key="col.key" :style="{ textAlign: col.align || 'left' }">
              <slot :name="col.key" :row="rowIndex">
                <div class="h-4 bg-bg-surface rounded animate-pulse" />
              </slot>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="showPagination && !loading && !empty" class="flex items-center justify-between px-md py-sm border-t border-default bg-bg-surface">
      <slot name="pagination">
        <div class="text-xs text-text-muted">
          Showing {{ (currentPage - 1) * pageSize + 1 }}–{{ Math.min(currentPage * pageSize, total) }} of {{ total }}
        </div>
        <div class="flex items-center gap-xs">
          <button
            class="theme-btn theme-btn-ghost theme-btn-sm"
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          >
            ←
          </button>
          <span class="text-xs text-text-secondary px-sm">{{ currentPage }} / {{ totalPages }}</span>
          <button
            class="theme-btn theme-btn-ghost theme-btn-sm"
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          >
            →
          </button>
        </div>
      </slot>
    </div>
  </div>
</template>
