import axios from 'axios'

export function formatApiError(error: unknown, fallback = 'Request failed'): string {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const data = error.response?.data as { detail?: string; message?: string; title?: string } | undefined
    const detail = data?.detail || data?.message || data?.title
    if (status === 401) {
      return detail || 'Authentication required — restart backend with profile dev or refresh dev token'
    }
    if (status && detail) {
      return `${status}: ${detail}`
    }
    if (status) {
      return `HTTP ${status}: ${fallback}`
    }
    return error.message || fallback
  }
  if (error instanceof Error) {
    return error.message
  }
  return fallback
}
