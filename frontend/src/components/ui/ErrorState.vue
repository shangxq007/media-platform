<script setup lang="ts">
import { ref } from 'vue'

const props = withDefaults(defineProps<{
  title?: string
  description?: string
  errorCode?: string | number
  errorDetails?: string
  showRetry?: boolean
  showDismiss?: boolean
  diagnosticId?: string
  showAdminDebug?: boolean
}>(), {
  title: 'Something went wrong',
  description: 'An unexpected error occurred. Please try again.',
  showRetry: true,
  showDismiss: true,
  showAdminDebug: false,
})

defineEmits<{
  retry: []
  dismiss: []
}>()

const copied = ref(false)
const showDebug = ref(false)

async function copyDiagnosticId() {
  const text = props.diagnosticId || ''
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    /* clipboard unavailable */
  }
}

async function copyError() {
  const text = [props.title, props.description, props.errorCode ? `Code: ${props.errorCode}` : '', props.errorDetails, props.diagnosticId ? `Diagnostic ID: ${props.diagnosticId}` : '']
    .filter(Boolean)
    .join('\n')
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    /* clipboard unavailable */
  }
}
</script>

<template>
  <div class="c-error-state">
    <div class="text-3xl mb-md">alert-triangle</div>
    <div class="text-lg font-semibold text-text-primary mb-xs">{{ title }}</div>
    <div class="text-sm text-text-secondary mb-md">{{ description }}</div>

    <!-- Error Code Display -->
    <div v-if="errorCode" class="flex items-center gap-sm mb-md">
      <span class="text-xs text-text-muted">Error Code:</span>
      <code class="text-xs font-mono bg-bg-surface px-sm py-xs rounded text-text-primary">{{ errorCode }}</code>
    </div>

    <!-- Diagnostic ID with copy button -->
    <div v-if="diagnosticId" class="flex items-center gap-sm mb-md p-sm bg-bg-surface rounded border border-default">
      <div class="flex-1 min-w-0">
        <div class="text-xs text-text-muted mb-xs">Diagnostic ID</div>
        <code class="text-xs font-mono text-text-primary truncate-text block">{{ diagnosticId }}</code>
      </div>
      <button class="theme-btn theme-btn-secondary theme-btn-sm flex-shrink-0" @click="copyDiagnosticId">
        {{ copied ? '✓ Copied!' : 'Copy' }}
      </button>
    </div>

    <!-- Error Details -->
    <div v-if="errorDetails" class="c-card mb-md text-left w-full max-w-md">
      <div class="c-card-body">
        <pre class="text-xs text-text-secondary whitespace-pre-wrap break-words bg-bg-surface p-sm rounded overflow-auto max-h-32 theme-scrollbar">{{ errorDetails }}</pre>
      </div>
    </div>

    <!-- Admin Debug Toggle -->
    <div v-if="showAdminDebug" class="mb-md">
      <button class="theme-btn theme-btn-ghost theme-btn-sm text-xs" @click="showDebug = !showDebug">
        {{ showDebug ? 'Hide' : 'Show' }} Debug Info
      </button>
      <div v-if="showDebug" class="mt-sm p-sm bg-bg-surface rounded border border-default text-left w-full max-w-md">
        <pre class="text-xs text-text-muted font-mono whitespace-pre-wrap">{{ JSON.stringify({ errorCode, diagnosticId, title, description }, null, 2) }}</pre>
      </div>
    </div>

    <div class="flex items-center gap-sm flex-wrap justify-center">
      <button v-if="showRetry" class="theme-btn theme-btn-primary" @click="$emit('retry')">
        Retry
      </button>
      <button class="theme-btn theme-btn-secondary" @click="copyError">
        {{ copied ? 'Copied!' : 'Copy Error' }}
      </button>
      <button v-if="showDismiss" class="theme-btn theme-btn-ghost" @click="$emit('dismiss')">
        Dismiss
      </button>
    </div>
  </div>
</template>
