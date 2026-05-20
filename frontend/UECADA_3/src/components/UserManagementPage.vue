<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import type { Ref } from 'vue'
import { RouterLink } from 'vue-router'
import { fetchRolePermissions, updateRolePermission } from '@/api/userApi'
import {
  Bell,
  CalendarDays,
  ChevronDown,
  KeyRound,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  MonitorDot,
  Search,
  UserPlus,
  Users,
  X,
} from 'lucide-vue-next'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useUsers } from '@/composables/useUsers'
import { ROLE_OPTIONS, roleLabel } from '@/types/user'
import type { RoleCode, RolePermissionResponse } from '@/types/user'

interface DisplayUser {
  id: string
  name: string
  email: string
  role: string
  roleCode: RoleCode
  lineId: string | null
  department: string
  status: string
  lastLogin: string
}

const { navItems } = useAppNav()
const logout = useLogout()

const roleFilter = ref<RoleCode | ''>('') as Ref<RoleCode | ''>
const searchTerm = ref('')
const lineOptions = [
  { value: '', label: '전체/관리자' },
  { value: 'LINE-01', label: 'LINE-01' },
  { value: 'LINE-02', label: 'LINE-02' },
  { value: 'LINE-03', label: 'LINE-03' },
]

const { users: backendUsers, isPending, isError, error, refetch, updateRole, create } = useUsers({
  roleName: roleFilter,
})

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').slice(0, 19)
}

const users = computed(() =>
  backendUsers.value.map((u) => ({
    id: u.userId,
    name: u.userName,
    email: u.email ?? '',
    role: roleLabel(u.roleName),
    roleCode: u.roleName,
    lineId: u.lineId,
    department: u.lineId ?? '전체',
    status: '활성', // 백엔드에 잠금/접속중 개념 없음 — 기본값
    lastLogin: formatDateTime(u.createdAt), // 백엔드 lastLoginAt 미노출 → 가입일시 표시
  })),
)

const filteredUsers = computed(() => {
  const q = searchTerm.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter(
    (u) =>
      u.name.toLowerCase().includes(q) ||
      u.id.toLowerCase().includes(q) ||
      u.email.toLowerCase().includes(q),
  )
})

const roles = [
  {
    name: '관리자',
    count: 8,
    links: [
      { id: 'admin_user_perm', label: '사용자 권한 관리', icon: KeyRound },
      { id: 'admin_worker_acct', label: '작업자 계정 관리', icon: Users },
      { id: 'admin_full_screen', label: '전체 화면 조회', icon: LayoutDashboard },
      { id: 'admin_board_write', label: '게시판 작성', icon: MessageSquare },
    ],
  },
  {
    name: '라인 관리자',
    count: 3,
    links: [
      { id: 'manager_line_view', label: '담당 라인 조회', icon: MapPinned },
      { id: 'manager_worker_notice', label: '라인 공지 발송', icon: MessageSquare },
      { id: 'manager_alarm_act', label: '알람 조치 관리', icon: Bell },
      { id: 'manager_report_view', label: '운영 보고서 조회', icon: LayoutDashboard },
    ],
  },
  {
    name: '작업자',
    count: 56,
    links: [
      { id: 'op_line_view', label: '담당 라인 조회', icon: MapPinned },
      { id: 'op_equip_view', label: '설비 상태 조회', icon: MonitorDot },
      { id: 'op_alarm_act', label: '알람 조치 등록', icon: Bell },
      { id: 'op_community_view', label: '커뮤니티 조회', icon: MessageSquare },
    ],
  },
]

type RolePermissionState = Record<string, Record<string, boolean>>

const ROLE_PERMISSION_STORAGE_KEY = 'uecada_role_permissions'
const ROLE_CODE_BY_NAME: Record<string, RoleCode> = {
  관리자: 'ADMIN',
  '라인 관리자': 'MANAGER',
  작업자: 'OPERATOR',
}
const ROLE_NAME_BY_CODE: Record<string, string> = {
  ADMIN: '관리자',
  MANAGER: '라인 관리자',
  OPERATOR: '작업자',
}

function handleRoleChange(user: DisplayUser, event: Event) {
  const next = (event.target as HTMLSelectElement | null)?.value as RoleCode | undefined
  if (!next || next === user.roleCode) return
  updateRole.mutate({
    userId: user.id,
    roleName: next,
    lineId: next === 'ADMIN' ? null : user.lineId,
  })
}

function handleLineChange(user: DisplayUser, event: Event) {
  const next = (event.target as HTMLSelectElement | null)?.value ?? ''
  updateRole.mutate({
    userId: user.id,
    roleName: user.roleCode,
    lineId: next || null,
  })
}

const showCreateModal = ref(false)
const createForm = reactive({
  userId: '',
  loginId: '',
  lineId: '',
  userName: '',
  email: '',
  roleName: 'OPERATOR',
  password: '',
  securityQuestion: '초기 보안 답변은?',
  securityAnswer: 'secret',
})
const createError = ref('')

function openCreateModal() {
  createError.value = ''
  Object.assign(createForm, {
    userId: '',
    loginId: '',
    lineId: '',
    userName: '',
    email: '',
    roleName: 'OPERATOR',
    password: '',
    securityQuestion: '초기 보안 답변은?',
    securityAnswer: 'secret',
  })
  showCreateModal.value = true
}

function closeCreateModal() {
  showCreateModal.value = false
}

async function submitCreateUser() {
  createError.value = ''
  if (!createForm.userId || !createForm.loginId || !createForm.userName || !createForm.password) {
    createError.value = '필수 항목을 모두 입력하세요.'
    return
  }
  try {
    await create.mutateAsync({
      userId: createForm.userId,
      loginId: createForm.loginId,
      lineId: createForm.lineId || null,
      userName: createForm.userName,
      email: createForm.email || undefined,
      roleName: createForm.roleName,
      password: createForm.password,
      securityQuestion: createForm.securityQuestion,
      securityAnswer: createForm.securityAnswer,
    })
    closeCreateModal()
  } catch (e: unknown) {
    const errorObj = e as { response?: { data?: { message?: string } }; message?: string }
    createError.value = errorObj?.response?.data?.message || errorObj?.message || '사용자 추가 실패'
  }
}

type SummaryKey = 'total' | 'admin' | 'manager' | 'operator' | 'locked' | null
const activeSummaryKey = ref<SummaryKey>(null)
const userListSectionRef = ref<HTMLElement | null>(null)

function closeSummaryMenu() {
  activeSummaryKey.value = null
}

function toggleSummaryMenu(key: Exclude<SummaryKey, null>) {
  activeSummaryKey.value = activeSummaryKey.value === key ? null : key
}

function onDocumentKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && activeSummaryKey.value) closeSummaryMenu()
}

function onDocumentPointerDown(e: MouseEvent) {
  if (!activeSummaryKey.value) return
  const t = e.target
  if (!(t instanceof Element)) return
  const openCell = document.querySelector('.user-perm-summary-cell--open')
  if (openCell?.contains(t)) return
  closeSummaryMenu()
}

onMounted(() => {
  loadRolePermissions()
  document.addEventListener('keydown', onDocumentKeydown)
  document.addEventListener('mousedown', onDocumentPointerDown, true)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onDocumentKeydown)
  document.removeEventListener('mousedown', onDocumentPointerDown, true)
})

function roleNameFromKey(key: Exclude<SummaryKey, null>): string {
  if (key === 'admin') return '관리자'
  if (key === 'manager') return '라인 관리자'
  if (key === 'operator') return '작업자'
  return ''
}

function roleLinksFromKey(key: Exclude<SummaryKey, null>) {
  const name = roleNameFromKey(key)
  return roles.find((r) => r.name === name)?.links ?? []
}

const lockedUsersList = computed(() => users.value.filter((u) => u.status === '잠금'))

const totalQuickActions = [
  { id: 'scroll-list', label: '사용자 목록으로 이동' },
  { id: 'dummy-refresh', label: '현황 새로고침 (샘플)' },
]

function onTotalQuickAction(actionId: string) {
  if (actionId === 'scroll-list' && userListSectionRef.value) {
    userListSectionRef.value.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
  closeSummaryMenu()
}

function onSummaryCardKeydown(e: KeyboardEvent, key: Exclude<SummaryKey, null>) {
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    toggleSummaryMenu(key)
  }
}

function makeDefaultRolePermState(): RolePermissionState {
  return Object.fromEntries(
    roles.map((r) => [
      r.name,
      Object.fromEntries(r.links.map((l, idx) => [l.id, r.name === '관리자' ? true : idx < 2])),
    ]),
  ) as RolePermissionState
}

const rolePermChecked = reactive<RolePermissionState>(makeDefaultRolePermState())
const rolePermSaving = reactive<Record<string, boolean>>({})
const rolePermSyncMessage = ref('')

function rolePermKey(roleName: string, permId: string): string {
  return `${roleName}:${permId}`
}

function roleCodeFromName(roleName: string): RoleCode {
  return ROLE_CODE_BY_NAME[roleName] ?? roleName
}

function readStoredRolePerms(): RolePermissionState | null {
  try {
    const raw = localStorage.getItem(ROLE_PERMISSION_STORAGE_KEY)
    return raw ? JSON.parse(raw) as RolePermissionState : null
  } catch {
    return null
  }
}

function applyRolePermState(next: RolePermissionState) {
  Object.entries(next).forEach(([roleName, perms]) => {
    if (!rolePermChecked[roleName]) rolePermChecked[roleName] = {}
    Object.entries(perms).forEach(([permId, allowed]) => {
      rolePermChecked[roleName][permId] = Boolean(allowed)
    })
  })
}

function saveRolePermLocal() {
  localStorage.setItem(ROLE_PERMISSION_STORAGE_KEY, JSON.stringify(rolePermChecked))
}

function applyRolePermissionRows(rows: RolePermissionResponse[]) {
  rows.forEach((row) => {
    const roleName = ROLE_NAME_BY_CODE[String(row.roleName).toUpperCase()] ?? row.roleName
    if (!rolePermChecked[roleName]) rolePermChecked[roleName] = {}
    rolePermChecked[roleName][row.permissionId] = row.allowed
  })
}

async function loadRolePermissions() {
  const stored = readStoredRolePerms()
  if (stored) applyRolePermState(stored)
  try {
    const rows = await fetchRolePermissions()
    applyRolePermissionRows(rows)
    saveRolePermLocal()
    rolePermSyncMessage.value = ''
  } catch {
    rolePermSyncMessage.value = '권한 설정은 로컬 저장 상태로 동작 중입니다.'
  }
}

function isRolePermSaving(roleName: string, permId: string): boolean {
  return Boolean(rolePermSaving[rolePermKey(roleName, permId)])
}

async function setRolePerm(roleName: string, permId: string, on: boolean) {
  if (!rolePermChecked[roleName]) rolePermChecked[roleName] = {}
  rolePermChecked[roleName][permId] = on
  saveRolePermLocal()

  const key = rolePermKey(roleName, permId)
  rolePermSaving[key] = true
  rolePermSyncMessage.value = '권한 설정 저장 중...'
  try {
    const saved = await updateRolePermission({
      roleName: roleCodeFromName(roleName),
      permissionId: permId,
      allowed: on,
    })
    applyRolePermissionRows([saved])
    saveRolePermLocal()
    rolePermSyncMessage.value = '권한 설정 저장 완료'
  } catch {
    rolePermSyncMessage.value = '서버 저장 실패: 화면에는 로컬 설정을 유지했습니다.'
  } finally {
    rolePermSaving[key] = false
  }
}

const userStats = computed(() => {
  const total = users.value.length
  const admin = users.value.filter((u) => u.roleCode === 'ADMIN').length
  const manager = users.value.filter((u) => u.roleCode === 'MANAGER').length
  const operator = users.value.filter((u) => u.roleCode === 'OPERATOR').length
  const locked = users.value.filter((u) => u.status === '잠금').length
  return { total, admin, manager, operator, locked }
})

type SummaryItem = {
  key: Exclude<SummaryKey, null>
  label: string
  value: number
  detail: string
  tone: string
}

const userSummaryItems = computed<SummaryItem[]>(() => {
  const { total, admin, manager, operator, locked } = userStats.value
  return [
    {
      key: 'total',
      label: '등록 사용자',
      value: total,
      detail: '목록에 표시된 계정 수',
      tone: 'info',
    },
    {
      key: 'admin',
      label: '관리자',
      value: admin,
      detail: '시스템·계정 관리 권한',
      tone: 'done',
    },
    {
      key: 'manager',
      label: '라인 관리자',
      value: manager,
      detail: '담당 라인 관리 권한',
      tone: 'done',
    },
    {
      key: 'operator',
      label: '작업자',
      value: operator,
      detail: '라인·설비 운영 권한',
      tone: 'pending',
    },
    {
      key: 'locked',
      label: '잠금 계정',
      value: locked,
      detail: '로그인 제한 상태',
      tone: locked > 0 ? 'critical' : 'info',
    },
  ]
})
</script>

<template>
  <main class="dashboard-shell">
    <aside class="dashboard-sidebar" aria-label="주요 메뉴">
      <RouterLink class="dashboard-brand" :to="{ name: 'dashboard' }">
        <span class="brand-symbol">U</span>
        <span>
          <strong>UECADA</strong>
          <small>우리들의 스카다</small>
        </span>
      </RouterLink>

      <nav class="dashboard-nav">
        <RouterLink v-for="item in navItems" :key="item.label" :to="item.to">
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="sidebar-status">
        <span>관리자</span>
        <strong>김관리</strong>
        <p>역할·권한 정책과 사용자 계정을 한 화면에서 관리합니다.</p>
      </div>
    </aside>

    <section class="dashboard-main user-perm-dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles">
          <p class="dashboard-kicker">User &amp; Access Control</p>
          <h1>사용자·권한 관리</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <button class="primary-action" type="button" @click="openCreateModal">
            <UserPlus :size="17" />
            <span>사용자 추가</span>
          </button>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <div class="user-perm-page-body">
        <div class="user-perm-centered-column">
          <div class="user-perm-main">
            <section class="alarm-summary-grid user-perm-summary-grid" aria-label="사용자 현황 요약">
              <div
                v-for="item in userSummaryItems"
                :key="item.key"
                class="user-perm-summary-cell"
                :class="{ 'user-perm-summary-cell--open': activeSummaryKey === item.key }"
              >
                <article
                  :class="[
                    'alarm-summary-card',
                    'user-perm-summary-card--menu',
                    item.tone,
                    {
                      'user-perm-summary-card--open': activeSummaryKey === item.key,
                    },
                  ]"
                  role="button"
                  tabindex="0"
                  aria-haspopup="true"
                  :aria-controls="`user-perm-dd-${item.key}`"
                  :aria-expanded="activeSummaryKey === item.key"
                  :aria-label="`${item.label} 상세 메뉴 (${item.value})`"
                  @click.stop="toggleSummaryMenu(item.key)"
                  @keydown="onSummaryCardKeydown($event, item.key)"
                >
                  <div class="user-perm-summary-card-title-row">
                    <span class="user-perm-summary-card-label">{{ item.label }}</span>
                    <ChevronDown
                      class="user-perm-summary-card-chevron"
                      :size="18"
                      :stroke-width="2.2"
                      aria-hidden="true"
                    />
                  </div>
                  <strong>{{ item.value }}</strong>
                  <p>{{ item.detail }}</p>
                </article>

                <div
                  v-show="activeSummaryKey === item.key"
                  :id="`user-perm-dd-${item.key}`"
                  class="user-perm-summary-dropdown"
                  role="region"
                  :aria-label="`${item.label} 메뉴`"
                  @click.stop
                >
                  <header class="user-perm-drop__head">
                    <p class="user-perm-drop__title">
                      <template v-if="item.key === 'admin' || item.key === 'manager' || item.key === 'operator'">
                        {{ roleNameFromKey(item.key) }} · 허용 기능 선택
                      </template>
                      <template v-else-if="item.key === 'total'">등록 사용자 · 빠른 작업</template>
                      <template v-else>잠금 계정 · 목록</template>
                    </p>
                    <p
                      v-if="item.key === 'admin' || item.key === 'manager' || item.key === 'operator'"
                      class="user-perm-drop__sync"
                    >
                      {{ rolePermSyncMessage || '체크 즉시 권한 정책에 저장됩니다.' }}
                    </p>
                  </header>

                  <ul
                    v-if="item.key === 'admin' || item.key === 'manager' || item.key === 'operator'"
                    class="user-perm-drop__list"
                    role="list"
                  >
                    <li v-for="link in roleLinksFromKey(item.key)" :key="link.id" role="listitem">
                      <label class="user-perm-drop__row user-perm-drop__row--toggle">
                        <input
                          type="checkbox"
                          class="user-perm-drop__check"
                          :checked="rolePermChecked[roleNameFromKey(item.key)]?.[link.id]"
                          :disabled="isRolePermSaving(roleNameFromKey(item.key), link.id)"
                          @change="
                            setRolePerm(
                              roleNameFromKey(item.key),
                              link.id,
                              ($event.target as HTMLInputElement).checked,
                            )
                          "
                        />
                        <span class="user-perm-drop__ico" aria-hidden="true">
                          <component :is="link.icon" :size="18" :stroke-width="2" />
                        </span>
                        <span class="user-perm-drop__label">{{ link.label }}</span>
                      </label>
                    </li>
                  </ul>

                  <ul v-else-if="item.key === 'total'" class="user-perm-drop__list" role="list">
                    <li v-for="act in totalQuickActions" :key="act.id" role="listitem">
                      <button
                        type="button"
                        class="user-perm-drop__row user-perm-drop__row--action"
                        @click="onTotalQuickAction(act.id)"
                      >
                        {{ act.label }}
                      </button>
                    </li>
                  </ul>

                  <ul v-else-if="item.key === 'locked'" class="user-perm-drop__list" role="list">
                    <template v-if="!lockedUsersList.length">
                      <li role="listitem">
                        <div class="user-perm-drop__row user-perm-drop__row--empty" role="status">
                          잠금 상태 계정이 없습니다.
                        </div>
                      </li>
                    </template>
                    <template v-else>
                      <li v-for="u in lockedUsersList" :key="u.id" role="listitem">
                        <div class="user-perm-drop__row user-perm-drop__row--info">
                          <div class="user-perm-drop__primary">
                            <strong>{{ u.name }}</strong>
                            <span class="user-perm-drop__meta">{{ u.id }}</span>
                          </div>
                          <span class="user-perm-drop__trail">{{ u.department }}</span>
                        </div>
                      </li>
                    </template>
                  </ul>
                </div>
              </div>
            </section>

            <section class="query-panel user-filter-panel">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">User Search</p>
                  <h2>사용자 조회</h2>
                </div>
                <button class="primary-action" type="button" @click="refetch()">
                  <Search :size="17" />
                  <span>새로고침</span>
                </button>
              </div>

              <div class="query-grid user-query-grid">
                <label>
                  <span>역할(권한)</span>
                  <select v-model="roleFilter">
                    <option value="">전체</option>
                    <option v-for="opt in ROLE_OPTIONS" :key="opt.code" :value="opt.code">
                      {{ opt.label }}
                    </option>
                  </select>
                </label>
                <label>
                  <span>계정 상태</span>
                  <select disabled title="백엔드 미지원">
                    <option>전체</option>
                  </select>
                </label>
                <label>
                  <span>검색어</span>
                  <input v-model="searchTerm" type="text" placeholder="이름·ID·이메일" />
                </label>
              </div>
            </section>

            <section ref="userListSectionRef" class="dashboard-panel user-list-panel">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Account List</p>
                  <h2>사용자 목록</h2>
                </div>
                <div class="section-title-trail">
                  <span class="section-note">로그인 시 최종 접속 일시가 갱신됩니다.</span>
                  <Users :size="22" />
                </div>
              </div>

              <p v-if="isPending" style="color: #64748b; padding: 12px">사용자 목록을 불러오는 중…</p>
              <p v-else-if="isError" style="color: #dc2626; padding: 12px">
                불러오기 실패: {{ error?.message ?? '알 수 없는 오류' }}
              </p>

              <div v-else class="user-table-wrap">
                <table class="user-table">
                  <thead>
                    <tr>
                      <th>사용자</th>
                      <th>역할(권한)</th>
                      <th>담당 라인</th>
                      <th>이메일</th>
                      <th>계정 상태</th>
                      <th>가입일시</th>
                      <th>권한/라인 변경</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="filteredUsers.length === 0">
                      <td colspan="7" style="text-align: center; padding: 24px; color: #64748b">
                        조건에 맞는 사용자가 없습니다.
                      </td>
                    </tr>
                    <tr v-for="user in filteredUsers" :key="user.id">
                      <td>
                        <strong>{{ user.name }}</strong>
                        <span>{{ user.id }}</span>
                      </td>
                      <td><span :class="['role-badge', user.roleCode]">{{ user.role }}</span></td>
                      <td>{{ user.lineId || '전체' }}</td>
                      <td>{{ user.email || '-' }}</td>
                      <td><span :class="['account-status-badge', user.status]">{{ user.status }}</span></td>
                      <td>
                        <div class="last-login-cell">
                          <CalendarDays :size="15" />
                          <span>{{ user.lastLogin }}</span>
                        </div>
                      </td>
                      <td>
                        <div class="user-assignment-controls">
                          <select
                            class="permission-button"
                            :value="user.roleCode"
                            :disabled="updateRole.isPending.value"
                            @change="handleRoleChange(user, $event)"
                          >
                            <option v-for="opt in ROLE_OPTIONS" :key="opt.code" :value="opt.code">
                              {{ opt.label }}
                            </option>
                          </select>
                          <select
                            class="permission-button"
                            :value="user.lineId || ''"
                            :disabled="updateRole.isPending.value || user.roleCode === 'ADMIN'"
                            @change="handleLineChange(user, $event)"
                          >
                            <option v-for="line in lineOptions" :key="line.value" :value="line.value">
                              {{ line.label }}
                            </option>
                          </select>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>

    <div v-if="showCreateModal" class="user-create-modal" @click.self="closeCreateModal">
      <div class="user-create-modal__card">
        <header class="user-create-modal__head">
          <h3>사용자 추가</h3>
          <button type="button" class="icon-link" aria-label="닫기" @click="closeCreateModal">
            <X :size="18" />
          </button>
        </header>
        <form class="user-create-modal__body" @submit.prevent="submitCreateUser">
          <label>
            <span>사용자 ID *</span>
            <input v-model="createForm.userId" type="text" maxlength="20" required />
          </label>
          <label>
            <span>로그인 ID *</span>
            <input v-model="createForm.loginId" type="text" maxlength="50" required />
          </label>
          <label>
            <span>라인</span>
            <select v-model="createForm.lineId">
              <option value="">전체/관리자</option>
              <option value="LINE-01">LINE-01</option>
              <option value="LINE-02">LINE-02</option>
              <option value="LINE-03">LINE-03</option>
            </select>
          </label>
          <label>
            <span>이름 *</span>
            <input v-model="createForm.userName" type="text" maxlength="50" required />
          </label>
          <label>
            <span>이메일</span>
            <input v-model="createForm.email" type="email" maxlength="100" />
          </label>
          <label>
            <span>역할 *</span>
            <select v-model="createForm.roleName">
              <option v-for="opt in ROLE_OPTIONS" :key="opt.code" :value="opt.code">
                {{ opt.label }}
              </option>
            </select>
          </label>
          <label>
            <span>비밀번호 *</span>
            <input v-model="createForm.password" type="password" required />
          </label>
          <label>
            <span>보안 질문 *</span>
            <input v-model="createForm.securityQuestion" type="text" required />
          </label>
          <label>
            <span>보안 답변 *</span>
            <input v-model="createForm.securityAnswer" type="text" required />
          </label>

          <p v-if="createError" class="user-create-modal__error">{{ createError }}</p>

          <footer class="user-create-modal__foot">
            <button type="button" class="ghost-button" @click="closeCreateModal">취소</button>
            <button type="submit" class="primary-action" :disabled="create.isPending.value">
              {{ create.isPending.value ? '추가 중…' : '추가' }}
            </button>
          </footer>
        </form>
      </div>
    </div>
  </main>
</template>

<style scoped>
.user-assignment-controls {
  display: grid;
  grid-template-columns: minmax(110px, 1fr) minmax(110px, 1fr);
  gap: 8px;
  min-width: 240px;
}

.user-assignment-controls select {
  width: 100%;
  min-width: 0;
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  font: inherit;
}

.user-create-modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.user-create-modal__card {
  width: min(440px, 92vw);
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.25);
  display: flex;
  flex-direction: column;
}
.user-create-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}
.user-create-modal__head h3 {
  margin: 0;
  font-size: 16px;
}
.user-create-modal__body {
  padding: 18px 20px;
  display: grid;
  gap: 12px;
}
.user-create-modal__body label {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: #475569;
}
.user-create-modal__body input,
.user-create-modal__body select {
  padding: 8px 10px;
  border: 1px solid #cbd5f5;
  border-radius: 8px;
  font-size: 14px;
}
.user-create-modal__error {
  margin: 0;
  padding: 8px 10px;
  background: #fef2f2;
  color: #b91c1c;
  border-radius: 8px;
  font-size: 13px;
}
.user-create-modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
