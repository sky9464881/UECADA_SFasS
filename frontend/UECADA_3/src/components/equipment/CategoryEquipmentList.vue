<script setup lang="ts">
import { Activity } from 'lucide-vue-next'
import type { EquipmentCategory, EquipmentDetailItem } from '@/composables/useEquipmentCatalog'

defineProps<{
  category: EquipmentCategory
  selectedEquipmentId: string
}>()

defineEmits<{
  (e: 'select', equipmentId: string): void
}>()

function stateClass(equipment: EquipmentDetailItem): string {
  if (equipment.state === '정지') return 'stop'
  if (equipment.state === '대기') return 'warn'
  return 'run'
}
</script>

<template>
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
        v-for="equipment in category.equipment"
        :key="equipment.id"
        :class="{ active: equipment.id === selectedEquipmentId }"
        type="button"
        @click="$emit('select', equipment.id)"
      >
        <span :class="['equipment-state-dot', stateClass(equipment)]"></span>
        <div>
          <strong>{{ equipment.id }}</strong>
          <p>{{ equipment.name }} · {{ equipment.line }}</p>
        </div>
        <b>{{ equipment.rate }}%</b>
      </button>
    </div>
  </aside>
</template>
