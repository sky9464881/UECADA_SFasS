<script setup>
import { computed } from 'vue'
import {
  Bell,
  CalendarDays,
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
  { label: '공장 레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 관리', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users', active: true },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const roles = [
  {
    name: '관리자',
    count: 8,
    description: '작업자와 계정·권한을 관리하고 전체 시스템 설정을 변경할 수 있습니다.',
    links: [
      { label: '사용자 권한 관리', icon: KeyRound },
      { label: '작업자 계정 관리', icon: Users },
      { label: '전체 화면 조회', icon: LayoutDashboard },
      { label: '게시판 작성', icon: MessageSquare },
    ],
  },
  {
    name: '작업자',
    count: 56,
    description: '담당 라인·설비 상태를 조회하고 알람 조치 내용을 등록할 수 있습니다.',
    links: [
      { label: '담당 라인 조회', icon: MapPinned },
      { label: '설비 상태 조회', icon: MonitorDot },
      { label: '알람 조치 등록', icon: Bell },
      { label: '커뮤니티 조회', icon: MessageSquare },
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

const userStats = computed(() => {
  const total = users.length
  const admin = users.filter((u) => u.role === '관리자').length
  const operator = users.filter((u) => u.role === '작업자').length
  const locked = users.filter((u) => u.status === '잠금').length
  return { total, admin, operator, locked }
})
</script>

<template>
  <main class="um-layout dashboard-shell">
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

    <section class="dashboard-main">
      <header class="dashboard-header user-perm-header">
        <div>
          <p class="dashboard-kicker">User &amp; Access Control</p>
          <h1>사용자·권한 관리</h1>
          <p class="user-page-subtitle">좌측에서 역할별 권한을 확인하고, 우측에서 계정을 조회·설정합니다.</p>
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

      <div class="user-perm-page-layout">
        <aside class="user-perm-sidebar" aria-label="역할 및 권한">
          <article class="dashboard-panel user-perm-intro">
            <p class="panel-kicker">Roles &amp; Permissions</p>
            <h2>역할·권한</h2>
            <p>
              아래 역할에 맞춰 우측 사용자에게 권한이 부여됩니다. 목록의 <strong>권한 관리</strong>에서 개별 조정할 수
              있습니다.
            </p>
          </article>

          <div class="user-role-stack">
            <article v-for="role in roles" :key="role.name" class="dashboard-panel user-role-card">
              <div class="role-card-head">
                <div>
                  <p class="panel-kicker">Role</p>
                  <h3>{{ role.name }}</h3>
                </div>
                <span>{{ role.count }}명</span>
              </div>
              <p class="um-role-desc">{{ role.description }}</p>
              <div class="um-role-link-grid" role="list">
                <button
                  v-for="link in role.links"
                  :key="link.label"
                  type="button"
                  class="um-role-link"
                  role="listitem"
                >
                  <component :is="link.icon" :size="16" :stroke-width="2" aria-hidden="true" />
                  <span>{{ link.label }}</span>
                </button>
              </div>
            </article>
          </div>
        </aside>

        <div class="user-perm-main">
          <div class="user-summary-grid user-perm-summary">
            <article class="user-summary-card good">
              <span>등록 사용자</span>
              <strong>{{ userStats.total }}</strong>
              <p>목록에 표시된 계정 수</p>
            </article>
            <article class="user-summary-card">
              <span>관리자</span>
              <strong>{{ userStats.admin }}</strong>
              <p>시스템·계정 관리 권한</p>
            </article>
            <article class="user-summary-card">
              <span>작업자</span>
              <strong>{{ userStats.operator }}</strong>
              <p>라인·설비 운영 권한</p>
            </article>
            <article class="user-summary-card warning">
              <span>잠금 계정</span>
              <strong>{{ userStats.locked }}</strong>
              <p>로그인 제한 상태</p>
            </article>
          </div>

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
              <span class="section-note">로그인 시 최종 접속 일시가 갱신됩니다.</span>
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
    </section>
  </main>
</template>

<style scoped>
/* 사용자·권한 화면: 참고 목업의 왼쪽(사이드바 + 역할 카드 열) 레이아웃만 보정 — 전역 style.css 미변경 */

.um-layout.dashboard-shell {
  grid-template-columns: 228px minmax(0, 1fr);
  background: #f1f5f9;
}

.um-layout .dashboard-sidebar {
  gap: 22px;
  padding: 22px 14px 20px;
  background: linear-gradient(180deg, #0c1e3d 0%, #06162e 55%, #051222 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.um-layout .dashboard-brand {
  gap: 11px;
}

.um-layout .brand-symbol {
  width: 40px;
  height: 40px;
  border-width: 2px;
  border-color: rgba(255, 255, 255, 0.85);
  color: #fff;
  font-size: 18px;
}

.um-layout .dashboard-nav a {
  min-height: 44px;
  padding: 0 12px 0 14px;
  border-radius: 10px;
  font-weight: 650;
  color: rgba(255, 255, 255, 0.7);
}

.um-layout .dashboard-nav a:hover {
  background: rgba(255, 255, 255, 0.07);
  color: #fff;
}

.um-layout .dashboard-nav a.active {
  background: rgba(56, 189, 248, 0.14);
  color: #fff;
  box-shadow: inset 4px 0 0 #38bdf8;
}

.um-layout .dashboard-nav a.active:hover {
  background: rgba(56, 189, 248, 0.18);
}

.um-layout .sidebar-status {
  margin-top: auto;
  padding: 15px 14px;
  border-radius: 12px;
  border-color: rgba(255, 255, 255, 0.1);
  background: rgba(12, 32, 58, 0.75);
}

.um-layout .user-perm-page-layout {
  grid-template-columns: minmax(252px, 296px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.um-layout .user-perm-sidebar {
  gap: 16px;
}

.um-layout .user-perm-intro,
.um-layout .user-role-stack .user-role-card {
  border: none;
  border-radius: 14px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 12px 28px rgba(15, 23, 42, 0.08);
  padding: 20px 18px;
}

.um-layout .user-perm-intro {
  padding-top: 22px;
  padding-bottom: 22px;
}

.um-layout .user-role-stack {
  gap: 16px;
}

.um-layout .role-card-head span {
  min-height: 30px;
  padding: 0 12px;
  background: #e0f2fe;
  color: #0369a1;
  font-weight: 800;
  font-size: 12px;
}

.um-layout .um-role-desc {
  margin: 14px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.65;
  font-weight: 600;
}

.um-role-link-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 10px;
  margin-top: 18px;
}

.um-role-link {
  margin: 0;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 0;
  border: none;
  background: transparent;
  color: #0284c7;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
}

.um-role-link:hover {
  color: #0369a1;
  text-decoration: underline;
}

.um-role-link:focus-visible {
  outline: 2px solid #38bdf8;
  outline-offset: 2px;
  border-radius: 4px;
}

.um-role-link :deep(svg) {
  flex-shrink: 0;
  margin-top: 1px;
  color: #0ea5e9;
}

@media (max-width: 1180px) {
  .um-layout.dashboard-shell {
    grid-template-columns: 1fr;
  }
}
</style>
