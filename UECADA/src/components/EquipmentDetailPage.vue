<script setup>
import { computed, ref } from 'vue'
import {
  Activity,
  AlertTriangle,
  Bell,
  CalendarDays,
  Factory,
  Gauge,
  LayoutDashboard,
  LogOut,
  MapPinned,
  MessageSquare,
  Play,
  Square,
  Users,
  Wrench,
  X,
} from 'lucide-vue-next'

const navItems = [
  { label: '대시보드', icon: LayoutDashboard, href: '#/dashboard' },
  { label: '레이아웃', icon: MapPinned, href: '#/layout' },
  { label: '설비 제어', icon: Wrench, href: '#/equipment', active: true },
  { label: '알람 및 이력', icon: Bell, href: '#/alarms' },
  { label: '사용자·권한', icon: Users, href: '#/users' },
  { label: '커뮤니티', icon: MessageSquare, href: '#/community' },
  { label: 'SWMP 테스트', icon: Wrench, href: '#/swmp-test' },
]

const categories = [
  {
    id: 'casting',
    name: '주조기',
    status: '경고',
    count: 22,
    running: 20,
    stopped: 1,
    waiting: 1,
    avgRate: 94,
    defectCount: 18,
    description: '압력, 용탕온도, 금형온도 중심 모니터링',
    equipment: [
      {
        id: 'CAST-02',
        name: '주조기 2호',
        line: 'Line A 주조',
        state: '운전',
        rate: 94,
        defects: 18,
        operator: 'OP-1042',
        cycle: '42.1s',
        common: [
          { label: '운전 상태', value: '운전' },
          { label: '작업자 코드', value: 'OP-1042' },
          { label: '싸이클 타임', value: '42.1s' },
          { label: 'OK', value: '12,840' },
          { label: 'NG', value: '18' },
          { label: '가동률', value: '94%' },
          { label: '전류', value: '42A' },
          { label: '전압', value: '380V' },
          { label: '온도', value: '68℃' },
          { label: '습도', value: '42%' },
          { label: '진동', value: '1.2mm/s' },
        ],
        specific: [
          { label: '압력', value: '18.2bar', status: '정상 16~19bar' },
          { label: '용탕온도', value: '681℃', status: '상한 680℃ 초과' },
          { label: '금형온도', value: '216℃', status: '정상 190~220℃' },
        ],
      },
      {
        id: 'CAST-04',
        name: '주조기 4호',
        line: 'Line A 주조',
        state: '운전',
        rate: 96,
        defects: 7,
        operator: 'OP-1038',
        cycle: '40.7s',
        common: [
          { label: '운전 상태', value: '운전' },
          { label: '작업자 코드', value: 'OP-1038' },
          { label: '싸이클 타임', value: '40.7s' },
          { label: 'OK', value: '12,120' },
          { label: 'NG', value: '7' },
          { label: '가동률', value: '96%' },
          { label: '전류', value: '39A' },
          { label: '전압', value: '380V' },
          { label: '온도', value: '65℃' },
          { label: '습도', value: '41%' },
          { label: '진동', value: '0.9mm/s' },
        ],
        specific: [
          { label: '압력', value: '17.6bar', status: '정상' },
          { label: '용탕온도', value: '665℃', status: '정상' },
          { label: '금형온도', value: '208℃', status: '정상' },
        ],
      },
    ],
  },
  {
    id: 'machining',
    name: '가공기',
    status: '정상',
    count: 38,
    running: 35,
    stopped: 1,
    waiting: 2,
    avgRate: 92,
    defectCount: 9,
    description: '스핀들속도, 스핀들 진동, 공구사용시간 중심 모니터링',
    equipment: [
      {
        id: 'MACH-07',
        name: '가공기 7호',
        line: 'Line B 가공',
        state: '운전',
        rate: 92,
        defects: 9,
        operator: 'OP-1130',
        cycle: '36.8s',
        common: [
          { label: '운전 상태', value: '운전' },
          { label: '작업자 코드', value: 'OP-1130' },
          { label: '싸이클 타임', value: '36.8s' },
          { label: 'OK', value: '10,220' },
          { label: 'NG', value: '9' },
          { label: '가동률', value: '92%' },
          { label: '전류', value: '38A' },
          { label: '전압', value: '380V' },
          { label: '온도', value: '54℃' },
          { label: '습도', value: '39%' },
          { label: '진동', value: '1.8mm/s' },
        ],
        specific: [
          { label: '스핀들 속도', value: '7,200rpm', status: '정상' },
          { label: '절삭속도', value: '180m/min', status: '목표 범위' },
          { label: '공구사용시간', value: '68h', status: '교체 기준 80h' },
          { label: '진동', value: '1.8mm/s', status: '정상 2.0 이하' },
        ],
      },
    ],
  },
  {
    id: 'washing',
    name: '세척기',
    status: '정상',
    count: 16,
    running: 14,
    stopped: 0,
    waiting: 2,
    avgRate: 89,
    defectCount: 6,
    description: '세척수 온도, 압력, 농도, 건조온도 중심 모니터링',
    equipment: [
      {
        id: 'WASH-03',
        name: '세척기 3호',
        line: 'Line C 조립',
        state: '운전',
        rate: 89,
        defects: 6,
        operator: 'OP-0921',
        cycle: '58.4s',
        common: [
          { label: '운전 상태', value: '운전' },
          { label: '작업자 코드', value: 'OP-0921' },
          { label: '싸이클 타임', value: '58.4s' },
          { label: 'OK', value: '8,911' },
          { label: 'NG', value: '6' },
          { label: '가동률', value: '89%' },
          { label: '전류', value: '31A' },
          { label: '전압', value: '220V' },
          { label: '온도', value: '49℃' },
          { label: '습도', value: '47%' },
          { label: '진동', value: '0.7mm/s' },
        ],
        specific: [
          { label: '세척수 온도', value: '62℃', status: '정상' },
          { label: '세척입력', value: '3.8bar', status: '정상' },
          { label: '세척농도', value: '4.2%', status: '목표 4.0~4.5%' },
          { label: '건조온도', value: '84℃', status: '정상' },
        ],
      },
    ],
  },
  {
    id: 'assembly',
    name: '조립기',
    status: '이상',
    count: 31,
    running: 26,
    stopped: 3,
    waiting: 2,
    avgRate: 76,
    defectCount: 22,
    description: '체결토크, 체결각도, 압입하중 중심 모니터링',
    equipment: [
      {
        id: 'ASM-05',
        name: '조립기 5호',
        line: 'Line C 조립',
        state: '정지',
        rate: 76,
        defects: 22,
        operator: 'OP-1008',
        cycle: '44.5s',
        common: [
          { label: '운전 상태', value: '정지' },
          { label: '작업자 코드', value: 'OP-1008' },
          { label: '싸이클 타임', value: '44.5s' },
          { label: 'OK', value: '9,144' },
          { label: 'NG', value: '22' },
          { label: '가동률', value: '76%' },
          { label: '전류', value: '27A' },
          { label: '전압', value: '220V' },
          { label: '온도', value: '45℃' },
          { label: '습도', value: '41%' },
          { label: '진동', value: '1.5mm/s' },
        ],
        specific: [
          { label: '체결토크', value: '42Nm', status: '편차 확인' },
          { label: '체결각도', value: '118deg', status: '정상' },
          { label: '압입하중', value: '5.6kN', status: '상한 접근' },
        ],
      },
    ],
  },
  {
    id: 'inspection',
    name: '검사기',
    status: '경고',
    count: 21,
    running: 18,
    stopped: 1,
    waiting: 2,
    avgRate: 82,
    defectCount: 31,
    description: '목표 물체 치수와 현재 물체 치수 중심 모니터링',
    equipment: [
      {
        id: 'INSP-02',
        name: '검사기 2호',
        line: 'Line D 검사',
        state: '운전',
        rate: 82,
        defects: 31,
        operator: 'OP-1187',
        cycle: '31.2s',
        common: [
          { label: '운전 상태', value: '운전' },
          { label: '작업자 코드', value: 'OP-1187' },
          { label: '싸이클 타임', value: '31.2s' },
          { label: 'OK', value: '13,012' },
          { label: 'NG', value: '31' },
          { label: '가동률', value: '82%' },
          { label: '전류', value: '18A' },
          { label: '전압', value: '220V' },
          { label: '온도', value: '39℃' },
          { label: '습도', value: '44%' },
          { label: '진동', value: '0.4mm/s' },
        ],
        specific: [
          { label: '목표 물체 치수', value: '24.00mm', status: '기준값' },
          { label: '현재 물체 치수', value: '24.18mm', status: '허용범위 이탈' },
        ],
      },
    ],
  },
]

const selectedCategoryId = ref('casting')
const selectedEquipmentId = ref('CAST-02')
const isEquipmentPopupOpen = ref(false)

const selectedCategory = computed(() =>
  categories.find((category) => category.id === selectedCategoryId.value),
)

const selectedEquipment = computed(() =>
  selectedCategory.value.equipment.find((equipment) => equipment.id === selectedEquipmentId.value),
)

const selectCategory = (category) => {
  selectedCategoryId.value = category.id
  selectedEquipmentId.value = category.equipment[0].id
  isEquipmentPopupOpen.value = false
}

const openEquipmentPopup = () => {
  isEquipmentPopupOpen.value = true
}

const closeEquipmentPopup = () => {
  isEquipmentPopupOpen.value = false
}

const metricNumber = (value) => {
  const match = String(value).replace(/,/g, '').match(/\d+(\.\d+)?/)
  return match ? Number(match[0]) : 0
}

const getCommonMetric = (label) =>
  selectedEquipment.value.common.find((metric) => metric.label === label)?.value ?? '-'

const clampPercent = (value) => Math.max(0, Math.min(100, Math.round(value)))

const qualityPercent = computed(() => {
  const ok = metricNumber(getCommonMetric('OK'))
  const ng = metricNumber(getCommonMetric('NG'))
  const total = ok + ng

  return total ? clampPercent((ok / total) * 100) : 0
})

const operationTrend = computed(() => {
  const base = selectedEquipment.value.rate
  return [
    { label: '08시', value: clampPercent(base - 7) },
    { label: '09시', value: clampPercent(base - 3) },
    { label: '10시', value: clampPercent(base + 1) },
    { label: '11시', value: clampPercent(base - 1) },
    { label: '12시', value: clampPercent(base) },
  ]
})

const sensorChart = computed(() => [
  { label: '가동률', value: `${selectedEquipment.value.rate}%`, percent: selectedEquipment.value.rate },
  { label: '전류', value: getCommonMetric('전류'), percent: clampPercent((metricNumber(getCommonMetric('전류')) / 50) * 100) },
  { label: '온도', value: getCommonMetric('온도'), percent: clampPercent((metricNumber(getCommonMetric('온도')) / 80) * 100) },
  { label: '습도', value: getCommonMetric('습도'), percent: clampPercent(metricNumber(getCommonMetric('습도'))) },
  { label: '진동', value: getCommonMetric('진동'), percent: clampPercent((metricNumber(getCommonMetric('진동')) / 3) * 100) },
])

const specificMetricPercent = (metric) => {
  const value = metricNumber(metric.value)
  const label = metric.label

  if (label.includes('압력')) return clampPercent((value / 20) * 100)
  if (label.includes('용탕')) return clampPercent((value / 700) * 100)
  if (label.includes('금형')) return clampPercent((value / 250) * 100)
  if (label.includes('스핀들')) return clampPercent((value / 8000) * 100)
  if (label.includes('절삭')) return clampPercent((value / 220) * 100)
  if (label.includes('공구')) return clampPercent((value / 80) * 100)
  if (label.includes('진동')) return clampPercent((value / 3) * 100)
  if (label.includes('세척수')) return clampPercent((value / 80) * 100)
  if (label.includes('세척입력')) return clampPercent((value / 5) * 100)
  if (label.includes('세척농도')) return clampPercent((value / 5) * 100)
  if (label.includes('건조')) return clampPercent((value / 100) * 100)
  if (label.includes('토크')) return clampPercent((value / 60) * 100)
  if (label.includes('각도')) return clampPercent((value / 180) * 100)
  if (label.includes('하중')) return clampPercent((value / 8) * 100)
  if (label.includes('치수')) return clampPercent((value / 30) * 100)

  return clampPercent(value)
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
        <p>설비 카테고리별 주요 데이터와 특정 설비 상세 정보 확인</p>
      </div>
    </aside>

    <section class="dashboard-main">
      <header class="dashboard-header">
        <div>
          <p class="dashboard-kicker">Equipment Monitoring</p>
          <h1>설비별 화면</h1>
        </div>
        <div class="header-actions">
          <span class="current-time">
            <CalendarDays :size="16" />
            2026-05-11 12:40
          </span>
          <a class="ghost-button" href="#/layout">
            <MapPinned :size="16" />
            <span>위치 보기</span>
          </a>
          <a class="icon-link" href="#/login">
            <LogOut :size="16" />
            <span>로그인 화면</span>
          </a>
        </div>
      </header>

      <section class="dashboard-panel equipment-category-panel">
        <div class="section-title-row">
          <div>
            <p class="panel-kicker">Equipment Category</p>
            <h2>설비 카테고리 선택</h2>
          </div>
          <Factory :size="22" />
        </div>

        <div class="equipment-category-grid">
          <button
            v-for="category in categories"
            :key="category.id"
            :class="{ active: category.id === selectedCategoryId }"
            type="button"
            @click="selectCategory(category)"
          >
            <Factory :size="20" />
            <strong>{{ category.name }}</strong>
            <span>{{ category.count }}대 · {{ category.status }}</span>
            <p>{{ category.description }}</p>
          </button>
        </div>
      </section>

      <section class="equipment-monitor-grid">
        <article class="dashboard-panel category-monitor-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Category Summary</p>
              <h2>{{ selectedCategory.name }} 주요 데이터 모니터링</h2>
            </div>
            <Gauge :size="22" />
          </div>

          <div class="category-summary-cards">
            <article>
              <span>운전 상태</span>
              <strong>{{ selectedCategory.status }}</strong>
              <p>가동 {{ selectedCategory.running }} · 정지 {{ selectedCategory.stopped }} · 대기 {{ selectedCategory.waiting }}</p>
            </article>
            <article>
              <span>평균 가동률</span>
              <strong>{{ selectedCategory.avgRate }}%</strong>
              <p>{{ selectedCategory.count }}대 설비 기준</p>
            </article>
            <article>
              <span>불량수량</span>
              <strong>{{ selectedCategory.defectCount }}</strong>
              <p>금일 누적 NG 수량</p>
            </article>
          </div>

          <div class="category-status-bar">
            <i class="run" :style="{ width: `${(selectedCategory.running / selectedCategory.count) * 100}%` }"></i>
            <i class="stop" :style="{ width: `${(selectedCategory.stopped / selectedCategory.count) * 100}%` }"></i>
            <i class="wait" :style="{ width: `${(selectedCategory.waiting / selectedCategory.count) * 100}%` }"></i>
          </div>
        </article>

        <aside class="dashboard-panel category-equipment-panel">
          <div class="section-title-row">
            <div>
              <p class="panel-kicker">Equipment Select</p>
              <h2>특정 설비 선택</h2>
            </div>
            <Activity :size="22" />
          </div>

          <div class="category-equipment-list">
            <button
              v-for="equipment in selectedCategory.equipment"
              :key="equipment.id"
              :class="{ active: equipment.id === selectedEquipmentId }"
              type="button"
              @click="selectedEquipmentId = equipment.id"
            >
              <span :class="['equipment-state-dot', equipment.state === '정지' ? 'stop' : equipment.state === '대기' ? 'warn' : 'run']"></span>
              <div>
                <strong>{{ equipment.id }}</strong>
                <p>{{ equipment.name }} · {{ equipment.line }}</p>
              </div>
              <b>{{ equipment.rate }}%</b>
            </button>
          </div>
        </aside>
      </section>

      <section class="dashboard-panel selected-equipment-panel">
        <div class="selected-equipment-head">
          <div>
            <p class="panel-kicker">Selected Equipment Detail</p>
            <h2>{{ selectedEquipment.id }} · {{ selectedEquipment.name }}</h2>
          </div>
          <div class="equipment-detail-actions">
            <button class="equipment-detail-open-button" type="button" @click="openEquipmentPopup">
              <Factory :size="16" />
              <span>설비 상세보기</span>
            </button>
            <span :class="['state-badge', selectedEquipment.state]">{{ selectedEquipment.state }}</span>
          </div>
        </div>

        <div class="selected-equipment-summary">
          <article>
            <span>라인</span>
            <strong>{{ selectedEquipment.line }}</strong>
          </article>
          <article>
            <span>가동률</span>
            <strong>{{ selectedEquipment.rate }}%</strong>
          </article>
          <article>
            <span>불량수량</span>
            <strong>{{ selectedEquipment.defects }}</strong>
          </article>
          <article>
            <span>싸이클 타임</span>
            <strong>{{ selectedEquipment.cycle }}</strong>
          </article>
        </div>

        <div class="equipment-detail-grid">
          <article class="equipment-detail-card">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Common Data</p>
                <h2>공통 운전 정보</h2>
              </div>
              <Activity :size="22" />
            </div>

            <div class="common-metric-grid compact">
              <article v-for="metric in selectedEquipment.common" :key="metric.label">
                <span>{{ metric.label }}</span>
                <strong>{{ metric.value }}</strong>
              </article>
            </div>
          </article>

          <article class="equipment-detail-card">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Category Specific Data</p>
                <h2>{{ selectedCategory.name }} 상세 데이터</h2>
              </div>
              <AlertTriangle :size="22" />
            </div>

            <div class="type-metric-grid category-specific-grid">
              <article v-for="metric in selectedEquipment.specific" :key="metric.label">
                <span>{{ metric.label }}</span>
                <strong>{{ metric.value }}</strong>
                <p>{{ metric.status }}</p>
              </article>
            </div>
          </article>
        </div>
      </section>

      <div
        v-if="selectedEquipment && isEquipmentPopupOpen"
        class="equipment-modal-backdrop"
        @click.self="closeEquipmentPopup"
      >
        <article
          class="equipment-detail-modal"
          role="dialog"
          aria-modal="true"
          :aria-label="`${selectedEquipment.name} 상세보기`"
        >
          <div class="equipment-popup-head">
            <div>
              <p class="panel-kicker">Equipment Detail</p>
              <h2>{{ selectedEquipment.id }} · {{ selectedEquipment.name }}</h2>
              <p>{{ selectedEquipment.line }} · {{ selectedCategory.name }} · 작업자 {{ selectedEquipment.operator }}</p>
            </div>
            <div class="equipment-popup-head-actions">
              <span :class="['state-badge', selectedEquipment.state]">{{ selectedEquipment.state }}</span>
              <button class="equipment-modal-close" type="button" aria-label="팝업 닫기" @click="closeEquipmentPopup">
                <X :size="18" />
              </button>
            </div>
          </div>

          <div class="equipment-popup-summary">
            <article>
              <span>운전 상태</span>
              <strong>{{ selectedEquipment.state }}</strong>
            </article>
            <article>
              <span>가동률</span>
              <strong>{{ selectedEquipment.rate }}%</strong>
            </article>
            <article>
              <span>싸이클 타임</span>
              <strong>{{ selectedEquipment.cycle }}</strong>
            </article>
            <article>
              <span>불량수량</span>
              <strong>{{ selectedEquipment.defects }}</strong>
            </article>
          </div>

          <section class="equipment-popup-command-panel">
            <div>
              <p class="panel-kicker">Control Command</p>
              <h3>제어명령</h3>
            </div>
            <div class="equipment-popup-command-row">
              <button :class="{ active: selectedEquipment.state === '정지' }" type="button">
                <Square :size="16" />
                <span>정지</span>
              </button>
              <button :class="{ active: selectedEquipment.state !== '정지' }" type="button">
                <Play :size="16" />
                <span>운전</span>
              </button>
            </div>
          </section>

          <div class="equipment-popup-data-grid">
            <section class="equipment-popup-section">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Common Data</p>
                  <h3>공통 상세내역</h3>
                </div>
                <Activity :size="22" />
              </div>
              <div class="equipment-popup-metric-grid">
                <article v-for="metric in selectedEquipment.common" :key="`popup-common-${metric.label}`">
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                </article>
              </div>
            </section>

            <section class="equipment-popup-section">
              <div class="section-title-row">
                <div>
                  <p class="panel-kicker">Type Data</p>
                  <h3>{{ selectedCategory.name }} 상세내역</h3>
                </div>
                <AlertTriangle :size="22" />
              </div>
              <div class="equipment-popup-specific-grid">
                <article v-for="metric in selectedEquipment.specific" :key="`popup-specific-${metric.label}`">
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                  <p>{{ metric.status }}</p>
                </article>
              </div>
            </section>
          </div>

          <section class="equipment-popup-section equipment-popup-graph-section">
            <div class="section-title-row">
              <div>
                <p class="panel-kicker">Equipment Graph</p>
                <h3>설비 그래프</h3>
              </div>
              <Gauge :size="22" />
            </div>

            <div class="equipment-popup-graph-grid">
              <article class="equipment-popup-chart-card">
                <div class="equipment-chart-head">
                  <strong>가동률 추이</strong>
                  <span>{{ selectedEquipment.rate }}%</span>
                </div>
                <div class="equipment-trend-bars">
                  <i
                    v-for="item in operationTrend"
                    :key="item.label"
                    :style="{ height: `${item.value}%` }"
                  >
                    <b>{{ item.value }}%</b>
                    <span>{{ item.label }}</span>
                  </i>
                </div>
              </article>

              <article class="equipment-popup-chart-card">
                <div class="equipment-chart-head">
                  <strong>OK / NG 품질</strong>
                  <span>{{ qualityPercent }}%</span>
                </div>
                <div class="equipment-quality-donut" :style="{ '--value': `${qualityPercent}%` }">
                  <strong>{{ qualityPercent }}%</strong>
                  <span>OK 기준</span>
                </div>
                <div class="equipment-quality-row">
                  <span>OK {{ getCommonMetric('OK') }}</span>
                  <span>NG {{ getCommonMetric('NG') }}</span>
                </div>
              </article>

              <article class="equipment-popup-chart-card">
                <div class="equipment-chart-head">
                  <strong>공통 센서 데이터</strong>
                  <span>현재값</span>
                </div>
                <div class="equipment-horizontal-bars">
                  <div v-for="item in sensorChart" :key="item.label">
                    <span>{{ item.label }}</span>
                    <i><b :style="{ width: `${item.percent}%` }"></b></i>
                    <strong>{{ item.value }}</strong>
                  </div>
                </div>
              </article>

              <article class="equipment-popup-chart-card">
                <div class="equipment-chart-head">
                  <strong>{{ selectedCategory.name }} 유형별 지표</strong>
                  <span>{{ selectedEquipment.specific.length }}개</span>
                </div>
                <div class="equipment-horizontal-bars">
                  <div v-for="metric in selectedEquipment.specific" :key="`chart-${metric.label}`">
                    <span>{{ metric.label }}</span>
                    <i><b :style="{ width: `${specificMetricPercent(metric)}%` }"></b></i>
                    <strong>{{ metric.value }}</strong>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </article>
      </div>
    </section>
  </main>
</template>
