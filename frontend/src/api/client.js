import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 8000
});

export async function fetchDatabaseStatus() {
  const response = await api.get('/database/status');
  return response.data;
}

export async function fetchEquipments() {
  const response = await api.get('/equipments');
  return response.data;
}

export async function fetchLatestRawWindow(equipmentCode) {
  try {
    const response = await api.get(`/equipments/${equipmentCode}/vibration-windows/latest/raw`, {
      params: { includeValues: false }
    });
    return response.data;
  } catch (error) {
    if (error.response && error.response.status === 404) {
      return { equipmentId: equipmentCode, values: [] };
    }
    throw error;
  }
}

export async function fetchRawVibrationSeries(equipmentCode, limit = 5, maxPoints = 8000) {
  try {
    const response = await api.get(`/equipments/${equipmentCode}/vibration-windows/raw-series`, {
      params: { limit, maxPoints }
    });
    return response.data;
  } catch (error) {
    if (error.response && error.response.status === 404) {
      return {
        equipmentId: equipmentCode,
        windowCount: 0,
        sampleCount: 0,
        originalSampleCount: 0,
        downsampled: false,
        points: []
      };
    }
    throw error;
  }
}

export async function fetchAnalysisResults(equipmentCode, limit = 300) {
  const response = await api.get(`/equipments/${equipmentCode}/analysis-results`, {
    params: { limit }
  });
  return response.data;
}

export async function fetchAlarms(limit = 100) {
  const response = await api.get('/alarms', {
    params: { limit }
  });
  return response.data;
}

export async function fetchAlarmFocusAnalysis(alarmId, paddingSeconds = 10, maxPoints = 40000) {
  const response = await api.get(`/alarms/${alarmId}/focus-analysis`, {
    params: { paddingSeconds, maxPoints }
  });
  return response.data;
}

export async function fetchAlarmFocusSelection(alarmId, startMillis, endMillis, maxSamples = 64000) {
  const response = await api.get(`/alarms/${alarmId}/focus-analysis/selection`, {
    params: { startMillis, endMillis, maxSamples }
  });
  return response.data;
}

export async function fetchDashboardSummary() {
  const response = await api.get('/dashboard/summary');
  return response.data;
}
