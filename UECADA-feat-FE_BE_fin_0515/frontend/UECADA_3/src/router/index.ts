import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const LoginPage = () => import('@/components/LoginPage.vue')
const DashboardPage = () => import('@/components/DashboardPage.vue')
const FactoryLayoutPage = () => import('@/components/FactoryLayoutPage.vue')
const EquipmentDetailPage = () => import('@/components/EquipmentDetailPage.vue')
const AlarmPage = () => import('@/components/AlarmPage.vue')
const UserManagementPage = () => import('@/components/UserManagementPage.vue')
const CommunityPage = () => import('@/components/CommunityPage.vue')
const SwmpTestPage = () => import('@/components/SwmpTestPage.vue')
const LineDetailPage = () => import('@/components/LineDetailPage.vue')

export const router = createRouter({
  history: createWebHashHistory(),
  linkActiveClass: 'active',
  routes: [
    { path: '/login', name: 'login', component: LoginPage, meta: { public: true } },
    { path: '/', redirect: { name: 'dashboard' } },
    { path: '/dashboard', name: 'dashboard', component: DashboardPage, meta: { requiresAuth: true } },
    { path: '/layout', name: 'layout', component: FactoryLayoutPage, meta: { requiresAuth: true } },
    { path: '/equipment', name: 'equipment', component: EquipmentDetailPage, meta: { requiresAuth: true } },
    { path: '/alarms', name: 'alarms', component: AlarmPage, meta: { requiresAuth: true } },
    {
      path: '/users',
      name: 'users',
      component: UserManagementPage,
      meta: { requiresAuth: true, roles: ['admin'] as const },
    },
    { path: '/community', name: 'community', component: CommunityPage, meta: { requiresAuth: true } },
    { path: '/swmp-test', name: 'swmp-test', component: SwmpTestPage, meta: { requiresAuth: true } },
    { path: '/lines', name: 'lines', component: LineDetailPage, meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.public) return true
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  const roles = to.meta.roles as readonly string[] | undefined
  if (roles?.length) {
    if (!auth.role || !roles.includes(auth.role)) {
      return { name: 'dashboard' }
    }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
  return true
})
