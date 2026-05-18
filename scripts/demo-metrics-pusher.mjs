#!/usr/bin/env node
/**
 * 1초마다 UECADA 백엔드에 변동 데모 KPI 를 POST 합니다.
 *
 * 사용:
 *   node scripts/demo-metrics-pusher.mjs
 *   API_BASE=http://localhost:8080 node scripts/demo-metrics-pusher.mjs
 *
 * 프론트 .env: VITE_REALTIME_DEMO=true, VITE_API_BASE_URL=http://localhost:8080
 */

const API_BASE = (process.env.API_BASE ?? 'http://localhost:8080').replace(/\/$/, '')
const INTERVAL_MS = Number(process.env.INTERVAL_MS ?? 1000)

function wave(base, amp, t, phase = 0) {
  return Math.round((base + amp * Math.sin(t + phase)) * 10) / 10
}

function buildPayload(t) {
  const lines = ['LINE-01', 'LINE-02', 'LINE-03'].map((lineId, i) => {
    const upmh = wave(1200, 80, t, i)
    const uph = wave(500, 40, t, i + 1)
    return {
      lineId,
      balanceRate: wave(85, 8, t, i + 2),
      uph,
      upmh,
      productivity: Math.min(100, Math.round(upmh / 13)),
      stationUtilization: [1, 2, 3, 4, 5, 6].map((n) => wave(75 + n, 12, t, i + n)),
    }
  })

  const equipments = [
    'LINE-01_CAST-01',
    'LINE-01_CNC-02',
    'LINE-02_CNC-01',
  ].map((equipmentCode, i) => ({
    equipmentCode,
    utilizationRate: wave(88, 12, t, i),
    defectCount: Math.max(0, Math.round(3 + 2 * Math.sin(t + i))),
    operatorName: ['김주조', '박가공', '이가공'][i],
    cycleTimeSec: wave(50, 8, t, i),
    currentAmp: wave(40, 10, t, i),
    temperatureC: wave(45, 15, t, i + 1),
    humidityPct: wave(48, 6, t, i + 2),
    vibrationMmS: wave(1.2, 0.8, t, i),
  }))

  return { lines, equipments }
}

async function push(body) {
  const res = await fetch(`${API_BASE}/api/demo/metrics/push`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(`${res.status} ${text}`)
  }
  return res.json()
}

let tick = 0
console.log(`Pushing demo metrics every ${INTERVAL_MS}ms → ${API_BASE}/api/demo/metrics/push`)

const timer = setInterval(async () => {
  tick += 1
  const t = tick / 3
  try {
    const result = await push(buildPayload(t))
    if (tick % 5 === 0) {
      console.log(`#${tick}`, result)
    }
  } catch (err) {
    console.error('push failed:', err.message)
  }
}, INTERVAL_MS)

process.on('SIGINT', () => {
  clearInterval(timer)
  console.log('\nStopped.')
  process.exit(0)
})
