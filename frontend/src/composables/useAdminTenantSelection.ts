import { ref, onMounted } from 'vue'
import { IdentityAPI } from '@/api/admin/identity'
import type { AdminTenant } from '@/api/admin/identity'

/**
 * Composable for admin tenant selection.
 *
 * Loads the list of all tenants from the admin API and manages the currently
 * selected tenant ID. Does NOT default to any hardcoded tenant — if no tenants
 * exist, the list is empty and selectedTenantId is empty.
 *
 * Usage:
 *   const { tenants, selectedTenantId, loading, error, loadTenants } = useAdminTenantSelection()
 */
export function useAdminTenantSelection() {
  const tenants = ref<AdminTenant[]>([])
  const selectedTenantId = ref('')
  const loading = ref(true)
  const error = ref<string | null>(null)

  async function loadTenants() {
    loading.value = true
    error.value = null
    try {
      const list = await IdentityAPI.listAllTenants()
      tenants.value = list
      // Auto-select first tenant only if none currently selected
      if (!selectedTenantId.value && list.length > 0) {
        selectedTenantId.value = list[0].id
      }
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load tenants'
      tenants.value = []
      selectedTenantId.value = ''
    } finally {
      loading.value = false
    }
  }

  onMounted(loadTenants)

  return {
    tenants,
    selectedTenantId,
    loading,
    error,
    loadTenants,
  }
}
