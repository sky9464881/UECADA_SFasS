<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AlertTriangle, Bell, X } from 'lucide-vue-next'
import { fetchAlarmsRaw, mapSeverityToType, mapStatusLabel, type AlarmResponse } from '@/api/alarmApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const SESSION_KEY = 'alarm-ack-ids'

function loadAcknowledgedIds(): Set<number> {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? new Set(JSON.parse(raw) as number[]) : new Set()
  } catch {
    return new Set()
  }
}

function persistAcknowledgedIds(set: Set<number>): void {
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify([...set]))
  } catch {}
}

const acknowledgedAlarmIds = ref<Set<number>>(loadAcknowledgedIds())
const selectedIndex = ref(0)

const alarmQuery = useQuery({
  queryKey: ['global-alarm-popup'],
  queryFn: () => fetchAlarmsRaw('OPEN'),
  enabled: computed(() => auth.isAuthenticated),
  refetchInterval: POLL_INTERVAL_MS.alarm,
  refetchIntervalInBackground: true,
  staleTime: 0,
})

const openUniqueAlarms = computed(() => {
  const map = new Map<number, AlarmResponse>()
  for (const alarm of alarmQuery.data.value ?? []) {
    if (alarm.status?.toUpperCase() !== 'OPEN') continue
    const sev = alarm.severity?.toUpperCase()
    if (sev !== 'DANGER' && sev !== 'CRITICAL') continue
    if (!map.has(alarm.alarmId)) map.set(alarm.alarmId, alarm)
  }
  return [...map.values()]
})

const pendingAlarms = computed(() =>
  openUniqueAlarms.value.filter((a) => !acknowledgedAlarmIds.value.has(a.alarmId)),
)

const activeAlarm = computed(() => pendingAlarms.value[selectedIndex.value] ?? null)

watch(pendingAlarms, (list) => {
  if (selectedIndex.value >= list.length) {
    selectedIndex.value = Math.max(0, list.length - 1)
  }
})

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
  const updated = new Set([...acknowledgedAlarmIds.value, activeAlarm.value.alarmId])
  acknowledgedAlarmIds.value = updated
  persistAcknowledgedIds(updated)
  if (selectedIndex.value >= pendingAlarms.value.length) {
    selectedIndex.value = Math.max(0, pendingAlarms.value.length - 1)
  }
}

function dismissAll(): void {
  const updated = new Set([
    ...acknowledgedAlarmIds.value,
    ...pendingAlarms.value.map((a) => a.alarmId),
  ])
  acknowledgedAlarmIds.value = updated
  persistAcknowledgedIds(updated)
}

function formatAlarmTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
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
            :key="alarm.alarmId"
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
              <p>Real-time Alarm · {{ selectedIndex + 1 }} / {{ pendingAlarms.length }}</p>
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
