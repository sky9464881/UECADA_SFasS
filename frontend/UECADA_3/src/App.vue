<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AlertTriangle } from 'lucide-vue-next'
import {
  fetchAlarmHistoriesRaw,
  fetchAlarmsRaw,
  type AlarmHistoryResponse,
  type AlarmResponse,
} from '@/api/alarmApi'
import { fetchEquipments, fetchEquipmentStatuses } from '@/api/equipmentApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'
import { useAuthStore } from '@/stores/auth'
import type { EquipmentStatusItem } from '@/types/equipment'

const auth = useAuthStore()

const SESSION_KEY = 'alarm-ack-ids'
const SENSOR_ALARM_RECENT_MS = 5 * 60 * 1000
const AI_ALARM_RECENT_MS = 5 * 60 * 1000

interface GlobalAlarmPopup {
  key: string
  source: 'sensor' | 'ai' | 'equipment'
  id: number
  equipmentCode: string
  alarmType: string
  severity: string
  status: string
  alarmMessage: string
  occurredAt: string
}

function loadAcknowledgedIds(): Set<string> {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? new Set((JSON.parse(raw) as Array<number | string>).map(String)) : new Set()
  } catch {
    return new Set()
  }
}

function persistAcknowledgedIds(set: Set<string>): void {
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify([...set]))
  } catch {}
}

const acknowledgedAlarmIds = ref<Set<string>>(loadAcknowledgedIds())
const selectedIndex = ref(0)
const equipmentOffEvents = ref<GlobalAlarmPopup[]>([])
const previousEquipmentStatuses = ref<Map<string, string>>(new Map())

const alarmQuery = useQuery({
  queryKey: ['global-alarm-popup'],
  queryFn: () => fetchAlarmsRaw('OPEN'),
  enabled: computed(() => auth.isAuthenticated),
  refetchInterval: POLL_INTERVAL_MS.alarm,
  refetchIntervalInBackground: true,
  staleTime: 0,
})

const alarmHistoryQuery = useQuery({
  queryKey: ['global-ai-alarm-popup'],
  queryFn: () => fetchAlarmHistoriesRaw(50),
  enabled: computed(() => auth.isAuthenticated),
  refetchInterval: POLL_INTERVAL_MS.alarm,
  refetchIntervalInBackground: true,
  staleTime: 0,
})

const equipmentsQuery = useQuery({
  queryKey: ['global-popup-equipments', 'FACTORY-01'],
  queryFn: () => fetchEquipments('FACTORY-01'),
  enabled: computed(() => auth.isAuthenticated),
  staleTime: 60_000,
})

const equipmentIds = computed(() => (equipmentsQuery.data.value ?? []).map((equipment) => equipment.equipmentCode))

const equipmentStatusQuery = useQuery({
  queryKey: computed(() => ['global-popup-equipment-status', equipmentIds.value]),
  queryFn: () => fetchEquipmentStatuses(equipmentIds.value),
  enabled: computed(() => auth.isAuthenticated && equipmentIds.value.length > 0),
  refetchInterval: POLL_INTERVAL_MS.equipmentRealtime,
  refetchIntervalInBackground: true,
  staleTime: 0,
})

const openUniqueAlarms = computed<GlobalAlarmPopup[]>(() => {
  const map = new Map<string, GlobalAlarmPopup>()
  for (const alarm of equipmentOffEvents.value) {
    if (!isRecentTimestamp(alarm.occurredAt, SENSOR_ALARM_RECENT_MS)) continue
    if (!map.has(alarm.key)) map.set(alarm.key, alarm)
  }
  for (const alarm of alarmQuery.data.value ?? []) {
    if (!shouldShowSensorAlarm(alarm)) continue
    const popup = sensorAlarmToPopup(alarm)
    if (!map.has(popup.key)) map.set(popup.key, popup)
  }
  for (const alarm of alarmHistoryQuery.data.value ?? []) {
    if (!shouldShowAiAlarm(alarm)) continue
    const popup = aiAlarmToPopup(alarm)
    if (!map.has(popup.key)) map.set(popup.key, popup)
  }
  return [...map.values()]
})

const pendingAlarms = computed(() =>
  openUniqueAlarms.value.filter(
    (a) => !acknowledgedAlarmIds.value.has(a.key) && !acknowledgedAlarmIds.value.has(String(a.id)),
  ),
)

const activeAlarm = computed(() => pendingAlarms.value[selectedIndex.value] ?? null)

watch(pendingAlarms, (list) => {
  if (selectedIndex.value >= list.length) {
    selectedIndex.value = Math.max(0, list.length - 1)
  }
})

watch(
  () => equipmentStatusQuery.data.value,
  (statuses) => {
    if (!statuses?.length) return

    const previous = previousEquipmentStatuses.value
    const initialized = previous.size > 0
    const next = new Map<string, string>()

    for (const status of statuses as EquipmentStatusItem[]) {
      const code = status.statusCode?.toUpperCase() || 'UNKNOWN'
      next.set(status.equipId, code)

      if (initialized && code === 'MAINTENANCE' && previous.get(status.equipId) !== 'MAINTENANCE') {
        equipmentOffEvents.value = [
          equipmentOffToPopup(status.equipId),
          ...equipmentOffEvents.value.filter((event) => isRecentTimestamp(event.occurredAt, SENSOR_ALARM_RECENT_MS)),
        ].slice(0, 20)
      }
    }

    previousEquipmentStatuses.value = next
  },
  { immediate: true },
)

const EQUIP_TYPE_KO: Record<string, string> = {
  TEST: '검사기',
  ASSY: '조립기',
  CAST: '주조기',
  CNC:  '가공기',
  WASH: '세척기',
}

function toKoreanEquipName(code: string): string {
  const m = /LINE-(\d+)_([A-Z]+)-(\d+)/i.exec(code)
  if (!m) return code
  const lineNum = parseInt(m[1], 10)
  const typeKo  = EQUIP_TYPE_KO[m[2].toUpperCase()] ?? m[2]
  const equipNum = parseInt(m[3], 10)
  return `${lineNum}라인 ${typeKo} ${equipNum}호`
}

function shortCode(code: string): string {
  const m = /LINE-(\d+)_([A-Z]+)-(\d+)/i.exec(code)
  if (!m) return code
  const typeKo   = EQUIP_TYPE_KO[m[2].toUpperCase()] ?? m[2]
  const equipNum = parseInt(m[3], 10)
  return `${typeKo} ${equipNum}호`
}

function dismissCurrent(): void {
  if (!activeAlarm.value) return
  const updated = new Set([...acknowledgedAlarmIds.value, activeAlarm.value.key])
  acknowledgedAlarmIds.value = updated
  persistAcknowledgedIds(updated)
  if (selectedIndex.value >= pendingAlarms.value.length) {
    selectedIndex.value = Math.max(0, pendingAlarms.value.length - 1)
  }
}

function dismissAll(): void {
  const updated = new Set([
    ...acknowledgedAlarmIds.value,
    ...pendingAlarms.value.map((a) => a.key),
  ])
  acknowledgedAlarmIds.value = updated
  persistAcknowledgedIds(updated)
}

function formatAlarmTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function sensorAlarmToPopup(alarm: AlarmResponse): GlobalAlarmPopup {
  return {
    key: `sensor:${alarm.alarmId}`,
    source: 'sensor',
    id: alarm.alarmId,
    equipmentCode: alarm.equipmentCode,
    alarmType: alarm.alarmType || '설비 알람',
    severity: alarm.severity,
    status: alarm.status,
    alarmMessage: alarm.alarmMessage || '확인이 필요한 설비 알람이 발생했습니다.',
    occurredAt: alarm.occurredAt,
  }
}

function equipmentOffToPopup(equipmentCode: string): GlobalAlarmPopup {
  const occurredAt = localDateTime()
  return {
    key: `equipment-off:${equipmentCode}:${occurredAt}`,
    source: 'equipment',
    id: Date.now(),
    equipmentCode,
    alarmType: '설비 가동 정지',
    severity: 'DANGER',
    status: 'OPEN',
    alarmMessage: `[${equipmentCode}] PLC 전원/가동 상태가 OFF로 전환되었습니다.`,
    occurredAt,
  }
}

function shouldShowSensorAlarm(alarm: AlarmResponse): boolean {
  if (alarm.status?.toUpperCase() !== 'OPEN') return false

  const sev = alarm.severity?.toUpperCase()
  if (sev !== 'DANGER' && sev !== 'CRITICAL') return false

  return isRecentTimestamp(alarm.occurredAt, SENSOR_ALARM_RECENT_MS)
}

function aiAlarmToPopup(alarm: AlarmHistoryResponse): GlobalAlarmPopup {
  const prediction = formatPrediction(alarm.prediction)
  const level = alarm.alarmLevel?.toUpperCase() || 'DANGER'
  return {
    key: `ai:${alarm.id}`,
    source: 'ai',
    id: alarm.id,
    equipmentCode: alarm.equipmentCode,
    alarmType: `AI 고장 예측: ${prediction}`,
    severity: level === 'DANGER' ? 'DANGER' : 'WARNING',
    status: alarm.status,
    alarmMessage: `[${alarm.equipmentCode}] AI가 ${prediction} 이상 패턴을 감지했습니다.`,
    occurredAt: alarm.occurredAt,
  }
}

function shouldShowAiAlarm(alarm: AlarmHistoryResponse): boolean {
  const level = alarm.alarmLevel?.toLowerCase()
  if (level !== 'warning' && level !== 'danger') return false

  const prediction = alarm.prediction?.trim().toLowerCase()
  if (!prediction || prediction === 'normal') return false

  return isRecentTimestamp(alarm.occurredAt, AI_ALARM_RECENT_MS)
}

function formatPrediction(value: string | null): string {
  switch (value?.trim().toLowerCase()) {
    case 'bearing':
      return '베어링 손상'
    case 'looseness':
      return '느슨함'
    case 'misalignment':
      return '축 정렬 불량'
    case 'unbalance':
      return '불균형'
    default:
      return value || '이상'
  }
}

function isRecentTimestamp(value: string | null | undefined, windowMs: number): boolean {
  if (!value) return false

  const candidates = [Date.parse(value)]
  if (!/[zZ]|[+-]\d{2}:?\d{2}$/.test(value)) {
    candidates.push(Date.parse(`${value}Z`))
  }

  const now = Date.now()
  return candidates.some((timestamp) => Number.isFinite(timestamp) && Math.abs(now - timestamp) <= windowMs)
}

function localDateTime(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>

<template>
  <RouterView />
  <Teleport to="body">
    <div v-if="pendingAlarms.length > 0 && activeAlarm" class="global-alarm-backdrop" role="presentation">
      <div class="global-alarm-wrapper">

        <div class="global-alarm-tabs" role="tablist" aria-label="미확인 알람 목록">
          <button
            v-for="(alarm, i) in pendingAlarms"
            :key="alarm.key"
            type="button"
            role="tab"
            :aria-selected="i === selectedIndex"
            :class="['global-alarm-tab', { 'global-alarm-tab--active': i === selectedIndex }]"
            @click="selectedIndex = i"
          >
            <span class="global-alarm-tab-dot" />
            {{ shortCode(alarm.equipmentCode) }}
          </button>
        </div>

        <article class="global-alarm-popup" role="alertdialog" aria-modal="true" aria-label="실시간 알람 발생">
          <header class="global-alarm-head">
            <span class="global-alarm-icon">
              <AlertTriangle :size="32" />
            </span>
            <div>
              <p>{{ activeAlarm.source === 'ai' ? 'AI Alarm' : activeAlarm.source === 'equipment' ? 'Equipment Alarm' : 'Real-time Alarm' }} · {{ selectedIndex + 1 }} / {{ pendingAlarms.length }}</p>
              <h2>{{ toKoreanEquipName(activeAlarm.equipmentCode) }}</h2>
            </div>
            <button type="button" class="global-alarm-btn-all" @click="dismissAll">
              전체 확인
            </button>
          </header>

          <section class="global-alarm-msg">
            <strong class="global-alarm-type">{{ activeAlarm.alarmType || '알람' }}</strong>
            <p>{{ activeAlarm.alarmMessage || '확인이 필요한 알람이 발생했습니다.' }}</p>
            <span class="global-alarm-time">{{ formatAlarmTime(activeAlarm.occurredAt) }}</span>
          </section>

          <footer class="global-alarm-footer">
            <button type="button" class="global-alarm-btn-confirm" @click="dismissCurrent">확인</button>
          </footer>
        </article>

      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.global-alarm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(3px);
}

.global-alarm-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: min(640px, calc(100vw - 48px));
}

.global-alarm-tabs {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  padding: 0 4px;
  width: 100%;
  overflow: hidden;
}

.global-alarm-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  border: 1px solid #fecaca;
  border-bottom: none;
  border-radius: 8px 8px 0 0;
  background: #ffe4e6;
  color: #9f1239;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  position: relative;
  bottom: -1px;
  flex: 1 1 0;
  min-width: 0;
  max-width: 180px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.global-alarm-tab--active {
  background: #fff1f2;
  color: #b91c1c;
  z-index: 1;
}

.global-alarm-tab:not(.global-alarm-tab--active):hover {
  background: #fecdd3;
}

.global-alarm-tab-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #dc2626;
  flex-shrink: 0;
}

.global-alarm-popup {
  width: 100%;
  border-radius: 0 8px 8px 8px;
  background: #fff;
  border: 1px solid #fecaca;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.32);
  overflow: hidden;
  position: relative;
  z-index: 0;
}

.global-alarm-head {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 14px;
  align-items: center;
  padding: 20px 22px;
  background: #fff1f2;
  border-bottom: 1px solid #fecaca;
}

.global-alarm-icon {
  width: 54px;
  height: 54px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: #fff;
  background: #dc2626;
  flex-shrink: 0;
}

.global-alarm-head p {
  margin: 0 0 3px;
  color: #b91c1c;
  font-weight: 900;
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.global-alarm-head h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  font-weight: 900;
}

.global-alarm-btn-all {
  padding: 10px 20px;
  border: 0;
  border-radius: 8px;
  background: #dc2626;
  color: #fff;
  font-weight: 900;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
}

.global-alarm-btn-all:hover { background: #b91c1c; }

.global-alarm-dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid #e2e8f0;
}

.global-alarm-dl div {
  padding: 14px;
  border-right: 1px solid #e2e8f0;
}

.global-alarm-dl div:last-child { border-right: 0; }

.global-alarm-dl dt {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.global-alarm-dl dd {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 900;
}

.global-alarm-msg {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px 22px;
}

.global-alarm-type {
  font-size: 22px;
  font-weight: 900;
  color: #0f172a;
  line-height: 1.2;
}

.global-alarm-msg p {
  margin: 0;
  font-size: 14px;
  color: #475569;
  font-weight: 600;
}

.global-alarm-time {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 700;
}

.global-alarm-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px 20px;
}

.global-alarm-btn-confirm {
  border: 0;
  border-radius: 8px;
  background: #dc2626;
  color: #fff;
  padding: 10px 28px;
  font-weight: 900;
  font-size: 15px;
  cursor: pointer;
}

.global-alarm-btn-confirm:hover { background: #b91c1c; }

@media (max-width: 720px) {
  .global-alarm-dl { grid-template-columns: 1fr 1fr; }
}
</style>
