<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PublishAPI } from '@/api/publish'
import type { ConnectedPlatform, SocialPost } from '@/api/publish'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const router = useRouter()

const PLATFORMS = [
  { key: 'TWITTER', label: 'Twitter', icon: '𝕏', maxChars: 280 },
  { key: 'INSTAGRAM', label: 'Instagram', icon: '📷', maxChars: 2200 },
  { key: 'LINKEDIN', label: 'LinkedIn', icon: 'briefcase', maxChars: 3000 },
  { key: 'TIKTOK', label: 'TikTok', icon: 'music', maxChars: 2200 },
  { key: 'YOUTUBE', label: 'YouTube', icon: '▶️', maxChars: 5000 },
]

const loading = ref(true)
const error = ref<string | null>(null)
const saving = ref(false)
const publishing = ref(false)

const platforms = ref<ConnectedPlatform[]>([])
const recentPosts = ref<SocialPost[]>([])

const selectedPlatform = ref<string>('')
const contentText = ref('')
const mediaUrls = ref<string[]>([])
const mediaInput = ref<HTMLInputElement | null>(null)

const activePlatform = computed(() => PLATFORMS.find(p => p.key === selectedPlatform.value))
const maxChars = computed(() => activePlatform.value?.maxChars ?? 280)
const charCount = computed(() => contentText.value.length)
const charOverLimit = computed(() => charCount.value > maxChars.value)

const connectedPlatformKeys = computed(() =>
  platforms.value.filter(p => p.status === 'ACTIVE').map(p => p.platformType)
)

const hashtagSuggestions = ['#trending', '#social', '#content', '#media', '#viral']

onMounted(loadData)

async function loadData() {
  loading.value = true
  error.value = null
  try {
    const [plats, postsData] = await Promise.allSettled([
      PublishAPI.getConnectedPlatforms(),
      PublishAPI.getPosts(0, 5),
    ])
    if (plats.status === 'fulfilled') {
      platforms.value = plats.value
      if (plats.value.length > 0 && !selectedPlatform.value) {
        selectedPlatform.value = plats.value[0].platformType
      }
    }
    if (postsData.status === 'fulfilled') {
      recentPosts.value = postsData.value.posts
    }
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load data'
  } finally {
    loading.value = false
  }
}

function selectPlatform(key: string) {
  selectedPlatform.value = key
}

function addHashtag(tag: string) {
  if (!contentText.value.endsWith(' ') && contentText.value.length > 0) {
    contentText.value += ' '
  }
  contentText.value += tag + ' '
}

function handleMediaSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files) return
  for (const file of Array.from(files)) {
    const url = URL.createObjectURL(file)
    mediaUrls.value.push(url)
  }
}

function removeMedia(index: number) {
  mediaUrls.value.splice(index, 1)
}

async function saveDraft() {
  if (!selectedPlatform.value || !contentText.value.trim()) return
  saving.value = true
  try {
    await PublishAPI.saveDraft({
      contentText: contentText.value,
      mediaUrls: mediaUrls.value,
      platformType: selectedPlatform.value,
    })
    contentText.value = ''
    mediaUrls.value = []
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to save draft'
  } finally {
    saving.value = false
  }
}

async function publishNow() {
  if (!selectedPlatform.value || !contentText.value.trim()) return
  publishing.value = true
  try {
    const post = await PublishAPI.createPost({
      contentText: contentText.value,
      mediaUrls: mediaUrls.value,
      platformType: selectedPlatform.value,
    })
    await PublishAPI.publishNow(post.id)
    contentText.value = ''
    mediaUrls.value = []
    await loadData()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to publish'
  } finally {
    publishing.value = false
  }
}

async function schedulePublish() {
  if (!selectedPlatform.value || !contentText.value.trim()) return
  publishing.value = true
  try {
    const post = await PublishAPI.createPost({
      contentText: contentText.value,
      mediaUrls: mediaUrls.value,
      platformType: selectedPlatform.value,
    })
    const scheduledAt = new Date(Date.now() + 3600000).toISOString()
    await PublishAPI.schedulePost(post.id, scheduledAt)
    contentText.value = ''
    mediaUrls.value = []
    await loadData()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to schedule'
  } finally {
    publishing.value = false
  }
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'PUBLISHED': case 'ACTIVE': return 'success'
    case 'SCHEDULED': return 'warning'
    case 'FAILED': case 'ERROR': case 'EXPIRED': return 'danger'
    case 'DRAFT': return 'neutral'
    default: return 'info'
  }
}

function formatTime(dateStr: string): string {
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return 'Just now'
    if (diffMin < 60) return `${diffMin}m ago`
    const diffHr = Math.floor(diffMin / 60)
    if (diffHr < 24) return `${diffHr}h ago`
    const diffDay = Math.floor(diffHr / 24)
    if (diffDay < 7) return `${diffDay}d ago`
    return d.toLocaleDateString()
  } catch {
    return dateStr
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Social Publish" subtitle="Create and publish content to your connected social platforms">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/me/scheduler')">View Scheduler</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadData">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading publish data..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadData" />

    <template v-else>
      <div class="flex gap-lg">
        <!-- Left: Editor (2/3) -->
        <div class="flex-1 min-w-0 space-y-lg" style="max-width: 66.67%">
          <!-- Platform Selector -->
          <PageSection title="Select Platform">
            <div class="flex gap-sm flex-wrap">
              <button
                v-for="plat in PLATFORMS"
                :key="plat.key"
                class="c-card flex items-center gap-sm px-md py-sm transition-colors cursor-pointer min-w-32"
                :class="selectedPlatform === plat.key ? 'border-primary-500 ring-1 ring-primary-500' : 'hover:border-primary-200'"
                :style="connectedPlatformKeys.includes(plat.key) ? 'border-color: var(--color-success-500); border-width: 2px' : ''"
                @click="selectPlatform(plat.key)">
                <span class="text-xl">{{ plat.icon }}</span>
                <span class="text-sm font-medium text-text-primary">{{ plat.label }}</span>
                <span v-if="connectedPlatformKeys.includes(plat.key)" class="w-2 h-2 rounded-full bg-success-500" title="Connected" />
              </button>
            </div>
          </PageSection>

          <!-- Content Editor -->
          <PageSection title="Content">
            <textarea
              v-model="contentText"
              class="w-full min-h-32 p-md rounded border border-default bg-bg-base text-text-primary text-sm resize-y focus:outline-none focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
              :placeholder="selectedPlatform ? `What's happening on ${activePlatform?.label}?` : 'Select a platform first...'"
              :disabled="!selectedPlatform"
              :maxlength="maxChars + 50"
            />
            <div class="flex items-center justify-between mt-sm">
              <div class="flex gap-xs flex-wrap">
                <button
                  v-for="tag in hashtagSuggestions"
                  :key="tag"
                  class="text-xs px-2 py-0.5 rounded bg-bg-surface text-text-muted hover:text-primary-500 hover:bg-primary-500/10 transition-colors"
                  @click="addHashtag(tag)">
                  {{ tag }}
                </button>
              </div>
              <span class="text-xs" :class="charOverLimit ? 'text-danger-500 font-medium' : 'text-text-muted'">
                {{ charCount }} / {{ maxChars }}
              </span>
            </div>
          </PageSection>

          <!-- Media Upload -->
          <PageSection title="Media">
            <div
              class="border-2 border-dashed border-default rounded-lg p-lg text-center cursor-pointer hover:border-primary-200 transition-colors"
              @click="mediaInput?.click()">
              <div class="text-2xl mb-sm">paperclip</div>
              <p class="text-sm text-text-secondary">Click to upload or drag and drop</p>
              <p class="text-xs text-text-muted mt-xs">PNG, JPG, GIF, MP4 up to 50MB</p>
              <input ref="mediaInput" type="file" multiple accept="image/*,video/*" class="hidden" @change="handleMediaSelect" />
            </div>
            <div v-if="mediaUrls.length > 0" class="flex gap-sm mt-md flex-wrap">
              <div v-for="(url, index) in mediaUrls" :key="index" class="relative w-24 h-24 rounded overflow-hidden border border-default group">
                <img :src="url" alt="Media preview" class="w-full h-full object-cover" />
                <button
                  class="absolute top-1 right-1 w-5 h-5 rounded-full bg-danger-500 text-white text-xs flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                  @click="removeMedia(index)">
                  ✕
                </button>
              </div>
            </div>
          </PageSection>

          <!-- Action Bar -->
          <div class="flex items-center gap-md pt-md border-t border-default">
            <button class="theme-btn theme-btn-ghost" :disabled="saving || !contentText.trim()" @click="saveDraft">
              {{ saving ? 'Saving...' : 'Save Draft' }}
            </button>
            <div class="flex-1" />
            <button class="theme-btn theme-btn-secondary" :disabled="publishing || !contentText.trim() || charOverLimit" @click="schedulePublish">
              {{ publishing ? 'Scheduling...' : 'Schedule' }}
            </button>
            <button class="theme-btn theme-btn-primary" :disabled="publishing || !contentText.trim() || charOverLimit" @click="publishNow">
              {{ publishing ? 'Publishing...' : 'Publish Now' }}
            </button>
          </div>
        </div>

        <!-- Right: Sidebar (1/3) -->
        <div class="w-80 flex-shrink-0 space-y-lg">
          <!-- Preview -->
          <PageSection title="Preview">
            <div v-if="selectedPlatform" class="c-card">
              <div class="c-card-body">
                <div class="flex items-center gap-sm mb-sm">
                  <span class="text-lg">{{ activePlatform?.icon }}</span>
                  <div>
                    <div class="text-sm font-medium text-text-primary">{{ activePlatform?.label }}</div>
                    <div class="text-xs text-text-muted">Preview</div>
                  </div>
                </div>
                <p class="text-sm text-text-secondary whitespace-pre-wrap break-words">{{ contentText || 'Your post content will appear here...' }}</p>
                <div v-if="mediaUrls.length > 0" class="flex gap-xs mt-sm flex-wrap">
                  <div v-for="(url, i) in mediaUrls.slice(0, 4)" :key="i" class="w-16 h-16 rounded overflow-hidden border border-default">
                    <img :src="url" alt="Preview" class="w-full h-full object-cover" />
                  </div>
                  <div v-if="mediaUrls.length > 4" class="w-16 h-16 rounded border border-default flex items-center justify-center text-xs text-text-muted">
                    +{{ mediaUrls.length - 4 }}
                  </div>
                </div>
              </div>
            </div>
            <EmptyState v-else title="No platform selected" description="Select a platform to see a preview." />
          </PageSection>

          <!-- Connected Platforms -->
          <PageSection title="Connected Platforms">
            <div v-if="platforms.length === 0" class="text-sm text-text-muted">No platforms connected</div>
            <div v-else class="space-y-sm">
              <div v-for="plat in platforms" :key="plat.id" class="flex items-center justify-between p-sm rounded bg-bg-surface border border-default">
                <div class="flex items-center gap-sm min-w-0">
                  <span class="text-base">{{ PLATFORMS.find(p => p.key === plat.platformType)?.icon || 'share-2' }}</span>
                  <div class="min-w-0">
                    <div class="text-xs font-medium text-text-primary truncate-text">{{ plat.platformUsername }}</div>
                    <div class="text-[10px] text-text-muted">{{ plat.platformType }}</div>
                  </div>
                </div>
                <StatusBadge :variant="statusVariant(plat.status)" :label="plat.status" size="sm" />
              </div>
            </div>
          </PageSection>

          <!-- Recent Posts -->
          <PageSection title="Recent Posts">
            <div v-if="recentPosts.length === 0" class="text-sm text-text-muted">No recent posts</div>
            <div v-else class="space-y-sm">
              <div v-for="post in recentPosts" :key="post.id" class="p-sm rounded bg-bg-surface border border-default">
                <div class="flex items-center justify-between mb-xs">
                  <span class="text-xs font-medium text-text-secondary">{{ post.platformType }}</span>
                  <StatusBadge :variant="statusVariant(post.status)" :label="post.status" size="sm" />
                </div>
                <p class="text-xs text-text-primary line-clamp-2">{{ post.contentText }}</p>
                <div class="text-[10px] text-text-muted mt-xs">{{ formatTime(post.createdAt) }}</div>
              </div>
            </div>
          </PageSection>
        </div>
      </div>
    </template>
  </div>
</template>
