import { ref, watch, onMounted } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'theme-preference'

const currentTheme = ref<ThemeMode>('system')
const isDark = ref(false)

function getSystemDark(): boolean {
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyTheme(mode: ThemeMode) {
  const resolved = mode === 'system' ? (getSystemDark() ? 'dark' : 'light') : mode
  isDark.value = resolved === 'dark'
  document.documentElement.setAttribute('data-theme', resolved === 'dark' ? 'dark' : 'light')
}

function getStoredTheme(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch { /* noop */ }
  return 'system'
}

function toggleTheme() {
  const next: ThemeMode = isDark.value ? 'light' : 'dark'
  setTheme(next)
}

function setTheme(mode: ThemeMode) {
  currentTheme.value = mode
  try {
    localStorage.setItem(STORAGE_KEY, mode)
  } catch {}
  applyTheme(mode)
}

function initTheme() {
  const mode = getStoredTheme()
  currentTheme.value = mode
  applyTheme(mode)

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (currentTheme.value === 'system') {
      applyTheme('system')
    }
  })
}

export function useTheme() {
  onMounted(() => {
    if (!document.documentElement.hasAttribute('data-theme')) {
      initTheme()
    }
  })

  watch(currentTheme, (mode) => {
    applyTheme(mode)
  })

  return {
    currentTheme,
    isDark,
    toggleTheme,
    setTheme,
    initTheme,
  }
}
