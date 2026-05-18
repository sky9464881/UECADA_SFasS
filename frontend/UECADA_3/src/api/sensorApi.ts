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

const LATEST_VALUES_CHUNK_SIZE = 60

export async function fetchSensorLatestValues(bufferKeys: string[]): Promise<SensorBufferLatest[]> {
  const keys = [...new Set(bufferKeys.map((key) => key.trim()).filter(Boolean))]
  if (!keys.length) return []

  const chunks: string[][] = []
  for (let i = 0; i < keys.length; i += LATEST_VALUES_CHUNK_SIZE) {
    chunks.push(keys.slice(i, i + LATEST_VALUES_CHUNK_SIZE))
  }

  const results = await Promise.all(chunks.map((chunk) => fetchSensorLatestValuesChunk(chunk)))
  return results.flat()
}

async function fetchSensorLatestValuesChunk(bufferKeys: string[]): Promise<SensorBufferLatest[]> {
  try {
    const { data } = await api.post<SensorBufferLatest[]>('/api/sensors/latest-values', { bufferKeys })
    return data
  } catch (error) {
    const status = (error as { response?: { status?: number } }).response?.status
    if (status && ![404, 405, 415].includes(status)) throw error
    return fetchSensorLatestValuesChunkByGet(bufferKeys)
  }
}

async function fetchSensorLatestValuesChunkByGet(bufferKeys: string[]): Promise<SensorBufferLatest[]> {
  const params = new URLSearchParams()
  for (const key of bufferKeys) {
    params.append('bufferKeys', key)
  }

  const { data } = await api.get<SensorBufferLatest[]>('/api/sensors/latest-values', {
    params,
  })
  return data
}
