<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { getLines } from '../api/lines.js'
import {
  Activity,
  BarChart3,
  Bell,
  CalendarDays,
  Factory,
  Gauge,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  TrendingUp,
  Users,
  Wrench,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '라인 상세', icon: BarChart3, href: '#/lines', active: true },
  { label: '설비 제어', icon: Wrench, href: '#/equipment' },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
]

const lines = ref([
  { name: '라인 1', oee: 91, equipment: 9, status: { run: 94, stop: 3, wait: 3, stopEnd: 97 }, balance: 88, stations: [90, 92, 88, 91, 86, 89], upmh: 420, uph: 60, productivity: 94, upmhPercent: 88, uphPercent: 85 },
  { name: '라인 2', oee: 84, equipment: 9, status: { run: 82, stop: 8, wait: 10, stopEnd: 90 }, balance: 81, stations: [78, 82, 86, 80, 76, 83], upmh: 395, uph: 60, productivity: 86, upmhPercent: 78, uphPercent: 80 },
  { name: '라인 3', oee: 88, equipment: 9, status: { run: 90, stop: 5, wait: 5, stopEnd: 95 }, balance: 86, stations: [88, 90, 85, 87, 84, 86], upmh: 402, uph: 60, productivity: 90, upmhPercent: 81, uphPercent: 82 },
])

let lineRefreshTimer = null

async function refreshLines() {
  try {
    const data = await getLines('FACTORY-01')
    if (data?.length) {
      lines.value = data.map(l => {
        const total = l.equipmentTotal || 1
        const runPct = Math.round(l.equipmentRunning / total * 100)
        const stopPct = Math.round(l.equipmentAlarm / total * 100)
        const waitPct = Math.max(0, 100 - runPct - stopPct)
        return {
          name: l.lineName,
          oee: l.latestOee != null ? Number(l.latestOee) : null,
          equipment: l.equipmentTotal,
          status: { run: runPct, stop: stopPct, wait: waitPct, stopEnd: runPct + stopPct },
          balance: 85,
          stations: [85, 87, 83, 86, 82, 84],
          upmh: 400, uph: 60, productivity: 88,
          upmhPercent: 82, uphPercent: 83,
        }
      })
    }
  } catch (e) {
    console.warn('[LineDetail] API 연결 실패, 데모 데이터 표시:', e.message)
  }
}

onMounted(() => {
  refreshLines()
  lineRefreshTimer = window.setInterval(refreshLines, 2000)
})

onUnmounted(() => {
  if (lineRefreshTimer) window.clearInterval(lineRefreshTimer)
})
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
        <p>라인별 OEE, 설비 상태 분포, 밸런싱/생산성 분석 전용 화면</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Line Analytics</p>
          <h1>라인별 상세보기</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <a class="ghost-button" href="#/layout">
            <MapPinned :size="16" />
            <span>레이아웃</span>
          </a>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="dashboard-panel line-oee-analysis-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line OEE</p>
            <h2>종합 설비 효율 라인</h2>
          </div>
          <Gauge :size="22" />
        </div>

        <div class="line-oee-donut-grid">
          <article v-for="line in lines" :key="line.name" class="line-oee-donut-card">
            <div class="line-analysis-donut" :style="{ '--value': `${line.oee}%` }">
              <strong>{{ line.oee }}%</strong>
            </div>
            <div>
              <h3>{{ line.name }}</h3>
              <p>{{ line.equipment }}대 설비 기준 종합 설비 효율</p>
            </div>
          </article>
        </div>
      </section>

      <section class="dashboard-panel line-status-analysis-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Line Equipment Status</p>
            <h2>라인에 해당하는 설비 상태 분포도</h2>
          </div>
          <Activity :size="22" />
        </div>

        <div class="line-status-donut-grid">
          <article v-for="line in lines" :key="line.name" class="line-status-card">
            <div
              class="line-status-donut"
              :style="{
                '--run-end': `${line.status.run}%`,
                '--stop-end': `${line.status.stopEnd}%`,
              }"
            >
              <strong>{{ line.equipment }}대</strong>
            </div>
            <div class="line-status-info">
              <h3>{{ line.name }}</h3>
              <div class="line-status-legend">
                <span class="run">가동 {{ line.status.run }}%</span>
                <span class="stop">정지 {{ line.status.stop }}%</span>
                <span class="wait">대기 {{ line.status.wait }}%</span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section class="line-analysis-grid">
        <article class="dashboard-panel line-balance-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Line Balancing</p>
              <h2>라인밸런싱 분석 차트</h2>
            </div>
            <TrendingUp :size="22" />
          </div>

          <div class="line-balance-chart-grid">
            <article v-for="line in lines" :key="line.name" class="line-balance-chart">
              <div class="line-chart-head">
                <strong>{{ line.name }}</strong>
                <span>{{ line.balance }}%</span>
              </div>
              <div class="line-station-bars">
                <i
                  v-for="(value, index) in line.stations"
                  :key="`${line.name}-${index}`"
                  :style="{ height: `${value}%` }"
                >
                  <b>{{ index + 1 }}</b>
                </i>
              </div>
            </article>
          </div>
        </article>

        <article class="dashboard-panel productivity-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">UPMH / UPH</p>
              <h2>라인별 생산성 분석 차트</h2>
            </div>
            <Factory :size="22" />
          </div>

          <div class="productivity-chart">
            <article v-for="line in lines" :key="line.name">
              <div class="productivity-label">
                <strong>{{ line.name }}</strong>
                <span>{{ line.productivity }}%</span>
              </div>
              <div class="productivity-bars">
                <div>
                  <i :style="{ width: `${line.upmhPercent}%` }"></i>
                  <span>UPMH {{ line.upmh.toLocaleString() }}</span>
                </div>
                <div>
                  <i :style="{ width: `${line.uphPercent}%` }"></i>
                  <span>UPH {{ line.uph.toLocaleString() }}</span>
                </div>
              </div>
            </article>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>
