<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AlertTriangle, Bell, X } from 'lucide-vue-next'
import { fetchAlarmsRaw, mapSeverityToType, mapStatusLabel, type AlarmResponse } from '@/api/alarmApi'
import { POLL_INTERVAL_MS } from '@/constants/polling'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const acknowledgedAlarmSignatures = ref(new Set<string>())
const activeAlarmSignature = ref('')

const alarmQuery = useQuery({
  queryKey: ['global-alarm-popup'],
  queryFn: fetchAlarmsRaw,
  enabled: computed(() => auth.isAuthenticated),
  refetchInterval: POLL_INTERVAL_MS.alarm,
  refetchIntervalInBackground: true,
  staleTime: 0,
})

function alarmSignature(alarm: AlarmResponse): string {
  return [
    alarm.equipmentCode ?? '-',
    alarm.alarmType ?? '-',
    alarm.alarmMessage ?? '-',
    alarm.severity ?? '-',
  ].join('|')
}

const openUniqueAlarms = computed(() => {
  const map = new Map<string, AlarmResponse>()
  for (const alarm of alarmQuery.data.value ?? []) {
    if (alarm.status?.toUpperCase() !== 'OPEN') continue
    const signature = alarmSignature(alarm)
    if (!map.has(signature)) {
      map.set(signature, alarm)
    }
  }
  return [...map.values()]
})

const activeAlarm = computed(() =>
  openUniqueAlarms.value.find((alarm) => alarmSignature(alarm) === activeAlarmSignature.value) ?? null,
)

watch(
  openUniqueAlarms,
  (alarms) => {
    if (!alarms.length) {
      activeAlarmSignature.value = ''
      return
    }
    if (activeAlarm.value) return
    const next = alarms.find((alarm) => !acknowledgedAlarmSignatures.value.has(alarmSignature(alarm)))
    if (next) {
      activeAlarmSignature.value = alarmSignature(next)
    }
  },
  { immediate: true },
)

function dismissAlarmPopup() {
  if (activeAlarm.value) {
    acknowledgedAlarmSignatures.value = new Set([
      ...acknowledgedAlarmSignatures.value,
      alarmSignature(activeAlarm.value),
    ])
  }
  activeAlarmSignature.value = ''
}

function formatAlarmTime(value: string | null | undefined): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}
</script>

<template>
  <RouterView />
  <Teleport to="body">
    <div v-if="activeAlarm" class="global-alarm-backdrop" role="presentation">
      <article class="global-alarm-popup" role="alertdialog" aria-modal="true" aria-label="실시간 알람 발생">
        <header>
          <span class="global-alarm-icon">
            <AlertTriangle :size="32" />
          </span>
          <div>
            <p>Real-time Alarm</p>
            <h2>실시간 알람 발생</h2>
          </div>
          <button type="button" aria-label="알람 팝업 닫기" @click="dismissAlarmPopup">
            <X :size="20" />
          </button>
        </header>

        <dl>
          <div>
            <dt>설비</dt>
            <dd>{{ activeAlarm.equipmentCode }}</dd>
          </div>
          <div>
            <dt>등급</dt>
            <dd>{{ mapSeverityToType(activeAlarm.severity) }}</dd>
          </div>
          <div>
            <dt>상태</dt>
            <dd>{{ mapStatusLabel(activeAlarm.status) }}</dd>
          </div>
          <div>
            <dt>발생 시각</dt>
            <dd>{{ formatAlarmTime(activeAlarm.occurredAt) }}</dd>
          </div>
        </dl>

        <section>
          <Bell :size="20" />
          <strong>{{ activeAlarm.alarmType || '알람' }}</strong>
          <p>{{ activeAlarm.alarmMessage || '확인이 필요한 알람이 발생했습니다.' }}</p>
        </section>

        <footer>
          <button type="button" @click="dismissAlarmPopup">확인</button>
        </footer>
      </article>
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
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(3px);
}

.global-alarm-popup {
  width: min(640px, calc(100vw - 48px));
  border-radius: 8px;
  background: #fff;
  border: 1px solid #fecaca;
  box-shadow: 0 28px 70px rgba(15, 23, 42, 0.32);
  overflow: hidden;
}

.global-alarm-popup header {
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
}

.global-alarm-popup header p {
  margin: 0 0 3px;
  color: #b91c1c;
  font-weight: 900;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.global-alarm-popup header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
  font-weight: 900;
}

.global-alarm-popup header button {
  border: 0;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  display: grid;
  place-items: center;
}

.global-alarm-popup dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid #e2e8f0;
}

.global-alarm-popup dl div {
  padding: 14px;
  border-right: 1px solid #e2e8f0;
}

.global-alarm-popup dl div:last-child {
  border-right: 0;
}

.global-alarm-popup dt {
  margin: 0 0 6px;
  color: #64748b;
  font-size: 12px;
  font-weight: 900;
}

.global-alarm-popup dd {
  margin: 0;
  color: #0f172a;
  font-size: 17px;
  font-weight: 900;
}

.global-alarm-popup section {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 10px;
  padding: 18px 22px;
}

.global-alarm-popup section strong {
  font-size: 18px;
}

.global-alarm-popup section p {
  grid-column: 2;
  margin: 0;
  color: #475569;
  font-weight: 700;
}

.global-alarm-popup footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 22px 20px;
}

.global-alarm-popup footer button {
  border: 0;
  border-radius: 8px;
  background: #dc2626;
  color: #fff;
  padding: 10px 22px;
  font-weight: 900;
}

@media (max-width: 720px) {
  .global-alarm-popup dl {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
