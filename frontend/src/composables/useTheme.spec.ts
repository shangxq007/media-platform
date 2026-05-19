import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { useTheme } from './useTheme'

describe('useTheme', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme')
    localStorage.clear()
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: false,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
  })

  afterEach(() => {
    vi.restoreAllMocks()
    document.documentElement.removeAttribute('data-theme')
    localStorage.clear()
  })

  it('defaults to system theme', () => {
    const { currentTheme, isDark } = useTheme()
    expect(currentTheme.value).toBe('system')
    expect(isDark.value).toBe(false)
  })

  it('applies light theme', () => {
    const { setTheme, isDark } = useTheme()
    setTheme('light')
    expect(isDark.value).toBe(false)
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('applies dark theme', () => {
    const { setTheme, isDark } = useTheme()
    setTheme('dark')
    expect(isDark.value).toBe(true)
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('toggles between light and dark', () => {
    const { setTheme, toggleTheme, isDark } = useTheme()
    setTheme('light')
    expect(isDark.value).toBe(false)
    toggleTheme()
    expect(isDark.value).toBe(true)
    toggleTheme()
    expect(isDark.value).toBe(false)
  })

  it('persists preference to localStorage', () => {
    const { setTheme } = useTheme()
    setTheme('dark')
    expect(localStorage.getItem('theme-preference')).toBe('dark')
  })

  it('initializes from localStorage', () => {
    localStorage.setItem('theme-preference', 'dark')
    const { initTheme, isDark } = useTheme()
    initTheme()
    expect(isDark.value).toBe(true)
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('falls back to system when localStorage has invalid value', () => {
    localStorage.setItem('theme-preference', 'invalid')
    const { initTheme, currentTheme } = useTheme()
    initTheme()
    expect(currentTheme.value).toBe('system')
  })

  it('applies system theme based on prefers-color-scheme', () => {
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue({
      matches: true,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    const { initTheme, isDark } = useTheme()
    initTheme()
    expect(isDark.value).toBe(true)
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})
