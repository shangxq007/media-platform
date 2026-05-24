<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { MeEntitlementAPI } from '@/api/me'
// types inferred from usage
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { formatApiError } from '@/utils/apiError'

interface SharedResource {
  id: string
  name: string
  description: string
  status: string
  sharedBy: string
  permission: string
  createdAt: string
  type: 'project' | 'export'
}

const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const sharedProjects = ref<SharedResource[]>([])
const sharedExports = ref<SharedResource[]>([])
const search = ref('')

onMounted(loadSharedResources)

function mapProject(p: { id: string; name?: string; description?: string; status?: string; createdAt?: string; sharedBy?: string; permission?: string }): SharedResource {
  return {
    id: p.id,
    name: p.name || p.id,
    description: p.description || '',
    status: p.status || 'ACTIVE',
    sharedBy: p.sharedBy || 'workspace',
    permission: p.permission || 'READ',
    createdAt: p.createdAt || '',
    type: 'project',
  }
}

function mapExport(e: { id: string; name?: string; description?: string; status?: string; createdAt?: string; sharedBy?: string; permission?: string }): SharedResource {
  return {
    id: e.id,
    name: e.name || e.id,
    description: e.description || '',
    status: e.status || 'ACTIVE',
    sharedBy: e.sharedBy || 'workspace',
    permission: e.permission || 'READ',
    createdAt: e.createdAt || '',
    type: 'export',
  }
}

async function loadSharedResources() {
  loading.value = true
  error.value = null
  try {
    const data = await MeEntitlementAPI.getSharedResources()
    sharedProjects.value = (data.sharedProjects || []).map((p: { id: string; name?: string; description?: string; status?: string; createdAt?: string; sharedBy?: string; permission?: string }) => mapProject(p))
    sharedExports.value = (data.sharedExports || []).map((e: { id: string; name?: string; description?: string; status?: string; createdAt?: string; sharedBy?: string; permission?: string }) => mapExport(e))
  } catch (e: unknown) {
    error.value = formatApiError(e, 'Failed to load shared resources')
  } finally {
    loading.value = false
  }
}

const allResources = computed(() => [...sharedProjects.value, ...sharedExports.value])

const filteredResources = computed(() => {
  if (!search.value) return allResources.value
  const q = search.value.toLowerCase()
  return allResources.value.filter(r =>
    r.name.toLowerCase().includes(q) ||
    (r.description && r.description.toLowerCase().includes(q))
  )
})

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' {
  switch (status) {
    case 'active': case 'ACTIVE': return 'success'
    case 'archived': case 'ARCHIVED': return 'neutral'
    case 'error': case 'ERROR': return 'danger'
    default: return 'warning'
  }
}

function permissionVariant(perm: string): 'success' | 'info' | 'neutral' {
  switch (perm) {
    case 'READ_WRITE': case 'FULL_CONTROL': return 'success'
    case 'READ': return 'info'
    default: return 'neutral'
  }
}

function resourceIcon(type: string): string {
  return type === 'project' ? 'folder-open' : 'upload'
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Shared Resources" subtitle="Projects and exports shared with you">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadSharedResources">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading shared resources..." />
    <ErrorState v-else-if="error" title="Unable to load shared resources" :description="error" @retry="loadSharedResources" />

    <template v-else>
      <div class="flex items-center gap-md">
        <input v-model="search" type="text" placeholder="Search shared resources..." class="theme-input flex-1" />
      </div>

      <EmptyState v-if="filteredResources.length === 0" icon="share-2" title="No shared resources" description="Resources shared with you by team members will appear here.">
        <template #action>
          <button class="theme-btn theme-btn-primary" @click="router.push('/me/projects')">View My Projects</button>
        </template>
      </EmptyState>

      <template v-else>
        <PageSection title="Projects" v-if="sharedProjects.length > 0">
          <div class="grid-auto">
            <div v-for="res in filteredResources.filter(r => r.type === 'project')" :key="res.id"
              class="c-card hover:border-primary-200 transition-colors cursor-pointer"
              @click="router.push(`/project/${res.id}`)">
              <div class="c-card-body">
                <div class="flex items-start justify-between mb-sm">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-sm">
                      <span class="text-base">{{ resourceIcon(res.type) }}</span>
                      <h3 class="text-sm font-medium text-text-primary truncate-text">{{ res.name }}</h3>
                    </div>
                    <p v-if="res.description" class="text-xs text-text-secondary mt-xs line-clamp-2">{{ res.description }}</p>
                  </div>
                  <StatusBadge :variant="statusVariant(res.status)" :label="res.status" />
                </div>
                <div class="flex items-center gap-sm text-xs text-text-muted">
                  <StatusBadge :variant="permissionVariant(res.permission)" :label="res.permission" size="sm" />
                  <span>Shared by: {{ res.sharedBy }}</span>
                  <span class="ml-auto">{{ res.createdAt }}</span>
                </div>
              </div>
            </div>
          </div>
        </PageSection>

        <PageSection title="Exports" v-if="sharedExports.length > 0">
          <div class="space-y-sm">
            <div v-for="res in filteredResources.filter(r => r.type === 'export')" :key="res.id"
              class="c-card">
              <div class="c-card-body">
                <div class="flex items-center justify-between">
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-sm">
                      <span class="text-base">{{ resourceIcon(res.type) }}</span>
                      <h3 class="text-sm font-medium text-text-primary">{{ res.name }}</h3>
                    </div>
                    <div class="text-xs text-text-muted mt-xs">Shared by: {{ res.sharedBy }} · {{ res.createdAt }}</div>
                  </div>
                  <StatusBadge :variant="statusVariant(res.status)" :label="res.status" />
                </div>
              </div>
            </div>
          </div>
        </PageSection>

        <div class="flex items-center justify-between text-xs text-text-muted pt-sm border-t border-default">
          <span>{{ filteredResources.length }} shared resources</span>
          <span>{{ sharedProjects.length }} projects · {{ sharedExports.length }} exports</span>
        </div>
      </template>
    </template>
  </div>
</template>
