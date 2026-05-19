<script setup lang="ts">
import { Gauge } from 'lucide-vue-next'
import type { EquipmentCategory } from '@/composables/useEquipmentCatalog'

const props = defineProps<{
  category: EquipmentCategory
}>()

// 0 div by zero 방지.
function pct(part: number): string {
  if (!props.category.count) return '0%'
  return `${(part / props.category.count) * 100}%`
}
</script>

<template>
  <article class="dashboard-panel category-monitor-panel">
    <div class="section-title-row">
      <div>
        <p class="panel-kicker">Category Summary</p>
        <h2>{{ category.name }} 주요 데이터 모니터링</h2>
      </div>
      <Gauge :size="22" />
    </div>

    <div class="category-summary-cards">
      <article>
        <span>운전 상태</span>
        <strong>{{ category.status }}</strong>
        <p>가동 {{ category.running }} · 정지 {{ category.stopped }} · 대기 {{ category.waiting }}</p>
      </article>
      <article>
        <span>평균 가동률</span>
        <strong>{{ category.avgRate }}%</strong>
        <p>{{ category.count }}대 설비 기준</p>
      </article>
      <article>
        <span>불량수량</span>
        <strong>{{ category.defectCount }}</strong>
        <p>금일 누적 NG 수량</p>
      </article>
    </div>

    <div class="category-status-bar">
      <i class="run" :style="{ width: pct(category.running) }"></i>
      <i class="stop" :style="{ width: pct(category.stopped) }"></i>
      <i class="wait" :style="{ width: pct(category.waiting) }"></i>
    </div>
  </article>
</template>
