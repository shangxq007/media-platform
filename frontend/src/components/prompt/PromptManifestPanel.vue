<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { PromptAPI } from '@/api/prompt'

const validationResult = ref<any>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const scanResult = ref<any>(null)

onMounted(validateManifest)

async function validateManifest() {
  loading.value = true
  error.value = null
  try {
    // Load the prompts/MANIFEST.md content
    const manifestResp = await fetch('/api/v1/prompts/manifest/validate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompts: {} })
    })
    if (manifestResp.ok) {
      validationResult.value = await manifestResp.json()
    }
  } catch (e: unknown) {
    error.value = e.message || 'Failed to validate manifest'
  } finally {
    loading.value = false
  }
}

async function scanPromptFiles() {
  loading.value = true
  error.value = null
  try {
    // Sample scan with empty files for now
    scanResult.value = await PromptAPI.scanFiles([], [])
  } catch (e: unknown) {
    error.value = e.message || 'Failed to scan files'
  } finally {
    loading.value = false
  }
}

function getStatusClass(valid: boolean): string {
  return valid ? 'text-success' : 'text-danger'
}
</script>

<template>
  <div class="flex flex-col h-full p-4 space-y-4 overflow-y-auto">
    <h2 class="text-lg font-semibold text-white">Manifest Management</h2>

    <!-- Validation -->
    <div class="p-3 rounded bg-surface-2/50 border border-border-subtle">
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-sm font-medium text-white">Manifest Validation</h3>
        <button class="text-xs text-info hover:text-info" @click="validateManifest"
          :disabled="loading">
          {{ loading ? 'Validating...' : 'Validate' }}
        </button>
      </div>
      <div v-if="validationResult" class="text-xs space-y-1">
        <div :class="getStatusClass(validationResult.valid)">
          {{ validationResult.valid ? '✓ Valid' : '✗ Invalid' }}
        </div>
        <div v-for="err in (validationResult.errors || [])" :key="err" class="text-danger">{{ err }}</div>
        <div v-for="warn in (validationResult.warnings || [])" :key="warn" class="text-warning">{{ warn }}</div>
      </div>
      <div v-if="error" class="text-danger text-xs">{{ error }}</div>
    </div>

    <!-- File Scan -->
    <div class="p-3 rounded bg-surface-2/50 border border-border-subtle">
      <div class="flex items-center justify-between mb-2">
        <h3 class="text-sm font-medium text-white">File Scan</h3>
        <button class="text-xs text-info hover:text-info" @click="scanPromptFiles"
          :disabled="loading">
          Scan Files
        </button>
      </div>
      <div v-if="scanResult" class="text-xs space-y-1">
        <div class="text-white">Imported: {{ scanResult.imported }}</div>
        <div class="text-warning">Conflicts: {{ scanResult.conflicts }}</div>
        <div class="text-text-secondary">Skipped: {{ scanResult.skipped }}</div>
        <div v-for="err in (scanResult.errors || [])" :key="err" class="text-danger">{{ err }}</div>
      </div>
    </div>

    <!-- Stats -->
    <div class="p-3 rounded bg-surface-2/50 border border-border-subtle">
      <h3 class="text-sm font-medium text-white mb-2">Platform Stats</h3>
      <div class="grid grid-cols-2 gap-2 text-xs">
        <div class="p-2 rounded bg-surface-3/50">
          <div class="text-text-secondary">Total Templates</div>
          <div class="text-white text-lg">-</div>
        </div>
        <div class="p-2 rounded bg-surface-3/50">
          <div class="text-text-secondary">Total Executions</div>
          <div class="text-white text-lg">-</div>
        </div>
        <div class="p-2 rounded bg-surface-3/50">
          <div class="text-text-secondary">Active Prompts</div>
          <div class="text-success text-lg">-</div>
        </div>
        <div class="p-2 rounded bg-surface-3/50">
          <div class="text-text-secondary">High Risk</div>
          <div class="text-danger text-lg">-</div>
        </div>
      </div>
    </div>
  </div>
</template>
