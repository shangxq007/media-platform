<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    await auth.completeCallback()
    const redirect = sessionStorage.getItem('oidc_post_login_redirect') || '/'
    sessionStorage.removeItem('oidc_post_login_redirect')
    await router.replace(redirect)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
})
</script>

<template>
  <div class="min-h-screen flex items-center justify-center bg-bg-base text-text-primary">
    <div v-if="error" class="text-danger text-sm max-w-md text-center">
      登录失败：{{ error }}
    </div>
    <div v-else class="text-sm text-text-secondary">正在完成登录…</div>
  </div>
</template>
