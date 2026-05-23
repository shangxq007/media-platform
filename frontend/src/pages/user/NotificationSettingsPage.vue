<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import type {
  NotificationChannelBinding,
  NotificationSubscription,
  NotificationPreference,
  NotificationEventCatalogItem
} from '@/api/me'
import { useI18nError } from '@/utils/i18n'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const { t } = useI18nError()

const loading = ref(true)
const error = ref<string | null>(null)
const saving = ref(false)
const errorCode = ref<string | null>(null)

const catalog = ref<NotificationEventCatalogItem[]>([])
const subscriptions = ref<NotificationSubscription[]>([])
const channels = ref<NotificationChannelBinding[]>([])
const preferences = ref<NotificationPreference | null>(null)

const channelError = ref<string | null>(null)
const channelSaving = ref(false)
const showBindChannel = ref(false)
const bindForm = reactive({ channelType: 'EMAIL' as string, destination: '', webhookSecret: '' })

const activeTab = ref<'subscriptions' | 'channels' | 'preferences'>('subscriptions')

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  error.value = null
  errorCode.value = null
  try {
    const [cat, subs, chans, prefs] = await Promise.allSettled([
      MeEntitlementAPI.getNotificationEventCatalog(),
      MeEntitlementAPI.getNotificationSubscriptions(),
      MeEntitlementAPI.getNotificationChannels(),
      MeEntitlementAPI.getNotificationPreferences(),
    ])
    if (cat.status === 'fulfilled') catalog.value = cat.value
    if (subs.status === 'fulfilled') subscriptions.value = subs.value
    if (chans.status === 'fulfilled') channels.value = chans.value
    if (prefs.status === 'fulfilled') preferences.value = prefs.value
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('COMMON-500-001', 'Failed to load notification settings')
    if (msg.includes('401')) errorCode.value = 'COMMON-401-001'
    else if (msg.includes('403')) errorCode.value = 'COMMON-403-001'
    else errorCode.value = 'COMMON-500-001'
  } finally {
    loading.value = false
  }
}

const subscriptionMap = computed(() => {
  const map = new Map<string, NotificationSubscription>()
  for (const s of subscriptions.value) map.set(s.eventKey, s)
  return map
})

const configurableEvents = computed(() => catalog.value.filter(e => e.userConfigurable))

const criticalEvents = computed(() => configurableEvents.value.filter(e => e.critical))

function getSubscription(eventKey: string): NotificationSubscription | undefined {
  return subscriptionMap.value.get(eventKey)
}

function isSubscribed(eventKey: string): boolean {
  const sub = getSubscription(eventKey)
  return sub ? sub.enabled : (catalog.value.find(e => e.eventKey === eventKey)?.defaultEnabled ?? true)
}

async function toggleSubscription(eventKey: string, enabled: boolean) {
  const event = catalog.value.find(e => e.eventKey === eventKey)
  if (event?.critical && !enabled) {
    error.value = t('NOTIFICATION-403-001', 'Critical notifications cannot be disabled')
    errorCode.value = 'NOTIFICATION-403-001'
    return
  }
  saving.value = true
  errorCode.value = null
  try {
    await MeEntitlementAPI.updateNotificationSubscription(eventKey, enabled)
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to update subscription')
    errorCode.value = 'NOTIFICATION-500-001'
  } finally {
    saving.value = false
  }
}

async function batchToggle(enabled: boolean) {
  saving.value = true
  errorCode.value = null
  try {
    const updates = configurableEvents.value
      .filter(e => !e.critical)
      .map(e => ({ eventKey: e.eventKey, enabled }))
    await MeEntitlementAPI.batchUpdateNotificationSubscriptions(updates)
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to update subscriptions')
    errorCode.value = 'NOTIFICATION-500-001'
  } finally {
    saving.value = false
  }
}

async function handleBindChannel() {
  if (!bindForm.destination.trim()) return
  channelSaving.value = true
  channelError.value = null
  try {
    await MeEntitlementAPI.bindNotificationChannel(bindForm.channelType, bindForm.destination, bindForm.webhookSecret || undefined)
    showBindChannel.value = false
    bindForm.destination = ''
    bindForm.webhookSecret = ''
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    channelError.value = msg || t('NOTIFICATION-400-001', 'Failed to bind channel')
  } finally {
    channelSaving.value = false
  }
}

async function handleVerify(bindingId: string) {
  try {
    await MeEntitlementAPI.verifyNotificationChannel(bindingId)
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    channelError.value = msg || t('NOTIFICATION-500-001', 'Verification failed')
  }
}

async function handleTest(bindingId: string) {
  try {
    await MeEntitlementAPI.testNotificationChannel(bindingId)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    channelError.value = msg || t('NOTIFICATION-500-001', 'Test failed')
  }
}

async function handleDisable(bindingId: string) {
  try {
    await MeEntitlementAPI.disableNotificationChannel(bindingId)
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    channelError.value = msg || t('NOTIFICATION-500-001', 'Failed to disable channel')
  }
}

async function handleDelete(bindingId: string) {
  try {
    await MeEntitlementAPI.deleteNotificationChannel(bindingId)
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    channelError.value = msg || t('NOTIFICATION-500-001', 'Failed to delete channel')
  }
}

async function handleSavePreferences() {
  if (!preferences.value) return
  saving.value = true
  errorCode.value = null
  try {
    await MeEntitlementAPI.updateNotificationPreferences({
      globalEnabled: preferences.value.globalEnabled,
      quietHoursStart: preferences.value.quietHoursStart,
      quietHoursEnd: preferences.value.quietHoursEnd,
      quietHoursTimezone: preferences.value.quietHoursTimezone,
      digestMode: preferences.value.digestMode,
      criticalOverride: preferences.value.criticalOverride,
      channelEnabled: preferences.value.channelEnabled,
    })
    await loadAll()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : ''
    error.value = msg || t('NOTIFICATION-500-001', 'Failed to save preferences')
    errorCode.value = 'NOTIFICATION-500-001'
  } finally {
    saving.value = false
  }
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

function channelIcon(type: string): string {
  switch (type) {
    case 'EMAIL': return '📧'
    case 'SMS': return '📱'
    case 'WEBHOOK': return '🔗'
    case 'CHAT': return '💬'
    case 'PUSH': return '🔔'
    case 'IN_APP': return '📌'
    default: return '📡'
  }
}

function categoryLabel(category: string): string {
  const map: Record<string, string> = {
    'BILLING': 'Billing',
    'SECURITY': 'Security',
    'SYSTEM': 'System',
    'COLLABORATION': 'Collaboration',
    'RENDER': 'Render',
    'EXPORT': 'Export',
  }
  return map[category] || category
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Notification Settings" subtitle="Manage how and when you receive notifications">
      <template #actions>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadAll">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading notification settings..." />
    <ErrorState v-else-if="error && !errorCode" :description="error" @retry="loadAll" />

    <template v-else>
      <!-- Error banner -->
      <div v-if="error" class="p-sm bg-danger-500/10 border border-danger-500/30 rounded text-xs text-danger-500 flex items-center gap-sm">
        <span>⚠️</span>
        <span>{{ error }}</span>
        <code v-if="errorCode" class="ml-auto text-[10px] font-mono bg-danger-500/10 px-xs py-0.5 rounded">{{ errorCode }}</code>
      </div>

      <!-- Critical events notice -->
      <div v-if="criticalEvents.length > 0" class="c-card border-warning-500/50 bg-warning-500/5">
        <div class="c-card-body flex items-start gap-md">
          <span class="text-lg flex-shrink-0">⚠️</span>
          <div>
            <div class="text-sm font-semibold text-warning-500">Critical Notifications</div>
            <div class="text-xs text-text-secondary mt-xs">
              The following events are critical and cannot be disabled:
              <span v-for="(evt, i) in criticalEvents" :key="evt.eventKey" class="font-medium">
                {{ evt.name }}<span v-if="i < criticalEvents.length - 1">, </span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab navigation -->
      <div class="flex gap-xs border-b border-default">
        <button
          class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'subscriptions' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'subscriptions'">
          Event Subscriptions
        </button>
        <button
          class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'channels' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'channels'">
          Channel Bindings
        </button>
        <button
          class="px-lg py-sm text-sm font-medium transition-colors"
          :class="activeTab === 'preferences' ? 'text-text-primary border-b-2 border-primary-500' : 'text-text-muted hover:text-text-secondary'"
          @click="activeTab = 'preferences'">
          Preferences
        </button>
      </div>

      <!-- Subscriptions Tab -->
      <template v-if="activeTab === 'subscriptions'">
        <PageSection title="Event Subscriptions" description="Choose which events you want to be notified about">
          <div class="c-card">
            <div class="c-card-body">
              <div class="flex items-center justify-between mb-md">
                <div class="text-xs text-text-muted">{{ configurableEvents.length }} configurable events</div>
                <div class="flex gap-sm">
                  <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="saving" @click="batchToggle(true)">Enable all</button>
                  <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="saving" @click="batchToggle(false)">Disable non-critical</button>
                </div>
              </div>

              <EmptyState v-if="configurableEvents.length === 0" icon="📭" title="No configurable events" description="No notification events are available for configuration." />

              <div v-else class="space-y-sm">
                <div v-for="event in configurableEvents" :key="event.eventKey"
                  class="flex items-center justify-between p-sm rounded border border-default hover:bg-bg-surface-hover transition-colors">
                  <div class="min-w-0 flex-1 mr-md">
                    <div class="flex items-center gap-sm mb-xs">
                      <span class="text-sm font-medium text-text-primary">{{ event.name }}</span>
                      <StatusBadge :variant="severityVariant(event.severity)" :label="event.severity" size="sm" />
                      <span v-if="event.critical" class="text-[10px] px-1.5 py-0.5 rounded bg-danger-500/10 text-danger-500 font-medium">CRITICAL</span>
                      <span class="text-[10px] px-1.5 py-0.5 rounded bg-bg-surface text-text-muted">{{ categoryLabel(event.category) }}</span>
                    </div>
                    <div class="text-xs text-text-muted">{{ event.description }}</div>
                    <div class="flex gap-xs mt-xs">
                      <span v-for="ch in event.supportedChannels" :key="ch" class="text-[10px] px-1 py-0.5 rounded bg-bg-surface text-text-muted font-mono">
                        {{ channelIcon(ch) }} {{ ch }}
                      </span>
                    </div>
                  </div>
                  <div class="flex items-center gap-sm">
                    <input
                      type="checkbox"
                      class="theme-toggle"
                      :checked="isSubscribed(event.eventKey)"
                      :disabled="event.critical || saving"
                      @change="toggleSubscription(event.eventKey, ($event.target as HTMLInputElement).checked)"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </PageSection>
      </template>

      <!-- Channels Tab -->
      <template v-if="activeTab === 'channels'">
        <PageSection title="Channel Bindings" description="Configure where notifications are sent">
          <div class="c-card">
            <div class="c-card-body">
              <div class="flex items-center justify-between mb-md">
                <div class="text-xs text-text-muted">{{ channels.length }} bound channels</div>
                <button class="theme-btn theme-btn-primary theme-btn-sm" @click="showBindChannel = !showBindChannel">
                  {{ showBindChannel ? 'Cancel' : '+ Bind Channel' }}
                </button>
              </div>

              <!-- Bind form -->
              <div v-if="showBindChannel" class="p-md bg-bg-surface rounded border border-default mb-md space-y-md">
                <div v-if="channelError" class="p-sm bg-danger-500/10 rounded text-xs text-danger-500">
                  {{ channelError }}
                </div>
                <div class="grid grid-cols-3 gap-md">
                  <div>
                    <label class="c-form-label">Channel Type</label>
                    <select v-model="bindForm.channelType" class="theme-input w-full">
                      <option value="EMAIL">Email</option>
                      <option value="SMS">SMS</option>
                      <option value="WEBHOOK">Webhook</option>
                      <option value="CHAT">Chat</option>
                      <option value="PUSH">Push</option>
                    </select>
                  </div>
                  <div>
                    <label class="c-form-label">{{ bindForm.channelType === 'WEBHOOK' ? 'Webhook URL' : 'Destination' }}</label>
                    <input v-model="bindForm.destination" type="text" class="theme-input w-full" :placeholder="bindForm.channelType === 'WEBHOOK' ? 'https://...' : 'email or phone'" />
                  </div>
                  <div v-if="bindForm.channelType === 'WEBHOOK'">
                    <label class="c-form-label">Webhook Secret (optional)</label>
                    <input v-model="bindForm.webhookSecret" type="password" class="theme-input w-full" placeholder="Secret for HMAC" />
                  </div>
                </div>
                <div class="flex justify-end">
                  <button class="theme-btn theme-btn-primary" :disabled="!bindForm.destination.trim() || channelSaving" @click="handleBindChannel">
                    <span v-if="channelSaving" class="c-spinner c-spinner-sm mr-xs" />
                    {{ channelSaving ? 'Binding...' : 'Bind Channel' }}
                  </button>
                </div>
              </div>

              <EmptyState v-if="channels.length === 0 && !showBindChannel" icon="📡" title="No channels bound" description="Bind a channel to start receiving notifications." />

              <div v-if="channels.length > 0" class="space-y-sm">
                <div v-for="ch in channels" :key="ch.bindingId" class="flex items-center justify-between p-sm rounded border border-default">
                  <div class="flex items-center gap-md">
                    <span class="text-lg">{{ channelIcon(ch.channelType) }}</span>
                    <div>
                      <div class="flex items-center gap-sm">
                        <span class="text-sm font-medium text-text-primary">{{ ch.channelType }}</span>
                        <span class="text-xs text-text-muted font-mono">{{ ch.destinationMasked }}</span>
                        <StatusBadge v-if="ch.verified" variant="success" label="Verified" size="sm" />
                        <StatusBadge v-else variant="warning" label="Unverified" size="sm" />
                        <StatusBadge v-if="!ch.enabled" variant="neutral" label="Disabled" size="sm" />
                      </div>
                      <div class="text-xs text-text-muted mt-xs">
                        Provider: {{ ch.provider }} · Created: {{ ch.createdAt }}
                        <span v-if="ch.failureCount > 0" class="text-danger-500 ml-sm">({{ ch.failureCount }} failures)</span>
                      </div>
                    </div>
                  </div>
                  <div class="flex items-center gap-xs">
                    <button v-if="!ch.verified" class="theme-btn theme-btn-secondary theme-btn-sm" @click="handleVerify(ch.bindingId)">Verify</button>
                    <button class="theme-btn theme-btn-ghost theme-btn-sm" @click="handleTest(ch.bindingId)">Test</button>
                    <button v-if="ch.enabled" class="theme-btn theme-btn-ghost theme-btn-sm" @click="handleDisable(ch.bindingId)">Disable</button>
                    <button class="theme-btn theme-btn-danger theme-btn-sm" @click="handleDelete(ch.bindingId)">Delete</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </PageSection>
      </template>

      <!-- Preferences Tab -->
      <template v-if="activeTab === 'preferences'">
        <PageSection title="Notification Preferences" description="Global notification behavior settings">
          <div class="c-card">
            <div class="c-card-body space-y-md">
              <div v-if="!preferences" class="text-sm text-text-muted">No preferences configured. Using defaults.</div>

              <template v-if="preferences">
                <!-- Global toggle -->
                <label class="flex items-center justify-between cursor-pointer">
                  <div>
                    <div class="text-sm font-medium text-text-primary">Enable Notifications</div>
                    <div class="text-xs text-text-muted">Master switch for all notifications</div>
                  </div>
                  <input v-model="preferences.globalEnabled" type="checkbox" class="theme-toggle" />
                </label>

                <div class="border-t border-default pt-md">
                  <div class="text-xs font-semibold text-text-muted mb-md">Channel Availability</div>
                  <div class="grid grid-cols-3 gap-md">
                    <label v-for="(_enabled, channel) in (preferences.channelEnabled || {})" :key="channel"
                      class="flex items-center justify-between p-sm rounded border border-default">
                      <div class="flex items-center gap-sm">
                        <span>{{ channelIcon(channel) }}</span>
                        <span class="text-sm text-text-primary">{{ channel }}</span>
                      </div>
                      <input v-model="preferences.channelEnabled[channel]" type="checkbox" class="theme-toggle" />
                    </label>
                  </div>
                </div>

                <div class="border-t border-default pt-md">
                  <div class="text-xs font-semibold text-text-muted mb-md">Quiet Hours</div>
                  <div class="grid grid-cols-3 gap-md">
                    <div>
                      <label class="c-form-label">Start Time</label>
                      <input v-model="preferences.quietHoursStart" type="time" class="theme-input w-full" />
                    </div>
                    <div>
                      <label class="c-form-label">End Time</label>
                      <input v-model="preferences.quietHoursEnd" type="time" class="theme-input w-full" />
                    </div>
                    <div>
                      <label class="c-form-label">Timezone</label>
                      <input v-model="preferences.quietHoursTimezone" type="text" class="theme-input w-full" placeholder="UTC" />
                    </div>
                  </div>
                </div>

                <div class="border-t border-default pt-md">
                  <div class="text-xs font-semibold text-text-muted mb-md">Digest Mode</div>
                  <select v-model="preferences.digestMode" class="theme-input w-full max-w-xs">
                    <option value="NONE">No digest (send immediately)</option>
                    <option value="HOURLY">Hourly digest</option>
                    <option value="DAILY">Daily digest</option>
                    <option value="WEEKLY">Weekly digest</option>
                  </select>
                </div>

                <label class="flex items-center justify-between cursor-pointer border-t border-default pt-md">
                  <div>
                    <div class="text-sm font-medium text-text-primary">Critical Override</div>
                    <div class="text-xs text-text-muted">Allow critical notifications during quiet hours</div>
                  </div>
                  <input v-model="preferences.criticalOverride" type="checkbox" class="theme-toggle" />
                </label>
              </template>

              <div class="pt-md border-t border-default flex justify-end">
                <button class="theme-btn theme-btn-primary" :disabled="saving" @click="handleSavePreferences">
                  <span v-if="saving" class="c-spinner c-spinner-sm mr-xs" />
                  {{ saving ? 'Saving...' : 'Save Preferences' }}
                </button>
              </div>
            </div>
          </div>
        </PageSection>
      </template>
    </template>
  </div>
</template>
