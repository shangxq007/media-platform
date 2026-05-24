<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { PublishAPI } from '@/api/publish'
import type { SocialPost } from '@/api/publish'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const FILTERS = [
  { key: 'ALL', label: 'All' },
  { key: 'PUBLISHED', label: 'Published' },
  { key: 'FAILED', label: 'Failed' },
  { key: 'DRAFT', label: 'Drafts' },
  { key: 'SCHEDULED', label: 'Scheduled' },
  { key: 'CANCELLED', label: 'Cancelled' },
]

const loading = ref(true)
const error = ref<string | null>(null)
const retrying = ref<string | null>(null)
const deleting = ref<string | null>(null)

const allPosts = ref<SocialPost[]>([])
const total = ref(0)
const currentPage = ref(0)
const pageSize = 20
const activeFilter = ref('ALL')

onMounted(loadPosts)

async function loadPosts() {
  loading.value = true
  error.value = null
  try {
    const result = await PublishAPI.getPosts(currentPage.value, pageSize)
    allPosts.value = result.posts
    total.value = result.total
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load posts'
  } finally {
    loading.value = false
  }
}

const filteredPosts = computed(() => {
  if (activeFilter.value === 'ALL') return allPosts.value
  return allPosts.value.filter(p => p.status === activeFilter.value)
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function retryPost(postId: string) {
  retrying.value = postId
  try {
    await PublishAPI.retryPost(postId)
    await loadPosts()
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to retry post'
  } finally {
    retrying.value = null
  }
}

async function deletePost(postId: string) {
  deleting.value = postId
  try {
    await PublishAPI.deletePost(postId)
    allPosts.value = allPosts.value.filter(p => p.id !== postId)
    total.value = Math.max(0, total.value - 1)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to delete post'
  } finally {
    deleting.value = null
  }
}

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'PUBLISHED': return 'success'
    case 'SCHEDULED': return 'warning'
    case 'FAILED': return 'danger'
    case 'DRAFT': return 'neutral'
    case 'CANCELLED': return 'neutral'
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

function formatDateTime(dateStr: string): string {
  try {
    return new Date(dateStr).toLocaleString(undefined, {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Publish History" subtitle="View and manage all your social media posts">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="$router.push('/me/publish')">New Post</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadPosts">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading posts..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadPosts" />

    <template v-else>
      <!-- Filter Tabs -->
      <div class="flex gap-xs border-b border-default overflow-x-auto">
        <button
          v-for="filter in FILTERS"
          :key="filter.key"
          class="px-lg py-sm text-sm font-medium transition-colors whitespace-nowrap"
          :class="activeFilter === filter.key
            ? 'text-text-primary border-b-2 border-primary-500'
            : 'text-text-muted hover:text-text-secondary'"
          @click="activeFilter = filter.key">
          {{ filter.label }}
          <span v-if="filter.key === 'ALL'" class="ml-xs text-xs text-text-muted">({{ total }})</span>
        </button>
      </div>

      <EmptyState v-if="filteredPosts.length === 0" icon="clipboard" title="No posts found" description="No posts match the selected filter. Try a different filter or create a new post.">
        <template #action>
          <button class="theme-btn theme-btn-primary theme-btn-sm" @click="$router.push('/me/publish')">Create Post</button>
        </template>
      </EmptyState>

      <PageSection v-else title="Posts">
        <div class="space-y-sm">
          <div v-for="post in filteredPosts" :key="post.id" class="c-card">
            <div class="c-card-body">
              <div class="flex items-start gap-md">
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-sm mb-sm flex-wrap">
                    <span class="text-sm font-medium text-text-primary">{{ post.platformType }}</span>
                    <StatusBadge :variant="statusVariant(post.status)" :label="post.status" size="sm" />
                    <span v-if="post.retryCount > 0" class="text-[10px] text-text-muted">(retried {{ post.retryCount }}x)</span>
                  </div>

                  <p class="text-sm text-text-secondary line-clamp-3 mb-sm">{{ post.contentText }}</p>

                  <div v-if="post.mediaUrls.length > 0" class="flex gap-xs mb-sm">
                    <div v-for="(url, i) in post.mediaUrls.slice(0, 4)" :key="i" class="w-12 h-12 rounded overflow-hidden border border-default">
                      <img :src="url" alt="Media" class="w-full h-full object-cover" />
                    </div>
                    <div v-if="post.mediaUrls.length > 4" class="w-12 h-12 rounded border border-default flex items-center justify-center text-[10px] text-text-muted">
                      +{{ post.mediaUrls.length - 4 }}
                    </div>
                  </div>

                  <div v-if="post.status === 'FAILED' && post.errorMessage" class="p-sm bg-danger-500/10 rounded text-xs text-danger-500 mb-sm">
                    {{ post.errorMessage }}
                  </div>

                  <div class="flex items-center gap-md text-xs text-text-muted flex-wrap">
                    <span>Created: {{ formatTime(post.createdAt) }}</span>
                    <span v-if="post.scheduledAt">calendar Scheduled: {{ formatDateTime(post.scheduledAt) }}</span>
                    <span v-if="post.publishedAt">check Published: {{ formatDateTime(post.publishedAt) }}</span>
                  </div>
                </div>

                <div class="flex flex-col gap-xs flex-shrink-0">
                  <a
                    v-if="post.platformPostUrl"
                    :href="post.platformPostUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="theme-btn theme-btn-ghost theme-btn-sm">
                    View
                  </a>
                  <button
                    v-if="post.status === 'FAILED'"
                    class="theme-btn theme-btn-secondary theme-btn-sm"
                    :disabled="retrying === post.id"
                    @click="retryPost(post.id)">
                    {{ retrying === post.id ? 'Retrying...' : 'Retry' }}
                  </button>
                  <button
                    class="theme-btn theme-btn-ghost theme-btn-sm text-danger-500 hover:text-danger-400"
                    :disabled="deleting === post.id"
                    @click="deletePost(post.id)">
                    {{ deleting === post.id ? 'Deleting...' : 'Delete' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Pagination -->
        <div v-if="totalPages > 1" class="flex items-center justify-between px-md py-sm border-t border-default bg-bg-surface mt-md">
          <div class="text-xs text-text-muted">
            Showing {{ currentPage * pageSize + 1 }}–{{ Math.min((currentPage + 1) * pageSize, total) }} of {{ total }}
          </div>
          <div class="flex items-center gap-xs">
            <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="currentPage <= 0" @click="currentPage -= 1; loadPosts()">←</button>
            <span class="text-xs text-text-secondary px-sm">{{ currentPage + 1 }} / {{ totalPages }}</span>
            <button class="theme-btn theme-btn-ghost theme-btn-sm" :disabled="currentPage >= totalPages - 1" @click="currentPage += 1; loadPosts()">→</button>
          </div>
        </div>
      </PageSection>
    </template>
  </div>
</template>
