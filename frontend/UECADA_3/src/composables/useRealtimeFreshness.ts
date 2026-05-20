import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * 폴링 기반 실시간 데이터의 수신 시각·지연(stale) 여부를 1초 단위로 갱신한다.
 */
export function useRealtimeFreshness(dataUpdatedAt: Ref<number>, pollIntervalMs: number) {
  const tick = ref(Date.now())
  let timer: ReturnType<typeof setInterval> | null = null

  onMounted(() => {
    timer = setInterval(() => {
      tick.value = Date.now()
    }, 1000)
  })

  onBeforeUnmount(() => {
    if (timer) clearInterval(timer)
  })

  const ageSec = computed(() => {
    const at = dataUpdatedAt.value
    if (!at) return null
    return Math.max(0, Math.floor((tick.value - at) / 1000))
  })

  const isStale = computed(() => {
    const at = dataUpdatedAt.value
    if (!at) return false
    return tick.value - at > pollIntervalMs * 2
  })

  const lastRxLabel = computed(() => {
    const at = dataUpdatedAt.value
    if (!at) return null
    const d = new Date(at)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  })

  return { ageSec, isStale, lastRxLabel }
}
