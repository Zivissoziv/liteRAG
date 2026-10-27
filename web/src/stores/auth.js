import { defineStore } from 'pinia'
import { authApi } from '../api'

/**
 * 登录状态。令牌存放在服务端下发的 HttpOnly Cookie 中，前端不接触令牌本身，
 * 因此刷新页面时通过 /api/auth/me 兜底恢复身份。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    permissions: [],
    ready: false
  }),

  getters: {
    loggedIn: (state) => !!state.user,
    displayName: (state) => state.user?.displayName || state.user?.username || '',
    role: (state) => state.user?.role || '',
    roleLabel: (state) => state.user?.roleLabel || '',
    isAdmin: (state) => state.user?.role === 'ADMIN',
    can: (state) => (permission) => state.permissions.includes(permission)
  },

  actions: {
    apply(payload) {
      this.user = payload?.user || null
      this.permissions = payload?.permissions || payload?.user?.permissions || []
      this.ready = true
    },

    async login(username, password) {
      const data = await authApi.login({ username, password })
      this.apply(data)
      return data
    },

    async fetchMe() {
      try {
        const data = await authApi.me()
        this.apply(data)
      } catch (e) {
        this.user = null
        this.permissions = []
      } finally {
        this.ready = true
      }
      return this.user
    },

    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 令牌可能已过期，忽略
      }
      this.user = null
      this.permissions = []
      this.ready = true
    },

    clear() {
      this.user = null
      this.permissions = []
    }
  }
})
