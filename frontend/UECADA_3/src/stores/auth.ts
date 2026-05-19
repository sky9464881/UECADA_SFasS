import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { AuthUser, UserRole } from '@/types/auth'

const TOKEN_KEY = 'uecada_access_token'
const ROLE_KEY = 'uecada_role'
const USER_KEY = 'uecada_user'

function readToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

function readRole(): UserRole | null {
  const r = sessionStorage.getItem(ROLE_KEY)
  if (r === 'admin' || r === 'manager' || r === 'operator') return r
  return null
}

function readUser(): AuthUser | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

function roleFromBackend(roleName: string | null | undefined): UserRole {
  const role = String(roleName ?? '').toUpperCase()
  if (role === 'ADMIN') return 'admin'
  if (role === 'MANAGER') return 'manager'
  return 'operator'
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(readToken())
  const role = ref<UserRole | null>(readRole())
  const user = ref<AuthUser | null>(readUser())

  const isAuthenticated = computed(() => Boolean(accessToken.value))

  function persist(token: string, userRole: UserRole, nextUser: AuthUser | null = null) {
    sessionStorage.setItem(TOKEN_KEY, token)
    sessionStorage.setItem(ROLE_KEY, userRole)
    if (nextUser) sessionStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    accessToken.value = token
    role.value = userRole
    user.value = nextUser
  }

  function login(nextUser: AuthUser) {
    const token =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? `demo-${crypto.randomUUID()}`
        : `demo-${Date.now()}`
    persist(token, roleFromBackend(nextUser.roleName), nextUser)
  }

  function clearSession() {
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(ROLE_KEY)
    sessionStorage.removeItem(USER_KEY)
    accessToken.value = null
    role.value = null
    user.value = null
  }

  return {
    accessToken,
    role,
    user,
    isAuthenticated,
    login,
    clearSession,
  }
})
