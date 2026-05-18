import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

export function useLogout() {
  const router = useRouter()
  const auth = useAuthStore()
  return async () => {
    auth.clearSession()
    await router.push({ name: 'login' })
  }
}
