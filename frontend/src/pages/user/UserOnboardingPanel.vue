<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

interface OnboardingState {
  hasProjects: boolean
  hasCompletedProfile: boolean
  hasInvitedTeamMembers: boolean
  hasCompletedFirstExport: boolean
  hasSetBilling: boolean
}

const props = defineProps<{
  onboarding: OnboardingState
}>()

const router = useRouter()

const checklist = computed(() => [
  {
    key: 'profile',
    label: 'Complete your profile',
    description: 'Set up your account information',
    icon: '👤',
    path: '/me/settings',
    done: props.onboarding.hasCompletedProfile,
  },
  {
    key: 'project',
    label: 'Create your first project',
    description: 'Start a new editing project',
    icon: '📁',
    path: '/project/new',
    done: props.onboarding.hasProjects,
  },
  {
    key: 'team',
    label: 'Invite team members',
    description: 'Collaborate with your team',
    icon: '👥',
    path: '/workspace/current/members',
    done: props.onboarding.hasInvitedTeamMembers,
  },
  {
    key: 'export',
    label: 'Complete your first export',
    description: 'Export a project to share',
    icon: '📤',
    path: '/',
    done: props.onboarding.hasCompletedFirstExport,
  },
  {
    key: 'billing',
    label: 'Set up billing',
    description: 'Configure your subscription',
    icon: '💳',
    path: '/me/billing',
    done: props.onboarding.hasSetBilling,
  },
])

const completedCount = computed(() => checklist.value.filter(i => i.done).length)
const totalCount = computed(() => checklist.value.length)
const progressPercent = computed(() => Math.round((completedCount.value / totalCount.value) * 100))
const isComplete = computed(() => completedCount.value === totalCount.value)
</script>

<template>
  <div class="c-card border-primary-200 bg-primary-500/5">
    <div class="c-card-header">
      <div class="flex items-center justify-between">
        <div>
          <h2 class="section-title">Getting Started</h2>
          <p class="text-xs text-text-secondary mt-xs">{{ completedCount }}/{{ totalCount }} steps completed</p>
        </div>
        <span v-if="isComplete" class="text-lg">🎉</span>
        <span v-else class="text-lg">🚀</span>
      </div>
    </div>
    <div class="c-card-body">
      <!-- Progress bar -->
      <div class="w-full bg-bg-surface rounded-full h-2 mb-md">
        <div class="h-2 rounded-full bg-primary-500 transition-all" :style="{ width: progressPercent + '%' }" />
      </div>

      <!-- Checklist -->
      <div class="space-y-sm">
        <div v-for="item in checklist" :key="item.key"
          class="flex items-center gap-md p-sm rounded transition-colors"
          :class="item.done ? 'bg-success-500/5' : 'bg-bg-surface hover:bg-bg-surface-hover'">
          <span class="text-lg flex-shrink-0" aria-hidden="true">
            {{ item.done ? '✅' : item.icon }}
          </span>
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-sm">
              <span class="text-sm font-medium" :class="item.done ? 'text-text-muted line-through' : 'text-text-primary'">
                {{ item.label }}
              </span>
            </div>
            <p class="text-xs text-text-muted">{{ item.description }}</p>
          </div>
          <button v-if="!item.done"
            class="theme-btn theme-btn-secondary theme-btn-sm flex-shrink-0"
            @click="router.push(item.path)">
            Do it
          </button>
          <span v-else class="text-xs text-success-500 flex-shrink-0">Done</span>
        </div>
      </div>

      <!-- Completion message -->
      <div v-if="isComplete" class="mt-md p-sm bg-success-500/10 rounded text-center">
        <span class="text-sm text-success-600 font-medium">🎉 You're all set! You've completed all the getting started steps.</span>
      </div>
    </div>
  </div>
</template>
