<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { getEquipments, getEquipmentStatus } from '../api/equipment.js'
import { getSensorBuffer, getSensorKeys, getSensorLatestValues } from '../api/sensor.js'
import { getRealtimeVibration } from '../api/vibration.js'
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

const categories = ref([
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
])

const selectedCategoryId = ref('casting')
const selectedEquipmentId = ref('CAST-02')
const isEquipmentPopupOpen = ref(false)
const realtimeVibration = ref(null)
const sensorBuffers = ref({})
const sensorKeySet = ref(new Set())
const vibrationTrendHistory = ref([])
const rawVibrationChartEl = ref(null)
const fftChartEl = ref(null)
const trendChartEl = ref(null)
let realtimeRefreshTimer = null
let sensorRefreshTimer = null
let sensorKeyRefreshTimer = null
let catalogRefreshTimer = null
let chartRenderPending = false
const chartInstances = {}

const commonSensorMetrics = [
  { key: 'cycle_time', label: '싸이클 타임', unit: 's', digits: 1, max: 300 },
  { key: 'sensor_temperature', label: '온도', unit: '℃', digits: 1, max: 90 },
  { key: 'sensor_current', label: '전류', unit: 'A', digits: 1, max: 60 },
  { key: 'sensor_voltage', label: '전압', unit: 'V', digits: 0, max: 420 },
  { key: 'sensor_vibration', label: '진동', unit: 'a.u.', digits: 3, max: 4 },
]

const selectedCategory = computed(() =>
  categories.value.find((category) => category.id === selectedCategoryId.value) ?? categories.value[0],
)

async function refreshEquipmentCatalog() {
  try {
    const equipData = await getEquipments('FACTORY-01')
    if (!equipData?.length) return false

    const ids = equipData.map(e => e.equipmentCode)
    let statusMap = {}
    try {
      const statuses = await getEquipmentStatus(ids)
      statuses.forEach(s => { statusMap[s.equipId] = s.statusCode })
    } catch (_) {}

    const processMap = { '주조': 'casting', '가공': 'machining', '세척': 'washing', '조립': 'assembly', '검사': 'inspection' }
    const processName = { casting: '주조기', machining: '가공기', washing: '세척기', assembly: '조립기', inspection: '검사기' }
    const statusKr = { RUNNING: '운전', STANDBY: '대기', ALARM: '이상', MAINTENANCE: '점검' }

    const grouped = {}
    equipData.forEach(e => {
      const catId = processMap[e.processType] ?? 'machining'
      if (!grouped[catId]) grouped[catId] = []
      const sc = statusMap[e.equipmentCode] ?? 'RUNNING'
      grouped[catId].push({
        id: e.equipmentCode,
        name: e.equipmentName,
        line: e.location ?? '-',
        state: statusKr[sc] ?? '운전',
        rate: sc === 'RUNNING' ? 94 : 0,
        defects: 0,
        operator: '-',
        cycle: '-',
        common: [
          { label: '운전 상태', value: statusKr[sc] ?? '운전' },
          { label: '설비 코드', value: e.equipmentCode },
          { label: '위치', value: e.location ?? '-' },
          { label: '모델', value: e.model ?? '-' },
          { label: '설치일', value: e.installDate ?? '-' },
          { label: '공정 유형', value: e.processType ?? '-' },
        ],
        specific: [
          { label: '상태', value: statusKr[sc] ?? '-', status: sc === 'ALARM' ? '이상' : '정상' },
        ],
      })
    })

    const order = ['casting', 'machining', 'washing', 'assembly', 'inspection']
    const updated = order
      .filter(id => grouped[id])
      .map(id => {
        const eqs = grouped[id]
        const running = eqs.filter(e => e.state === '운전').length
        const stopped = eqs.filter(e => e.state === '이상').length
        const waiting = eqs.filter(e => e.state === '대기' || e.state === '점검').length
        const existingCat = categories.value.find(c => c.id === id)
        const avgRate = eqs.length
          ? Math.round(eqs.reduce((sum, eq) => sum + Number(eq.rate || 0), 0) / eqs.length)
          : 0
        return {
          ...(existingCat ?? {}),
          id,
          name: processName[id],
          status: stopped > 0 ? '이상' : waiting > 0 ? '대기' : '정상',
          count: eqs.length,
          running,
          stopped,
          waiting,
          avgRate,
          defectCount: eqs.reduce((sum, eq) => sum + Number(eq.defects || 0), 0),
          equipment: eqs,
        }
      })

    if (updated.length) {
      const currentEquipmentId = selectedEquipmentId.value
      categories.value = updated
      const currentStillExists = updated.some(category =>
        category.equipment.some(equipment => equipment.id === currentEquipmentId),
      )
      if (!currentStillExists || !isRealtimeEquipmentCode(currentEquipmentId)) {
        const firstRealtime = firstEquipment(updated, true)
        const firstAny = firstEquipment(updated, false)
        const nextEquipment = firstRealtime ?? firstAny
        if (nextEquipment) {
          selectedCategoryId.value = nextEquipment.category.id
          selectedEquipmentId.value = nextEquipment.equipment.id
        }
      } else {
        const currentCategory = updated.find(category =>
          category.equipment.some(equipment => equipment.id === currentEquipmentId),
        )
        if (currentCategory) selectedCategoryId.value = currentCategory.id
      }
    }
    return true
  } catch (e) {
    console.warn('[EquipmentDetail] API 연결 실패, 데모 데이터 표시:', e.message)
    return false
  }
}

async function refreshRealtimeVibration() {
  if (!isRealtimeEquipmentCode(selectedEquipmentId.value)) return
  try {
    realtimeVibration.value = await getRealtimeVibration(selectedEquipmentId.value)
    recordTrendPoint()
    queueChartRender()
  } catch (e) {
    realtimeVibration.value = null
  }
}

async function refreshSensorBuffers() {
  if (!isRealtimeEquipmentCode(selectedEquipmentId.value)) return
  if (!sensorKeySet.value.size) await refreshSensorKeySet()
  const candidateKeysByMetric = Object.fromEntries(
    commonSensorMetrics.map(metric => [metric.key, sensorBufferKeyCandidates(metric.key)]),
  )
  const latestRows = await readLatestSensorRows(Object.values(candidateKeysByMetric).flat())
  const latestByKey = new Map(latestRows.map(row => [row.bufferKey, row]))
  const entries = await Promise.all(commonSensorMetrics.map(async (metric) => {
    const latest = candidateKeysByMetric[metric.key]
      .map(bufferKey => latestByKey.get(bufferKey))
      .find(row => row?.latest)
    if (latest) {
      return [metric.key, { ...latest, frames: [] }]
    }
    const buffer = await readSensorBuffer(metric.key, 240)
    return [metric.key, buffer]
  }))
  sensorBuffers.value = Object.fromEntries(entries)
  queueChartRender()
}

async function refreshSensorKeySet() {
  try {
    const keys = await getSensorKeys()
    sensorKeySet.value = new Set(Array.isArray(keys) ? keys : [])
  } catch (_) {
    sensorKeySet.value = new Set()
  }
}

onMounted(async () => {
  await refreshEquipmentCatalog()
  await refreshSensorKeySet()
  refreshRealtimeVibration()
  refreshSensorBuffers()
  catalogRefreshTimer = window.setInterval(refreshEquipmentCatalog, 5000)
  sensorKeyRefreshTimer = window.setInterval(refreshSensorKeySet, 5000)
  realtimeRefreshTimer = window.setInterval(refreshRealtimeVibration, 1000)
  sensorRefreshTimer = window.setInterval(refreshSensorBuffers, 2000)
  window.addEventListener('resize', resizeEquipmentCharts)
})

onUnmounted(() => {
  if (catalogRefreshTimer) window.clearInterval(catalogRefreshTimer)
  if (sensorKeyRefreshTimer) window.clearInterval(sensorKeyRefreshTimer)
  if (realtimeRefreshTimer) window.clearInterval(realtimeRefreshTimer)
  if (sensorRefreshTimer) window.clearInterval(sensorRefreshTimer)
  window.removeEventListener('resize', resizeEquipmentCharts)
  disposeEquipmentCharts()
})

watch(selectedEquipmentId, () => {
  vibrationTrendHistory.value = []
  sensorBuffers.value = {}
  refreshRealtimeVibration()
  refreshSensorBuffers()
})

watch(isEquipmentPopupOpen, (open) => {
  if (open) queueChartRender()
  else disposeEquipmentCharts()
})

const selectedEquipment = computed(() =>
  selectedCategory.value?.equipment.find((equipment) => equipment.id === selectedEquipmentId.value)
    ?? selectedCategory.value?.equipment[0]
    ?? null,
)

function firstEquipment(categoryList, realtimeOnly = false) {
  for (const category of categoryList) {
    const equipment = category.equipment.find(item => !realtimeOnly || isRealtimeEquipmentCode(item.id))
    if (equipment) return { category, equipment }
  }
  return null
}

function isRealtimeEquipmentCode(equipmentId) {
  return /^LINE-\d+_/.test(equipmentId ?? '')
}

const sensorBaseKeys = computed(() => {
  const equipmentId = selectedEquipmentId.value ?? ''
  const normalized = equipmentId.replace(/-/g, '')
  const match = equipmentId.match(/^LINE-(\d+)_(.+)$/)
  if (!match) return [equipmentId, normalized].filter(Boolean)

  const lineKey = `LINE${match[1].padStart(2, '0')}`
  const equipmentKey = match[2].replace(/-/g, '')
  return [`${lineKey}.${equipmentKey}`, equipmentKey, equipmentId]
})

function sensorBufferKeyCandidates(sensorType) {
  return sensorBaseKeys.value.map(baseKey => `${baseKey}:${sensorType}`)
}

async function readLatestSensorRows(bufferKeys) {
  const uniqueKeys = [...new Set(bufferKeys.filter(Boolean))]
  if (!uniqueKeys.length) return []
  try {
    return await getSensorLatestValues(uniqueKeys)
  } catch (_) {
    return []
  }
}

async function readSensorBuffer(sensorType, last = 240) {
  const candidateKeys = sensorBufferKeyCandidates(sensorType)
  const existingKeys = sensorKeySet.value.size
    ? candidateKeys.filter(bufferKey => sensorKeySet.value.has(bufferKey))
    : candidateKeys
  for (const bufferKey of existingKeys) {
    try {
      return await getSensorBuffer(bufferKey, last)
    } catch (_) {
      // Try the next registered key shape. X_DAS uses LINE01.CNC02 while some aliases use CNC02.
    }
  }
  return null
}

function latestSensorValue(sensorType) {
  return sensorBuffers.value?.[sensorType]?.latest?.value ?? null
}

function sensorDisplay(sensorType, fallback = '-', unit = '', digits = 1) {
  const value = latestSensorValue(sensorType)
  if (value === null || value === undefined || Number.isNaN(Number(value))) return fallback
  return `${Number(value).toFixed(digits)}${unit}`
}

function sensorDisplayValue(sensorType, unit = '', digits = 1) {
  return sensorDisplay(sensorType, '-', unit, digits)
}

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

const commonDataItems = computed(() => {
  const equipment = selectedEquipment.value
  const existing = new Map((equipment?.common ?? []).map((metric) => [metric.label, metric.value]))
  const ok = existing.get('OK') ?? existing.get('생산수량') ?? '-'
  const ng = existing.get('NG') ?? equipment?.defects ?? '-'

  return [
    { label: '운전 상태', value: equipment?.state ?? existing.get('운전 상태') ?? '-' },
    { label: '가동률', value: `${equipment?.rate ?? '-'}%` },
    { label: '싸이클 타임', value: sensorDisplay('cycle_time', equipment?.cycle ?? existing.get('싸이클 타임') ?? '-', 's', 1) },
    { label: '진동', value: realtimeAnalysis.value?.features?.rms != null ? `${fixedMetric(realtimeAnalysis.value.features.rms)} a.u.` : sensorDisplay('sensor_vibration', existing.get('진동') ?? '-', ' a.u.', 3) },
    { label: '온도', value: sensorDisplay('sensor_temperature', existing.get('온도') ?? '-', '℃', 1) },
    { label: '전압', value: sensorDisplay('sensor_voltage', existing.get('전압') ?? '-', 'V', 0) },
    { label: '전류', value: sensorDisplay('sensor_current', existing.get('전류') ?? '-', 'A', 1) },
    { label: '생산수량', value: ok },
    { label: '불량수량', value: ng },
    { label: '설비 코드', value: equipment?.id ?? existing.get('설비 코드') ?? '-' },
    { label: '위치', value: equipment?.line ?? existing.get('위치') ?? '-' },
    { label: '모델', value: existing.get('모델') ?? '-' },
  ]
})

const popupSummaryItems = computed(() => [
  { label: '운전 상태', value: selectedEquipment.value?.state ?? '-' },
  { label: '가동률', value: `${selectedEquipment.value?.rate ?? '-'}%` },
  { label: '싸이클 타임', value: sensorDisplay('cycle_time', selectedEquipment.value?.cycle ?? '-', 's', 1) },
  { label: '온도', value: sensorDisplayValue('sensor_temperature', '℃', 1) },
  { label: '전류', value: sensorDisplayValue('sensor_current', 'A', 1) },
  { label: '전압', value: sensorDisplayValue('sensor_voltage', 'V', 0) },
])

const getCommonMetric = (label) =>
  commonDataItems.value.find((metric) => metric.label === label)?.value
    ?? selectedEquipment.value?.common.find((metric) => metric.label === label)?.value
    ?? '-'

const fixedMetric = (value, digits = 3) =>
  value === null || value === undefined || Number.isNaN(Number(value))
    ? '-'
    : Number(value).toFixed(digits)

const realtimeAnalysis = computed(() => realtimeVibration.value?.analysis ?? null)

const realtimeItems = computed(() => {
  const analysis = realtimeAnalysis.value
  const window = realtimeVibration.value?.window
  return [
    { label: '수신 상태', value: realtimeVibration.value?.received ? '실시간 수신' : '대기' },
    { label: 'Window', value: window?.windowIndex ?? '-' },
    { label: 'Samples', value: window?.valuesLength ?? '-' },
    { label: 'RMS', value: fixedMetric(analysis?.features?.rms) },
    { label: 'Peak Hz', value: fixedMetric(analysis?.features?.peakFrequency, 1) },
    { label: '예측', value: analysis?.prediction ?? '-' },
    { label: '알림', value: analysis?.alarmLevel ?? '-' },
    { label: '모델', value: analysis?.modelVersion ?? '-' },
  ]
})

const analysisMetricCards = computed(() => {
  const analysis = realtimeAnalysis.value
  return [
    { label: 'AI 예측 후보', value: analysis?.prediction ?? '-' },
    { label: '신뢰도', value: analysis?.confidence != null ? `${fixedMetric(analysis.confidence * 100, 1)}%` : '-' },
    { label: 'RMS', value: fixedMetric(analysis?.features?.rms) },
    { label: 'Peak-to-Peak', value: fixedMetric(analysis?.features?.peakToPeak) },
    { label: 'Crest Factor', value: fixedMetric(analysis?.features?.crestFactor) },
    { label: 'Kurtosis', value: fixedMetric(analysis?.features?.kurtosis) },
  ]
})

const vibrationValues = computed(() => {
  const rootValues = realtimeVibration.value?.values
  const windowValues = realtimeVibration.value?.window?.values
  if (Array.isArray(rootValues) && rootValues.length) return rootValues
  if (Array.isArray(windowValues) && windowValues.length) return windowValues
  return []
})

const vibrationSampleCount = computed(() =>
  realtimeVibration.value?.window?.valuesLength
    ?? vibrationValues.value.length
    ?? 0,
)

function recordTrendPoint() {
  const analysis = realtimeAnalysis.value
  if (!analysis?.features) return

  const timestamp = Date.parse(realtimeVibration.value?.receivedAt ?? analysis.timestamp ?? new Date().toISOString())
  const windowIndex = realtimeVibration.value?.window?.windowIndex ?? analysis.windowIndex ?? timestamp
  const last = vibrationTrendHistory.value.at(-1)
  if (last?.windowIndex === windowIndex) return

  vibrationTrendHistory.value = [
    ...vibrationTrendHistory.value,
    {
      windowIndex,
      timestamp,
      rms: analysis.features.rms ?? 0,
      peakToPeak: analysis.features.peakToPeak ?? 0,
      anomalyScore: analysis.anomalyScore ?? 0,
    },
  ].slice(-90)
}

function queueChartRender() {
  if (!isEquipmentPopupOpen.value || chartRenderPending) return
  chartRenderPending = true
  window.requestAnimationFrame(() => {
    chartRenderPending = false
    nextTick(renderEquipmentCharts)
  })
}

function ensureChart(name, el) {
  if (!el) return null
  if (!chartInstances[name] || chartInstances[name].isDisposed?.()) {
    chartInstances[name] = echarts.init(el)
  }
  return chartInstances[name]
}

function renderEquipmentCharts() {
  if (!isEquipmentPopupOpen.value) return
  renderRawVibrationChart()
  renderFftChart()
  renderTrendChart()
}

function resizeEquipmentCharts() {
  Object.values(chartInstances).forEach(chart => chart?.resize?.())
}

function disposeEquipmentCharts() {
  Object.values(chartInstances).forEach(chart => chart?.dispose?.())
  Object.keys(chartInstances).forEach(key => delete chartInstances[key])
}

function downsampleIndexed(values, maxPoints = 1600) {
  if (!Array.isArray(values) || values.length === 0) return []
  const step = Math.max(1, Math.ceil(values.length / maxPoints))
  const points = []
  for (let i = 0; i < values.length; i += step) {
    const slice = values.slice(i, i + step)
    const avg = slice.reduce((sum, value) => sum + Number(value || 0), 0) / slice.length
    points.push({ index: i, value: Number(avg.toFixed(5)) })
  }
  return points
}

function vibrationTimeSeries() {
  const values = vibrationValues.value
  const window = realtimeVibration.value?.window ?? {}
  const samplingRate = window.samplingRate || realtimeAnalysis.value?.samplingRate || 16000
  const timestamp = Date.parse(window.timestamp ?? realtimeAnalysis.value?.timestamp ?? new Date().toISOString())
  const start = timestamp - (values.length / samplingRate) * 1000

  return downsampleIndexed(values).map(point => [
    start + (point.index / samplingRate) * 1000,
    point.value,
  ])
}

function fftSeries() {
  const fft = realtimeAnalysis.value?.fft
  const frequencies = fft?.frequencies ?? []
  const magnitudes = fft?.magnitudes ?? []
  if (!frequencies.length || !magnitudes.length) return []
  const step = Math.max(1, Math.ceil(frequencies.length / 1200))
  const points = []
  for (let i = 0; i < frequencies.length; i += step) {
    points.push([Number(frequencies[i].toFixed(2)), Number((magnitudes[i] ?? 0).toFixed(6))])
  }
  return points
}

function renderRawVibrationChart() {
  const chart = ensureChart('raw', rawVibrationChartEl.value)
  if (!chart) return
  const data = vibrationTimeSeries()
  const startX = data[0]?.[0]
  const endX = data.at(-1)?.[0]
  const span = startX && endX ? endX - startX : 0

  chart.setOption({
    useUTC: true,
    animation: false,
    tooltip: { trigger: 'axis' },
    grid: { left: 54, right: 18, top: 36, bottom: 56 },
    toolbox: {
      right: 8,
      top: 4,
      feature: { dataZoom: { yAxisIndex: 'none' }, restore: {}, saveAsImage: {} },
    },
    graphic: data.length ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: {
        text: '실시간 진동 데이터 수신 대기',
        fill: '#64748b',
        fontWeight: 800,
      },
    }],
    xAxis: {
      type: 'time',
      axisLabel: {
        formatter: value => echarts.time.format(value, '{HH}:{mm}:{ss}', true),
      },
    },
    yAxis: {
      type: 'value',
      name: '진동 진폭 (a.u.)',
      min: 'dataMin',
      max: 'dataMax',
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0 },
      { type: 'slider', xAxisIndex: 0, height: 24, bottom: 16 },
    ],
    series: [
      {
        name: '측정값',
        type: 'line',
        symbol: 'none',
        sampling: 'lttb',
        lineStyle: { width: 1, color: '#0f5e8c' },
        areaStyle: { color: 'rgba(23, 121, 178, 0.08)' },
        data,
        markArea: span ? {
          silent: true,
          itemStyle: { color: 'rgba(34, 197, 94, 0.09)' },
          label: { color: '#334155', fontWeight: 800 },
          data: [
            [
              { name: '회복 확인 구간', xAxis: startX + span * 0.32 },
              { xAxis: startX + span * 0.46 },
            ],
            [
              { name: '회복 확인 구간', xAxis: startX + span * 0.66 },
              { xAxis: startX + span * 0.80 },
            ],
          ],
        } : undefined,
      },
    ],
  }, true)
}

function renderFftChart() {
  const chart = ensureChart('fft', fftChartEl.value)
  if (!chart) return
  const data = fftSeries()
  chart.setOption({
    animation: false,
    tooltip: { trigger: 'axis' },
    grid: { left: 54, right: 18, top: 34, bottom: 48 },
    toolbox: {
      right: 8,
      top: 2,
      feature: { dataZoom: { yAxisIndex: 'none' }, restore: {}, saveAsImage: {} },
    },
    graphic: data.length ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: {
        text: 'FFT 데이터 수신 대기',
        fill: '#64748b',
        fontWeight: 800,
      },
    }],
    xAxis: {
      type: 'value',
      name: '주파수 (Hz)',
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    yAxis: {
      type: 'value',
      name: 'FFT 크기',
      min: 0,
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    dataZoom: [
      { type: 'inside', xAxisIndex: 0 },
      { type: 'slider', xAxisIndex: 0, height: 20, bottom: 12 },
    ],
    series: [
      {
        name: 'FFT',
        type: 'line',
        symbol: 'none',
        lineStyle: { width: 1.2, color: '#645bff' },
        areaStyle: { color: 'rgba(100, 91, 255, 0.08)' },
        data,
      },
    ],
  }, true)
}

function renderTrendChart() {
  const chart = ensureChart('trend', trendChartEl.value)
  if (!chart) return
  const rows = vibrationTrendHistory.value
  const anomalyPoints = rows.map(row => [row.timestamp, row.anomalyScore])
  const maxAnomaly = anomalyPoints.reduce((max, point) => point[1] > max[1] ? point : max, [null, -Infinity])

  chart.setOption({
    useUTC: true,
    animation: false,
    legend: { top: 2, right: 44, itemWidth: 14, textStyle: { color: '#334155', fontWeight: 700 } },
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 18, top: 42, bottom: 46 },
    toolbox: {
      right: 8,
      top: 2,
      feature: { dataZoom: { yAxisIndex: 'none' }, restore: {}, saveAsImage: {} },
    },
    graphic: rows.length ? [] : [{
      type: 'text',
      left: 'center',
      top: 'middle',
      style: {
        text: '구간 특성값 수집 중',
        fill: '#64748b',
        fontWeight: 800,
      },
    }],
    xAxis: {
      type: 'time',
      axisLabel: { formatter: value => echarts.time.format(value, '{HH}:{mm}:{ss}', true) },
    },
    yAxis: {
      type: 'value',
      name: '특징값',
      min: 0,
      splitLine: { lineStyle: { color: '#e3ebf5' } },
    },
    dataZoom: [{ type: 'inside', xAxisIndex: 0 }],
    series: [
      {
        name: 'RMS',
        type: 'line',
        symbolSize: 4,
        lineStyle: { width: 1.5, color: '#4f6df5' },
        itemStyle: { color: '#4f6df5' },
        data: rows.map(row => [row.timestamp, Number(row.rms.toFixed(4))]),
      },
      {
        name: 'Peak-to-Peak',
        type: 'line',
        symbolSize: 4,
        lineStyle: { width: 1.5, color: '#f59e0b' },
        itemStyle: { color: '#f59e0b' },
        data: rows.map(row => [row.timestamp, Number(row.peakToPeak.toFixed(4))]),
      },
      {
        name: '이상 점수',
        type: 'line',
        symbolSize: 4,
        lineStyle: { width: 1.5, color: '#ef4444' },
        itemStyle: { color: '#ef4444' },
        data: anomalyPoints,
        markPoint: maxAnomaly[0] ? {
          symbol: 'pin',
          symbolSize: 56,
          label: { formatter: '최고 위험', color: '#ffffff', fontSize: 10, fontWeight: 900 },
          data: [{ coord: maxAnomaly, value: fixedMetric(maxAnomaly[1], 3) }],
        } : undefined,
      },
    ],
  }, true)
}

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
              <article v-for="metric in commonDataItems" :key="metric.label">
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
            <article v-for="metric in popupSummaryItems" :key="`popup-summary-${metric.label}`">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
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
                <article v-for="metric in commonDataItems" :key="`popup-common-${metric.label}`">
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
                <h3>진동 데이터 집중 분석</h3>
              </div>
              <Gauge :size="22" />
            </div>

            <div class="equipment-vibration-analysis-grid">
              <article class="equipment-echart-card equipment-echart-card--raw">
                <div class="equipment-chart-head">
                  <div>
                    <strong>원본 진동 데이터 윈도우</strong>
                    <span>회복 확인 구간</span>
                  </div>
                  <small>{{ vibrationSampleCount }} samples</small>
                </div>
                <div ref="rawVibrationChartEl" class="equipment-echart"></div>
              </article>

              <aside class="equipment-analysis-metrics" aria-label="AI 분석 요약">
                <article v-for="metric in analysisMetricCards" :key="metric.label">
                  <span>{{ metric.label }}</span>
                  <strong>{{ metric.value }}</strong>
                </article>
              </aside>

              <article class="equipment-echart-card">
                <div class="equipment-chart-head">
                  <div>
                    <strong>선택 구간 FFT</strong>
                    <span>FFT 크기</span>
                  </div>
                  <small>{{ realtimeAnalysis?.fft?.binCount ?? 0 }} bin</small>
                </div>
                <div ref="fftChartEl" class="equipment-echart equipment-echart--small"></div>
              </article>

              <article class="equipment-echart-card">
                <div class="equipment-chart-head">
                  <div>
                    <strong>구간 특성값 흐름</strong>
                    <span>최근 {{ vibrationTrendHistory.length }} window</span>
                  </div>
                  <small>{{ realtimeAnalysis?.alarmLevel ?? 'normal' }}</small>
                </div>
                <div ref="trendChartEl" class="equipment-echart equipment-echart--small"></div>
              </article>
            </div>
          </section>
        </article>
      </div>
    </section>
  </main>
</template>
