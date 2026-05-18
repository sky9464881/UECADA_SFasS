import { get } from './index.js'

export const getRealtimeVibration = (equipmentCode) =>
  get(`/api/vibration/realtime/${encodeURIComponent(equipmentCode)}`)

export const getRealtimeVibrations = () =>
  get('/api/vibration/realtime')
