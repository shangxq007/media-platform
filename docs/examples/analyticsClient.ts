/**
 * analyticsClient.ts — Media Platform Analytics Frontend SDK
 *
 * Provides typed methods for:
 *   - Ingesting user behavior events
 *   - Querying user profiles
 *   - Querying user habits
 *   - Listing and computing user segments
 *
 * Usage:
 *   import { AnalyticsClient } from './analyticsClient';
 *   const client = new AnalyticsClient({ baseUrl: '/api/v1', tenantId: 'tenant-1' });
 *   await client.trackEvent({ userId: 'user-1', eventType: 'page_view', action: 'view' });
 */

export interface AnalyticsConfig {
  baseUrl: string;
  tenantId: string;
  apiKey?: string;
}

export interface TrackEventRequest {
  userId: string;
  eventType: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  metadata?: Record<string, string>;
}

export interface UserBehaviorEvent {
  eventId: string;
  tenantId: string;
  userId: string;
  eventType: string;
  action: string | null;
  resourceType: string | null;
  resourceId: string | null;
  metadata: Record<string, string>;
  occurredAt: string;
}

export interface UserProfile {
  profileId: string;
  tenantId: string;
  userId: string;
  preferredLanguages: string[];
  featureUsageCounts: Record<string, number>;
  actionCounts: Record<string, number>;
  totalSessions: number;
  totalActions: number;
  firstSeenAt: string;
  lastActiveAt: string;
  updatedAt: string;
}

export interface UserHabits {
  tenantId: string;
  userId: string;
  dailyActivityBuckets: Record<string, number>;
  weeklyActivityPattern: Record<string, number>;
  mostUsedFeatures: string[];
  mostUsedActions: string[];
  averageSessionDepth: number;
  peakActivityHour: string;
  peakActivityDay: string;
  retentionDays: number;
  computedAt: string;
}

export interface UserSegment {
  segmentId: string;
  tenantId: string;
  name: string;
  description: string;
  criteria: Record<string, string>;
  userIds: string[];
  userCount: number;
  computedAt: string;
}

export class AnalyticsClient {
  private baseUrl: string;
  private tenantId: string;
  private apiKey?: string;

  constructor(config: AnalyticsConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '');
    this.tenantId = config.tenantId;
    this.apiKey = config.apiKey;
  }

  private headers(): Record<string, string> {
    const h: Record<string, string> = {
      'Content-Type': 'application/json',
      'X-Tenant-ID': this.tenantId,
    };
    if (this.apiKey) {
      h['Authorization'] = `Bearer ${this.apiKey}`;
    }
    return h;
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const resp = await fetch(`${this.baseUrl}${path}`, {
      ...init,
      headers: { ...this.headers(), ...init?.headers },
    });
    if (!resp.ok) {
      throw new AnalyticsError(resp.status, await resp.text());
    }
    return resp.json() as Promise<T>;
  }

  // ─── Event Tracking ───────────────────────────────────────────────

  async trackEvent(event: TrackEventRequest): Promise<UserBehaviorEvent> {
    return this.request<UserBehaviorEvent>('/analytics/events', {
      method: 'POST',
      body: JSON.stringify({
        userId: event.userId,
        eventType: event.eventType,
        action: event.action ?? null,
        resourceType: event.resourceType ?? null,
        resourceId: event.resourceId ?? null,
        metadata: event.metadata ?? {},
      }),
    });
  }

  async trackPageView(userId: string, page: string, metadata?: Record<string, string>): Promise<UserBehaviorEvent> {
    return this.trackEvent({
      userId,
      eventType: 'page_view',
      action: 'view',
      resourceType: 'page',
      resourceId: page,
      metadata,
    });
  }

  async trackRenderJob(userId: string, jobId: string, action: string, metadata?: Record<string, string>): Promise<UserBehaviorEvent> {
    return this.trackEvent({
      userId,
      eventType: 'render_job',
      action,
      resourceType: 'render-job',
      resourceId: jobId,
      metadata,
    });
  }

  async trackArtifact(userId: string, artifactId: string, action: string): Promise<UserBehaviorEvent> {
    return this.trackEvent({
      userId,
      eventType: 'artifact',
      action,
      resourceType: 'artifact',
      resourceId: artifactId,
    });
  }

  async trackNotification(userId: string, notificationId: string, action: string): Promise<UserBehaviorEvent> {
    return this.trackEvent({
      userId,
      eventType: 'notification',
      action,
      resourceType: 'notification',
      resourceId: notificationId,
    });
  }

  async listEvents(limit = 100): Promise<UserBehaviorEvent[]> {
    return this.request<UserBehaviorEvent[]>(`/analytics/events?limit=${limit}`);
  }

  // ─── User Profile ──────────────────────────────────────────────────

  async getProfile(userId: string): Promise<UserProfile> {
    return this.request<UserProfile>(`/analytics/profiles/${encodeURIComponent(userId)}`);
  }

  async listProfiles(limit = 100): Promise<UserProfile[]> {
    return this.request<UserProfile[]>(`/analytics/profiles?limit=${limit}`);
  }

  // ─── User Habits ───────────────────────────────────────────────────

  async getHabits(userId: string): Promise<UserHabits> {
    return this.request<UserHabits>(`/analytics/habits/${encodeURIComponent(userId)}`);
  }

  // ─── Segments ──────────────────────────────────────────────────────

  async getSegment(segmentId: string): Promise<UserSegment> {
    return this.request<UserSegment>(`/analytics/segments/${encodeURIComponent(segmentId)}`);
  }

  async listSegments(): Promise<UserSegment[]> {
    return this.request<UserSegment[]>('/analytics/segments');
  }

  async computeActiveSegment(activeWithinDays = 30): Promise<UserSegment> {
    return this.request<UserSegment>(`/analytics/segments/active?activeWithinDays=${activeWithinDays}`, {
      method: 'POST',
    });
  }

  async computePowerUsersSegment(minActions = 100): Promise<UserSegment> {
    return this.request<UserSegment>(`/analytics/segments/power-users?minActions=${minActions}`, {
      method: 'POST',
    });
  }
}

export class AnalyticsError extends Error {
  status: number;
  constructor(status: number, body: string) {
    super(`Analytics API error ${status}: ${body}`);
    this.status = status;
    this.name = 'AnalyticsError';
  }
}
