<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import UserAccountMenu from './UserAccountMenu.vue'

const router = useRouter()

const props = withDefaults(defineProps<{
  userName?: string
  userInitials?: string
  avatarUrl?: string
  isAdmin?: boolean
  ariaLabel?: string
}>(), {
  userName: 'User',
  userInitials: 'U',
  isAdmin: false,
  ariaLabel: 'User account menu',
})

const emit = defineEmits<{
  open: []
  close: []
}>()

const isOpen = ref(false)
const menuRef = ref<HTMLElement | null>(null)
const buttonRef = ref<HTMLButtonElement | null>(null)

const initials = computed(() => {
  if (props.userInitials) return props.userInitials
  const parts = props.userName.trim().split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return props.userName.slice(0, 2).toUpperCase()
})

function toggle() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    emit('open')
  } else {
    emit('close')
  }
}

function close() {
  if (isOpen.value) {
    isOpen.value = false
    emit('close')
  }
}

function signOut() {
  close()
  router.push('/')
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
  if (menuRef.value && menuRef.value.contains(target)) return
  if (buttonRef.value && buttonRef.value.contains(target)) return
  close()
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div ref="menuRef" class="relative">
    <button
      ref="buttonRef"
      type="button"
      class="w-8 h-8 rounded-full flex items-center justify-center text-xs font-medium transition-all duration-fast"
      :class="isOpen
        ? 'bg-primary-600 text-white ring-2 ring-primary-400 ring-offset-1 ring-offset-bg-base'
        : 'bg-primary-600 text-white hover:bg-primary-500'"
      :aria-label="ariaLabel"
      :aria-expanded="isOpen"
      aria-haspopup="true"
      @click="toggle"
    >
      <img
        v-if="avatarUrl"
        :src="avatarUrl"
        :alt="`${userName} avatar`"
        class="w-full h-full rounded-full object-cover"
      />
      <span v-else>{{ initials }}</span>
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
        aria-label="Account menu dropdown"
      >
        <div class="c-card-body px-sm py-xs border-b border-default">
          <div class="text-sm font-medium text-text-primary truncate-text">{{ userName }}</div>
        </div>
        <UserAccountMenu :is-admin="isAdmin" @close="close" />
        <div class="border-t border-default">
          <button
            type="button"
            class="w-full flex items-center gap-sm px-md py-sm text-sm text-text-secondary hover:bg-bg-surface-hover hover:text-text-primary transition-colors duration-fast text-left"
            role="menuitem"
            @click="signOut"
          >
            <span class="text-base flex-shrink-0" aria-hidden="true">🚪</span>
            <span>Sign Out</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
