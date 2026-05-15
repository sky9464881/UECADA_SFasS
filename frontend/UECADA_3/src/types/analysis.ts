export interface AnalysisResult {
  id: number
  vibrationWindowId: number | null
  equipmentCode: string
  analysisType: string
  resultJson: string | null
  measuredAt: string | null
  rms: number | null
  peakFrequency: number | null
  peakToPeak: number | null
  crestFactor: number | null
  kurtosis: number | null
  prediction: string | null
  confidence: number | null
  modelVersion: string | null
  modelInputType: string | null
  modelInputSize: number | null
  modelExpectedInputSize: number | null
  modelInputStrategy: string | null
  modelStatus: string | null
  anomalyScore: number | null
  alarmLevel: string | null
  createdAt: string | null
}
