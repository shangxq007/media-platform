<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { AdminAnalyticsAPI } from '@/api/admin/analytics'
import type { UserProfile, UserHabit, UserSegment } from '@/api/admin/analytics'

const loading = ref(true)
const tenantId = ref('tenant-1')
const profiles = ref<UserProfile[]>([])
const segments = ref<UserSegment[]>([])
const selectedUserId = ref<string | null>(null)
const selectedProfile = ref<UserProfile | null>(null)
const selectedHabits = ref<UserHabit | null>(null)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const [p, s] = await Promise.allSettled([
      AdminAnalyticsAPI.listProfiles(tenantId.value),
      AdminAnalyticsAPI.listSegments(tenantId.value),
    ])
    if (p.status === 'fulfilled') profiles.value = p.value
    if (s.status === 'fulfilled') segments.value = s.value
  } catch { /* backend may not be running */ }
  loading.value = false
}

async function viewProfile(userId: string) {
  selectedUserId.value = userId
  try {
    const [p, h] = await Promise.allSettled([
      AdminAnalyticsAPI.getProfile(userId),
      AdminAnalyticsAPI.getHabits(userId),
    ])
    if (p.status === 'fulfilled') selectedProfile.value = p.value
    if (h.status === 'fulfilled') selectedHabits.value = h.value
  } catch { /* backend may not be running */ }
}

async function computeSegment(type: 'active' | 'power') {
  if (type === 'active') await AdminAnalyticsAPI.computeActiveSegment(tenantId.value)
  else await AdminAnalyticsAPI.computePowerUsersSegment(tenantId.value)
  await loadData()
}
</script>

<template>
  <div class="flex-1 overflow-y-auto p-6">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-xl font-bold">User Analytics</h1>
      <div class="flex items-center gap-3">
        <input
          v-model="tenantId"
          type="text"
          class="bg-gray-800 border border-gray-600 rounded px-2 py-1.5 text-sm text-white w-48"
          placeholder="Tenant ID"
        />
        <button class="px-3 py-1.5 bg-gray-700 hover:bg-gray-600 text-sm rounded" @click="loadData">Refresh</button>
      </div>
    </div>

    <div v-if="loading" class="text-gray-400 text-sm">Loading...</div>
    <template v-else>
      <div class="grid grid-cols-2 gap-6">
        <!-- Profiles -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">User Profiles ({{ profiles.length }})</h2>
          <div v-if="profiles.length === 0" class="text-xs text-gray-500">No profiles</div>
          <div v-else class="space-y-1.5">
            <button
              v-for="p in profiles"
              :key="p.userId"
              class="w-full text-left px-2 py-1.5 rounded text-xs"
              :class="selectedUserId === p.userId ? 'bg-blue-600/20 text-blue-300' : 'text-gray-300 hover:bg-gray-700/50'"
              @click="viewProfile(p.userId!)"
            >
              <div class="flex items-center justify-between">
                <span class="font-mono">{{ p.userId }}</span>
                <span class="text-gray-500">{{ p.eventCount || 0 }} events</span>
              </div>
              <div class="text-gray-500 mt-0.5">Last active: {{ p.lastActive || '—' }}</div>
            </button>
          </div>
        </div>

        <!-- Profile Detail -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4">
          <h2 class="text-sm font-semibold mb-3 text-gray-300">Profile Detail</h2>
          <div v-if="!selectedUserId" class="text-xs text-gray-500">Select a user</div>
          <template v-else>
            <div v-if="selectedProfile" class="space-y-2 text-xs">
              <div class="flex justify-between"><span class="text-gray-400">User ID</span><span class="font-mono">{{ selectedProfile.userId }}</span></div>
              <div class="flex justify-between"><span class="text-gray-400">Events</span><span>{{ selectedProfile.eventCount || 0 }}</span></div>
              <div class="flex justify-between"><span class="text-gray-400">Last Active</span><span>{{ selectedProfile.lastActive || '—' }}</span></div>
              <div v-if="selectedProfile.segments?.length" class="mt-2">
                <span class="text-gray-400">Segments:</span>
                <div class="flex flex-wrap gap-1 mt-1">
                  <span v-for="seg in selectedProfile.segments" :key="seg" class="text-xs px-1.5 py-0.5 bg-purple-600/20 text-purple-300 rounded">{{ seg }}</span>
                </div>
              </div>
            </div>
            <div v-if="selectedHabits" class="mt-4 pt-3 border-t border-gray-700">
              <h3 class="text-xs font-semibold text-gray-400 mb-2">Habits</h3>
              <div class="space-y-1 text-xs">
                <div class="flex justify-between"><span class="text-gray-400">Most Used</span><span>{{ selectedHabits.mostUsedFeature || '—' }}</span></div>
                <div class="flex justify-between"><span class="text-gray-400">Avg Session</span><span>{{ selectedHabits.avgSessionMinutes || 0 }} min</span></div>
                <div class="flex justify-between"><span class="text-gray-400">Render Freq</span><span>{{ selectedHabits.renderFrequency || '—' }}</span></div>
              </div>
            </div>
          </template>
        </div>

        <!-- Segments -->
        <div class="bg-gray-800 border border-gray-700 rounded-lg p-4 col-span-2">
          <div class="flex items-center justify-between mb-3">
            <h2 class="text-sm font-semibold text-gray-300">Segments</h2>
            <div class="flex gap-2">
              <button class="text-xs px-2 py-1 bg-blue-600/20 text-blue-300 rounded" @click="computeSegment('active')">Compute Active</button>
              <button class="text-xs px-2 py-1 bg-purple-600/20 text-purple-300 rounded" @click="computeSegment('power')">Compute Power Users</button>
            </div>
          </div>
          <div v-if="segments.length === 0" class="text-xs text-gray-500">No segments computed</div>
          <div v-else class="grid grid-cols-3 gap-3">
            <div v-for="seg in segments" :key="seg.segmentId" class="bg-gray-700/50 rounded p-3">
              <div class="text-sm font-medium">{{ seg.name }}</div>
              <div class="text-xs text-gray-400 mt-1">{{ seg.userCount || 0 }} users</div>
              <div class="text-xs text-gray-500 mt-0.5 font-mono">{{ seg.segmentId }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
