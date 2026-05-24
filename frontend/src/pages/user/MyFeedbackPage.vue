<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type { FeedbackItem } from '@/api/me'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import RiskBadge from '@/components/ui/RiskBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import { formatApiError } from '@/utils/apiError'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(true)
const error = ref<string | null>(null)
const feedbackItems = ref<FeedbackItem[]>([])
const showSubmitDialog = ref(false)
const submitLoading = ref(false)
const submitError = ref<string | null>(null)
const newFeedback = ref({ type: 'GENERAL' as 'BUG' | 'FEATURE' | 'GENERAL', severity: 'low' as 'low' | 'medium' | 'high' | 'critical', title: '', description: '' })

onMounted(loadFeedback)

async function loadFeedback() {
  loading.value = true
  error.value = null
  try {
    const result = await MeEntitlementAPI.getMyFeedback()
    feedbackItems.value = result.feedback
  } catch (e: unknown) {
    error.value = formatApiError(e, 'Failed to load feedback')
  } finally {
    loading.value = false
  }
}

function typeVariant(type: string): 'danger' | 'info' | 'neutral' {
  switch (type) {
    case 'BUG': return 'danger'
    case 'FEATURE': return 'info'
    default: return 'neutral'
  }
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'neutral' | 'info' {
  switch (status) {
    case 'RESOLVED': case 'CLOSED': return 'success'
    case 'IN_PROGRESS': return 'warning'
    case 'OPEN': return 'info'
    default: return 'neutral'
  }
}

async function handleSubmit() {
  if (!newFeedback.value.title.trim()) return
  submitLoading.value = true
  submitError.value = null
  try {
    await MeEntitlementAPI.submitFeedback(
      newFeedback.value.type,
      newFeedback.value.severity,
      newFeedback.value.title,
      newFeedback.value.description
    )
    showSubmitDialog.value = false
    newFeedback.value = { type: 'GENERAL', severity: 'low', title: '', description: '' }
    await loadFeedback()
  } catch (e: unknown) {
    submitError.value = e instanceof Error ? e.message : 'Failed to submit feedback'
  } finally {
    submitLoading.value = false
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Feedback" subtitle="View and submit feedback">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="showSubmitDialog = true">+ Submit Feedback</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadFeedback">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading feedback..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadFeedback" />

    <template v-else>
      <EmptyState v-if="feedbackItems.length === 0" icon="message-circle" title="No feedback submitted" description="Submit feedback to help us improve the platform.">
        <template #action>
          <button class="theme-btn theme-btn-primary" @click="showSubmitDialog = true">Submit Feedback</button>
        </template>
      </EmptyState>

      <PageSection v-else title="Your Feedback">
        <div class="space-y-md">
          <div v-for="item in feedbackItems" :key="item.id" class="c-card">
            <div class="c-card-body">
              <div class="flex items-start justify-between mb-sm">
                <div class="min-w-0 flex-1">
                  <h3 class="text-sm font-medium text-text-primary">{{ item.title }}</h3>
                  <p v-if="item.description" class="text-xs text-text-secondary mt-xs line-clamp-2">{{ item.description }}</p>
                </div>
              </div>
              <div class="flex items-center gap-sm flex-wrap">
                <StatusBadge :variant="typeVariant(item.type)" :label="item.type" />
                <RiskBadge :level="item.severity" />
                <StatusBadge :variant="statusVariant(item.status)" :label="item.status" />
                <span class="text-xs text-text-muted ml-auto">{{ item.createdAt }}</span>
              </div>
            </div>
          </div>
        </div>
      </PageSection>
    </template>

    <!-- Submit Feedback Dialog -->
    <Teleport to="body">
      <div v-if="showSubmitDialog" class="c-dialog-overlay" @click.self="showSubmitDialog = false">
        <div class="c-dialog" style="max-width: 520px;">
          <div class="c-dialog-header">
            <h3 class="text-lg font-semibold text-text-primary">Submit Feedback</h3>
          </div>
          <div class="c-dialog-body space-y-md">
            <div v-if="submitError" class="p-sm bg-danger-500/10 rounded text-xs text-danger-500">
              {{ submitError }}
            </div>
            <div class="grid grid-cols-2 gap-md">
              <div>
                <label class="c-form-label">Type</label>
                <select v-model="newFeedback.type" class="theme-input w-full">
                  <option value="BUG">Bug Report</option>
                  <option value="FEATURE">Feature Request</option>
                  <option value="GENERAL">General</option>
                </select>
              </div>
              <div>
                <label class="c-form-label">Severity</label>
                <select v-model="newFeedback.severity" class="theme-input w-full">
                  <option value="low">Low</option>
                  <option value="medium">Medium</option>
                  <option value="high">High</option>
                  <option value="critical">Critical</option>
                </select>
              </div>
            </div>
            <div>
              <label class="c-form-label">Title</label>
              <input v-model="newFeedback.title" type="text" class="theme-input w-full" placeholder="Brief summary" />
            </div>
            <div>
              <label class="c-form-label">Description</label>
              <textarea v-model="newFeedback.description" rows="4" class="theme-input w-full" placeholder="Describe your feedback in detail..." />
            </div>
          </div>
          <div class="c-dialog-footer">
            <button class="theme-btn theme-btn-secondary" @click="showSubmitDialog = false">Cancel</button>
            <button class="theme-btn theme-btn-primary" :disabled="!newFeedback.title.trim() || submitLoading" @click="handleSubmit">
              {{ submitLoading ? 'Submitting...' : 'Submit' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
