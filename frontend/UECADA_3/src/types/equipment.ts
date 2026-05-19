export interface Equipment {
  id: number
  equipmentCode: string
  equipmentName: string
  processType: string
  model: string | null
  installDate: string | null
  location: string | null
  locationX: number | null
  locationY: number | null
  createdAt: string | null
  utilizationRate?: number | null
  defectCount?: number | null
  operatorName?: string | null
  cycleTimeSec?: number | null
  currentAmp?: number | null
  temperatureC?: number | null
  humidityPct?: number | null
  vibrationMmS?: number | null
}

export type EquipmentStatusCode = 'RUNNING' | 'STANDBY' | 'ALARM' | 'MAINTENANCE' | string

export interface EquipmentStatusItem {
  equipId: string
  statusCode: EquipmentStatusCode
  updatedAt: string | null
}
