<script setup lang="ts">
import {
  Activity,
  CalendarDays,
  Factory,
  Gauge,
  LogOut,
  MapPinned,
  TrendingUp,
} from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'
import { useLineDetails } from '@/composables/useLineDetails'

const { navItems } = useAppNav('line')
const logout = useLogout()
const { lines } = useLineDetails()
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
        <p>라인별 OEE, 설비 상태 분포, 밸런싱/생산성 분석 전용 화면</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles">
          <p class="dashboard-kicker">Line Analytics</p>
          <h1>라인별 상세보기</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <RouterLink class="ghost-button" :to="{ name: 'layout' }">
            <MapPinned :size="16" />
            <span>레이아웃</span>
          </RouterLink>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
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
