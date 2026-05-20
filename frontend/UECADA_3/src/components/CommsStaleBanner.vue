<script setup lang="ts">
defineProps<{
  show: boolean
  ageSec: number | null
  lastRxLabel?: string | null
  label?: string
}>()
</script>

<template>
  <div
    v-if="show"
    class="comms-stale-banner"
    role="alert"
    aria-live="polite"
  >
    <strong>{{ label ?? '실시간 통신 지연' }}</strong>
    <span v-if="ageSec != null">마지막 수신 {{ ageSec }}초 전</span>
    <span v-if="lastRxLabel" class="comms-stale-banner__time">({{ lastRxLabel }})</span>
  </div>
</template>
