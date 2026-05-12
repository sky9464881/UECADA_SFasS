<script setup>
import {
  Bell,
  CalendarDays,
  KeyRound,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Search,
  ShieldCheck,
  UserPlus,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '공장 레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 관리', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자 관리', icon: Users, href: '#/users', active: true },
  { label: '권한 설정', icon: ShieldCheck, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const roles = [
  {
    name: '관리자',
    count: 8,
    description: '작업자들과 사용자 권한을 관리하고 전체 시스템 설정을 변경할 수 있습니다.',
    permissions: ['사용자 권한 관리', '작업자 계정 관리', '전체 화면 조회', '게시판 작성'],
  },
  {
    name: '작업자',
    count: 56,
    description: '담당 라인과 설비 상태를 조회하고 알람 조치 내용을 등록할 수 있습니다.',
    permissions: ['담당 라인 조회', '설비 상태 조회', '알람 조치 등록', '커뮤니티 조회'],
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
        <p>권한 설정, 사용자 리스트, 최종 로그인 일시 관리</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">User Management</p>
          <h1>사용자 관리</h1>
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

      <section class="query-panel user-filter-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">User Search</p>
            <h2>사용자 리스트 조회</h2>
          </div>
          <button class="primary-action" type="button">
            <Search :size="17" />
            <span>조회</span>
          </button>
        </div>

        <div class="query-grid user-query-grid">
          <label>
            <span>권한</span>
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

      <section class="user-role-grid">
        <article v-for="role in roles" :key="role.name" class="dashboard-panel user-role-card">
          <div class="role-card-head">
            <div>
              <p class="panel-kicker">Role Setting</p>
              <h2>{{ role.name }}</h2>
            </div>
            <span>{{ role.count }}명</span>
          </div>
          <p>{{ role.description }}</p>
          <div class="role-permission-list">
            <span v-for="permission in role.permissions" :key="permission">
              <KeyRound :size="14" />
              {{ permission }}
            </span>
          </div>
        </article>
      </section>

      <section class="dashboard-panel user-list-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Account List</p>
            <h2>사용자 리스트</h2>
          </div>
          <span class="section-note">시스템 로그인 시 최종 로그인 일시 업데이트</span>
        </div>

        <div class="user-table-wrap">
          <table class="user-table">
            <thead>
              <tr>
                <th>사용자</th>
                <th>권한</th>
                <th>부서</th>
                <th>계정 상태</th>
                <th>최종 로그인 일시</th>
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
                    <ShieldCheck :size="16" />
                    <span>설정</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </section>
  </main>
</template>
