import api from './index'

export interface ConnectedPlatform {
  id: string
  platformType: string
  platformUsername: string
  status: 'ACTIVE' | 'ERROR' | 'EXPIRED'
  connectedAt: string
}

export interface SocialPost {
  id: string
  contentText: string
  mediaUrls: string[]
  platformType: string
  status: 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'FAILED' | 'CANCELLED'
  platformPostUrl?: string
  scheduledAt?: string
  publishedAt?: string
  errorCode?: string
  errorMessage?: string
  retryCount: number
  createdAt: string
}

export interface PostAnalytics {
  postId: string
  platformType: string
  impressions: number
  reach: number
  likes: number
  comments: number
  shares: number
  clicks: number
}

export const PublishAPI = {
  async getConnectedPlatforms(): Promise<ConnectedPlatform[]> {
    const { data } = await api.get('/social/platforms')
    return data
  },
  async connectPlatform(platform: string, authCode: string): Promise<ConnectedPlatform> {
    const { data } = await api.post(`/social/platforms/${platform}/connect`, { authCode })
    return data
  },
  async disconnectPlatform(platform: string): Promise<void> {
    await api.delete(`/social/platforms/${platform}`)
  },
  async createPost(post: { contentText: string; mediaUrls: string[]; platformType: string }): Promise<SocialPost> {
    const { data } = await api.post('/social/posts', post)
    return data
  },
  async schedulePost(postId: string, scheduledAt: string): Promise<SocialPost> {
    const { data } = await api.post(`/social/posts/${postId}/schedule`, { scheduledAt })
    return data
  },
  async publishNow(postId: string): Promise<SocialPost> {
    const { data } = await api.post(`/social/posts/${postId}/publish`)
    return data
  },
  async cancelScheduled(postId: string): Promise<void> {
    await api.delete(`/social/posts/${postId}/schedule`)
  },
  async getPosts(page = 0, size = 20): Promise<{ posts: SocialPost[]; total: number }> {
    const { data } = await api.get('/social/posts', { params: { page, size } })
    return data
  },
  async getPost(postId: string): Promise<SocialPost> {
    const { data } = await api.get(`/social/posts/${postId}`)
    return data
  },
  async retryPost(postId: string): Promise<SocialPost> {
    const { data } = await api.post(`/social/posts/${postId}/retry`)
    return data
  },
  async deletePost(postId: string): Promise<void> {
    await api.delete(`/social/posts/${postId}`)
  },
  async getDrafts(): Promise<SocialPost[]> {
    const { data } = await api.get('/social/drafts')
    return data
  },
  async saveDraft(post: { contentText: string; mediaUrls: string[]; platformType: string }): Promise<SocialPost> {
    const { data } = await api.post('/social/drafts', post)
    return data
  },
  async getOverviewAnalytics(): Promise<{ totalPosts: number; publishedPosts: number; failedPosts: number; scheduledPosts: number; totalImpressions: number; totalEngagement: number }> {
    const { data } = await api.get('/social/analytics/overview')
    return data
  },
}
