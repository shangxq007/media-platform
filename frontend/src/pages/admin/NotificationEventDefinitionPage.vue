<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { NotificationAPI } from '@/api/admin/notification'
import type { NotificationEventDefinition } from '@/api/admin/notification'
import { useI18nError } from '@/utils/i18n'
import PageHeader from '@/components/ui/PageHeader.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const { t } = useI18nError()

const loading = ref(true)
const error = ref<string | null>(null)
const errorCode = ref<string | null>(null)
const saving = ref(false)

const eventDefs = ref<NotificationEventDefinition[]>([])
const showForm = ref(false)
const editingKey = ref<string | null>(null)

const form = reactive({
  eventKey: '',
  name: '',
  description: '',
  category: 'SYSTEM',
  severity: 'MEDIUM',
  visibility: 'PUBLIC',
  userConfigurable: true,
  critical: false,
  defaultEnabled: true,
  supportedChannels: [] as string[],
  featureFlagKey: '',
})

const allChannels = ['IN_APP', 'EMAIL', 'SMS', 'WEBHOOK', 'CHAT', 'PUSH']
const categories = ['SYSTEM', 'SECURITY', 'BILLING', 'COLLABORATION', 'RENDER', 'EXPORT']
const severities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

onMounted(loadDefinitions)

async function loadDefinitions() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    eventDefs.value = await NotificationAPI.getEventDefinitions()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('COMMON-500-001', 'Failed to load event definitions')
    errorCode.value = 'COMMON-500-001'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingKey.value = null
  form.eventKey = ''
  form.name = ''
  form.description = ''
  form.category = 'SYSTEM'
  form.severity = 'MEDIUM'
  form.visibility = 'PUBLIC'
  form.userConfigurable = true
  form.critical = false
  form.defaultEnabled = true
  form.supportedChannels = ['IN_APP']
  form.featureFlagKey = ''
  showForm.value = true
}

function openEdit(evt: NotificationEventDefinition) {
  editingKey.value = evt.eventKey
  form.eventKey = evt.eventKey
  form.name = evt.name
  form.description = evt.description
  form.category = evt.category
  form.severity = evt.severity
  form.visibility = evt.visibility
  form.userConfigurable = evt.userConfigurable
  form.critical = evt.critical
  form.defaultEnabled = evt.defaultEnabled
  form.supportedChannels = [...evt.supportedChannels]
  form.featureFlagKey = evt.featureFlagKey || ''
  showForm.value = true
}

async function handleSave() {
  if (!form.eventKey.trim() || !form.name.trim()) return
  saving.value = true
  errorCode.value = null
  try {
    if (editingKey.value) {
      await NotificationAPI.updateEventDefinition(editingKey.value, {
        name: form.name,
        description: form.description,
        category: form.category,
        severity: form.severity,
        visibility: form.visibility,
        userConfigurable: form.userConfigurable,
        critical: form.critical,
        defaultEnabled: form.defaultEnabled,
        supportedChannels: form.supportedChannels,
        featureFlagKey: form.featureFlagKey || undefined,
      })
    } else {
      await NotificationAPI.createEventDefinition({
        eventKey: form.eventKey,
        name: form.name,
        description: form.description,
        category: form.category,
        severity: form.severity,
        visibility: form.visibility,
        userConfigurable: form.userConfigurable,
        critical: form.critical,
        defaultEnabled: form.defaultEnabled,
        supportedChannels: form.supportedChannels,
        featureFlagKey: form.featureFlagKey || undefined,
      })
    }
    showForm.value = false
    await loadDefinitions()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to save event definition')
    errorCode.value = 'NOTIFICATION-500-001'
  } finally {
    saving.value = false
  }
}

async function handleArchive(eventKey: string) {
  try {
    await NotificationAPI.archiveEventDefinition(eventKey)
    await loadDefinitions()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to archive event definition')
    errorCode.value = 'NOTIFICATION-500-001'
  }
}

function toggleChannel(ch: string) {
  const idx = form.supportedChannels.indexOf(ch)
  if (idx >= 0) form.supportedChannels.splice(idx, 1)
  else form.supportedChannels.push(ch)
}

function severityVariant(severity: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return 'info'
    case 'LOW': return 'success'
    default: return 'neutral'
  }
}

const activeEvents = computed(() => eventDefs.value.filter(e => !e.archived))
const archivedEvents = computed(() => eventDefs.value.filter(e => e.archived))
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Event Definitions" subtitle="Manage notification event definitions">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadDefinitions">Refresh</button>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="openCreate">+ New Event</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading event definitions..." />
    <ErrorState v-else-if="error && eventDefs.length === 0" :description="error" @retry="loadDefinitions" />

    <template v-else>
      <!-- Error banner -->
      <div v-if="errorCode && error" class="p-sm bg-danger-500/10 border border-danger-500/30 rounded text-xs text-danger-500 flex items-center gap-sm">
        <span>⚠️</span>
        <span>{{ error }}</span>
        <code class="ml-auto text-[10px] font-mono bg-danger-500/10 px-xs py-0.5 rounded">{{ errorCode }}</code>
      </div>

      <!-- Create/Edit Form -->
      <div v-if="showForm" class="c-card">
        <div class="c-card-header">
          <h3 class="text-sm font-semibold text-text-primary">{{ editingKey ? 'Edit Event Definition' : 'New Event Definition' }}</h3>
        </div>
        <div class="c-card-body space-y-md">
          <div class="grid grid-cols-2 gap-md">
            <div>
              <label class="c-form-label">Event Key *</label>
              <input v-model="form.eventKey" type="text" class="theme-input w-full" :disabled="!!editingKey" placeholder="e.g. BILLING_PAYMENT_FAILED" />
            </div>
            <div>
              <label class="c-form-label">Name *</label>
              <input v-model="form.name" type="text" class="theme-input w-full" placeholder="Human-readable name" />
            </div>
          </div>
          <div>
            <label class="c-form-label">Description</label>
            <textarea v-model="form.description" rows="2" class="theme-input w-full resize-none" placeholder="What triggers this event" />
          </div>
          <div class="grid grid-cols-4 gap-md">
            <div>
              <label class="c-form-label">Category</label>
              <select v-model="form.category" class="theme-input w-full">
                <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
              </select>
            </div>
            <div>
              <label class="c-form-label">Severity</label>
              <select v-model="form.severity" class="theme-input w-full">
                <option v-for="sev in severities" :key="sev" :value="sev">{{ sev }}</option>
              </select>
            </div>
            <div>
              <label class="c-form-label">Visibility</label>
              <select v-model="form.visibility" class="theme-input w-full">
                <option value="PUBLIC">Public</option>
                <option value="INTERNAL">Internal</option>
                <option value="ADMIN_ONLY">Admin Only</option>
              </select>
            </div>
            <div>
              <label class="c-form-label">Feature Flag Key</label>
              <input v-model="form.featureFlagKey" type="text" class="theme-input w-full" placeholder="Optional" />
            </div>
          </div>

          <!-- Supported channels -->
          <div>
            <label class="c-form-label">Supported Channels</label>
            <div class="flex gap-sm flex-wrap">
              <label v-for="ch in allChannels" :key="ch" class="flex items-center gap-xs p-sm rounded border border-default cursor-pointer hover:bg-bg-surface-hover"
                :class="form.supportedChannels.includes(ch) ? 'bg-primary-500/10 border-primary-200' : ''">
                <input type="checkbox" class="theme-toggle" :checked="form.supportedChannels.includes(ch)" @change="toggleChannel(ch)" />
                <span class="text-xs text-text-primary">{{ ch }}</span>
              </label>
            </div>
          </div>

          <!-- Toggles -->
          <div class="flex gap-lg">
            <label class="flex items-center gap-sm cursor-pointer">
              <input v-model="form.userConfigurable" type="checkbox" class="theme-toggle" />
              <span class="text-sm text-text-primary">User Configurable</span>
            </label>
            <label class="flex items-center gap-sm cursor-pointer">
              <input v-model="form.critical" type="checkbox" class="theme-toggle" />
              <span class="text-sm text-text-primary">Critical</span>
            </label>
            <label class="flex items-center gap-sm cursor-pointer">
              <input v-model="form.defaultEnabled" type="checkbox" class="theme-toggle" />
              <span class="text-sm text-text-primary">Default Enabled</span>
            </label>
          </div>

          <div class="flex justify-end gap-sm pt-md border-t border-default">
            <button class="theme-btn theme-btn-secondary" @click="showForm = false">Cancel</button>
            <button class="theme-btn theme-btn-primary" :disabled="!form.eventKey.trim() || !form.name.trim() || saving" @click="handleSave">
              <span v-if="saving" class="c-spinner c-spinner-sm mr-xs" />
              {{ saving ? 'Saving...' : (editingKey ? 'Update' : 'Create') }}
            </button>
          </div>
        </div>
      </div>

      <!-- Active Events -->
      <div class="c-card">
        <div class="c-card-header">
          <h3 class="text-sm font-semibold text-text-primary">Active Events ({{ activeEvents.length }})</h3>
        </div>
        <div class="c-card-body">
          <EmptyState v-if="activeEvents.length === 0" icon="📋" title="No active events" description="Create a new event definition to get started." />
          <table v-else class="w-full text-sm">
            <thead>
              <tr class="border-b border-default text-xs text-text-muted">
                <th class="text-left px-sm py-sm">Event Key</th>
                <th class="text-left px-sm py-sm">Name</th>
                <th class="text-left px-sm py-sm">Category</th>
                <th class="text-left px-sm py-sm">Severity</th>
                <th class="text-left px-sm py-sm">Channels</th>
                <th class="text-left px-sm py-sm">Configurable</th>
                <th class="text-left px-sm py-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="evt in activeEvents" :key="evt.eventKey" class="border-b border-default/50 hover:bg-bg-surface-hover">
                <td class="px-sm py-sm text-xs font-mono text-primary-400">{{ evt.eventKey }}</td>
                <td class="px-sm py-sm">
                  <div class="flex items-center gap-xs">
                    <span class="text-text-primary">{{ evt.name }}</span>
                    <span v-if="evt.critical" class="text-[10px] px-1 py-0.5 rounded bg-danger-500/10 text-danger-500">CRITICAL</span>
                  </div>
                </td>
                <td class="px-sm py-sm text-text-muted">{{ evt.category }}</td>
                <td class="px-sm py-sm"><StatusBadge :variant="severityVariant(evt.severity)" :label="evt.severity" size="sm" /></td>
                <td class="px-sm py-sm text-xs text-text-muted">{{ evt.supportedChannels.join(', ') }}</td>
                <td class="px-sm py-sm">
                  <StatusBadge :variant="evt.userConfigurable ? 'success' : 'neutral'" :label="evt.userConfigurable ? 'Yes' : 'No'" size="sm" />
                </td>
                <td class="px-sm py-sm">
                  <div class="flex gap-xs">
                    <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="openEdit(evt)">Edit</button>
                    <button class="theme-btn theme-btn-danger theme-btn-sm" @click="handleArchive(evt.eventKey)">Archive</button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Archived Events -->
      <div v-if="archivedEvents.length > 0" class="c-card">
        <div class="c-card-header">
          <h3 class="text-sm font-semibold text-text-muted">Archived Events ({{ archivedEvents.length }})</h3>
        </div>
        <div class="c-card-body">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-default text-xs text-text-muted">
                <th class="text-left px-sm py-sm">Event Key</th>
                <th class="text-left px-sm py-sm">Name</th>
                <th class="text-left px-sm py-sm">Category</th>
                <th class="text-left px-sm py-sm">Archived</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="evt in archivedEvents" :key="evt.eventKey" class="border-b border-default/50 opacity-60">
                <td class="px-sm py-sm text-xs font-mono text-text-muted">{{ evt.eventKey }}</td>
                <td class="px-sm py-sm text-text-muted">{{ evt.name }}</td>
                <td class="px-sm py-sm text-text-muted">{{ evt.category }}</td>
                <td class="px-sm py-sm text-xs text-text-muted">{{ evt.updatedAt || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>
