<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import AlarmPage from './components/AlarmPage.vue'
import CommunityPage from './components/CommunityPage.vue'
import DashboardPage from './components/DashboardPage.vue'
import EquipmentDetailPage from './components/EquipmentDetailPage.vue'
import FactoryLayoutPage from './components/FactoryLayoutPage.vue'
import LoginPage from './components/LoginPage.vue'
import SwmpTestPage from './components/SwmpTestPage.vue'
import UserManagementPage from './components/UserManagementPage.vue'

const routeHash = ref(window.location.hash || '#/dashboard')

const syncRoute = () => {
  routeHash.value = window.location.hash || '#/dashboard'
}

onMounted(() => {
  window.addEventListener('hashchange', syncRoute)
})

onUnmounted(() => {
  window.removeEventListener('hashchange', syncRoute)
})

const currentView = computed(() => {
  if (routeHash.value === '#/login') return 'login'
  if (routeHash.value === '#/layout') return 'layout'
  if (routeHash.value === '#/equipment') return 'equipment'
  if (routeHash.value === '#/alarms') return 'alarms'
  if (routeHash.value === '#/users') return 'users'
  if (routeHash.value === '#/community') return 'community'
  if (routeHash.value === '#/swmp-test') return 'swmp-test'
  return 'dashboard'
})
</script>

<template>
  <LoginPage v-if="currentView === 'login'" />
  <FactoryLayoutPage v-else-if="currentView === 'layout'" />
  <EquipmentDetailPage v-else-if="currentView === 'equipment'" />
  <AlarmPage v-else-if="currentView === 'alarms'" />
  <UserManagementPage v-else-if="currentView === 'users'" />
  <CommunityPage v-else-if="currentView === 'community'" />
  <SwmpTestPage v-else-if="currentView === 'swmp-test'" />
  <DashboardPage v-else />
</template>
