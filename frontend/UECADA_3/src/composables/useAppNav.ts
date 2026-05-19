import { computed } from 'vue'
import type { Component } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  BarChart3,
  Bell,
  LayoutDashboard,
  MapPinned,
  MessageSquare,
  Users,
  Wrench,
} from 'lucide-vue-next'

export type NavRouteName =
  | 'dashboard'
  | 'layout'
  | 'lines'
  | 'equipment'
  | 'alarms'
  | 'users'
  | 'community'
  | 'swmp-test'

export interface AppNavItem {
  label: string
  icon: Component
  to: { name: NavRouteName }
}

const DEFAULT_NAV: readonly AppNavItem[] = [
  { label: '대시보드', icon: LayoutDashboard, to: { name: 'dashboard' } },
  { label: '레이아웃', icon: MapPinned, to: { name: 'layout' } },
  { label: '라인 상세', icon: BarChart3, to: { name: 'lines' } },
  { label: '설비 제어', icon: Wrench, to: { name: 'equipment' } },
  { label: '알람 및 이력', icon: Bell, to: { name: 'alarms' } },
  { label: '사용자·권한', icon: Users, to: { name: 'users' } },
  { label: '커뮤니티', icon: MessageSquare, to: { name: 'community' } },
  { label: 'SWMP 테스트', icon: Wrench, to: { name: 'swmp-test' } },
] as const

const LINE_NAV: readonly AppNavItem[] = [
  { label: '대시보드', icon: LayoutDashboard, to: { name: 'dashboard' } },
  { label: '레이아웃', icon: MapPinned, to: { name: 'layout' } },
  { label: '라인 상세', icon: BarChart3, to: { name: 'lines' } },
  { label: '설비 제어', icon: Wrench, to: { name: 'equipment' } },
  { label: '알람 및 이력', icon: Bell, to: { name: 'alarms' } },
  { label: '사용자·권한', icon: Users, to: { name: 'users' } },
  { label: '커뮤니티', icon: MessageSquare, to: { name: 'community' } },
] as const

export function useAppNav(variant: 'default' | 'line' = 'default') {
  const auth = useAuthStore()
  const items = variant === 'line' ? LINE_NAV : DEFAULT_NAV
  const navItems = computed(() =>
    items.filter((item) => (item.to.name === 'users' ? auth.role === 'admin' : true)),
  )
  return { navItems }
}
