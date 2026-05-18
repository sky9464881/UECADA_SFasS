import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { UserRole } from '@/types/auth'

const TOKEN_KEY = 'uecada_access_token'
const ROLE_KEY = 'uecada_role'

function readToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

function readRole(): UserRole | null {
  const r = sessionStorage.getItem(ROLE_KEY)
  if (r === 'admin' || r === 'operator') return r
  return null
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(readToken())
  const role = ref<UserRole | null>(readRole())

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persist(token: string, userRole: UserRole) {
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(ROLE_KEY, userRole)
    accessToken.value = token
    role.value = userRole
  }

  function login(userRole: UserRole) {
    const token =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? `demo-${crypto.randomUUID()}`
        : `demo-${Date.now()}`
    persist(token, userRole)
  }

  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(ROLE_KEY)
    accessToken.value = null
    role.value = null
  }

  return {
    accessToken,
    role,
    isAuthenticated,
    login,
    clearSession,
  }
})
