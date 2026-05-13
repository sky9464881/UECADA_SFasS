<script setup>
/**
 * Smart Factory SCADA Dashboard (mockup parity)
 * 스타일은 본 컴포넌트 scoped CSS만 사용 — 전역 style.css 미변경
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  AlertTriangle,
  ChevronDown,
  Clock,
  Factory,
  FileText,
  LayoutDashboard,
  Settings,
  ShieldCheck,
  TrendingDown,
  TrendingUp,
  Wrench,
} from 'lucide-vue-next'

const now = ref(new Date())
let tick
onMounted(() => {
  tick = setInterval(() => {
    now.value = new Date()
  }, 1000)
})
onBeforeUnmount(() => clearInterval(tick))

const headerDateTime = computed(() => {
  const d = now.value
  const w = d.toLocaleDateString('en-US', { weekday: 'long' })
  const rest = d.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })
  const t = d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true })
  return `${w}, ${rest} | ${t}`
})

const sideNav = [
  { id: 'dash', label: 'Dashboard', icon: LayoutDashboard, href: '#/dashboard', active: true },
  { id: 'prod', label: 'Production', icon: Factory, href: '#/dashboard' },
  { id: 'qual', label: 'Quality', icon: ShieldCheck, href: '#/dashboard' },
  { id: 'equip', label: 'Equipment', icon: Wrench, href: '#/equipment' },
  { id: 'rep', label: 'Reports', icon: FileText, href: '#/dashboard' },
  { id: 'set', label: 'Settings', icon: Settings, href: '#/users' },
]

const outputRange = ref('day')
const qualityRange = ref('day')
const energyRange = ref('day')

const hourLabels = ['00:00', '03:00', '06:00', '09:00', '12:00', '15:00', '18:00', '21:00', '00:00']

const productionSeries = ref([
  { name: 'Series A', data: [120, 210, 180, 260, 310, 290, 240, 200, 160] },
  { name: 'Series B', data: [100, 190, 200, 230, 280, 270, 220, 190, 140] },
])

const productionOptions = ref({
  chart: { type: 'bar', toolbar: { show: false }, fontFamily: 'inherit' },
  plotOptions: { bar: { columnWidth: '72%', borderRadius: 3 } },
  colors: ['#1e3a5f', '#14b8a6'],
  dataLabels: { enabled: false },
  stroke: { show: true, width: 0 },
  xaxis: {
    categories: hourLabels,
    labels: { style: { colors: '#64748b', fontSize: '11px' } },
    axisBorder: { color: '#e2e8f0' },
  },
  yaxis: {
    title: { text: 'Units', style: { color: '#64748b', fontSize: '12px', fontWeight: 600 } },
    min: 0,
    max: 400,
    tickAmount: 5,
    labels: { style: { colors: '#64748b' } },
  },
  legend: { position: 'bottom', horizontalAlign: 'center', fontSize: '12px' },
  grid: { borderColor: '#e2e8f0', strokeDashArray: 4, padding: { top: 8, right: 8, bottom: 0, left: 8 } },
  tooltip: { theme: 'light' },
})

const qualitySeries = ref([
  {
    name: 'Quality',
    data: [72, 74, 76, 75, 78, 80, 82, 81, 79],
  },
])

const qualityOptions = ref({
  chart: { type: 'area', toolbar: { show: false }, fontFamily: 'inherit', zoom: { enabled: false } },
  colors: ['#0d9488'],
  stroke: { curve: 'smooth', width: 2 },
  fill: {
    type: 'gradient',
    gradient: { shadeIntensity: 1, opacityFrom: 0.45, opacityTo: 0.06, stops: [0, 92, 100] },
  },
  dataLabels: { enabled: false },
  xaxis: {
    categories: hourLabels,
    labels: { style: { colors: '#64748b', fontSize: '11px' } },
    axisBorder: { color: '#e2e8f0' },
  },
  yaxis: {
    min: 0,
    max: 100,
    tickAmount: 5,
    labels: { formatter: (v) => `${v}%`, style: { colors: '#64748b' } },
  },
  annotations: {
    yaxis: [
      {
        y: 78,
        borderColor: '#94a3b8',
        borderWidth: 2,
        strokeDashArray: 6,
        label: {
          text: 'Target Quality',
          position: 'right',
          style: { background: '#f8fafc', color: '#475569', fontSize: '11px', fontWeight: 600 },
        },
      },
    ],
  },
  grid: { borderColor: '#e2e8f0', strokeDashArray: 4, padding: { top: 8, right: 12, bottom: 0, left: 8 } },
  tooltip: { theme: 'light', y: { formatter: (v) => `${v}%` } },
})

const energySeries = ref([
  {
    name: 'Actual',
    data: [118, 132, 128, 145, 152, 148, 140, 135, 128],
  },
  {
    name: 'Target',
    data: [125, 125, 125, 125, 125, 125, 125, 125, 125],
  },
])

const energyOptions = ref({
  chart: { type: 'line', toolbar: { show: false }, fontFamily: 'inherit', zoom: { enabled: false } },
  colors: ['#1e3a5f', '#5eead4'],
  stroke: { width: [3, 2], curve: 'smooth' },
  markers: { size: [4, 0], strokeWidth: [2, 0] },
  dataLabels: { enabled: false },
  xaxis: {
    categories: hourLabels,
    labels: { style: { colors: '#64748b', fontSize: '11px' } },
    axisBorder: { color: '#e2e8f0' },
  },
  yaxis: {
    title: { text: 'kWh', style: { color: '#64748b', fontSize: '12px', fontWeight: 600 } },
    min: 0,
    max: 200,
    tickAmount: 5,
    labels: { style: { colors: '#64748b' } },
  },
  legend: { position: 'top', horizontalAlign: 'right', fontSize: '12px', offsetY: -4 },
  grid: { borderColor: '#e2e8f0', strokeDashArray: 4, padding: { top: 12, right: 8, bottom: 0, left: 8 } },
  tooltip: { theme: 'light', shared: true },
})

const oeeSeries = ref([38, 34, 28])

const oeeOptions = ref({
  chart: { type: 'donut', fontFamily: 'inherit' },
  labels: ['Availability', 'Performance', 'Quality'],
  colors: ['#2563eb', '#14b8a6', '#cbd5e1'],
  plotOptions: {
    pie: {
      donut: {
        size: '70%',
        labels: {
          show: true,
          name: { show: false },
          value: { show: false },
          total: {
            show: true,
            showAlways: true,
            label: 'OEE',
            fontSize: '24px',
            fontWeight: 700,
            color: '#0f172a',
            formatter: () => '88.5%',
          },
        },
      },
    },
  },
  dataLabels: { enabled: false },
  stroke: { width: 2, colors: ['#ffffff'] },
  legend: { position: 'bottom', fontSize: '11px', markers: { width: 8, height: 8 } },
  tooltip: { theme: 'light', y: { formatter: (v) => `${v}%` } },
})
</script>

<template>
  <div class="sf-root">
    <aside class="sf-side" aria-label="Navigation">
      <div class="sf-side-logo">U</div>
      <nav class="sf-side-nav">
        <a
          v-for="item in sideNav"
          :key="item.id"
          :href="item.href"
          :class="['sf-side-link', { 'sf-side-link--active': item.active }]"
          :title="item.label"
        >
          <component :is="item.icon" class="sf-side-ico" :stroke-width="1.75" />
          <span>{{ item.label }}</span>
        </a>
      </nav>
    </aside>

    <div class="sf-main">
      <header class="sf-top">
        <div class="sf-top-left">
          <span class="sf-top-factory">
            <Factory class="sf-top-factory-ico" :stroke-width="2" />
          </span>
          <h1 class="sf-top-title">SMART FACTORY SCADA</h1>
        </div>
        <div class="sf-top-right">
          <div class="sf-top-time">
            <Clock class="sf-top-time-ico" :stroke-width="2" />
            <time>{{ headerDateTime }}</time>
          </div>
          <a class="sf-profile" href="#/login">
            <span class="sf-avatar" aria-hidden="true">JD</span>
            <span class="sf-profile-name">John Doe</span>
            <ChevronDown class="sf-profile-chev" :stroke-width="2" />
          </a>
        </div>
      </header>

      <div class="sf-content">
        <div class="sf-grid">
          <!-- OEE -->
          <section class="sf-card sf-card--oee">
            <h2 class="sf-card-title">OEE (Overall Equipment Effectiveness)</h2>
            <div class="sf-oee-body">
              <div class="sf-oee-chart">
                <apexchart type="donut" height="280" :options="oeeOptions" :series="oeeSeries" />
              </div>
              <div class="sf-oee-side">
                <div class="sf-line-stat">
                  <span class="sf-line-label">Line A</span>
                  <span class="sf-line-pct sf-line-pct--up">88.5%</span>
                  <span class="sf-badge sf-badge--ok">Status</span>
                  <TrendingUp class="sf-trend sf-trend--up" :stroke-width="2" />
                </div>
                <div class="sf-line-stat sf-line-stat--b">
                  <span class="sf-line-label">Line B</span>
                  <span class="sf-line-pct sf-line-pct--down">82.1%</span>
                  <span class="sf-badge sf-badge--bad">Status</span>
                  <TrendingDown class="sf-trend sf-trend--down" :stroke-width="2" />
                </div>
              </div>
            </div>
            <div class="sf-downtime-pop" role="status">
              <div class="sf-downtime-pop-head">
                <AlertTriangle class="sf-downtime-warn" :stroke-width="2" />
                <strong>Unplanned Downtime (Last 24h)</strong>
              </div>
              <p class="sf-downtime-total">0.2 hrs (Total)</p>
              <p class="sf-downtime-detail">Line R: Pump failure (0.2 hrs)</p>
            </div>
          </section>

          <!-- Production -->
          <section class="sf-card">
            <div class="sf-card-head">
              <div class="sf-card-head-left">
                <h2 class="sf-card-title sf-card-title--inline">Production Output</h2>
                <span class="sf-delta sf-delta--up">
                  <TrendingUp class="sf-delta-ico" :stroke-width="2" />
                  5.2%
                </span>
              </div>
              <div class="sf-toggle" role="group" aria-label="Production range">
                <button type="button" :class="{ 'sf-toggle-on': outputRange === 'day' }" @click="outputRange = 'day'">
                  Day
                </button>
                <button
                  type="button"
                  :class="{ 'sf-toggle-on': outputRange === 'month' }"
                  @click="outputRange = 'month'"
                >
                  Month
                </button>
                <button type="button" :class="{ 'sf-toggle-on': outputRange === 'year' }" @click="outputRange = 'year'">
                  Year
                </button>
              </div>
            </div>
            <div class="sf-chart">
              <apexchart type="bar" height="300" :options="productionOptions" :series="productionSeries" />
            </div>
          </section>

          <!-- Quality -->
          <section class="sf-card">
            <div class="sf-card-head">
              <div class="sf-card-head-left">
                <h2 class="sf-card-title sf-card-title--inline">Quality Rate</h2>
                <span class="sf-delta sf-delta--down">
                  <TrendingDown class="sf-delta-ico" :stroke-width="2" />
                  1.1%
                </span>
              </div>
              <div class="sf-toggle" role="group" aria-label="Quality range">
                <button type="button" :class="{ 'sf-toggle-on': qualityRange === 'day' }" @click="qualityRange = 'day'">
                  Day
                </button>
                <button
                  type="button"
                  :class="{ 'sf-toggle-on': qualityRange === 'month' }"
                  @click="qualityRange = 'month'"
                >
                  Month
                </button>
                <button
                  type="button"
                  :class="{ 'sf-toggle-on': qualityRange === 'year' }"
                  @click="qualityRange = 'year'"
                >
                  Year
                </button>
              </div>
            </div>
            <div class="sf-chart">
              <apexchart type="area" height="300" :options="qualityOptions" :series="qualitySeries" />
            </div>
          </section>

          <!-- Energy -->
          <section class="sf-card">
            <div class="sf-card-head">
              <div class="sf-card-head-left">
                <h2 class="sf-card-title sf-card-title--inline">Energy Consumption</h2>
                <span class="sf-delta sf-delta--save">
                  <TrendingDown class="sf-delta-ico" :stroke-width="2" />
                  3.8%
                </span>
              </div>
              <div class="sf-toggle" role="group" aria-label="Energy range">
                <button type="button" :class="{ 'sf-toggle-on': energyRange === 'day' }" @click="energyRange = 'day'">
                  Day
                </button>
                <button
                  type="button"
                  :class="{ 'sf-toggle-on': energyRange === 'month' }"
                  @click="energyRange = 'month'"
                >
                  Month
                </button>
                <button type="button" :class="{ 'sf-toggle-on': energyRange === 'year' }" @click="energyRange = 'year'">
                  Year
                </button>
              </div>
            </div>
            <div class="sf-chart">
              <apexchart type="line" height="300" :options="energyOptions" :series="energySeries" />
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sf-root {
  box-sizing: border-box;
  min-height: 100vh;
  display: flex;
  background: #f8fafc;
  color: #0f172a;
  font-family: ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, 'Noto Sans KR', sans-serif;
}

.sf-side {
  width: 72px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  box-shadow: 1px 0 0 rgba(15, 23, 42, 0.04);
}

.sf-side-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #0f172a;
  color: #fff;
  font-weight: 900;
  font-size: 14px;
  display: grid;
  place-items: center;
  margin-bottom: 20px;
}

.sf-side-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  align-items: center;
}

.sf-side-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 56px;
  padding: 10px 4px;
  border-radius: 12px;
  text-decoration: none;
  color: #64748b;
  font-size: 10px;
  font-weight: 600;
  text-align: center;
  line-height: 1.15;
  transition: background 0.15s, color 0.15s;
}

.sf-side-link:hover {
  background: #f1f5f9;
  color: #334155;
}

.sf-side-link--active {
  background: #eff6ff;
  color: #1d4ed8;
  box-shadow: inset 0 0 0 1px #bfdbfe;
}

.sf-side-ico {
  width: 22px;
  height: 22px;
}

.sf-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.sf-top {
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.sf-top-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sf-top-factory {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1e40af, #0d9488);
  display: grid;
  place-items: center;
  color: #fff;
  box-shadow: 0 4px 14px rgba(30, 64, 175, 0.35);
}

.sf-top-factory-ico {
  width: 22px;
  height: 22px;
}

.sf-top-title {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #020617;
}

.sf-top-right {
  display: flex;
  align-items: center;
  gap: 28px;
}

.sf-top-time {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #475569;
}

.sf-top-time-ico {
  width: 18px;
  height: 18px;
  color: #94a3b8;
}

.sf-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}

.sf-avatar {
  width: 40px;
  height: 40px;
  border-radius: 999px;
  border: 2px solid #e2e8f0;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 800;
  color: #1e40af;
  background: linear-gradient(145deg, #dbeafe, #e0f2fe);
}

.sf-profile-name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.sf-profile-chev {
  width: 18px;
  height: 18px;
  color: #94a3b8;
}

.sf-content {
  flex: 1;
  padding: 24px;
  overflow: auto;
}

.sf-grid {
  max-width: 1600px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.sf-card {
  position: relative;
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  border: 1px solid #f1f5f9;
}

.sf-card-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.sf-card-title--inline {
  margin: 0;
}

.sf-card-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.sf-card-head-left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.sf-delta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
}

.sf-delta-ico {
  width: 14px;
  height: 14px;
}

.sf-delta--up {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.sf-delta--down {
  background: #fef2f2;
  color: #b91c1c;
  border: 1px solid #fecaca;
}

.sf-delta--save {
  background: #ecfdf5;
  color: #047857;
  border: 1px solid #a7f3d0;
}

.sf-toggle {
  display: inline-flex;
  padding: 3px;
  border-radius: 10px;
  background: #f1f5f9;
  gap: 2px;
}

.sf-toggle button {
  border: 0;
  background: transparent;
  padding: 8px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  border-radius: 8px;
  cursor: pointer;
}

.sf-toggle button:hover {
  color: #334155;
}

.sf-toggle-on {
  background: #2563eb !important;
  color: #ffffff !important;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.35);
}

.sf-chart {
  margin-top: 4px;
}

.sf-oee-body {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-start;
}

.sf-oee-chart {
  flex: 1 1 260px;
  min-width: 220px;
  max-width: 320px;
}

.sf-oee-side {
  flex: 1 1 200px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding-top: 8px;
}

.sf-line-stat {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.sf-line-label {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.sf-line-pct {
  font-size: 22px;
  font-weight: 800;
}

.sf-line-pct--up {
  color: #047857;
}

.sf-line-pct--down {
  color: #b91c1c;
}

.sf-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 999px;
}

.sf-badge--ok {
  background: #ecfdf5;
  color: #047857;
}

.sf-badge--bad {
  background: #fef2f2;
  color: #b91c1c;
}

.sf-trend {
  width: 24px;
  height: 24px;
}

.sf-trend--up {
  color: #10b981;
}

.sf-trend--down {
  color: #ef4444;
}

.sf-downtime-pop {
  position: absolute;
  right: 20px;
  bottom: 20px;
  width: min(280px, calc(100% - 40px));
  padding: 14px 16px;
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  z-index: 2;
}

.sf-downtime-pop-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.sf-downtime-warn {
  width: 18px;
  height: 18px;
  color: #dc2626;
  flex-shrink: 0;
}

.sf-downtime-total {
  margin: 8px 0 0;
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.sf-downtime-detail {
  margin: 6px 0 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.45;
}

@media (max-width: 1200px) {
  .sf-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .sf-top {
    flex-direction: column;
    height: auto;
    align-items: flex-start;
    gap: 12px;
    padding: 14px 16px;
  }

  .sf-top-right {
    width: 100%;
    justify-content: space-between;
  }

  .sf-content {
    padding: 16px;
  }
}
</style>
