<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  Activity,
  CalendarDays,
  CheckCircle2,
  Factory,
  Gauge,
  LogOut,
  Wrench,
  X,
} from 'lucide-vue-next'
import { useAppNav } from '@/composables/useAppNav'
import { useLogout } from '@/composables/useLogout'

const { navItems } = useAppNav()
const logout = useLogout()

type WebScadaLink = {
  id: string
  label: string
  detail: string
  url: string
}

const SWMP_BASE_URL = 'http://192.168.0.100:11005/?Pro=myseo_260430'

const lineScadaLinks: readonly WebScadaLink[] = [
  { id: 'line-a', label: 'A라인', detail: 'LDV_A', url: `${SWMP_BASE_URL}#LDV_A` },
  { id: 'line-b', label: 'B라인', detail: 'LDV_B', url: `${SWMP_BASE_URL}#LDV_B` },
  { id: 'line-c', label: 'C라인', detail: 'LDV_C', url: `${SWMP_BASE_URL}#LDV_C` },
]

const equipmentDetailLinks: readonly WebScadaLink[] = [
  { id: 'equipment-cast', label: 'CAST', detail: 'ED_CAST', url: `${SWMP_BASE_URL}#ED_CAST` },
  { id: 'equipment-cnc', label: 'CNC', detail: 'ED_CNC', url: `${SWMP_BASE_URL}#ED_CNC` },
  { id: 'equipment-wash', label: 'WASH', detail: 'ED_WASH', url: `${SWMP_BASE_URL}#ED_WASH` },
  { id: 'equipment-assy', label: 'ASSY', detail: 'ED_ASSY', url: `${SWMP_BASE_URL}#ED_ASSY` },
  { id: 'equipment-test', label: 'TEST', detail: 'ED_TEST', url: `${SWMP_BASE_URL}#ED_TEST` },
]

const connectionMessage = ref('웹스카다 연결 준비 완료')
const activePopup = ref<WebScadaLink | null>(null)

const openSwmpPopup = (item: WebScadaLink) => {
  if (!item.url) {
    connectionMessage.value = '나중에 전달받은 SWMP URL을 등록하면 웹스카다 버튼으로 화면을 띄웁니다.'
    return
  }

  connectionMessage.value = `${item.label} 팝업 실행 중`
  activePopup.value = item
}

const closeSwmpPopup = () => {
  activePopup.value = null
  connectionMessage.value = '웹스카다 연결 준비 완료'
}

const testItems = [
  { label: '접속 방식', value: '팝업 표시', detail: '웹스카다 URL을 iframe 팝업으로 표시' },
  { label: '라인 화면', value: 'A/B/C', detail: '라인별 LDV 화면 연결 완료' },
  { label: '상세설비', value: '5종', detail: 'CAST/CNC/WASH/ASSY/TEST 연결 완료' },
]

const checklist = [
  'A/B/C 라인 URL 연결 완료',
  'CAST/CNC/WASH/ASSY/TEST URL 연결 완료',
  '라인/설비 팝업 버튼 구성',
  '팝업 화면 표시 확인',
]
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
        <span>연동 테스트</span>
        <strong>SWMP</strong>
        <p>웹스카다 화면 호출을 위한 테스트 진입 페이지</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div class="dashboard-header-titles">
          <p class="dashboard-kicker">SWMP Integration Test</p>
          <h1>SWMP 연동 테스트</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-12 12:40
          </span>
          <button type="button" class="icon-link" @click="logout">
            <LogOut :size="16" />
            <span>로그아웃</span>
          </button>
        </div>
      </header>

      <section class="swmp-test-grid">
        <article class="dashboard-panel swmp-launch-panel">
          <div class="swmp-launch-copy">
            <p class="panel-kicker">Web SCADA</p>
            <h2>웹스카다 실행 테스트</h2>
            <p>라인별 웹스카다와 설비별 상세 화면을 팝업으로 실행합니다.</p>
          </div>

          <div class="swmp-action-stack">
            <div class="swmp-action-group">
              <span class="swmp-action-label">라인</span>
              <div class="swmp-launch-actions">
                <button
                  v-for="item in lineScadaLinks"
                  :key="item.label"
                  class="web-scada-button"
                  type="button"
                  @click="openSwmpPopup(item)"
                >
                  <Factory :size="24" />
                  <span>{{ item.label }}</span>
                  <small>{{ item.detail }}</small>
                </button>
              </div>
            </div>

            <div class="swmp-action-group">
              <span class="swmp-action-label">상세설비</span>
              <div class="swmp-launch-actions swmp-launch-actions--compact">
                <button
                  v-for="item in equipmentDetailLinks"
                  :key="item.label"
                  class="web-scada-button equipment-detail-button"
                  type="button"
                  @click="openSwmpPopup(item)"
                >
                  <Wrench :size="22" />
                  <span>{{ item.label }}</span>
                  <small>{{ item.detail }}</small>
                </button>
              </div>
            </div>
          </div>

          <div class="swmp-status-banner">
            <Activity :size="18" />
            <strong>{{ connectionMessage }}</strong>
          </div>
        </article>

        <article class="dashboard-panel swmp-preview-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Preview Area</p>
              <h2>SWMP 화면 표시 영역</h2>
            </div>
            <Gauge :size="22" />
          </div>

          <div class="swmp-preview-box">
            <div>
              <Factory :size="38" />
              <strong>웹스카다 연결 완료</strong>
              <p>A/B/C 라인과 CAST/CNC/WASH/ASSY/TEST 상세 화면을 팝업으로 엽니다.</p>
            </div>
          </div>
        </article>

        <article class="dashboard-panel swmp-info-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Connection Info</p>
              <h2>연동 정보</h2>
            </div>
            <CheckCircle2 :size="22" />
          </div>

          <div class="swmp-info-cards">
            <article v-for="item in testItems" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
              <p>{{ item.detail }}</p>
            </article>
          </div>
        </article>

        <article class="dashboard-panel swmp-check-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Checklist</p>
              <h2>확인 항목</h2>
            </div>
            <Wrench :size="22" />
          </div>

          <div class="swmp-check-list">
            <article v-for="item in checklist" :key="item">
              <CheckCircle2 :size="18" />
              <span>{{ item }}</span>
            </article>
          </div>
        </article>
      </section>

      <div
        v-if="activePopup"
        class="swmp-modal-backdrop"
        @click.self="closeSwmpPopup"
      >
        <article class="swmp-modal" role="dialog" aria-modal="true" :aria-label="`${activePopup.label} 팝업`">
          <div class="swmp-modal-head">
            <div>
              <strong>{{ activePopup.label }}</strong>
              <span>{{ activePopup.detail }}</span>
            </div>
            <button class="swmp-modal-close" type="button" aria-label="팝업 닫기" @click="closeSwmpPopup">
              <X :size="18" />
            </button>
          </div>

          <iframe
            class="swmp-frame"
            :src="activePopup.url"
            :title="`SWMP ${activePopup.label}`"
          ></iframe>
        </article>
      </div>
    </section>
  </main>
</template>
