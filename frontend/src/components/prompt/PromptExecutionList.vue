<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { PromptAPI } from '@/api/prompt'
import type { PromptExecutionRun } from '@/types'

const executions = ref<PromptExecutionRun[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const selectedExecution = ref<PromptExecutionRun | null>(null)

const statusColors: Record<string, string> = {
  PENDING: 'bg-gray-500',
  RUNNING: 'bg-blue-500',
  SUCCEEDED: 'bg-green-500',
  FAILED: 'bg-red-500',
  CANCELLED: 'bg-yellow-500',
  REQUIRE_REVIEW: 'bg-orange-500'
}

onMounted(loadExecutions)

async function loadExecutions() {
  loading.value = true
  try {
    executions.value = await PromptAPI.listExecutions()
  } catch (e: any) {
    error.value = e.message || 'Failed to load executions'
  } finally {
    loading.value = false
  }
}

function selectExecution(execution: PromptExecutionRun) {
  selectedExecution.value = execution
}
</script>

<template>
  <div class="flex flex-col h-full">
    <div class="flex items-center justify-between p-3 border-b border-gray-700">
      <h2 class="text-lg font-semibold text-white">Executions</h2>
      <button class="text-xs text-blue-400 hover:text-blue-300" @click="loadExecutions">Refresh</button>
    </div>

    <div v-if="error" class="p-3 text-red-400 text-sm">{{ error }}</div>
    <div v-if="loading" class="p-3 text-gray-400 text-sm">Loading...</div>

    <div class="flex-1 flex overflow-hidden">
      <!-- List -->
      <div class="w-1/2 border-r border-gray-700 overflow-y-auto">
        <div v-if="!loading && executions.length === 0" class="p-3 text-gray-500 text-sm">
          No executions found
        </div>
        <div v-for="exec in executions" :key="exec.executionId"
          class="p-3 border-b border-gray-700 hover:bg-gray-800/50 cursor-pointer"
          :class="selectedExecution?.executionId === exec.executionId ? 'bg-gray-800' : ''"
          @click="selectExecution(exec)">
          <div class="flex items-center justify-between">
            <span class="text-white text-sm font-mono">{{ exec.executionId?.slice(0, 12) }}</span>
            <span class="px-1.5 py-0.5 rounded text-[10px]"
              :class="statusColors[exec.status] || 'bg-gray-600'">
              {{ exec.status }}
            </span>
          </div>
          <div class="text-gray-400 text-xs mt-1">
            {{ exec.modelProvider }} / {{ exec.modelName }}
          </div>
          <div class="text-gray-500 text-xs">
            Tokens: {{ exec.tokenEstimate }} | Cost: ${{ exec.costEstimate?.toFixed(4) }}
          </div>
        </div>
      </div>

      <!-- Detail -->
      <div class="w-1/2 overflow-y-auto p-3">
        <div v-if="!selectedExecution" class="text-gray-500 text-sm">
          Select an execution to view details
        </div>
        <div v-else class="space-y-3">
          <div>
            <span class="text-xs text-gray-400">Execution ID</span>
            <div class="text-white text-sm font-mono">{{ selectedExecution.executionId }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Template</span>
            <div class="text-white text-sm">{{ selectedExecution.templateId }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Version</span>
            <div class="text-white text-sm">{{ selectedExecution.promptVersion }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Provider / Model</span>
            <div class="text-white text-sm">{{ selectedExecution.modelProvider }} / {{ selectedExecution.modelName }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Status</span>
            <div><span class="px-1.5 py-0.5 rounded text-[10px]"
              :class="statusColors[selectedExecution.status]">{{ selectedExecution.status }}</span></div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Risk Level</span>
            <div class="text-white text-sm">{{ selectedExecution.riskLevel }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Token Estimate</span>
            <div class="text-white text-sm">{{ selectedExecution.tokenEstimate }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Cost Estimate</span>
            <div class="text-white text-sm">${{ selectedExecution.costEstimate?.toFixed(4) }}</div>
          </div>
          <div>
            <span class="text-xs text-gray-400">Started</span>
            <div class="text-white text-sm">{{ selectedExecution.startedAt }}</div>
          </div>
          <div v-if="selectedExecution.finishedAt">
            <span class="text-xs text-gray-400">Finished</span>
            <div class="text-white text-sm">{{ selectedExecution.finishedAt }}</div>
          </div>
          <div v-if="selectedExecution.errorCode">
            <span class="text-xs text-gray-400">Error</span>
            <div class="text-red-400 text-sm">{{ selectedExecution.errorCode }}: {{ selectedExecution.errorDetailsJson }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
