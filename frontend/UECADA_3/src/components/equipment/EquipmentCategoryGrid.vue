<script setup lang="ts">
import type { Component } from 'vue'
import type { EquipmentCategory } from '@/composables/useEquipmentCatalog'

export interface CategoryWithIcon extends EquipmentCategory {
  icon: Component
}

defineProps<{
  categories: CategoryWithIcon[]
  selectedCategoryId: string
}>()

defineEmits<{
  (e: 'select', category: CategoryWithIcon): void
}>()
</script>

<template>
  <div class="equipment-category-grid">
    <button
      v-for="category in categories"
      :key="category.id"
      :class="{ active: category.id === selectedCategoryId }"
      type="button"
      @click="$emit('select', category)"
    >
      <span class="equipment-category-icon-wrap" aria-hidden="true">
        <component :is="category.icon" :size="22" :stroke-width="2" />
      </span>
      <strong>{{ category.name }}</strong>
      <span>{{ category.count }}대 · {{ category.status }}</span>
      <p>{{ category.description }}</p>
    </button>
  </div>
</template>
