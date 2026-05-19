<script setup lang="ts">
withDefaults(defineProps<{
  featureName: string
  featureIcon?: string
  reason?: string
  upgradePath?: string
  showUpgrade?: boolean
  showContactAdmin?: boolean
}>(), {
  featureIcon: '🚧',
  reason: 'This feature is currently disabled for your account.',
  showUpgrade: true,
  showContactAdmin: true,
})

defineEmits<{
  upgrade: []
  contactAdmin: []
}>()
</script>

<template>
  <div class="c-state-page" role="status" aria-labelledby="disabled-title">
    <div class="c-state-icon" aria-hidden="true">
      <span class="text-5xl">{{ featureIcon }}</span>
    </div>
    <h2 id="disabled-title" class="c-state-title">{{ featureName }} is disabled</h2>
    <p class="c-state-description">{{ reason }}</p>

    <div v-if="upgradePath" class="c-upgrade-hint mt-md">
      <span class="text-sm text-text-secondary">
        Available on <span class="font-medium text-primary-400">{{ upgradePath }}</span> plan
      </span>
    </div>

    <div class="c-state-actions">
      <button v-if="showUpgrade" class="theme-btn theme-btn-primary" @click="$emit('upgrade')">
        Upgrade Plan
      </button>
      <button v-if="showContactAdmin" class="theme-btn theme-btn-secondary" @click="$emit('contactAdmin')">
        Contact Admin
      </button>
      <router-link to="/" class="theme-btn theme-btn-ghost">
        Go Home
      </router-link>
    </div>
  </div>
</template>
