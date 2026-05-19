<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { RenderAdminAPI } from '@/api/admin/render'
import type { AdminRenderJob, RenderWorker } from '@/api/admin/render'

const loading = ref(true)
const jobs = ref<AdminRenderJob[]>([])
const workers = ref<RenderWorker[]>([])
const filterStatus = ref<string>('ALL')

const statusColors: Record<string, string> = {
  QUEUED: 'bg-yellow-600/20 text-yellow-300',
  PROCESSING: 'bg-blue-600/20 text-blue-300',
  COMPLETED: 'bg-green-600/20 text-green-300',
  FAILED: 'bg-red-600/20 text-red-300',
}

const filteredJobs = computed(() => {
  if (filterStatus.value === 'ALL') return jobs.value
  return jobs.value.filter(j => j.status === filterStatus.value)
})

onMounted(async () => {
  try {
    const [j, w] = await Promise.allSettled([
      RenderAdminAPI.listJobs(),
      RenderAdminAPI.listWorkers(),
    ])
    if (j.status === 'fulfilled') jobs.value = j.value
    if (w.status === 'fulfilled') workers.value = w.value
  } catch { /* backend may not be running */ }
  loading.value = false
})

async function cancelJob(jobId: string) {
  await RenderAdminAPI.cancelJob(jobId)
  const job = jobs.value.find(j => j.id === jobId)
  if (job) job.status = 'FAILED'
}

async function retryJob(jobId: string) {
  await RenderAdminAPI.retryJob(jobId)
  const job = jobs.value.find(j => j.id === jobId)
  if (job) job.status = 'QUEUED'
}

function statusCount(status: string) {
  return jobs.value.filter(j => j.status === status).length
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <h1 class="text-xl font-bold mb-6">Render Job Management</h1>

    <!-- Stats -->
    <div class="grid grid-cols-4 gap-4 mb-6">
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-3 text-center">
        <div class="text-xs text-gray-400">Queued</div>
        <div class="text-lg font-bold text-yellow-400">{{ statusCount('QUEUED') }}</div>
      </div>
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-3 text-center">
        <div class="text-xs text-gray-400">Processing</div>
        <div class="text-lg font-bold text-blue-400">{{ statusCount('PROCESSING') }}</div>
      </div>
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-3 text-center">
        <div class="text-xs text-gray-400">Completed</div>
        <div class="text-lg font-bold text-green-400">{{ statusCount('COMPLETED') }}</div>
      </div>
      <div class="bg-gray-800 border border-gray-700 rounded-lg p-3 text-center">
        <div class="text-xs text-gray-400">Failed</div>
        <div class="text-lg font-bold text-red-400">{{ statusCount('FAILED') }}</div>
      </div>
    </div>

    <!-- Filters -->
    <div class="flex items-center gap-2 mb-4">
      <button
        v-for="s in ['ALL', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED']"
        :key="s"
        class="text-xs px-2 py-1 rounded border"
        :class="filterStatus === s ? 'bg-blue-600/30 border-blue-500 text-blue-300' : 'border-gray-600 text-gray-400 hover:text-white'"
        @click="filterStatus = s"
      >
        {{ s }}
      </button>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <div v-else-if="filteredJobs.length === 0" class="text-gray-500 text-sm">No jobs found</div>

    <!-- Job Table -->
    <div v-else class="bg-gray-800 border border-gray-700 rounded-lg overflow-hidden">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-700 text-xs text-gray-400">
            <th class="text-left px-3 py-2">Job ID</th>
            <th class="text-left px-3 py-2">Project</th>
            <th class="text-left px-3 py-2">Status</th>
            <th class="text-left px-3 py-2">Format</th>
            <th class="text-left px-3 py-2">Resolution</th>
            <th class="text-left px-3 py-2">Profile</th>
            <th class="text-left px-3 py-2">Created</th>
            <th class="text-right px-3 py-2">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="job in filteredJobs" :key="job.id" class="border-b border-gray-700/50 hover:bg-gray-700/30">
            <td class="px-3 py-2 font-mono text-xs">{{ job.id?.slice(0, 12) }}...</td>
            <td class="px-3 py-2 text-xs">{{ job.projectId || '—' }}</td>
            <td class="px-3 py-2">
              <span class="text-xs px-1.5 py-0.5 rounded" :class="statusColors[job.status || '']">
                {{ job.status }}
              </span>
            </td>
            <td class="px-3 py-2 text-xs">{{ job.format || '—' }}</td>
            <td class="px-3 py-2 text-xs">{{ job.resolution || '—' }}</td>
            <td class="px-3 py-2 text-xs">{{ job.profile || '—' }}</td>
            <td class="px-3 py-2 text-xs text-gray-400">{{ job.createdAt || '—' }}</td>
            <td class="px-3 py-2 text-right">
              <button
                v-if="job.status === 'FAILED'"
                class="text-xs px-2 py-0.5 bg-blue-600/20 text-blue-300 rounded mr-1"
                @click="retryJob(job.id!)"
              >
                Retry
              </button>
              <button
                v-if="job.status === 'QUEUED' || job.status === 'PROCESSING'"
                class="text-xs px-2 py-0.5 bg-red-600/20 text-red-300 rounded"
                @click="cancelJob(job.id!)"
              >
                Cancel
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Workers -->
    <h2 class="text-base font-semibold mt-8 mb-4">Render Workers</h2>
    <div v-if="workers.length === 0" class="text-gray-500 text-sm">No workers registered</div>
    <div v-else class="grid grid-cols-3 gap-3">
      <div v-for="w in workers" :key="w.workerId" class="bg-gray-800 border border-gray-700 rounded-lg p-3">
        <div class="flex items-center justify-between mb-1">
          <span class="text-sm font-mono">{{ w.workerId }}</span>
          <span class="text-xs px-1.5 py-0.5 rounded bg-green-600/20 text-green-300">{{ w.status || 'ONLINE' }}</span>
        </div>
        <div class="text-xs text-gray-400">Last heartbeat: {{ w.lastHeartbeat || '—' }}</div>
        <div class="flex flex-wrap gap-1 mt-2">
          <span v-for="cap in (w.capabilities || [])" :key="cap" class="text-xs px-1 py-0.5 bg-gray-700 rounded">{{ cap }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
