<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PublishAPI } from '@/api/publish'
import type { SocialPost } from '@/api/publish'
import PageHeader from '@/components/ui/PageHeader.vue'
import PageSection from '@/components/ui/PageSection.vue'
import StatusBadge from '@/components/ui/StatusBadge.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const router = useRouter()

const loading = ref(true)
const error = ref<string | null>(null)
const cancelling = ref<string | null>(null)

const scheduledPosts = ref<SocialPost[]>([])

onMounted(loadScheduled)

async function loadScheduled() {
  loading.value = true
  error.value = null
  try {
    const result = await PublishAPI.getPosts(0, 50)
    scheduledPosts.value = result.posts.filter(p => p.status === 'SCHEDULED')
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to load scheduled posts'
  } finally {
    loading.value = false
  }
}

async function cancelScheduled(postId: string) {
  cancelling.value = postId
  try {
    await PublishAPI.cancelScheduled(postId)
    scheduledPosts.value = scheduledPosts.value.filter(p => p.id !== postId)
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Failed to cancel scheduled post'
  } finally {
    cancelling.value = null
  }
}

function formatDateTime(dateStr: string): string {
  try {
    const d = new Date(dateStr)
    return d.toLocaleString(undefined, {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}

function isUpcoming(dateStr: string): boolean {
  try {
    return new Date(dateStr).getTime() > Date.now()
  } catch {
    return false
  }
}

const upcomingPosts = computed(() =>
  scheduledPosts.value
    .filter(p => p.scheduledAt && isUpcoming(p.scheduledAt))
    .sort((a, b) => new Date(a.scheduledAt!).getTime() - new Date(b.scheduledAt!).getTime())
)

const pastPosts = computed(() =>
  scheduledPosts.value
    .filter(p => p.scheduledAt && !isUpcoming(p.scheduledAt))
    .sort((a, b) => new Date(b.scheduledAt!).getTime() - new Date(a.scheduledAt!).getTime())
)

function statusVariant(status: string): 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'SCHEDULED': return 'warning'
    case 'PUBLISHED': return 'success'
    case 'FAILED': return 'danger'
    case 'CANCELLED': return 'neutral'
    default: return 'info'
  }
}
</script>

<template>
  <div class="flex-1 overflow-y-auto layout-content-padded space-y-xl">
    <PageHeader title="Post Scheduler" subtitle="Manage your scheduled social media posts">
      <template #actions>
        <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/me/publish')">New Post</button>
        <button class="theme-btn theme-btn-secondary theme-btn-sm" @click="loadScheduled">Refresh</button>
      </template>
    </PageHeader>

    <LoadingState v-if="loading" message="Loading scheduled posts..." />
    <ErrorState v-else-if="error" :description="error" @retry="loadScheduled" />

    <template v-else>
      <!-- Upcoming -->
      <PageSection title="Upcoming" :description="`${upcomingPosts.length} post(s) scheduled`">
        <EmptyState v-if="upcomingPosts.length === 0" icon="📅" title="No upcoming posts" description="Schedule a post from the publish page to see it here.">
          <template #action>
            <button class="theme-btn theme-btn-primary theme-btn-sm" @click="router.push('/me/publish')">Create Post</button>
          </template>
        </EmptyState>

        <div v-else class="space-y-sm">
          <div v-for="post in upcomingPosts" :key="post.id" class="c-card">
            <div class="c-card-body">
              <div class="flex items-start gap-md">
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-sm mb-sm flex-wrap">
                    <span class="text-sm font-medium text-text-primary">{{ post.platformType }}</span>
                    <StatusBadge :variant="statusVariant(post.status)" :label="post.status" size="sm" />
                  </div>
                  <p class="text-sm text-text-secondary line-clamp-2 mb-sm">{{ post.contentText }}</p>
                  <div v-if="post.mediaUrls.length > 0" class="flex gap-xs mb-sm">
                    <div v-for="(url, i) in post.mediaUrls.slice(0, 4)" :key="i" class="w-12 h-12 rounded overflow-hidden border border-default">
                      <img :src="url" alt="Media" class="w-full h-full object-cover" />
                    </div>
                    <div v-if="post.mediaUrls.length > 4" class="w-12 h-12 rounded border border-default flex items-center justify-center text-[10px] text-text-muted">
                      +{{ post.mediaUrls.length - 4 }}
                    </div>
                  </div>
                  <div class="flex items-center gap-md text-xs text-text-muted">
                    <span>📅 {{ post.scheduledAt ? formatDateTime(post.scheduledAt) : '—' }}</span>
                    <span>Created: {{ formatDateTime(post.createdAt) }}</span>
                  </div>
                </div>
                <button
                  class="theme-btn theme-btn-ghost theme-btn-sm text-danger-500 hover:text-danger-400 flex-shrink-0"
                  :disabled="cancelling === post.id"
                  @click="cancelScheduled(post.id)">
                  {{ cancelling === post.id ? 'Cancelling...' : 'Cancel' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </PageSection>

      <!-- Past Scheduled -->
      <PageSection v-if="pastPosts.length > 0" title="Past Scheduled" description="Previously scheduled posts">
        <div class="space-y-sm">
          <div v-for="post in pastPosts" :key="post.id" class="c-card opacity-70">
            <div class="c-card-body">
              <div class="flex items-start gap-md">
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-sm mb-sm flex-wrap">
                    <span class="text-sm font-medium text-text-primary">{{ post.platformType }}</span>
                    <StatusBadge :variant="statusVariant(post.status)" :label="post.status" size="sm" />
                  </div>
                  <p class="text-sm text-text-secondary line-clamp-2 mb-sm">{{ post.contentText }}</p>
                  <div class="text-xs text-text-muted">
                    📅 {{ post.scheduledAt ? formatDateTime(post.scheduledAt) : '—' }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </PageSection>
    </template>
  </div>
</template>
