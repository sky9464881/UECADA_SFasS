<script setup>
import {
  Bell,
  CalendarDays,
  Factory,
  LayoutDashboard,
  LogOut,
  MapPinned,
  Megaphone,
  MessageSquare,
  Paperclip,
  Pin,
  Send,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '공장 레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 관리', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community', active: true },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const lineGroups = [
  { name: 'Line A 주조', manager: '박주조', members: ['OP-1042', 'OP-1038', 'OP-1024', 'OP-1011'], task: 'CAST-02 온도 알람 확인' },
  { name: 'Line B 가공', manager: '이가공', members: ['OP-1130', 'OP-1125', 'OP-1104', 'MT-014'], task: 'MACH-11 공구 교체 준비' },
  { name: 'Line C 조립', manager: '최조립', members: ['OP-1008', 'OP-1072', 'MT-021', 'OP-1099'], task: '압입하중 편차 원인 확인' },
  { name: 'Line D 검사', manager: '한검사', members: ['OP-1187', 'OP-1171', 'QC-022', 'QC-018'], task: '치수 편차 샘플 재측정' },
]

const notices = [
  { category: '공지', title: '금일 15:30 Line B 공구 교체 작업 안내', author: '김관리', target: 'Line B 가공', time: '12:20', pinned: true },
  { category: '작업지시', title: 'CAST-02 용탕온도 알람 조치 결과 등록 요청', author: '김관리', target: 'Line A 주조', time: '11:48', pinned: true },
  { category: '품질공지', title: '검사기 치수 편차 발생 시 샘플링 기준 공유', author: '박품질', target: 'Line D 검사', time: '10:35', pinned: false },
  { category: '인수인계', title: '야간조 Line C 압입하중 확인 포인트', author: '김관리', target: 'Line C 조립', time: '09:12', pinned: false },
]

const chatMessages = [
  { user: '김관리', role: '관리자', line: '전체', time: '12:35', message: 'CAST-02 알람 조치 상황 공유 부탁드립니다.', mine: false },
  { user: '박주조', role: '작업자', line: 'Line A', time: '12:36', message: '용탕온도 확인했고 센서값 재확인 중입니다.', mine: false },
  { user: '김관리', role: '관리자', line: '전체', time: '12:38', message: '13시 전까지 금형온도도 같이 확인해서 결과 남겨주세요.', mine: true },
  { user: '한검사', role: '작업자', line: 'Line D', time: '12:39', message: '검사 치수 편차 알람은 샘플 10개 추가 측정했습니다.', mine: false },
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
        <p>라인별 작업자 그룹, 관리자 게시판, 업무지시 채팅 관리</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Community</p>
          <h1>커뮤니티</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <button class="primary-action" type="button">
            <Megaphone :size="17" />
            <span>알림 작성</span>
          </button>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="community-main-grid">
        <article class="dashboard-panel line-group-management-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Line Group</p>
              <h2>라인별 그룹화</h2>
            </div>
            <Users :size="22" />
          </div>

          <div class="line-worker-group-list">
            <article v-for="group in lineGroups" :key="group.name">
              <div class="line-worker-head">
                <div>
                  <strong>{{ group.name }}</strong>
                  <span>담당 {{ group.manager }}</span>
                </div>
                <b>{{ group.members.length }}명</b>
              </div>
              <p>{{ group.task }}</p>
              <div class="worker-chip-list">
                <span v-for="member in group.members" :key="member">{{ member }}</span>
              </div>
            </article>
          </div>
        </article>

        <article class="dashboard-panel board-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Admin Board</p>
              <h2>게시판</h2>
            </div>
            <span class="section-note">관리자 작성</span>
          </div>

          <div class="notice-list simple">
            <article v-for="notice in notices" :key="notice.title">
              <div class="notice-icon">
                <Pin v-if="notice.pinned" :size="17" />
                <Megaphone v-else :size="17" />
              </div>
              <div>
                <div class="notice-meta">
                  <span>{{ notice.category }}</span>
                  <span>{{ notice.target }}</span>
                  <time>{{ notice.time }}</time>
                </div>
                <strong>{{ notice.title }}</strong>
                <p>{{ notice.author }}가 작업자들에게 전달</p>
              </div>
            </article>
          </div>
        </article>

        <article class="dashboard-panel chat-panel community-chat-wide">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Work Chat</p>
              <h2>채팅</h2>
            </div>
            <MessageSquare :size="22" />
          </div>

          <div class="chat-room-tabs">
            <button type="button" class="active">전체</button>
            <button type="button">Line A</button>
            <button type="button">Line B</button>
            <button type="button">Line C</button>
            <button type="button">Line D</button>
          </div>

          <div class="chat-message-list">
            <article v-for="message in chatMessages" :key="`${message.user}-${message.time}`" :class="{ mine: message.mine }">
              <div class="chat-avatar">{{ message.user.slice(0, 1) }}</div>
              <div class="chat-bubble">
                <div class="chat-meta">
                  <strong>{{ message.user }}</strong>
                  <span>{{ message.role }} · {{ message.line }} · {{ message.time }}</span>
                </div>
                <p>{{ message.message }}</p>
              </div>
            </article>
          </div>

          <div class="chat-input-row">
            <button type="button" title="첨부">
              <Paperclip :size="17" />
            </button>
            <input type="text" value="구체적인 업무지시를 입력하세요" />
            <button type="button" title="전송">
              <Send :size="17" />
            </button>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>
