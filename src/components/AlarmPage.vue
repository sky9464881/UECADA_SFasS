<script setup>
import {
  AlertTriangle,
  BarChart3,
  Bell,
  CalendarDays,
  Factory,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '공장 레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 관리', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms', active: true },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const alarmSummary = [
  { label: '전체 알람 수', value: 47, detail: '금일 00:00 ~ 12:40', tone: 'info' },
  { label: '긴급 알람', value: 2, detail: '즉시 조치 필요', tone: 'critical' },
  { label: '처리 완료', value: 38, detail: '완료율 80.8%', tone: 'done' },
  { label: '미처리 알람', value: 7, detail: '담당자 확인 필요', tone: 'pending' },
]

const trendHours = [
  { time: '06', count: 5, percent: 26 },
  { time: '07', count: 8, percent: 42 },
  { time: '08', count: 12, percent: 63 },
  { time: '09', count: 16, percent: 84 },
  { time: '10', count: 13, percent: 68 },
  { time: '11', count: 19, percent: 100 },
  { time: '12', count: 15, percent: 79 },
  { time: '13', count: 9, percent: 47 },
]

const alarmRows = [
  { time: '12:38:22', equipment: 'CAST-02', type: '긴급', category: '온도', message: '용탕온도 상한 초과', status: '조치중' },
  { time: '12:21:05', equipment: 'ASM-05', type: '경고', category: '하중', message: '압입하중 편차 발생', status: '미처리' },
  { time: '11:54:44', equipment: 'INSP-02', type: '경고', category: '치수', message: '현재 물체 치수 허용범위 이탈', status: '미처리' },
  { time: '11:18:12', equipment: 'MACH-11', type: '정보', category: '공구', message: '공구사용시간 교체 기준 80% 도달', status: '처리완료' },
  { time: '10:42:39', equipment: 'WASH-03', type: '점검', category: '농도', message: '세척농도 보정 작업 등록', status: '처리완료' },
]

const equipmentFrequency = [
  { label: 'CAST-02', count: 9, percent: 100, type: '주조기' },
  { label: 'INSP-02', count: 7, percent: 78, type: '검사기' },
  { label: 'ASM-05', count: 6, percent: 67, type: '조립기' },
  { label: 'MACH-11', count: 4, percent: 44, type: '가공기' },
  { label: 'WASH-03', count: 3, percent: 33, type: '세척기' },
]

const typeFrequency = [
  { label: '온도', count: 14, percent: 100 },
  { label: '치수', count: 11, percent: 79 },
  { label: '하중', count: 8, percent: 57 },
  { label: '진동', count: 7, percent: 50 },
  { label: '공구', count: 5, percent: 36 },
  { label: '농도', count: 2, percent: 14 },
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
        <p>알림 현황, 발생 이력 및 추이, 조건별 빈도 분석</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Alarm Analytics</p>
          <h1>알람 화면</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <a class="ghost-button" href="#/equipment">
            <Wrench :size="16" />
            <span>설비별 화면</span>
          </a>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="alarm-summary-grid" aria-label="알림 현황요약">
        <article v-for="item in alarmSummary" :key="item.label" :class="['alarm-summary-card', item.tone]">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <p>{{ item.detail }}</p>
        </article>
      </section>

      <section class="alarm-trend-history-grid">
        <article class="dashboard-panel alarm-trend-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Alarm Trend</p>
              <h2>알람 발생 추이</h2>
            </div>
            <BarChart3 :size="22" />
          </div>

          <div class="alarm-trend-chart">
            <div v-for="item in trendHours" :key="item.time" class="alarm-trend-column">
              <i :style="{ height: `${item.percent}%` }"></i>
              <b>{{ item.count }}건</b>
              <span>{{ item.time }}시</span>
            </div>
          </div>
        </article>

        <article class="dashboard-panel alarm-history-panel-wide">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Alarm History</p>
              <h2>알람 발생 이력</h2>
            </div>
            <AlertTriangle :size="22" />
          </div>

          <div class="alarm-history-table-wrap">
            <table class="alarm-history-table">
              <thead>
                <tr>
                  <th>발생 시간</th>
                  <th>설비명</th>
                  <th>알람 유형</th>
                  <th>분류</th>
                  <th>내용</th>
                  <th>상태</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in alarmRows" :key="`${row.time}-${row.equipment}`">
                  <td>{{ row.time }}</td>
                  <td><strong>{{ row.equipment }}</strong></td>
                  <td><span :class="['alarm-level', row.type]">{{ row.type }}</span></td>
                  <td>{{ row.category }}</td>
                  <td>{{ row.message }}</td>
                  <td><span :class="['alarm-status-badge', row.status]">{{ row.status }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <section class="alarm-frequency-grid">
        <article class="dashboard-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Equipment Frequency</p>
              <h2>설비별 알람 발생 횟수</h2>
            </div>
            <Factory :size="22" />
          </div>

          <div class="frequency-bar-list">
            <article v-for="item in equipmentFrequency" :key="item.label">
              <div>
                <strong>{{ item.label }}</strong>
                <span>{{ item.type }}</span>
              </div>
              <div class="frequency-meter">
                <i :style="{ width: `${item.percent}%` }"></i>
              </div>
              <b>{{ item.count }}건</b>
            </article>
          </div>
        </article>

        <article class="dashboard-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Type Frequency</p>
              <h2>알람 유형별 발생 횟수</h2>
            </div>
            <Bell :size="22" />
          </div>

          <div class="frequency-bar-list type-frequency">
            <article v-for="item in typeFrequency" :key="item.label">
              <div>
                <strong>{{ item.label }}</strong>
                <span>알람 유형</span>
              </div>
              <div class="frequency-meter">
                <i :style="{ width: `${item.percent}%` }"></i>
              </div>
              <b>{{ item.count }}건</b>
            </article>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>
