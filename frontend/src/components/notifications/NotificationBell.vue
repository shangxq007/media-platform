<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { MeEntitlementAPI } from '@/api/me'
import NotificationDropdown from './NotificationDropdown.vue'

const unreadCount = ref(0)
const isOpen = ref(false)
const bellRef = ref<HTMLElement | null>(null)
const buttonRef = ref<HTMLButtonElement | null>(null)

let pollInterval: ReturnType<typeof setInterval> | null = null

async function fetchUnreadCount() {
  try {
    const result = await MeEntitlementAPI.getMyNotifications(0, 1, 'UNREAD')
    unreadCount.value = result.unreadCount ?? 0
  } catch {
    unreadCount.value = 0
  }
}

function toggle() {
  isOpen.value = !isOpen.value
}

function close() {
  isOpen.value = false
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    e.preventDefault()
    close()
    buttonRef.value?.focus()
  }
}

function handleClickOutside(e: MouseEvent) {
  if (!isOpen.value) return
  const target = e.target as Node
  if (bellRef.value && bellRef.value.contains(target)) return
  close()
}

onMounted(() => {
  fetchUnreadCount()
  pollInterval = setInterval(fetchUnreadCount, 60000)
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  if (pollInterval) clearInterval(pollInterval)
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div ref="bellRef" class="relative">
    <button
      ref="buttonRef"
      type="button"
      class="theme-btn theme-btn-ghost theme-btn-sm relative"
      :aria-label="`Notifications${unreadCount > 0 ? `, ${unreadCount} unread` : ''}`"
      :aria-expanded="isOpen"
      aria-haspopup="true"
      @click="toggle"
    >
      <span class="text-sm">🔔</span>
      <span
        v-if="unreadCount > 0"
        class="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-1 rounded-full bg-danger-500 text-[10px] text-white font-medium flex items-center justify-center"
        aria-hidden="true"
      >
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>

    <Transition
      enter-active-class="transition-all duration-fast ease-out"
      enter-from-class="opacity-0 scale-95 -translate-y-1"
      enter-to-class="opacity-100 scale-100 translate-y-0"
      leave-active-class="transition-all duration-fast ease-in"
      leave-from-class="opacity-100 scale-100 translate-y-0"
      leave-to-class="opacity-0 scale-95 -translate-y-1"
    >
      <div
        v-if="isOpen"
        class="absolute right-0 top-full mt-sm c-card shadow-lg z-50"
        role="dialog"
        aria-label="Notifications dropdown"
      >
        <NotificationDropdown />
      </div>
    </Transition>
  </div>
</template>
