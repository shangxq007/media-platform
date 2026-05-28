<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import FormSection from '@/components/ui/FormSection.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import { MeEntitlementAPI } from '@/api/me'

const saving = ref(false)
const saveMessage = ref<string | null>(null)
const saveMessageType = ref<'success' | 'error' | 'info'>('info')
const showDeleteDialog = ref(false)

const profile = reactive({
  name: '',
  email: '',
  avatar: '',
})

const notifications = reactive({
  emailUpdates: true,
  exportComplete: true,
  billingAlerts: true,
  betaInvites: false,
  marketingEmails: false,
})

const preferences = reactive({
  theme: 'system',
  language: 'en',
})

// Load notification preferences from API
onMounted(async () => {
  try {
    const prefs = await MeEntitlementAPI.getNotificationPreferences()
    if (prefs) {
      notifications.emailUpdates = prefs.globalEnabled !== false
      notifications.exportComplete = prefs.globalEnabled !== false
      notifications.billingAlerts = prefs.criticalOverride !== false
    }
  } catch {
    // API not available or not authenticated — use defaults
  }

  // Load local-only preferences from localStorage
  const savedTheme = localStorage.getItem('pref_theme')
  const savedLang = localStorage.getItem('pref_language')
  if (savedTheme) preferences.theme = savedTheme
  if (savedLang) preferences.language = savedLang
})

function showMessage(msg: string, type: 'success' | 'error' | 'info' = 'success') {
  saveMessage.value = msg
  saveMessageType.value = type
  setTimeout(() => { saveMessage.value = null }, 4000)
}

// Profile — no backend API yet (button is disabled)

// Notifications — real API
async function handleSaveNotifications() {
  saving.value = true
  saveMessage.value = null
  try {
    await MeEntitlementAPI.updateNotificationPreferences({
      globalEnabled: notifications.emailUpdates,
      criticalOverride: notifications.billingAlerts,
    })
    showMessage('Notification preferences saved to server.', 'success')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : 'Failed to save notification preferences'
    showMessage(msg, 'error')
  } finally {
    saving.value = false
  }
}

// Appearance — local-only (no backend API)
async function handleSavePreferences() {
  localStorage.setItem('pref_theme', preferences.theme)
  localStorage.setItem('pref_language', preferences.language)
  showMessage('Appearance preferences saved locally on this device only. They will not sync to other devices or the server.', 'info')
}

function handleDeleteAccount() {
  showDeleteDialog.value = false
  showMessage('Account deletion is not yet implemented.', 'error')
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Settings" subtitle="Manage your account settings and preferences" />

    <!-- Save message banner -->
    <div v-if="saveMessage" :class="[
      'p-sm rounded text-xs flex items-center gap-sm',
      saveMessageType === 'success' ? 'bg-success/10 text-success border border-success/30' :
      saveMessageType === 'error' ? 'bg-danger-500/10 text-danger-500 border border-danger-500/30' :
      'bg-info/10 text-info border border-info/30'
    ]">
      {{ saveMessage }}
    </div>

    <!-- Profile Settings -->
    <PageSection title="Profile" description="Your personal information">
      <div class="c-card">
        <div class="c-card-body">
          <FormSection :columns="2">
            <div>
              <label class="c-form-label">Full Name</label>
              <input v-model="profile.name" type="text" class="theme-input w-full" placeholder="Your name" />
            </div>
            <div>
              <label class="c-form-label">Email</label>
              <input v-model="profile.email" type="email" class="theme-input w-full" placeholder="you@example.com" />
            </div>
            <div class="col-span-full">
              <label class="c-form-label">Avatar URL</label>
              <input v-model="profile.avatar" type="url" class="theme-input w-full" placeholder="https://..." />
              <div class="c-form-hint">Link to your profile picture</div>
            </div>
          </FormSection>
          <div class="mt-md pt-md border-t border-default flex items-center justify-between">
            <span class="text-xs text-text-muted">Profile saving requires a server API (coming soon).</span>
            <button class="theme-btn theme-btn-secondary" disabled title="Profile saving is not yet supported by the server">
              Save Profile
            </button>
          </div>
        </div>
      </div>
    </PageSection>

    <!-- Notification Preferences -->
    <PageSection title="Notifications" description="Choose what notifications you receive">
      <div class="c-card">
        <div class="c-card-body space-y-md">
          <label class="flex items-center justify-between cursor-pointer">
            <div>
              <div class="text-sm font-medium text-text-primary">Email Updates</div>
              <div class="text-xs text-text-muted">Product updates and announcements</div>
            </div>
            <input v-model="notifications.emailUpdates" type="checkbox" class="theme-toggle" />
          </label>
          <label class="flex items-center justify-between cursor-pointer">
            <div>
              <div class="text-sm font-medium text-text-primary">Export Complete</div>
              <div class="text-xs text-text-muted">When a render/export finishes</div>
            </div>
            <input v-model="notifications.exportComplete" type="checkbox" class="theme-toggle" />
          </label>
          <label class="flex items-center justify-between cursor-pointer">
            <div>
              <div class="text-sm font-medium text-text-primary">Billing Alerts</div>
              <div class="text-xs text-text-muted">Payment and billing notifications</div>
            </div>
            <input v-model="notifications.billingAlerts" type="checkbox" class="theme-toggle" />
          </label>
          <label class="flex items-center justify-between cursor-pointer">
            <div>
              <div class="text-sm font-medium text-text-primary">Beta Invites</div>
              <div class="text-xs text-text-muted">Invitations to try new features</div>
            </div>
            <input v-model="notifications.betaInvites" type="checkbox" class="theme-toggle" />
          </label>
          <label class="flex items-center justify-between cursor-pointer">
            <div>
              <div class="text-sm font-medium text-text-primary">Marketing Emails</div>
              <div class="text-xs text-text-muted">Promotional content and tips</div>
            </div>
            <input v-model="notifications.marketingEmails" type="checkbox" class="theme-toggle" />
          </label>
          <div class="pt-md border-t border-default flex justify-end">
            <button class="theme-btn theme-btn-primary" :disabled="saving" @click="handleSaveNotifications">
              <span v-if="saving" class="c-spinner c-spinner-sm mr-xs" />
              {{ saving ? 'Saving...' : 'Save Preferences' }}
            </button>
          </div>
        </div>
      </div>
    </PageSection>

    <!-- Appearance -->
    <PageSection title="Appearance" description="Customize the look and feel">
      <div class="c-card">
        <div class="c-card-body">
          <FormSection :columns="2">
            <div>
              <label class="c-form-label">Theme</label>
              <select v-model="preferences.theme" class="theme-input w-full">
                <option value="light">Light</option>
                <option value="dark">Dark</option>
                <option value="system">System</option>
              </select>
            </div>
            <div>
              <label class="c-form-label">Language</label>
              <select v-model="preferences.language" class="theme-input w-full">
                <option value="en">English</option>
                <option value="es">Spanish</option>
                <option value="fr">French</option>
                <option value="de">German</option>
                <option value="ja">Japanese</option>
                <option value="zh">Chinese</option>
              </select>
            </div>
          </FormSection>
          <div class="mt-md pt-md border-t border-default flex items-center justify-between">
            <span class="text-xs text-text-muted">Saved locally on this device only.</span>
            <button class="theme-btn theme-btn-secondary" :disabled="saving" @click="handleSavePreferences">
              Save Locally
            </button>
          </div>
        </div>
      </div>
    </PageSection>

    <!-- Danger Zone -->
    <PageSection title="Danger Zone" description="Irreversible actions">
      <div class="c-card border-danger-500/30">
        <div class="c-card-body">
          <div class="flex items-center justify-between">
            <div>
              <div class="text-sm font-medium text-text-primary">Delete Account</div>
              <div class="text-xs text-text-muted">Permanently delete your account and all associated data.</div>
            </div>
            <button class="theme-btn theme-btn-danger" disabled title="Account deletion is not yet implemented">
              Delete Account
            </button>
          </div>
        </div>
      </div>
    </PageSection>

    <!-- Delete Account Confirmation -->
    <ConfirmDialog
      :open="showDeleteDialog"
      title="Delete Account"
      description="Are you sure you want to delete your account? This action cannot be undone. All your projects, exports, and data will be permanently removed."
      confirm-label="Delete Account"
      cancel-label="Cancel"
      variant="danger"
      @confirm="handleDeleteAccount"
      @cancel="showDeleteDialog = false" />
  </div>
</template>
