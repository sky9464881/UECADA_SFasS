import { api } from '@/api/client'

export interface VibrationWindowSummary {
  equipmentId: string
  timestamp: string | null
  samplingRate: number | null
  rpm: number | null
  windowSize: number | null
  windowIndex: number | null
  valuesLength: number
}

export interface VibrationFeatures {
  rms?: number | null
  peakFrequency?: number | null
  peakToPeak?: number | null
  crestFactor?: number | null
  kurtosis?: number | null
}

export interface VibrationFft {
  frequencyResolution?: number | null
  binCount?: number | null
  frequencies?: number[] | null
  magnitudes?: number[] | null
}

export interface VibrationAnalysis {
  equipmentId?: string | null
  timestamp?: string | null
  samplingRate?: number | null
  rpm?: number | null
  windowSize?: number | null
  windowIndex?: number | null
  features?: VibrationFeatures | null
  fft?: VibrationFft | null
  anomalyScore?: number | null
  alarmLevel?: string | null
  prediction?: string | null
  confidence?: number | null
  modelVersion?: string | null
  modelStatus?: string | null
}

export interface VibrationRealtimeResponse {
  received: boolean
  equipmentId: string
  receivedAt: string | null
  window: VibrationWindowSummary | null
  values: number[]
  analysis: VibrationAnalysis | null
}

export async function fetchRealtimeVibration(equipmentCode: string): Promise<VibrationRealtimeResponse> {
  const { data } = await api.get<VibrationRealtimeResponse>(
    `/api/vibration/realtime/${encodeURIComponent(equipmentCode)}`,
  )
  return data
}
