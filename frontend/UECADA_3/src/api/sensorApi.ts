import { api } from '@/api/client'

export interface SensorFrame {
  timestampMs: number
  value: number
}

export interface SensorBufferLatest {
  bufferKey: string
  size: number
  capacity: number
  latest: SensorFrame | null
}

export async function fetchSensorLatestValues(bufferKeys: string[]): Promise<SensorBufferLatest[]> {
  if (!bufferKeys.length) return []
  const { data } = await api.get<SensorBufferLatest[]>('/api/sensors/latest-values', {
    params: { bufferKeys: bufferKeys.join(',') },
  })
  return data
}
