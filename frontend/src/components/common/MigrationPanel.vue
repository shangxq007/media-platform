<script setup lang="ts">
import { ref, computed } from 'vue'
import { useTimelineStore } from '@/stores/timeline'

const timelineStore = useTimelineStore()
const dryRunResult = ref<any>(null)
const migrateResult = ref<any>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const currentVersion = computed(() => {
  const json = timelineStore.toJSON()
  return json.schemaVersion || '1.0.0'
})

const needsMigration = computed(() => currentVersion.value.startsWith('1.'))

async function runDryRun() {
  loading.value = true
  error.value = null
  try {
    const timelineJson = timelineStore.toJSON()
    const resp = await fetch('/api/v1/internal/migrations/dry-run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Tenant-ID': 'tenant-1' },
      body: JSON.stringify({
        schemaFamily: 'OTIO_TIMELINE',
        schemaVersion: currentVersion.value,
        targetVersion: '2.0.0',
        payload: timelineJson,
        metadata: {},
        tenantId: 'tenant-1',
        sourceObjectRef: 'current-timeline'
      })
    })
    if (!resp.ok) throw new Error(`API error: ${resp.status}`)
    dryRunResult.value = await resp.json()
  } catch (err: any) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function runMigration() {
  loading.value = true
  error.value = null
  try {
    const timelineJson = timelineStore.toJSON()
    const resp = await fetch('/api/v1/internal/migrations/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Tenant-ID': 'tenant-1' },
      body: JSON.stringify({
        schemaFamily: 'OTIO_TIMELINE',
        schemaVersion: currentVersion.value,
        targetVersion: '2.0.0',
        payload: timelineJson,
        metadata: {},
        tenantId: 'tenant-1',
        userId: 'current-user',
        sourceObjectRef: 'current-timeline'
      })
    })
    if (!resp.ok) throw new Error(`API error: ${resp.status}`)
    migrateResult.value = await resp.json()
    if (migrateResult.value.status === 'COMPLETED' && migrateResult.value.migratedPayload) {
      timelineStore.loadFromJSON(migrateResult.value.migratedPayload.payload)
    }
  } catch (err: any) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex flex-col h-full p-3 space-y-3 overflow-y-auto">
    <h3 class="text-sm font-semibold text-white">Schema Migration</h3>

    <div class="p-2 rounded bg-gray-800/50 border border-gray-700 text-xs space-y-1">
      <div class="flex justify-between">
        <span class="text-gray-400">Current Version</span>
        <span class="text-white">{{ currentVersion }}</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Target Version</span>
        <span class="text-white">2.0.0</span>
      </div>
      <div class="flex justify-between">
        <span class="text-gray-400">Status</span>
        <span :class="needsMigration ? 'text-yellow-400' : 'text-green-400'">
          {{ needsMigration ? 'Migration available' : 'Up to date' }}
        </span>
      </div>
    </div>

    <div v-if="needsMigration" class="space-y-2">
      <button
        class="w-full py-2 bg-blue-600/20 text-blue-400 text-xs rounded hover:bg-blue-600/30"
        :disabled="loading"
        @click="runDryRun"
      >
        {{ loading ? 'Running...' : '🔍 Dry Run' }}
      </button>
      <button
        class="w-full py-2 bg-clip-video/20 text-clip-video text-xs rounded hover:bg-clip-video/30"
        :disabled="loading || !dryRunResult"
        @click="runMigration"
      >
        ▶ Run Migration
      </button>
    </div>

    <!-- Dry Run Result -->
    <div v-if="dryRunResult" class="p-2 rounded bg-gray-800/30 border border-gray-700 text-xs space-y-1">
      <div class="text-gray-400">Dry Run Result</div>
      <div>Status: <span :class="dryRunResult.status === 'COMPLETED' ? 'text-green-400' : 'text-yellow-400'">{{ dryRunResult.status }}</span></div>
      <div v-if="dryRunResult.warnings?.length" class="text-yellow-400">
        <div v-for="w in dryRunResult.warnings" :key="w">{{ w }}</div>
      </div>
    </div>

    <!-- Migration Result -->
    <div v-if="migrateResult" class="p-2 rounded bg-gray-800/30 border border-gray-700 text-xs space-y-1">
      <div class="text-gray-400">Migration Result</div>
      <div>Status: <span :class="migrateResult.status === 'COMPLETED' ? 'text-green-400' : 'text-red-400'">{{ migrateResult.status }}</span></div>
      <div v-if="migrateResult.errors?.length" class="text-red-400">
        <div v-for="e in migrateResult.errors" :key="e.errorCode">{{ e.message }}</div>
      </div>
    </div>

    <!-- Error -->
    <div v-if="error" class="p-2 rounded bg-red-900/30 border border-red-700 text-xs text-red-400">
      {{ error }}
    </div>

    <!-- Migration Notes -->
    <div class="p-2 rounded bg-gray-800/30 border border-gray-700 text-xs text-gray-400 space-y-1">
      <div class="font-medium text-gray-300">v1 → v2 Changes</div>
      <ul class="list-disc list-inside space-y-0.5">
        <li>clips[].effects[].effectId → effectKey</li>
        <li>clips[].effects[].provider → providerPreference[]</li>
        <li>track.type standardized (VIDEO/AUDIO/TEXT)</li>
        <li>schemaVersion field added</li>
      </ul>
    </div>
  </div>
</template>
