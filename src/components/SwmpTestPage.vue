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

const defaultSwmp = import.meta.env.VITE_SWMP_DEFAULT_URL ?? ''
// SWMP 주소를 받으면 여기에 넣어 웹스카다 팝업으로 표시할 수 있습니다.
const swmpUrl = ref(defaultSwmp)
const connectionMessage = ref('SWMP URL 등록 완료')
const isSwmpPopupOpen = ref(false)

const openWebScada = () => {
  if (!swmpUrl.value) {
    connectionMessage.value = '나중에 전달받은 SWMP URL을 등록하면 웹스카다 버튼으로 화면을 띄웁니다.'
    return
  }

  connectionMessage.value = '웹스카다 팝업 실행 중'
  isSwmpPopupOpen.value = true
}

const closeWebScada = () => {
  isSwmpPopupOpen.value = false
  connectionMessage.value = 'SWMP URL 등록 완료'
}

const testItems = [
  { label: '접속 방식', value: '팝업 표시', detail: '등록된 URL을 팝업 iframe으로 표시' },
  { label: '연동 대상', value: 'SWMP', detail: '웹스카다 화면 호출 예정' },
  { label: '현재 상태', value: '등록 완료', detail: '웹스카다 버튼으로 SWMP 팝업 호출 가능' },
]

const checklist = [
  'SWMP URL 수신 완료',
  '웹스카다 버튼 연결 완료',
  '팝업 화면 표시 확인',
  '로그인/세션 전달 방식 확인',
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
            <p>전달받을 SWMP URL을 연결해서 웹스카다 화면을 띄우기 위한 테스트 페이지입니다.</p>
          </div>

          <button class="web-scada-button" type="button" @click="openWebScada">
            <Factory :size="24" />
            <span>웹스카다</span>
          </button>

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
              <strong>SWMP URL 등록 완료</strong>
              <p>웹스카다 버튼 클릭 시 등록된 SWMP 로그인 화면을 팝업으로 엽니다.</p>
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
        v-if="isSwmpPopupOpen"
        class="swmp-modal-backdrop"
        @click.self="closeWebScada"
      >
        <article class="swmp-modal" role="dialog" aria-modal="true" aria-label="웹스카다 팝업">
          <div class="swmp-modal-head">
            <button class="swmp-modal-close" type="button" aria-label="팝업 닫기" @click="closeWebScada">
              <X :size="18" />
            </button>
          </div>

          <iframe
            class="swmp-frame"
            :src="swmpUrl"
            title="SWMP 웹스카다"
          ></iframe>
        </article>
      </div>
    </section>
  </main>
</template>
