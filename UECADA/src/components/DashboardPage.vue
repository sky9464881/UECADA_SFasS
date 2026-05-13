<script setup>
import {
  Activity,
  AlertTriangle,
  Bell,
  CalendarDays,
  Gauge,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  ShieldCheck,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard', active: true },
  { label: '공장 레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 관리', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자 관리', icon: Users, href: '#/users' },
  { label: '권한 설정', icon: ShieldCheck, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const lineOee = [
  { name: '전체 설비', value: 86.4, detail: '가용률 91.8 · 성능 94.1 · 품질 99.7', size: 'large' },
  { name: 'Line A 주조', value: 91, detail: '24대 기준', size: 'small' },
  { name: 'Line B 가공', value: 84, detail: '38대 기준', size: 'small' },
  { name: 'Line C 조립', value: 88, detail: '42대 기준', size: 'small' },
  { name: 'Line D 검사', value: 79, detail: '24대 기준', size: 'small' },
]

const statusDistribution = [
  { label: '가동', count: 91, percent: 71, tone: 'run' },
  { label: '정지', count: 12, percent: 9, tone: 'stop' },
  { label: '대기', count: 25, percent: 20, tone: 'wait' },
]

const alarmTabs = [
  { label: '전체', count: 4, active: true },
  { label: '긴급', count: 1 },
  { label: '경고', count: 2 },
  { label: '정보', count: 1 },
]

const recentAlarms = [
  { level: '긴급', equipment: 'CAST-02', line: 'Line A 주조', message: '용탕온도 상한 초과', time: '12:38', state: '조치중' },
  { level: '경고', equipment: 'ASM-05', line: 'Line C 조립', message: '압입하중 편차 발생', time: '12:21', state: '확인필요' },
  { level: '경고', equipment: 'INSP-02', line: 'Line D 검사', message: '현재 물체 치수 허용범위 이탈', time: '11:54', state: '미조치' },
  { level: '정보', equipment: 'MACH-11', line: 'Line B 가공', message: '공구사용시간 교체 기준 80% 도달', time: '11:18', state: '예정' },
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
        <p>메인 대시보드는 핵심 효율, 설비 상태, 최근 알람만 요약 표시</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Integrated Facility Control</p>
          <h1>메인 대시보드</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <a class="ghost-button" href="#/alarms">
            <Bell :size="16" />
            <span>최근 알람 4</span>
          </a>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="dashboard-home-grid">
        <article class="dashboard-panel home-oee-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Overall Equipment Effectiveness</p>
              <h2>종합 설비 효율</h2>
            </div>
            <Gauge :size="22" />
          </div>

          <div class="home-oee-content">
            <div class="home-oee-primary">
              <div class="home-donut home-donut-large" style="--value: 86.4">
                <strong>86.4%</strong>
                <span>전체 설비</span>
              </div>
              <p>전체 설비 기준과 라인별 설비 기준 OEE를 원 그래프로 표시합니다.</p>
            </div>

            <div class="home-line-oee-grid">
              <article
                v-for="line in lineOee.slice(1)"
                :key="line.name"
                class="home-line-oee"
              >
                <div class="home-donut" :style="{ '--value': line.value }">
                  <strong>{{ line.value }}%</strong>
                </div>
                <div>
                  <b>{{ line.name }}</b>
                  <span>{{ line.detail }}</span>
                </div>
              </article>
            </div>
          </div>
        </article>

        <article class="dashboard-panel home-status-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Equipment Status</p>
              <h2>설비 상태 분포</h2>
            </div>
            <Activity :size="22" />
          </div>

          <div class="status-donut-wrap">
            <div class="status-donut">
              <strong>128대</strong>
              <span>전체 설비</span>
            </div>
          </div>

          <div class="status-distribution-list">
            <article v-for="item in statusDistribution" :key="item.label">
              <span :class="['status-dot', item.tone]"></span>
              <div>
                <strong>{{ item.label }}</strong>
                <p>{{ item.count }}대</p>
              </div>
              <b>{{ item.percent }}%</b>
            </article>
          </div>
        </article>

        <article class="dashboard-panel home-alarm-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Recent Alarm</p>
              <h2>최근 알람 정보</h2>
            </div>
            <AlertTriangle :size="22" />
          </div>

          <div class="recent-alarm-tabs" role="tablist" aria-label="최근 알람 필터">
            <button
              v-for="tab in alarmTabs"
              :key="tab.label"
              :class="{ active: tab.active }"
              type="button"
            >
              <span>{{ tab.label }}</span>
              <b>{{ tab.count }}</b>
            </button>
          </div>

          <div class="recent-alarm-list">
            <article v-for="alarm in recentAlarms" :key="`${alarm.equipment}-${alarm.time}`">
              <span :class="['alarm-level', alarm.level]">{{ alarm.level }}</span>
              <div>
                <strong>{{ alarm.equipment }} · {{ alarm.line }}</strong>
                <p>{{ alarm.message }}</p>
              </div>
              <div class="recent-alarm-meta">
                <time>{{ alarm.time }}</time>
                <b>{{ alarm.state }}</b>
              </div>
            </article>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>
