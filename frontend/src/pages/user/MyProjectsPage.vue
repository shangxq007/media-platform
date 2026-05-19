<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { Project } from '@/types'
import PageHeader from '@/components/ui/PageHeader.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import FilterBar from '@/components/ui/FilterBar.vue'

const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const projects = ref<Project[]>([])
const search = ref('')
const currentPage = ref(1)
const pageSize = 12

onMounted(loadProjects)

async function loadProjects() {
  loading.value = true
  error.value = null
  try {
    projects.value = []
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load projects'
  } finally {
    loading.value = false
  }
}

const filteredProjects = computed(() => {
  if (!search.value) return projects.value
  const q = search.value.toLowerCase()
  return projects.value.filter(p =>
    p.name.toLowerCase().includes(q) ||
    p.description.toLowerCase().includes(q)
  )
})

const paginatedProjects = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredProjects.value.slice(start, start + pageSize)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredProjects.value.length / pageSize)))

function projectStatusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'active': return 'success'
    case 'archived': return 'neutral'
    case 'error': return 'danger'
    default: return 'warning'
  }
}

function navigateToProject(id: string) {
  router.push(`/project/${id}`)
}

function navigateToNew() {
  router.push('/project/new')
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="My Projects" subtitle="Manage your editing projects">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="navigateToNew">+ New Project</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadProjects">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading projects..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadProjects" />

    <template v-else>
      <FilterBar v-model:search="search" search-placeholder="Search projects..." />

      <EmptyState v-if="filteredProjects.length === 0" icon="📂" title="No projects found" description="Create your first project to get started.">
        <template #action>
          <button class="theme-btn theme-btn-primary" @click="navigateToNew">Create Project</button>
        </template>
      </EmptyState>

      <template v-else>
        <div class="grid-auto">
          <div v-for="proj in paginatedProjects" :key="proj.id"
            class="c-card hover:border-primary-200 transition-colors cursor-pointer"
            @click="navigateToProject(proj.id)">
            <div class="c-card-body">
              <div class="flex items-start justify-between mb-sm">
                <div class="min-w-0 flex-1">
                  <h3 class="text-sm font-medium text-text-primary truncate-text">{{ proj.name }}</h3>
                  <StatusBadge :variant="projectStatusVariant(proj.status)" :label="proj.status" class="mt-xs" />
                </div>
              </div>
              <p v-if="proj.description" class="text-xs text-text-secondary line-clamp-2 mb-sm">{{ proj.description }}</p>
              <div class="flex items-center justify-between text-xs text-text-muted">
                <span>Created: {{ proj.createdAt }}</span>
                <span>{{ proj.tenantId }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="totalPages > 1" class="flex items-center justify-between px-md py-sm border-t border-default bg-bg-surface">
          <div class="text-xs text-text-muted">
            Showing {{ (currentPage - 1) * pageSize + 1 }}–{{ Math.min(currentPage * pageSize, filteredProjects.length) }} of {{ filteredProjects.length }}
          </div>
          <div class="flex items-center gap-xs">
            <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="currentPage <= 1" @click="currentPage -= 1">←</button>
            <span class="text-xs text-text-secondary px-sm">{{ currentPage }} / {{ totalPages }}</span>
            <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="currentPage >= totalPages" @click="currentPage += 1">→</button>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>
