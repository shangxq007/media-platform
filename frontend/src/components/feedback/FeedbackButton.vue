<script setup lang="ts">
import { ref, computed } from 'vue'
import { submitOpenReplayFeedback, getOpenReplaySessionId, isOpenReplayInitialized } from '@/utils/openreplay'
import { captureSentryException, getSentryReplayId, isSentryInitialized } from '@/utils/sentry'

const showFeedbackModal = ref(false)
const feedbackType = ref<'bug' | 'feature' | 'other'>('bug')
const feedbackTitle = ref('')
const feedbackDescription = ref('')
const feedbackSeverity = ref<'low' | 'medium' | 'high' | 'critical'>('medium')
const renderJobId = ref('')
const promptExecutionId = ref('')
const sessionId = ref('')
const submitting = ref(false)
const submitResult = ref<'success' | 'error' | null>(null)

const sentryInitialized = isSentryInitialized()
const openReplayInitialized = isOpenReplayInitialized()

const canSubmit = computed(() =>
  feedbackTitle.value.trim().length > 0 &&
  feedbackDescription.value.trim().length > 0 &&
  !submitting.value
)

const monitoringActive = computed(() => sentryInitialized.value || openReplayInitialized.value)

function openFeedbackModal() {
  showFeedbackModal.value = true
  submitResult.value = null
}

function closeFeedbackModal() {
  showFeedbackModal.value = false
  feedbackTitle.value = ''
  feedbackDescription.value = ''
  feedbackType.value = 'bug'
  feedbackSeverity.value = 'medium'
  submitResult.value = null
  renderJobId.value = ''
  promptExecutionId.value = ''
  sessionId.value = ''
}

async function submitFeedback() {
  if (!canSubmit.value) return
  submitting.value = true
  submitResult.value = null

  try {
    const sentryReplayId = getSentryReplayId()
    const openReplaySessionId = getOpenReplaySessionId()

    const orSuccess = await submitOpenReplayFeedback({
      type: feedbackType.value,
      title: feedbackTitle.value.trim(),
      description: feedbackDescription.value.trim(),
      severity: feedbackSeverity.value,
      customData: {
        sentryReplayId,
        openReplaySessionId,
        renderJobId: renderJobId.value.trim() || undefined,
        promptExecutionId: promptExecutionId.value.trim() || undefined,
        sessionId: sessionId.value.trim() || undefined,
        userAgent: navigator.userAgent,
        url: window.location.href,
        timestamp: new Date().toISOString()
      }
    })

    if (sentryInitialized.value) {
      captureSentryException(
        new Error(`[User Feedback] ${feedbackTitle.value.trim()}`),
        {
          feedbackType: feedbackType.value,
          severity: feedbackSeverity.value,
          openReplaySessionId,
          sentryReplayId,
          renderJobId: renderJobId.value.trim() || undefined,
          promptExecutionId: promptExecutionId.value.trim() || undefined,
          sessionId: sessionId.value.trim() || undefined,
        }
      )
    }

    submitResult.value = orSuccess ? 'success' : 'error'

    if (orSuccess) {
      setTimeout(() => {
        closeFeedbackModal()
      }, 1500)
    }
  } catch (e) {
    console.error('Feedback submission failed:', e)
    submitResult.value = 'error'
  } finally {
    submitting.value = false
  }
}

const feedbackTypes = [
  { value: 'bug' as const, label: 'Bug', icon: '🐛' },
  { value: 'feature' as const, label: 'Feature', icon: '💡' },
  { value: 'other' as const, label: 'Other', icon: '💬' },
]

const severityLevels = [
  { value: 'low' as const, label: 'Low', variant: 'neutral' as const },
  { value: 'medium' as const, label: 'Medium', variant: 'warning' as const },
  { value: 'high' as const, label: 'High', variant: 'danger' as const },
  { value: 'critical' as const, label: 'Critical', variant: 'danger' as const },
]
</script>

<template>
  <!-- Feedback Button -->
  <button
    class="fixed bottom-4 right-4 z-50 flex items-center gap-sm px-md py-sm bg-primary-600 hover:bg-primary-700 text-text-inverse text-sm rounded-lg shadow-lg transition-colors"
    title="Report an issue or suggest a feature"
    @click="openFeedbackModal">
    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
        d="M7 8h10M7 12h4m1 8l-4-4H5a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v8a2 2 0 01-2 2h-3l-4 4z" />
    </svg>
    <span>Feedback</span>
    <span v-if="monitoringActive"
      class="w-2 h-2 rounded-full bg-success-500" title="Monitoring active"></span>
  </button>

  <!-- Feedback Modal -->
  <Teleport to="body">
    <div v-if="showFeedbackModal" class="c-dialog-overlay" @click.self="closeFeedbackModal">
      <div class="c-dialog" style="max-width: 520px;">
        <!-- Header -->
        <div class="c-dialog-header">
          <div class="flex items-center gap-sm">
            <span class="text-lg">💬</span>
            <h3 class="text-lg font-semibold text-text-primary">Send Feedback</h3>
          </div>
          <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="closeFeedbackModal">✕</button>
        </div>

        <!-- Body -->
        <div class="c-dialog-body space-y-lg">
          <!-- Type -->
          <div>
            <label class="text-xs text-text-muted font-medium block mb-sm">Type</label>
            <div class="flex gap-sm">
              <button v-for="type in feedbackTypes" :key="type.value"
                class="theme-btn flex-1 justify-center"
                :class="feedbackType === type.value ? 'theme-btn-primary' : 'theme-btn-secondary'"
                @click="feedbackType = type.value">
                <span>{{ type.icon }}</span>
                <span>{{ type.label }}</span>
              </button>
            </div>
          </div>

          <!-- Severity -->
          <div>
            <label class="text-xs text-text-muted font-medium block mb-sm">Severity</label>
            <div class="flex gap-sm">
              <button v-for="sev in severityLevels" :key="sev.value"
                class="theme-btn flex-1 justify-center"
                :class="feedbackSeverity === sev.value ? (sev.variant === 'danger' ? 'theme-btn-danger' : sev.variant === 'warning' ? 'bg-warning-600 text-white hover:bg-warning-700' : 'theme-btn-primary') : 'theme-btn-secondary'"
                @click="feedbackSeverity = sev.value">
                {{ sev.label }}
              </button>
            </div>
          </div>

          <!-- Title -->
          <div>
            <label class="text-xs text-text-muted font-medium block mb-xs">Title <span class="text-danger-500">*</span></label>
            <input v-model="feedbackTitle" placeholder="Brief description of the issue..."
              class="w-full theme-input" />
          </div>

          <!-- Description -->
          <div>
            <label class="text-xs text-text-muted font-medium block mb-xs">Description <span class="text-danger-500">*</span></label>
            <textarea v-model="feedbackDescription" rows="4"
              placeholder="Detailed description, steps to reproduce, expected behavior..."
              class="w-full theme-input resize-none" />
          </div>

          <!-- Context Fields -->
          <div>
            <label class="text-xs text-text-muted font-medium block mb-sm">Context (optional)</label>
            <div class="space-y-sm">
              <input v-model="renderJobId" placeholder="Render Job ID"
                class="w-full theme-input" />
              <input v-model="promptExecutionId" placeholder="Prompt Execution ID"
                class="w-full theme-input" />
              <input v-model="sessionId" placeholder="Session ID"
                class="w-full theme-input" />
            </div>
          </div>

          <!-- Monitoring Status -->
          <div class="flex items-center gap-xs text-xs text-text-muted">
            <span v-if="monitoringActive" class="w-1.5 h-1.5 rounded-full bg-success-500"></span>
            <span v-else class="w-1.5 h-1.5 rounded-full bg-text-muted"></span>
            <span v-if="sentryInitialized">Sentry active</span>
            <span v-if="sentryInitialized && openReplayInitialized"> · </span>
            <span v-if="openReplayInitialized">OpenReplay recording</span>
            <span v-if="!monitoringActive">No monitoring configured</span>
          </div>

          <!-- Result -->
          <div v-if="submitResult === 'success'" class="p-sm rounded bg-success-500/10 border border-success-200 text-sm text-success-500 flex items-center gap-sm">
            <span>✓</span>
            <span>Feedback submitted successfully!</span>
          </div>
          <div v-if="submitResult === 'error'" class="p-sm rounded bg-danger-500/10 border border-danger-200 text-sm text-danger-500 flex items-center gap-sm">
            <span>✗</span>
            <span>Failed to submit. Please try again.</span>
          </div>
        </div>

        <!-- Footer -->
        <div class="c-dialog-footer">
          <button class="theme-btn theme-btn-ghost" @click="closeFeedbackModal">
            Cancel
          </button>
          <button class="theme-btn theme-btn-primary"
            :disabled="!canSubmit" @click="submitFeedback">
            <span v-if="submitting" class="c-spinner c-spinner-sm mr-xs" />
            {{ submitting ? 'Submitting...' : 'Submit Feedback' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
