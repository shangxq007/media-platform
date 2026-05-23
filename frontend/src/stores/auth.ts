import { defineStore } from 'pinia'
import {
  getAccessToken,
  getOidcUser,
  handleOAuthCallback,
  signInRedirect,
  signOutOidc,
} from '@/auth/oidcClient'
import { isOidcEnabled } from '@/auth/oidcConfig'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    oidcReady: false,
    userSub: null as string | null,
  }),

  getters: {
    oidcEnabled: () => isOidcEnabled(),
  },

  actions: {
    async init() {
      if (!isOidcEnabled()) {
        this.oidcReady = true
        return
      }
      const user = await getOidcUser()
      this.userSub = user?.profile?.sub ?? null
      this.oidcReady = true
    },

    async login() {
      await signInRedirect()
    },

    async completeCallback() {
      const user = await handleOAuthCallback()
      this.userSub = user.profile?.sub ?? null
    },

    async bearerToken(): Promise<string | null> {
      return getAccessToken()
    },

    async logout() {
      await signOutOidc()
      this.userSub = null
    },
  },
})
