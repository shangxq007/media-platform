import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useSaveProject } from './useSaveProject'

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({
    currentProject: { id: 'p1', name: 'Test', tenantId: 't1', description: '', status: 'active', createdAt: '' },
    setProject: vi.fn(),
    currentTenant: 't1',
  }),
}))

vi.mock('@/api', () => ({
  ProjectAPI: {
    create: vi.fn().mockResolvedValue({ id: 'p1', name: 'Test', tenantId: 't1', description: '', status: 'active', createdAt: '2024-01-01' }),
  },
}))

vi.mock('@/utils/i18n', () => ({
  getErrorMessage: (code: string) => code,
}))

describe('useSaveProject', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('initializes with clean state', () => {
    const { isSaving, isDirty, saveError, lastSavedAt } = useSaveProject()
    expect(isSaving.value).toBe(false)
    expect(isDirty.value).toBe(false)
    expect(saveError.value).toBeNull()
    expect(lastSavedAt.value).toBeNull()
  })

  it('marks dirty', () => {
    const { isDirty, markDirty } = useSaveProject()
    markDirty()
    expect(isDirty.value).toBe(true)
  })

  it('clears dirty state', () => {
    const { isDirty, markDirty, clearDirty } = useSaveProject()
    markDirty()
    clearDirty()
    expect(isDirty.value).toBe(false)
  })

  it('saves project successfully', async () => {
    const { isSaving, isDirty, saveError, lastSavedAt, saveProject } = useSaveProject()
    await saveProject()
    expect(isSaving.value).toBe(false)
    expect(isDirty.value).toBe(false)
    expect(saveError.value).toBeNull()
    expect(lastSavedAt.value).toBeInstanceOf(Date)
  })

  it('returns correct status text when saving', () => {
    const { isSaving, getSaveStatusText } = useSaveProject()
    isSaving.value = true
    expect(getSaveStatusText()).toBe('Saving...')
  })

  it('returns correct status text when dirty', () => {
    const { isDirty, getSaveStatusText } = useSaveProject()
    isDirty.value = true
    expect(getSaveStatusText()).toBe('Unsaved changes')
  })

  it('returns correct status text when saved recently', () => {
    const { lastSavedAt, getSaveStatusText } = useSaveProject()
    lastSavedAt.value = new Date()
    const text = getSaveStatusText()
    expect(text).toContain('saved')
  })
})
