<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  showBillingCta?: boolean
  showContactAdmin?: boolean
  billingPath?: string
}>(), {
  title: 'Billing Required',
  description: 'A valid billing method is required to access this feature. Please update your billing information to continue.',
  showBillingCta: true,
  showContactAdmin: true,
  billingPath: '/billing',
})

defineEmits<{
  goToBilling: []
  contactAdmin: []
}>()
</script>

<template>
  <div class="c-state-page" role="alert" aria-labelledby="billing-title">
    <div class="c-state-icon" aria-hidden="true">
      <svg class="w-16 h-16 text-warning-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
          d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
      </svg>
    </div>
    <h2 id="billing-title" class="c-state-title">{{ title }}</h2>
    <p class="c-state-description">{{ description }}</p>

    <div class="c-state-actions">
      <router-link v-if="showBillingCta" :to="billingPath" class="theme-btn theme-btn-primary">
        Go to Billing
      </router-link>
      <button v-if="showContactAdmin" class="theme-btn theme-btn-secondary" @click="$emit('contactAdmin')">
        Contact Admin
      </button>
      <router-link to="/" class="theme-btn theme-btn-ghost">
        Go Home
      </router-link>
    </div>
  </div>
</template>
