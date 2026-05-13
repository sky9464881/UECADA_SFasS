<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
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
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 제어', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users', active: true },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

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

const users = [
  { id: 'ADM-001', name: '김관리', role: '관리자', department: '생산기술', status: '활성', lastLogin: '2026-05-11 12:31:18' },
  { id: 'MT-021', name: '정설비', role: '관리자', department: '설비보전', status: '활성', lastLogin: '2026-05-11 10:42:03' },
  { id: 'OP-1042', name: '박주조', role: '작업자', department: '주조 A조', status: '접속중', lastLogin: '2026-05-11 12:08:44' },
  { id: 'OP-1130', name: '이가공', role: '작업자', department: '가공 B조', status: '접속중', lastLogin: '2026-05-11 11:55:20' },
  { id: 'OP-1008', name: '최조립', role: '작업자', department: '조립 C조', status: '활성', lastLogin: '2026-05-11 09:36:11' },
  { id: 'OP-1187', name: '한검사', role: '작업자', department: '품질검사', status: '잠금', lastLogin: '2026-05-10 22:18:09' },
]

/** '관리자' | '작업자' | null — 같은 역할을 다시 누르면 닫힘 */
const activeRole = ref(null)

function closeRoleMenu() {
  activeRole.value = null
}

function toggleRole(name) {
  activeRole.value = activeRole.value === name ? null : name
}

function onDocumentKeydown(e) {
  if (e.key === 'Escape' && activeRole.value) closeRoleMenu()
}

onMounted(() => {
  document.addEventListener('keydown', onDocumentKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', onDocumentKeydown)
})

const activeRoleLinks = computed(() => {
  const r = roles.find((role) => role.name === activeRole.value)
  return r?.links ?? []
})

/** 역할별 권한 기능 on/off (UI 샘플) */
const rolePermChecked = reactive(
  Object.fromEntries(
    roles.map((r) => [
      r.name,
      Object.fromEntries(
        r.links.map((l, idx) => [l.id, r.name === '관리자' ? true : idx < 2]),
      ),
    ]),
  ),
)

function setRolePerm(roleName, permId, on) {
  if (!rolePermChecked[roleName]) rolePermChecked[roleName] = {}
  rolePermChecked[roleName][permId] = on
}

const userStats = computed(() => {
  const total = users.length
  const admin = users.filter((u) => u.role === '관리자').length
  const operator = users.filter((u) => u.role === '작업자').length
  const locked = users.filter((u) => u.status === '잠금').length
  return { total, admin, operator, locked }
})

const userSummaryItems = computed(() => {
  const { total, admin, operator, locked } = userStats.value
  return [
    { label: '등록 사용자', value: total, detail: '목록에 표시된 계정 수', tone: 'info', roleMenu: null },
    {
      label: '관리자',
      value: admin,
      detail: '시스템·계정 관리 권한',
      tone: 'done',
      roleMenu: '관리자',
    },
    {
      label: '작업자',
      value: operator,
      detail: '라인·설비 운영 권한',
      tone: 'pending',
      roleMenu: '작업자',
    },
    {
      label: '잠금 계정',
      value: locked,
      detail: '로그인 제한 상태',
      tone: locked > 0 ? 'critical' : 'info',
      roleMenu: null,
    },
  ]
})

function onSummaryRoleCardKeydown(e, roleName) {
  if (!roleName) return
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    toggleRole(roleName)
  }
}
</script>

<template>
  <main class="dashboard-shell">
    <aside class="dashboard-sidebar" aria-label="주요 메뉴">
      <a class="dashboard-brand" href="#/dashboard">
        <span class="brand-symbol">U</span>
        <span>
          <strong>UECADA</strong>
          <small>우리들의 스카다</small>
        </span>
      </a>

      <nav class="dashboard-nav">
        <a
          v-for="item in navItems"
          :key="item.label"
          :class="{ active: item.active }"
          :href="item.href"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </a>
      </nav>

      <div class="sidebar-status">
        <span>관리자</span>
        <strong>김관리</strong>
        <p>역할·권한 정책과 사용자 계정을 한 화면에서 관리합니다.</p>
      </div>
    </aside>

    <section class="dashboard-main user-perm-dashboard-main">
      <header class="dashboard-header user-perm-dashboard-header">
        <div class="user-perm-header-left">
          <p class="dashboard-kicker">User &amp; Access Control</p>
          <div class="user-perm-title-row">
            <h1 class="user-perm-page-title">사용자·권한 관리</h1>
          </div>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <button class="primary-action" type="button">
            <UserPlus :size="17" />
            <span>사용자 추가</span>
          </button>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <Teleport to="body">
        <Transition name="user-perm-overlay">
          <div v-if="activeRole" class="user-perm-overlay-root">
            <div
              class="user-perm-overlay-backdrop"
              aria-hidden="true"
              @click="closeRoleMenu"
            />
            <div
              id="user-perm-role-overlay-panel"
              class="user-perm-overlay-dropdown"
              role="dialog"
              aria-modal="true"
              :aria-label="`${activeRole} 허용 기능 선택`"
              @click.stop
            >
              <p class="user-perm-drawer-kicker">{{ activeRole }} · 허용 기능 선택</p>
              <ul class="user-perm-perm-list user-perm-overlay-perm-list">
                <li v-for="link in activeRoleLinks" :key="link.id">
                  <label class="user-perm-perm-row">
                    <input
                      type="checkbox"
                      class="user-perm-perm-check"
                      :checked="rolePermChecked[activeRole]?.[link.id]"
                      @change="setRolePerm(activeRole, link.id, $event.target.checked)"
                    />
                    <span class="user-perm-perm-ico" aria-hidden="true">
                      <component :is="link.icon" :size="18" :stroke-width="2" />
                    </span>
                    <span class="user-perm-perm-label">{{ link.label }}</span>
                  </label>
                </li>
              </ul>
            </div>
          </div>
        </Transition>
      </Teleport>

      <div class="user-perm-page-body">
        <div class="user-perm-centered-column">
          <div class="user-perm-main">
            <section class="alarm-summary-grid user-perm-summary-grid" aria-label="사용자 현황 요약">
              <article
                v-for="item in userSummaryItems"
                :key="item.label"
                :class="[
                  'alarm-summary-card',
                  item.tone,
                  { 'user-perm-summary-card--role': item.roleMenu },
                  {
                    'user-perm-summary-card--open':
                      item.roleMenu && activeRole === item.roleMenu,
                  },
                ]"
                :role="item.roleMenu ? 'button' : undefined"
                :tabindex="item.roleMenu ? 0 : undefined"
                :aria-haspopup="item.roleMenu ? 'dialog' : undefined"
                :aria-controls="item.roleMenu ? 'user-perm-role-overlay-panel' : undefined"
                :aria-expanded="item.roleMenu ? activeRole === item.roleMenu : undefined"
                :aria-label="
                  item.roleMenu
                    ? `${item.label} 허용 기능 열기 (${item.value}명)`
                    : undefined
                "
                @click="item.roleMenu && toggleRole(item.roleMenu)"
                @keydown="onSummaryRoleCardKeydown($event, item.roleMenu)"
              >
                <div class="user-perm-summary-card-title-row">
                  <span class="user-perm-summary-card-label">{{ item.label }}</span>
                  <ChevronDown
                    v-if="item.roleMenu"
                    class="user-perm-summary-card-chevron"
                    :size="18"
                    :stroke-width="2.2"
                    aria-hidden="true"
                  />
                </div>
                <strong>{{ item.value }}</strong>
                <p>{{ item.detail }}</p>
              </article>
            </section>

            <section class="query-panel user-filter-panel">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">User Search</p>
                  <h2>사용자 조회</h2>
                </div>
                <button class="primary-action" type="button">
                  <Search :size="17" />
                  <span>조회</span>
                </button>
              </div>

              <div class="query-grid user-query-grid">
                <label>
                  <span>역할(권한)</span>
                  <select>
                    <option>전체</option>
                    <option>관리자</option>
                    <option>작업자</option>
                  </select>
                </label>
                <label>
                  <span>계정 상태</span>
                  <select>
                    <option>전체</option>
                    <option>활성</option>
                    <option>접속중</option>
                    <option>잠금</option>
                  </select>
                </label>
                <label>
                  <span>검색어</span>
                  <input type="text" value="김관리" />
                </label>
              </div>
            </section>

            <section class="dashboard-panel user-list-panel">
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

              <div class="user-table-wrap">
                <table class="user-table">
                  <thead>
                    <tr>
                      <th>사용자</th>
                      <th>역할(권한)</th>
                      <th>부서</th>
                      <th>계정 상태</th>
                      <th>최종 로그인</th>
                      <th>권한 관리</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="user in users" :key="user.id">
                      <td>
                        <strong>{{ user.name }}</strong>
                        <span>{{ user.id }}</span>
                      </td>
                      <td><span :class="['role-badge', user.role]">{{ user.role }}</span></td>
                      <td>{{ user.department }}</td>
                      <td><span :class="['account-status-badge', user.status]">{{ user.status }}</span></td>
                      <td>
                        <div class="last-login-cell">
                          <CalendarDays :size="15" />
                          <span>{{ user.lastLogin }}</span>
                        </div>
                      </td>
                      <td>
                        <button class="permission-button" type="button">
                          <KeyRound :size="16" />
                          <span>역할·권한</span>
                        </button>
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
  </main>
</template>
