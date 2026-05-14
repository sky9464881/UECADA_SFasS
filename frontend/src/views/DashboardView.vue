<template>
  <div class="dashboard-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M3 21V10.5l5 2.8V10l5 3.3V10l5 3.2V21H3z"></path>
            <path d="M4.5 10.5V5h3v7.2"></path>
            <path d="M7 21v-4h3v4"></path>
            <path d="M13 17h2M17 17h2M13 20h2M17 20h2"></path>
          </svg>
        </span>
        <span class="brand-text">{{ t('brand') }}</span>
      </div>
      <nav class="nav-section">
        <button
          v-for="item in navItems"
          :key="item.key"
          :class="['nav-item', activeNav === item.key ? 'active' : '']"
          type="button"
          @click="selectNav(item)"
        >
          {{ t(item.labelKey) }}
        </button>
      </nav>
      <div class="side-foot">
        <span :class="['status-dot', dbStatus.connected ? 'ok' : 'warn']"></span>
        <span>{{ dbStatus.connected ? t('dbConnected') : t('dbOffline') }}</span>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <div>
          <div class="eyebrow">{{ t('eyebrow') }}</div>
          <h1>{{ activeTitle }}</h1>
        </div>
        <div class="toolbar">
          <select v-model="language" class="easy-select lang-select">
            <option value="ko">한국어</option>
            <option value="en">English</option>
          </select>
          <button
            :class="['icon-action', autoRefresh ? 'active' : '']"
            type="button"
            :title="autoRefresh ? t('autoOn') : t('autoOff')"
            :aria-label="autoRefresh ? t('autoOn') : t('autoOff')"
            @click="toggleAutoRefresh"
          >
            <svg v-if="autoRefresh" viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 5h3v14H8zM13 5h3v14h-3z"></path>
            </svg>
            <svg v-else viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 5v14l11-7z"></path>
            </svg>
          </button>
          <button
            :class="['icon-action', isRefreshing ? 'spinning' : '']"
            type="button"
            :disabled="isRefreshing"
            :title="isRefreshing ? t('refreshing') : t('refresh')"
            :aria-label="isRefreshing ? t('refreshing') : t('refresh')"
            @click="loadDashboard"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M19 8a7 7 0 0 0-12.1-3.9L5 6"></path>
              <path d="M5 3v3h3"></path>
              <path d="M5 16a7 7 0 0 0 12.1 3.9L19 18"></path>
              <path d="M19 21v-3h-3"></path>
            </svg>
          </button>
        </div>
      </header>

      <section v-if="isEquipmentScopedSegment" class="equipment-control-strip">
        <div class="equipment-control-copy">
          <span>{{ t('equipmentSelector') }}</span>
          <strong>{{ selectedEquipment || '-' }}</strong>
          <em>{{ selectedEquipmentName }} · {{ latestRaw.rpm || '-' }} RPM · {{ latestRaw.samplingRate || '-' }} Hz</em>
        </div>
        <select v-model="selectedEquipment" class="easy-select equipment-select" @change="selectEquipment(selectedEquipment)">
          <option v-for="equipment in equipments" :key="equipment.equipmentCode" :value="equipment.equipmentCode">
            {{ equipment.equipmentCode }} · {{ displayEquipmentName(equipment) }}
          </option>
        </select>
      </section>

      <section v-if="isFleetSegment" class="system-strip fleet-strip">
        <article class="system-card">
          <span class="system-label">{{ t('fleetStatus') }}</span>
          <strong>{{ summary.equipmentCount || equipments.length }}</strong>
          <small>{{ t('equipmentUnit') }} · {{ t('allEquipmentOverview') }}</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('alarmSummary') }}</span>
          <strong>{{ alarmCounts.warning + alarmCounts.danger }}</strong>
          <small>{{ t('warning') }} {{ alarmCounts.warning }} · {{ t('danger') }} {{ alarmCounts.danger }}</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('windowsStored') }}</span>
          <strong>{{ summary.recentAnalysisCount || 0 }}</strong>
          <small>{{ t('recentAlarms') }} {{ summary.recentAlarmCount || alarms.length }} {{ t('alarmUnit') }}</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('serverStatus') }}</span>
          <strong>{{ dbStatus.connected ? 'OK' : 'CHECK' }}</strong>
          <small>Spring Boot · FastAPI · MySQL</small>
        </article>
      </section>

      <section v-if="isEquipmentScopedSegment" class="system-strip">
        <article class="system-card">
          <span class="system-label">{{ t('collectionStatus') }}</span>
          <strong :class="['alarm-text', latestAnalysis.alarmLevel || 'normal']">{{ tLevel(latestAnalysis.alarmLevel) }}</strong>
          <small>{{ t('lastUpdated') }} {{ formattedLastUpdated }}</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('windowsStored') }}</span>
          <strong>{{ summary.recentAnalysisCount || analysisResults.length }}</strong>
          <small>
            {{ t('rawWindows') }} {{ rawSeries.windowCount || 0 }} ·
            {{ t('displaySamples') }} {{ rawSeries.sampleCount || 0 }}/{{ rawSeries.originalSampleCount || rawSeries.sampleCount || 0 }}
          </small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('latestEquipment') }}</span>
          <strong>{{ selectedEquipment || '-' }}</strong>
          <small>{{ selectedEquipmentName }} · {{ latestRaw.rpm || '-' }} RPM · {{ latestRaw.samplingRate || '-' }} Hz</small>
        </article>
        <article class="system-card">
          <span class="system-label">{{ t('serverStatus') }}</span>
          <strong>{{ dbStatus.connected ? 'OK' : 'CHECK' }}</strong>
          <small>Spring Boot · FastAPI · MySQL</small>
        </article>
      </section>

      <section v-if="isEquipmentScopedSegment" class="summary-line">
        <div class="summary-pill">
          <span class="label">{{ t('latestWindow') }}</span>
          <strong>#{{ latestRaw.windowIndex ?? '-' }}</strong>
        </div>
        <div class="summary-pill">
          <span class="label">{{ t('rms') }}</span>
          <strong>{{ formatNumber(latestAnalysis.rms, 5) }}</strong>
        </div>
        <div class="summary-pill">
          <span class="label">{{ t('peakFrequency') }}</span>
          <strong>{{ formatNumber(latestAnalysis.peakFrequency, 2) }} Hz</strong>
        </div>
        <div class="summary-pill">
          <span class="label">{{ t('anomalyScore') }}</span>
          <strong :class="['alarm-text', latestAnalysis.alarmLevel || 'normal']">
            {{ formatNumber(latestAnalysis.anomalyScore, 4) }}
          </strong>
        </div>
      </section>

      <section v-show="isSegmentVisible('raw')" class="hero-chart">
        <div class="raw-chart-card">
          <EChartPanel :option="rawSignalOption" />
        </div>
      </section>

      <section v-show="isSegmentVisible('fft')" class="metric-workspace">
        <div class="fft-analysis-grid">
          <div class="fft-metric-column">
            <div class="kpi-grid">
              <article
                v-for="metric in metricCards"
                :key="metric.key"
                :class="['metric-card', selectedMetricKey === metric.key ? 'selected' : '']"
                role="button"
                tabindex="0"
                :aria-label="`${metric.label} ${t('detailAnalysis')}`"
                @click="selectMetric(metric.key)"
                @keydown.enter.prevent="selectMetric(metric.key)"
                @keydown.space.prevent="selectMetric(metric.key)"
              >
                <span
                  class="metric-hit-area"
                  aria-hidden="true"
                  @click.stop="selectMetric(metric.key)"
                  @mouseenter="showMetricTooltip(metric.key, $event)"
                  @mousemove="moveMetricTooltip($event)"
                  @mouseleave="hideMetricTooltip"
                ></span>
                <div class="metric-rank">{{ metric.rank }}</div>
                <span class="metric-open-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24">
                    <path d="M8 5l8 7-8 7z"></path>
                  </svg>
                </span>
                <div class="metric-title">{{ metric.label }}</div>
                <div class="metric-value">{{ metric.value }}</div>
                <div class="mini-chart">
                  <EChartPanel :option="metric.option" />
                </div>
              </article>
            </div>

            <article class="monitor-panel focus-launch-panel">
              <header class="panel-heading">
                <div>
                  <span>{{ t('focusAnalysis') }}</span>
                  <strong>{{ t('currentFocusTitle') }}</strong>
                </div>
                <em>{{ selectedEquipment || '-' }}</em>
              </header>
              <div class="focus-launch-body">
                <p>{{ t('currentFocusHelp') }}</p>
                <button
                  class="focus-open-button"
                  type="button"
                  @click="openCurrentFocusAnalysis"
                >
                  {{ t('openCurrentFocus') }}
                </button>
              </div>
            </article>
          </div>

          <aside class="monitor-panel ai-analysis-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('aiAnalysis') }}</span>
                <strong>{{ formatPrediction(latestAnalysis.prediction) }}</strong>
              </div>
              <em>{{ t('modelStatus') }} · {{ tModelStatus(latestAnalysis.modelStatus) }}</em>
            </header>

            <div class="confidence-gauge">
              <EChartPanel :option="confidenceGaugeOption" />
            </div>

            <div class="ai-fact-grid">
              <div>
                <span>{{ t('rawPrediction') }}</span>
                <strong>{{ latestAnalysis.prediction || '-' }}</strong>
              </div>
              <div>
                <span>{{ t('alarmDecision') }}</span>
                <strong :class="['alarm-text', latestAnalysis.alarmLevel || 'normal']">{{ tLevel(latestAnalysis.alarmLevel) }}</strong>
              </div>
              <div>
                <span>{{ t('modelVersion') }}</span>
                <strong>{{ latestAnalysis.modelVersion || '-' }}</strong>
              </div>
              <div>
                <span>{{ t('modelInputType') }}</span>
                <strong>{{ latestAnalysis.modelInputType || '-' }}</strong>
              </div>
              <div>
                <span>{{ t('modelExpectedInput') }}</span>
                <strong>{{ latestAnalysis.modelExpectedInputSize || '-' }}</strong>
              </div>
              <div>
                <span>{{ t('inputStrategy') }}</span>
                <strong>{{ latestAnalysis.modelInputStrategy || '-' }}</strong>
              </div>
            </div>

            <p v-if="predictionNote" class="ai-model-note">{{ predictionNote }}</p>
            <p class="ai-model-note muted">{{ t('modelConfidenceHelp') }}</p>
          </aside>
        </div>
      </section>

      <section v-show="isSegmentVisible('overview')" class="overview-workspace">
        <div class="overview-grid">
          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('fleetStatus') }}</span>
                <strong>{{ t('equipmentDistribution') }}</strong>
              </div>
              <em>{{ summary.equipmentCount || equipments.length }} {{ t('equipmentUnit') }}</em>
            </header>
            <EChartPanel :option="statusPieOption" />
          </article>

          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('alarmInsight') }}</span>
                <strong>{{ t('alarmDistribution') }}</strong>
              </div>
              <em>{{ summary.recentAlarmCount || alarms.length }} {{ t('alarmUnit') }}</em>
            </header>
            <EChartPanel :option="alarmPieOption" />
          </article>

          <article class="monitor-panel equipment-health-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('fleetStatus') }}</span>
                <strong>{{ t('equipmentLatestStatus') }}</strong>
              </div>
              <em>{{ formattedLastUpdated }}</em>
            </header>
            <div class="equipment-health-list">
	              <button
	                v-for="row in equipmentHealthRows"
	                :key="row.equipmentCode"
	                class="equipment-health-row"
	                type="button"
	                @click="selectEquipment(row.equipmentCode, 'raw')"
	              >
                <span class="equipment-code">{{ row.equipmentCode }}</span>
                <span class="equipment-name">{{ row.displayName }}</span>
                <span :class="['level-badge', row.alarmLevel]">{{ tLevel(row.alarmLevel) }}</span>
                <span class="equipment-reading">{{ t('rms') }} {{ formatNumber(row.rms, 4) }}</span>
                <span class="equipment-reading">{{ t('anomalyScore') }} {{ formatNumber(row.anomalyScore, 3) }}</span>
              </button>
            </div>
          </article>
        </div>

        <div class="overview-grid bottom">
          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('fleetStatus') }}</span>
                <strong>{{ t('fleetScoreTitle') }}</strong>
              </div>
              <em>{{ t('anomalyScore') }}</em>
            </header>
            <EChartPanel :option="fleetScoreBarOption" />
          </article>

          <article class="monitor-panel alarm-feed-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('recentAlarms') }}</span>
                <strong>{{ t('latestAlarmFeed') }}</strong>
              </div>
              <em>{{ alarms.length }} {{ t('rowsUnit') }}</em>
            </header>
            <div v-if="actualAlarmRows.length === 0" class="empty-state">
              {{ t('noAlarm') }}
            </div>
            <div v-else class="alarm-feed-list">
              <button
                v-for="alarm in recentAlarmItems"
                :key="alarm.id"
                class="alarm-feed-item"
                type="button"
                :title="t('clickAlarmForFocus')"
                @click="openFocusAnalysis(alarm)"
              >
                <span :class="['level-badge', alarm.rawLevel]">{{ alarm.alarmLevel }}</span>
                <strong>{{ alarm.equipmentCode }}</strong>
                <span>{{ alarm.displayMessage }}</span>
                <time>{{ alarm.occurredAt }}</time>
              </button>
            </div>
          </article>
        </div>
      </section>

      <section v-show="isSegmentVisible('alarms')" class="alarm-workspace">
        <div class="alarm-summary-grid">
          <article class="monitor-panel chart-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('alarmInsight') }}</span>
                <strong>{{ t('alarmDistribution') }}</strong>
              </div>
              <em>{{ summary.recentAlarmCount || alarms.length }} {{ t('alarmUnit') }}</em>
            </header>
            <EChartPanel :option="alarmPieOption" />
          </article>
          <article class="monitor-panel alarm-count-panel">
            <header class="panel-heading">
              <div>
                <span>{{ t('recentAlarms') }}</span>
                <strong>{{ t('alarmSummary') }}</strong>
              </div>
              <em>{{ formattedLastUpdated }}</em>
            </header>
            <div class="alarm-count-grid">
              <div>
                <span>{{ t('danger') }}</span>
                <strong class="alarm-text danger">{{ alarmCounts.danger }}</strong>
              </div>
              <div>
                <span>{{ t('warning') }}</span>
                <strong class="alarm-text warning">{{ alarmCounts.warning }}</strong>
              </div>
              <div>
                <span>{{ t('normal') }}</span>
                <strong class="alarm-text normal">{{ alarmCounts.normal }}</strong>
              </div>
            </div>
          </article>
        </div>

        <article class="monitor-panel alarm-table-panel">
          <header class="panel-heading">
            <div>
              <span>{{ t('recentAlarms') }}</span>
              <strong>{{ t('alarmHistoryTable') }}</strong>
            </div>
            <em>{{ actualAlarmRows.length }} {{ t('rowsUnit') }}</em>
          </header>
          <div v-if="actualAlarmRows.length === 0" class="empty-state">
            {{ t('noAlarm') }}
          </div>
          <div v-else class="alarm-table-wrap">
            <table class="alarm-table">
              <thead>
                <tr>
                  <th>{{ t('occurredAt') }}</th>
                  <th>{{ t('endedAt') }}</th>
                  <th>{{ t('duration') }}</th>
                  <th>{{ t('equipment') }}</th>
                  <th>{{ t('level') }}</th>
                  <th>{{ t('status') }}</th>
                  <th>{{ t('evidence') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="alarm in actualAlarmRows"
                  :key="alarm.id"
                  class="clickable-row"
                  :title="t('clickAlarmForFocus')"
                  @click="openFocusAnalysis(alarm)"
                >
                  <td>{{ alarm.occurredAt }}</td>
                  <td>{{ alarm.endedAt }}</td>
                  <td>{{ alarm.duration }}</td>
                  <td>{{ alarm.equipmentCode }}</td>
                  <td><span :class="['level-badge', alarm.rawLevel]">{{ alarm.alarmLevel }}</span></td>
                  <td><span :class="['status-badge', alarm.rawStatus]">{{ alarm.status }}</span></td>
                  <td>{{ alarm.displayMessage }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </section>

      <div
        v-if="metricHover.visible && !showMetricDetail"
        class="metric-hover-tooltip"
        :style="metricHoverStyle"
      >
        <strong>{{ hoverMetricSpec.label }}</strong>
        <span>{{ t('current') }} {{ formatNumber(hoverMetricStats.current, hoverMetricSpec.decimals) }}{{ hoverMetricSpec.unit ? ` ${hoverMetricSpec.unit}` : '' }}</span>
        <span>{{ t('average') }} {{ formatNumber(hoverMetricStats.average, hoverMetricSpec.decimals) }}{{ hoverMetricSpec.unit ? ` ${hoverMetricSpec.unit}` : '' }}</span>
        <span>{{ t('minimum') }} {{ formatNumber(hoverMetricStats.minimum, hoverMetricSpec.decimals) }} / {{ t('maximum') }} {{ formatNumber(hoverMetricStats.maximum, hoverMetricSpec.decimals) }}</span>
      </div>

      <div v-if="showMetricDetail" class="metric-expanded-backdrop" @click.self="closeMetricDetail">
        <section class="metric-expanded-panel" role="dialog" aria-modal="true" :aria-label="metricDetailTitle">
          <header class="metric-expanded-header">
            <div>
              <span class="metric-expanded-kicker">{{ t('fftFeatures') }}</span>
              <h2>{{ metricDetailTitle }}</h2>
              <p>{{ t('expandedHint') }}</p>
            </div>
            <button class="icon-action metric-close-button" type="button" :title="t('close')" :aria-label="t('close')" @click="closeMetricDetail">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12"></path>
                <path d="M18 6L6 18"></path>
              </svg>
            </button>
          </header>
          <div class="metric-stat-strip expanded">
            <div class="metric-stat">
              <span>{{ t('current') }}</span>
              <strong>{{ formatNumber(metricDetailStats.current, selectedMetricSpec.decimals) }}</strong>
            </div>
            <div class="metric-stat">
              <span>{{ t('average') }}</span>
              <strong>{{ formatNumber(metricDetailStats.average, selectedMetricSpec.decimals) }}</strong>
            </div>
            <div class="metric-stat">
              <span>{{ t('minimum') }}</span>
              <strong>{{ formatNumber(metricDetailStats.minimum, selectedMetricSpec.decimals) }}</strong>
            </div>
            <div class="metric-stat">
              <span>{{ t('maximum') }}</span>
              <strong>{{ formatNumber(metricDetailStats.maximum, selectedMetricSpec.decimals) }}</strong>
            </div>
          </div>
          <EChartPanel :option="metricDetailOption" />
        </section>
      </div>

      <div v-if="showFocusModal" class="focus-modal-backdrop" @click.self="closeFocusModal">
        <section class="focus-modal-panel" role="dialog" aria-modal="true" :aria-label="t('focusAnalysis')">
          <header class="metric-expanded-header">
            <div>
              <span class="metric-expanded-kicker">{{ t('focusAnalysis') }}</span>
              <h2>{{ focusModalTitle }}</h2>
              <p>{{ focusAnalysis ? formatFocusRange(focusAnalysis) : t('loading') }}</p>
            </div>
            <button class="icon-action metric-close-button" type="button" :title="t('close')" :aria-label="t('close')" @click="closeFocusModal">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M6 6l12 12"></path>
                <path d="M18 6L6 18"></path>
              </svg>
            </button>
          </header>

          <div v-if="focusLoading" class="focus-loading">{{ t('loading') }}...</div>
          <div v-else-if="focusAnalysis" class="focus-modal-body">
            <div class="focus-main-grid">
              <article class="monitor-panel focus-raw-panel">
                <header class="panel-heading">
                  <div>
                    <span>{{ t('focusRange') }}</span>
                      <strong>{{ focusMode === 'alarm' ? t('focusRawTitle') : t('currentFocusRawTitle') }}</strong>
                  </div>
                  <em>{{ focusAnalysis.sampleCount }}/{{ focusAnalysis.originalSampleCount }} {{ t('samples') }}</em>
                </header>
                <EChartPanel :option="focusRawOption" @datazoom="handleFocusRawZoom" />
              </article>

              <aside class="monitor-panel focus-side-panel">
                <header class="panel-heading">
                  <div>
                    <span>{{ t('selectedRange') }}</span>
                    <strong>{{ focusSelectionRangeText }}</strong>
                  </div>
                  <em>{{ focusSelectionLoading ? t('loading') : t('exactRange') }}</em>
                </header>

                <div v-if="focusSelection" class="focus-selection-grid">
                  <div>
                    <span>{{ t('rms') }}</span>
                    <strong>{{ formatNumber(focusSelection.features?.rms, 5) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('peakToPeak') }}</span>
                    <strong>{{ formatNumber(focusSelection.features?.peakToPeak, 5) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('crestFactor') }}</span>
                    <strong>{{ formatNumber(focusSelection.features?.crestFactor, 3) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('kurtosis') }}</span>
                    <strong>{{ formatNumber(focusSelection.features?.kurtosis, 3) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('aiPrediction') }}</span>
                    <strong>{{ formatPrediction(focusSelection.prediction) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('confidence') }}</span>
                    <strong>{{ formatPercent(focusSelection.confidence) }}</strong>
                  </div>
                  <div>
                    <span>{{ t('samplesAnalyzed') }}</span>
                    <strong>{{ focusSelection.sampleCount }}</strong>
                  </div>
                  <div>
                    <span>{{ t('originalSamples') }}</span>
                    <strong>{{ focusSelection.originalSampleCount }}</strong>
                  </div>
                </div>
                <p v-else class="focus-help-text">{{ t('noSelection') }}</p>
              </aside>
            </div>

            <div class="focus-chart-grid">
              <article class="monitor-panel focus-fft-panel">
                <header class="panel-heading">
                  <div>
                    <span>{{ t('selectedRange') }}</span>
                    <strong>{{ t('selectedFft') }}</strong>
                  </div>
                  <em>{{ focusSelection?.downsampled ? t('downsampled') : '-' }}</em>
                </header>
                <EChartPanel :option="focusFftOption" />
              </article>

              <article class="monitor-panel focus-trend-panel">
                <header class="panel-heading">
                  <div>
                    <span>{{ focusAnalysis.equipmentCode }}</span>
                    <strong>{{ t('focusTrendTitle') }}</strong>
                  </div>
                  <em>{{ focusAnalysis.analysisTrend.length }} {{ t('windowUnit') }}</em>
                </header>
                <EChartPanel :option="focusFeatureTrendOption" />
              </article>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script>
import EChartPanel from '../components/EChartPanel.vue';
import {
  fetchAlarmFocusAnalysis,
  fetchAlarmFocusSelection,
  fetchAlarms,
  fetchAnalysisResults,
  fetchDashboardSummary,
  fetchDatabaseStatus,
  fetchEquipments,
  fetchLatestRawWindow,
  fetchRawVibrationSeries
} from '../api/client';

const alarmColor = {
  normal: '#1f9d55',
  warning: '#f6a609',
  danger: '#d64545',
  unknown: '#7a8699'
};

const messages = {
  ko: {
    brand: 'PHM 모니터',
    search: '리포트 및 설비 검색',
    dbConnected: 'MySQL 연결됨',
    dbOffline: 'DB 연결 확인',
    eyebrow: '로컬 스마트팩토리 PHM MVP',
    dashboard: '대시보드',
    realtime: '실시간',
    vibration: '진동 신호',
    fft: 'FFT 분석',
    alarms: '알람 이력',
    focusAnalysis: '집중 분석',
    overview: '개요',
    raw: '원본 신호',
    fftFeatures: 'FFT 특징',
    alarmTab: '알람',
    focusTab: '집중 분석',
    overviewTitle: '진동 모니터링 개요',
    rawTitle: '원본 진동 시계열',
    fftTitle: 'FFT 특징 분석',
    alarmsTitle: '알람 이력',
    focusTitle: '알림 집중 분석',
    autoOn: '자동 갱신 ON',
    autoOff: '자동 갱신 OFF',
    refreshing: '갱신 중',
    refresh: '새로고침',
    collectionStatus: '수집 상태',
    lastUpdated: '최종 갱신',
    windowsStored: '최근 분석 Window',
    rawWindows: '원본 Window',
      samples: '샘플',
      displaySamples: '표시 샘플',
      latestEquipment: '선택 설비',
      serverStatus: '서버 상태',
      equipmentSelector: '분석 설비 선택',
      allEquipmentOverview: '전체 설비 기준',
      currentFocusTitle: '현재 데이터 집중 분석',
      currentFocusHelp: '현재 선택된 설비의 최근 원본 진동과 특징값 흐름을 확대해서 분석합니다.',
      openCurrentFocus: '현재 데이터 분석',
      latestWindow: '최신 Window',
    rms: 'RMS',
    peakFrequency: 'Peak Frequency',
    anomalyScore: '이상 점수',
    aiPrediction: 'AI 예측 후보',
    aiAnalysis: 'AI 분석',
    confidence: '신뢰도',
    confidenceGauge: '신뢰도 게이지',
    modelVersion: '모델',
    modelStatus: '모델 상태',
    modelInputType: '입력',
    modelExpectedInput: '기대 입력',
    inputStrategy: '입력 전략',
    rawPrediction: '원본 예측값',
    modelLoaded: '로드됨',
    modelMissing: '모델 없음',
    modelError: '오류',
    modelUnavailable: '미사용',
    predictionBearing: '베어링 결함(통합 라벨)',
    predictionBall: '베어링 볼 손상',
    predictionInnerRace: '베어링 내륜 손상',
    predictionOuterRace: '베어링 외륜 손상',
    predictionLooseness: '헐거움',
    predictionMisalignment: '정렬불량',
    predictionUnbalance: '불균형',
    predictionNormal: '정상',
    predictionUnknown: '미확인',
    broadBearingNote: '현재 모델은 볼/내륜/외륜을 구분하지 않고 bearing으로 통합 예측합니다.',
    aiReferenceNote: '알람 판정은 특징값 기반 이상 점수와 별도로 계산됩니다.',
    alarmDecision: '알람 판정',
    modelConfidenceHelp: '모델 predict_proba 기준',
    gradeA: '높음',
    gradeB: '양호',
    gradeC: '보통',
    gradeD: '낮음',
      peakToPeak: 'Peak-to-Peak',
      crestFactor: 'Crest Factor',
      kurtosis: 'Kurtosis',
      rawSeriesTitle: '누적 원본 진동 시계열',
      timeAxis: '측정 시각',
	    vibrationAmplitude: '진동 진폭',
	    vibrationAmplitudeUnit: '진동 진폭 (a.u.)',
	    frequencyAxis: '주파수 (Hz)',
	    magnitudeAxis: 'FFT 크기',
	    featureAxis: '특징값',
      scoreAxis: '이상 점수 (0-1)',
      featureTrendTitle: 'FFT/특징값 추세',
    sampleIndex: '샘플',
    equipmentDistribution: '설비 상태 분포',
    riskScatterTitle: 'RMS-Kurtosis 위험 공간',
    alarmDistribution: '알람 단계 분포',
    recentAlarms: '최근 알람 이력',
    fleetStatus: '전체 설비',
    alarmInsight: '알람 분석',
    equipmentLatestStatus: '설비별 최신 상태',
    latestAlarmFeed: '최근 알람 피드',
    alarmSummary: '알람 요약',
    alarmHistoryTable: '알람 이력 테이블',
    fleetScoreTitle: '설비별 이상 점수',
    riskMap: '위험 맵',
    equipmentUnit: '대',
    alarmUnit: '건',
    windowUnit: 'window',
    rowsUnit: 'rows',
    occurredAt: '발생 시각',
    endedAt: '종료 시각',
    duration: '지속 시간',
    status: '상태',
    open: '진행 중',
    closed: '종료',
    equipment: '설비',
    level: '단계',
    message: '메시지',
    evidence: '판단 근거',
    normal: '정상',
    warning: '주의',
    danger: '위험',
    unknown: '미확인',
    noAlarm: '현재 warning/danger 알람 이력이 없습니다.',
    rawSeries: '원본 진동',
    anomaly: '이상 점수',
    faultSpace: '위험 공간',
    detailAnalysis: '상세 분석',
    current: '현재값',
    average: '평균',
    minimum: '최소',
    maximum: '최대',
    normalRange: '정상',
    attentionRange: '관찰',
    cautionRange: '주의',
    warningRange: '경고',
    dangerRange: '위험',
    metricDetailHelp: '카드를 선택하면 해당 지표를 확대 분석합니다.',
    expandedHint: '마우스 휠/드래그로 구간을 확대하고, 우측 도구로 복원하거나 이미지로 저장할 수 있습니다.',
    close: '닫기',
    focusEmptyTitle: '분석할 알림을 선택하세요',
    focusEmptyHelp: '알람 이력에서 특정 알림 행을 클릭하면 발생 전후 10초 구간을 불러와 집중 분석 팝업을 엽니다.',
    focusReadyHelp: '선택한 알림의 전후 구간을 원본 진동, FFT, 특징값 흐름으로 다시 확인할 수 있습니다.',
    openFocusModal: '집중 분석 열기',
    focusRange: '분석 구간',
    preAlarmRange: '알림 전 구간',
    alarmActiveRange: '위험 지속 구간',
    postAlarmRange: '회복 확인 구간',
    peakRiskRange: '최고 위험 구간',
    peakRisk: '최고 위험',
    selectedRange: '선택 구간',
    selectedFft: '선택 구간 FFT',
    focusRawTitle: '알림 전후 원본 진동',
    currentFocusRawTitle: '현재 원본 진동 구간',
    focusTrendTitle: '구간 특징값 흐름',
    focusAiResult: '선택 구간 AI 분석',
    loading: '불러오는 중',
    noSelection: '원본 진동 차트에서 구간을 드래그하거나 하단 줌 바를 조절하면 선택 구간 FFT와 특징값을 계산합니다.',
    clickAlarmForFocus: '행을 클릭하면 집중 분석',
    samplesAnalyzed: '분석 샘플',
    originalSamples: '원본 샘플',
    downsampled: '다운샘플됨',
    exactRange: '선택 구간 기준'
  },
  en: {
    brand: 'PHM Monitor',
    search: 'Find reports & assets',
    dbConnected: 'MySQL Connected',
    dbOffline: 'DB Offline',
    eyebrow: 'Local Smart Factory PHM MVP',
    dashboard: 'Dashboard',
    realtime: 'Realtime',
    vibration: 'Vibration',
    fft: 'FFT Analysis',
    alarms: 'Alarm History',
    focusAnalysis: 'Focus Analysis',
    overview: 'Overview',
    raw: 'Raw Signal',
    fftFeatures: 'FFT Features',
    alarmTab: 'Alarms',
    focusTab: 'Focus',
    overviewTitle: 'Vibration Monitoring Overview',
    rawTitle: 'Raw Signal Replay',
    fftTitle: 'FFT Feature Analysis',
    alarmsTitle: 'Alarm History',
    focusTitle: 'Alarm Focus Analysis',
    autoOn: 'Auto Refresh ON',
    autoOff: 'Auto Refresh OFF',
    refreshing: 'Refreshing',
    refresh: 'Refresh',
    collectionStatus: 'Collection Status',
    lastUpdated: 'Last updated',
    windowsStored: 'Recent Analysis Windows',
    rawWindows: 'Raw Windows',
      samples: 'Samples',
      displaySamples: 'Display Samples',
      latestEquipment: 'Selected Equipment',
      serverStatus: 'Server Status',
      equipmentSelector: 'Analysis Equipment',
      allEquipmentOverview: 'All equipment',
      currentFocusTitle: 'Current Data Focus Analysis',
      currentFocusHelp: 'Inspect the selected equipment using its latest raw vibration and feature trend.',
      openCurrentFocus: 'Analyze Current Data',
      latestWindow: 'Latest Window',
    rms: 'RMS',
    peakFrequency: 'Peak Frequency',
    anomalyScore: 'Anomaly Score',
    aiPrediction: 'AI Prediction Candidate',
    aiAnalysis: 'AI Analysis',
    confidence: 'Confidence',
    confidenceGauge: 'Confidence Gauge',
    modelVersion: 'Model',
    modelStatus: 'Model Status',
    modelInputType: 'Input',
    modelExpectedInput: 'Expected Input',
    inputStrategy: 'Input Strategy',
    rawPrediction: 'Raw Prediction',
    modelLoaded: 'Loaded',
    modelMissing: 'Missing',
    modelError: 'Error',
    modelUnavailable: 'Unavailable',
    predictionBearing: 'Bearing Fault (Grouped Label)',
    predictionBall: 'Bearing Ball Fault',
    predictionInnerRace: 'Bearing Inner Race Fault',
    predictionOuterRace: 'Bearing Outer Race Fault',
    predictionLooseness: 'Looseness',
    predictionMisalignment: 'Misalignment',
    predictionUnbalance: 'Unbalance',
    predictionNormal: 'Normal',
    predictionUnknown: 'Unknown',
    broadBearingNote: 'This model groups ball, inner race, and outer race faults into bearing.',
    aiReferenceNote: 'Alarm decision is calculated separately from feature-based anomaly score.',
    alarmDecision: 'Alarm Decision',
    modelConfidenceHelp: 'Based on model predict_proba',
    gradeA: 'High',
    gradeB: 'Good',
    gradeC: 'Moderate',
    gradeD: 'Low',
      peakToPeak: 'Peak-to-Peak',
      crestFactor: 'Crest Factor',
      kurtosis: 'Kurtosis',
      rawSeriesTitle: 'Accumulated Raw Vibration Time Series',
      timeAxis: 'Measured Time',
	    vibrationAmplitude: 'Vibration Amplitude',
	    vibrationAmplitudeUnit: 'Vibration Amplitude (a.u.)',
	    frequencyAxis: 'Frequency (Hz)',
	    magnitudeAxis: 'FFT Magnitude',
	    featureAxis: 'Feature Value',
      scoreAxis: 'Anomaly Score (0-1)',
      featureTrendTitle: 'FFT / Feature Trend',
    sampleIndex: 'Sample',
    equipmentDistribution: 'Equipment Status Distribution',
    riskScatterTitle: 'RMS vs Kurtosis Risk Space',
    alarmDistribution: 'Alarm Level Distribution',
    recentAlarms: 'Recent Alarm History',
    fleetStatus: 'Fleet',
    alarmInsight: 'Alarm Insight',
    equipmentLatestStatus: 'Latest Equipment Status',
    latestAlarmFeed: 'Latest Alarm Feed',
    alarmSummary: 'Alarm Summary',
    alarmHistoryTable: 'Alarm History Table',
    fleetScoreTitle: 'Anomaly Score by Equipment',
    riskMap: 'Risk Map',
    equipmentUnit: 'assets',
    alarmUnit: 'alarms',
    windowUnit: 'windows',
    rowsUnit: 'rows',
    occurredAt: 'Occurred At',
    endedAt: 'Ended At',
    duration: 'Duration',
    status: 'Status',
    open: 'Open',
    closed: 'Closed',
    equipment: 'Equipment',
    level: 'Level',
    message: 'Message',
    evidence: 'Evidence',
    normal: 'normal',
    warning: 'warning',
    danger: 'danger',
    unknown: 'unknown',
    noAlarm: 'No warning or danger alarm has been recorded.',
    rawSeries: 'Raw Vibration',
    anomaly: 'Anomaly Score',
    faultSpace: 'Fault Space',
    detailAnalysis: 'Detail Analysis',
    current: 'Current',
    average: 'Average',
    minimum: 'Min',
    maximum: 'Max',
    normalRange: 'Normal',
    attentionRange: 'Observe',
    cautionRange: 'Caution',
    warningRange: 'Warning',
    dangerRange: 'Danger',
    metricDetailHelp: 'Select a card to inspect the metric in detail.',
    expandedHint: 'Use wheel or drag to zoom, then use the right-side tools to restore or save the chart.',
    close: 'Close',
    focusEmptyTitle: 'Select an alarm to inspect',
    focusEmptyHelp: 'Click an alarm row to load the 10-second pre/post event window in a focus analysis popup.',
    focusReadyHelp: 'Review the selected alarm interval with raw vibration, FFT, and feature trends.',
    openFocusModal: 'Open Focus Analysis',
    focusRange: 'Analysis Range',
    preAlarmRange: 'Pre-alarm range',
    alarmActiveRange: 'Active risk range',
    postAlarmRange: 'Recovery range',
    peakRiskRange: 'Peak risk range',
    peakRisk: 'Peak risk',
    selectedRange: 'Selected Range',
    selectedFft: 'Selected Range FFT',
    focusRawTitle: 'Raw Vibration Around Alarm',
    currentFocusRawTitle: 'Current Raw Vibration Range',
    focusTrendTitle: 'Feature Flow',
    focusAiResult: 'Selected Range AI Analysis',
    loading: 'Loading',
    noSelection: 'Drag or zoom the raw vibration chart to calculate FFT and features for the selected interval.',
    clickAlarmForFocus: 'Click row for focus analysis',
    samplesAnalyzed: 'Analyzed Samples',
    originalSamples: 'Original Samples',
    downsampled: 'Downsampled',
    exactRange: 'Selected interval'
  }
};

export default {
  name: 'DashboardView',
  components: {
    EChartPanel
  },
  data() {
    return {
      language: 'ko',
      navItems: [
        { key: 'dashboard', labelKey: 'dashboard', segment: 'overview' },
        { key: 'alarms', labelKey: 'alarms', segment: 'alarms' },
        { key: 'vibration', labelKey: 'vibration', segment: 'raw' },
        { key: 'fft', labelKey: 'fft', segment: 'fft' }
      ],
      activeNav: 'dashboard',
      activeSegment: 'overview',
      selectedMetricKey: 'rms',
      showMetricDetail: false,
      autoRefresh: true,
      isRefreshing: false,
      lastUpdatedAt: null,
      dbStatus: { connected: false },
      equipments: [],
      selectedEquipment: 'MOTOR_001',
      latestRaw: {},
      rawSeries: { points: [], sampleCount: 0, originalSampleCount: 0, downsampled: false },
      analysisResults: [],
      equipmentLatestMap: {},
      alarms: [],
      focusMode: 'alarm',
      focusAlarm: null,
      focusAnalysis: null,
      focusSelection: null,
      focusLoading: false,
      focusSelectionLoading: false,
      focusSelectionRange: null,
      focusSelectionTimer: null,
      showFocusModal: false,
      summary: {},
      metricHover: {
        visible: false,
        key: 'rms',
        x: 0,
        y: 0
      },
      refreshTimer: null,
      refreshIntervalMs: 3000,
      rawSeriesWindowLimit: 5,
      rawSeriesMaxPoints: 8000,
      analysisResultLimit: 180,
      alarmLimit: 50
    };
  },
  computed: {
      activeTitle() {
        const titleMap = {
          overview: 'overviewTitle',
          raw: 'rawTitle',
          fft: 'fftTitle',
          alarms: 'alarmsTitle'
        };
        return this.t(titleMap[this.activeSegment] || 'overviewTitle');
      },
      isEquipmentScopedSegment() {
        return ['raw', 'fft'].includes(this.activeSegment);
      },
      isFleetSegment() {
        return ['overview', 'alarms'].includes(this.activeSegment);
      },
    formattedLastUpdated() {
      if (!this.lastUpdatedAt) {
        return '-';
      }
      return this.lastUpdatedAt.toLocaleTimeString();
    },
    latestAnalysis() {
      return this.analysisResults[0] || {};
    },
    ascendingAnalysis() {
      return [...this.analysisResults].reverse();
    },
    currentReplayAnalysis() {
      const rows = [...this.analysisResults]
        .filter((row) => row.createdAt || row.measuredAt)
        .sort((left, right) => this.toTime(right.createdAt || right.measuredAt) - this.toTime(left.createdAt || left.measuredAt));

      if (rows.length === 0) {
        return [];
      }

      const currentRows = [];
      let previousReceivedAt = null;
      rows.forEach((row) => {
        if (previousReceivedAt === null) {
          currentRows.push(row);
          previousReceivedAt = this.toTime(row.createdAt || row.measuredAt);
          return;
        }

        const receivedAt = this.toTime(row.createdAt || row.measuredAt);
        if (previousReceivedAt - receivedAt <= 30000) {
          currentRows.push(row);
          previousReceivedAt = receivedAt;
        }
      });

      return currentRows.sort((left, right) => this.toTime(left.measuredAt || left.createdAt) - this.toTime(right.measuredAt || right.createdAt));
    },
    metricSpecs() {
        return {
          rms: { label: this.t('rms'), decimals: 5, unit: 'a.u.', thresholdMode: 'data' },
          peakFrequency: { label: this.t('peakFrequency'), decimals: 2, unit: 'Hz', thresholdMode: 'data' },
          peakToPeak: { label: this.t('peakToPeak'), decimals: 5, unit: 'a.u.', thresholdMode: 'data' },
          crestFactor: { label: this.t('crestFactor'), decimals: 3, unit: 'ratio', thresholdMode: 'fixed' },
          kurtosis: { label: this.t('kurtosis'), decimals: 3, unit: '-', thresholdMode: 'fixed' },
          anomalyScore: { label: this.t('anomalyScore'), decimals: 4, unit: '0-1', thresholdMode: 'fixed' }
        };
      },
    selectedMetricSpec() {
      return this.metricSpecs[this.selectedMetricKey] || this.metricSpecs.rms;
    },
    hoverMetricSpec() {
      return this.metricSpecs[this.metricHover.key] || this.metricSpecs.rms;
    },
    hoverMetricStats() {
      return this.statsForMetric(this.metricHover.key);
    },
    metricHoverStyle() {
      const width = 250;
      const height = 122;
      const viewportWidth = window.innerWidth || 1280;
      const viewportHeight = window.innerHeight || 720;
      const left = Math.min(this.metricHover.x + 16, viewportWidth - width - 12);
      const top = Math.min(this.metricHover.y + 16, viewportHeight - height - 12);
      return {
        left: `${Math.max(12, left)}px`,
        top: `${Math.max(12, top)}px`
      };
    },
    metricDetailTitle() {
      return `${this.selectedMetricSpec.label} ${this.t('detailAnalysis')}`;
    },
    metricDetailStats() {
      return this.statsForMetric(this.selectedMetricKey, this.currentReplayAnalysis);
    },
    metricDetailOption() {
      const rows = this.currentReplayAnalysis.length > 0 ? this.currentReplayAnalysis : this.ascendingAnalysis;
      const data = rows
        .filter((row) => row[this.selectedMetricKey] !== null && row[this.selectedMetricKey] !== undefined)
        .map((row) => [this.toTime(row.measuredAt || row.createdAt), row[this.selectedMetricKey]]);
      const values = data.map((item) => item[1]);
      const pieces = this.metricVisualPieces(this.selectedMetricKey, values);
      const markLines = pieces
        .filter((piece) => piece.lte !== undefined)
        .map((piece) => ({ yAxis: piece.lte }));

      return {
        title: {
          text: this.selectedMetricSpec.label,
          left: '1%',
          textStyle: {
            color: '#3f454f',
            fontSize: 20,
            fontWeight: 700
          },
          subtext: this.t('metricDetailHelp')
        },
        tooltip: {
          trigger: 'axis',
          confine: true,
          axisPointer: {
            type: 'cross'
          },
          position: (pt, params, dom, rect, size) => {
            const x = Math.min(pt[0] + 14, size.viewSize[0] - size.contentSize[0] - 12);
            const y = Math.min(pt[1] + 14, size.viewSize[1] - size.contentSize[1] - 12);
            return [Math.max(12, x), Math.max(12, y)];
          },
          formatter: (params) => {
            if (!params || params.length === 0) {
              return '';
            }
            const point = params[0];
            const value = point.value[1];
            const time = new Date(point.value[0]).toLocaleString();
            const unit = this.selectedMetricSpec.unit ? ` ${this.selectedMetricSpec.unit}` : '';
            return [
              `<strong>${this.selectedMetricSpec.label}</strong>`,
              `${time}`,
              `${this.formatNumber(value, this.selectedMetricSpec.decimals)}${unit}`
            ].join('<br/>');
          }
        },
        grid: {
          left: '5%',
          right: '15%',
          bottom: '14%',
          top: 76
        },
        xAxis: {
          type: 'time',
          boundaryGap: false,
          min: data[0] ? data[0][0] : undefined,
          max: data.length > 0 ? data[data.length - 1][0] : undefined
        },
	        yAxis: {
	          type: 'value',
	          name: this.selectedMetricSpec.unit
	            ? `${this.selectedMetricSpec.label} (${this.selectedMetricSpec.unit})`
	            : this.selectedMetricSpec.label,
	          scale: true
	        },
        toolbox: {
          right: 10,
          feature: {
            dataZoom: {
              yAxisIndex: 'none'
            },
            restore: {},
            saveAsImage: {}
          }
        },
        dataZoom: [
          {
            start: 0,
            end: 100,
            height: 26
          },
          {
            type: 'inside'
          }
        ],
        visualMap: {
          top: 50,
          right: 10,
          dimension: 1,
          pieces,
          outOfRange: {
            color: '#999'
          }
        },
        series: {
          name: this.selectedMetricSpec.label,
          type: 'line',
          smooth: false,
          symbol: 'circle',
          symbolSize: 5,
          data,
          markLine: {
            silent: true,
            lineStyle: {
              color: '#333',
              type: 'dashed'
            },
            data: markLines
          }
        }
      };
    },
      rawSignalOption() {
        const data = this.buildRawSignalData();
        return {
          tooltip: {
            trigger: 'axis',
            position: (pt) => [pt[0], '10%'],
            formatter: (params) => {
              if (!params || params.length === 0) {
                return '';
              }
              const point = params[0].value;
              return [
                `<strong>${this.t('rawSeries')}</strong>`,
                `${this.t('timeAxis')}: ${new Date(point[0]).toLocaleString(this.language === 'ko' ? 'ko-KR' : 'en-US')}`,
                `${this.t('vibrationAmplitude')}: ${Number(point[1]).toFixed(5)} a.u.`
              ].join('<br/>');
            }
          },
        title: {
          left: 'center',
          text: this.t('rawSeriesTitle'),
          textStyle: {
            color: '#111827',
            fontSize: 20,
            fontWeight: 700
          }
        },
        toolbox: {
          right: 18,
          top: 10,
          feature: {
            dataZoom: { yAxisIndex: 'none' },
            restore: {},
            saveAsImage: {}
          }
        },
          grid: { top: 72, left: 58, right: 34, bottom: 72 },
          xAxis: {
            type: 'time',
            name: this.t('timeAxis'),
            boundaryGap: false
          },
          yAxis: {
            type: 'value',
            name: this.t('vibrationAmplitudeUnit'),
            boundaryGap: [0, '100%']
          },
        dataZoom: [
          {
            type: 'inside',
            start: 0,
            end: 20
          },
          {
            start: 0,
            end: 20,
            height: 28
          }
        ],
        series: [
          {
            name: this.t('rawSeries'),
            type: 'line',
            smooth: false,
            symbol: 'none',
            sampling: 'lttb',
            areaStyle: {
              color: 'rgba(0, 44, 95, 0.16)'
            },
            lineStyle: {
              color: '#002c5f',
              width: 1.8
            },
            data
          }
        ]
      };
    },
    featureTrendOption() {
      const rows = this.ascendingAnalysis;
      return {
        tooltip: { trigger: 'axis' },
        legend: {
          top: 4,
          data: [this.t('rms'), this.t('anomalyScore'), this.t('peakFrequency')]
        },
        grid: { left: 54, right: 70, bottom: 48, top: 48 },
        toolbox: {
          right: 10,
          feature: {
            dataZoom: { yAxisIndex: 'none' },
            restore: {},
            saveAsImage: {}
          }
        },
        dataZoom: [{ start: 0, end: 100 }, { type: 'inside' }],
        visualMap: {
          top: 54,
          right: 8,
          dimension: 1,
          pieces: [
            { gt: 0, lte: 0.45, color: '#1f9d55' },
            { gt: 0.45, lte: 0.7, color: '#f6a609' },
            { gt: 0.7, color: '#d64545' }
          ],
          outOfRange: { color: '#7a8699' }
        },
        xAxis: {
          type: 'time',
          boundaryGap: false,
          axisLine: { lineStyle: { color: '#b8c2cc' } }
        },
          yAxis: [
            { type: 'value', name: `${this.t('rms')} / ${this.t('scoreAxis')}` },
            { type: 'value', name: 'Hz' }
          ],
        series: [
          {
            name: this.t('rms'),
            type: 'line',
            smooth: false,
            symbol: 'circle',
            symbolSize: 5,
            data: rows.map((row) => [this.toTime(row.createdAt || row.measuredAt), row.rms]),
            markLine: {
              silent: true,
              lineStyle: { color: '#a6b1c2' },
              data: [{ yAxis: 0.45 }, { yAxis: 0.7 }]
            }
          },
          {
            name: this.t('anomalyScore'),
            type: 'line',
            smooth: false,
            symbol: 'none',
            data: rows.map((row) => [this.toTime(row.createdAt || row.measuredAt), row.anomalyScore])
          },
          {
            name: this.t('peakFrequency'),
            type: 'line',
            smooth: false,
            symbol: 'none',
            yAxisIndex: 1,
            data: rows.map((row) => [this.toTime(row.createdAt || row.measuredAt), row.peakFrequency]),
            lineStyle: { color: '#6254a8' }
          }
        ]
      };
    },
    statusPieOption() {
      const rows = this.summary.equipmentStatusDistribution || this.buildStatusDistribution();
      return this.buildDonutOption(this.t('equipmentDistribution'), rows);
    },
    alarmPieOption() {
      const rows = this.summary.alarmLevelDistribution || this.buildAlarmDistribution();
      return this.buildDonutOption(this.t('alarmDistribution'), rows);
    },
    fleetScoreBarOption() {
      const rows = this.equipmentHealthRows;
      return {
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: (params) => {
            if (!params || params.length === 0) {
              return '';
            }
            const item = params[0];
            const row = item.data.raw;
            return [
              `<strong>${row.equipmentCode}</strong>`,
              `${this.t('level')}: ${this.tLevel(row.alarmLevel)}`,
              `${this.t('anomalyScore')}: ${this.formatNumber(row.anomalyScore, 4)}`,
              `${this.t('rms')}: ${this.formatNumber(row.rms, 5)}`
            ].join('<br/>');
          }
        },
        grid: { left: 46, right: 24, top: 24, bottom: 42 },
        xAxis: {
          type: 'category',
          data: rows.map((row) => row.equipmentCode),
          axisLabel: { color: '#445064' }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 1,
          axisLabel: { color: '#445064' }
        },
        series: [
          {
            name: this.t('anomalyScore'),
            type: 'bar',
            barMaxWidth: 34,
            data: rows.map((row) => ({
              value: Number(row.anomalyScore || 0),
              raw: row,
              itemStyle: { color: alarmColor[row.alarmLevel] || alarmColor.unknown }
            })),
            markLine: {
              silent: true,
              lineStyle: { color: '#a6b1c2', type: 'dashed' },
              data: [{ yAxis: 0.45 }, { yAxis: 0.7 }]
            }
          }
        ]
      };
    },
    riskScatterOption() {
      const rows = this.ascendingAnalysis;
      return {
        tooltip: {
          trigger: 'item',
          formatter: (params) => {
            const row = params.data.raw;
            return `${row.equipmentCode}<br/>RMS: ${row.rms}<br/>Kurtosis: ${row.kurtosis}<br/>Score: ${row.anomalyScore}`;
          }
        },
        grid: { left: 48, right: 24, bottom: 42, top: 24 },
        xAxis: { name: 'RMS', type: 'value' },
        yAxis: { name: 'Kurtosis', type: 'value' },
        series: [
          {
            name: this.t('faultSpace'),
            type: 'scatter',
            symbolSize: (value) => Math.max(8, Math.min(26, value[2] * 30)),
            data: rows.map((row) => ({
              value: [row.rms || 0, row.kurtosis || 0, row.anomalyScore || 0],
              raw: row,
              itemStyle: { color: alarmColor[row.alarmLevel || 'normal'] || alarmColor.unknown }
            }))
          }
        ]
      };
    },
	    metricCards() {
	      return [
	        this.metricCard('1', this.t('rms'), 'rms', 5, 'a.u.'),
	        this.metricCard('2', this.t('peakFrequency'), 'peakFrequency', 2, 'Hz'),
	        this.metricCard('3', this.t('peakToPeak'), 'peakToPeak', 5, 'a.u.'),
	        this.metricCard('4', this.t('crestFactor'), 'crestFactor', 3, 'ratio'),
	        this.metricCard('5', this.t('kurtosis'), 'kurtosis', 3, '-'),
	        this.metricCard('6', this.t('anomalyScore'), 'anomalyScore', 4, '0-1')
	      ];
	    },
    confidenceGaugeOption() {
      const confidence = Math.max(0, Math.min(1, Number(this.latestAnalysis.confidence || 0)));
      return {
        series: [
          {
            type: 'gauge',
            startAngle: 180,
            endAngle: 0,
            center: ['50%', '76%'],
            radius: '94%',
            min: 0,
            max: 1,
            splitNumber: 8,
            axisLine: {
              lineStyle: {
                width: 6,
                color: [
                  [0.25, '#FF6E76'],
                  [0.5, '#FDDD60'],
                  [0.75, '#58D9F9'],
                  [1, '#7CFFB2']
                ]
              }
            },
            pointer: {
              icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
              length: '13%',
              width: 18,
              offsetCenter: [0, '-58%'],
              itemStyle: {
                color: 'auto'
              }
            },
            axisTick: {
              length: 10,
              lineStyle: {
                color: 'auto',
                width: 2
              }
            },
            splitLine: {
              length: 18,
              lineStyle: {
                color: 'auto',
                width: 4
              }
            },
            axisLabel: {
              color: '#464646',
              fontSize: 12,
              distance: -42,
              rotate: 'tangential',
              formatter: (value) => {
                if (Math.abs(value - 0.875) < 0.001) {
                  return this.t('gradeA');
                }
                if (Math.abs(value - 0.625) < 0.001) {
                  return this.t('gradeB');
                }
                if (Math.abs(value - 0.375) < 0.001) {
                  return this.t('gradeC');
                }
                if (Math.abs(value - 0.125) < 0.001) {
                  return this.t('gradeD');
                }
                return '';
              }
            },
            title: {
              offsetCenter: [0, '-8%'],
              fontSize: 14,
              color: '#445064'
            },
            detail: {
              fontSize: 28,
              fontWeight: 700,
              offsetCenter: [0, '-34%'],
              valueAnimation: true,
              formatter: (value) => `${Math.round(value * 100)}%`,
              color: 'inherit'
            },
            data: [
              {
                value: confidence,
                name: this.t('confidence')
              }
            ]
          }
        ]
      };
    },
    actualAlarmRows() {
      return this.alarms.map((alarm) => ({
        ...alarm,
        rawLevel: alarm.alarmLevel || 'normal',
        rawStatus: alarm.status || 'open',
        alarmLevel: this.tLevel(alarm.alarmLevel),
        status: this.t(alarm.status || 'open'),
        occurredAt: this.formatDateTime(alarm.occurredAt),
        endedAt: this.formatDateTime(alarm.endedAt),
        duration: this.formatDuration(alarm.durationSeconds),
        displayMessage: this.buildAlarmDisplayMessage(alarm)
      }));
    },
    recentAlarmItems() {
      return this.actualAlarmRows.slice(0, 8);
    },
    alarmCounts() {
      const counts = { normal: 0, warning: 0, danger: 0 };
      const rows = this.summary.alarmLevelDistribution || [];
      if (rows.length > 0) {
        rows.forEach((row) => {
          counts[row.name] = Number(row.value || 0);
        });
      } else {
        this.alarms.forEach((alarm) => {
          const level = alarm.alarmLevel || 'normal';
          counts[level] = (counts[level] || 0) + 1;
        });
      }
      return counts;
    },
    equipmentHealthRows() {
      return this.equipments.map((equipment) => {
        const latest = this.equipmentLatestMap[equipment.equipmentCode] || {};
        const fallback = equipment.equipmentCode === this.selectedEquipment ? this.latestAnalysis : {};
        const analysis = latest.id ? latest : fallback;
        return {
          equipmentCode: equipment.equipmentCode,
          equipmentName: equipment.equipmentName,
          displayName: this.displayEquipmentName(equipment),
          location: equipment.location,
          alarmLevel: analysis.alarmLevel || 'normal',
          rms: analysis.rms,
          peakFrequency: analysis.peakFrequency,
          anomalyScore: analysis.anomalyScore,
          updatedAt: analysis.createdAt || analysis.measuredAt
        };
      });
    },
    selectedEquipmentName() {
      const equipment = this.equipments.find((item) => item.equipmentCode === this.selectedEquipment);
      return this.displayEquipmentName(equipment || { equipmentCode: this.selectedEquipment });
    },
    normalizedPrediction() {
      return this.normalizePrediction(this.latestAnalysis.prediction);
    },
    isBroadBearingPrediction() {
      return ['bearing', 'bearing_fault'].includes(this.normalizedPrediction);
    },
    predictionMetaText() {
      const rawPrediction = this.latestAnalysis.prediction || '-';
      const modelVersion = this.latestAnalysis.modelVersion || '-';
      return `${rawPrediction} · ${this.t('modelVersion')} ${modelVersion}`;
    },
    predictionNote() {
      if (this.isBroadBearingPrediction) {
        return this.t('broadBearingNote');
      }
      if (this.latestAnalysis.prediction && this.latestAnalysis.alarmLevel === 'normal') {
        return this.t('aiReferenceNote');
      }
      return '';
    },
      focusModalTitle() {
        if (!this.focusAnalysis) {
          return this.t('focusAnalysis');
        }
        if (this.focusMode === 'current') {
          return `${this.focusAnalysis.equipmentCode} · ${this.t('currentFocusTitle')}`;
        }
        return `${this.focusAnalysis.equipmentCode} · ${this.tLevel(this.focusAnalysis.alarmLevel)} #${this.focusAnalysis.alarmId}`;
      },
      focusSelectionRangeText() {
        if (!this.focusSelectionRange) {
          return '-';
        }
        return `${this.formatTimeOnly(this.focusSelectionRange.start)} - ${this.formatTimeOnly(this.focusSelectionRange.end)}`;
      },
      focusPeakRiskPoint() {
        const rows = this.focusAnalysis?.analysisTrend || [];
        return rows.reduce((peak, row) => {
          const time = this.toTime(row.measuredAt || row.createdAt);
          const value = Number(row.anomalyScore);
          if (!Number.isFinite(time) || !Number.isFinite(value)) {
            return peak;
          }
          if (!peak || value > peak.value) {
            return { time, value, row };
          }
          return peak;
        }, null);
      },
      focusRiskMarkAreas() {
        if (!this.focusAnalysis) {
          return [];
        }
        const points = this.focusAnalysis.points || [];
        const firstPoint = points[0]?.timestamp;
        const lastPoint = points[points.length - 1]?.timestamp;
        const rangeStart = this.firstFiniteTime(this.focusAnalysis.rangeStart, firstPoint);
        const rangeEnd = this.firstFiniteTime(this.focusAnalysis.rangeEnd, lastPoint);
        const occurredAt = this.firstFiniteTime(this.focusAnalysis.occurredAt);
        const endedAt = this.firstFiniteTime(this.focusAnalysis.endedAt, this.focusAnalysis.occurredAt);
        const areas = [];
        const addArea = (name, start, end, color) => {
          if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
            return;
          }
          areas.push([
            { name, xAxis: start, itemStyle: { color } },
            { xAxis: end }
          ]);
        };

        addArea(this.t('preAlarmRange'), rangeStart, occurredAt, 'rgba(0, 44, 95, 0.07)');
        addArea(this.t('alarmActiveRange'), occurredAt, endedAt, 'rgba(214, 69, 69, 0.14)');
        addArea(this.t('postAlarmRange'), endedAt, rangeEnd, 'rgba(31, 157, 85, 0.08)');

        if (!Number.isFinite(occurredAt)) {
          const rows = this.focusAnalysis.analysisTrend || [];
          rows.forEach((row, index) => {
            const level = row.alarmLevel || 'normal';
            if (!['warning', 'danger'].includes(level)) {
              return;
            }
            const start = this.toTime(row.measuredAt || row.createdAt);
            const next = rows[index + 1];
            const nextTime = next ? this.toTime(next.measuredAt || next.createdAt) : NaN;
            const end = Number.isFinite(nextTime)
              ? nextTime
              : start + this.focusWindowDurationMillis();
            addArea(
              this.t(level === 'danger' ? 'dangerRange' : 'warningRange'),
              start,
              end,
              level === 'danger' ? 'rgba(214, 69, 69, 0.16)' : 'rgba(246, 166, 9, 0.14)'
            );
          });
        }

        if (this.focusPeakRiskPoint && Number.isFinite(rangeStart) && Number.isFinite(rangeEnd)) {
          const halfWidth = Math.max(1000, Math.min(3000, (rangeEnd - rangeStart) * 0.04));
          addArea(
            this.t('peakRiskRange'),
            Math.max(rangeStart, this.focusPeakRiskPoint.time - halfWidth),
            Math.min(rangeEnd, this.focusPeakRiskPoint.time + halfWidth),
            'rgba(214, 69, 69, 0.22)'
          );
        }
        return areas;
      },
      focusRiskMarkLines() {
        const occurredAt = this.firstFiniteTime(this.focusAnalysis?.occurredAt);
        const endedAt = this.firstFiniteTime(this.focusAnalysis?.endedAt, this.focusAnalysis?.occurredAt);
        const lines = [];
        if (Number.isFinite(occurredAt)) {
          lines.push({ name: this.t('alarmActiveRange'), xAxis: occurredAt });
        }
        if (Number.isFinite(endedAt) && endedAt !== occurredAt) {
          lines.push({ name: this.t('postAlarmRange'), xAxis: endedAt });
        }
        if (this.focusPeakRiskPoint) {
          lines.push({ name: this.t('peakRisk'), xAxis: this.focusPeakRiskPoint.time });
        }
        return lines;
      },
      focusPeakRiskMarkPoint() {
        if (!this.focusPeakRiskPoint) {
          return [];
        }
        return [
          {
            name: this.t('peakRisk'),
            coord: [this.focusPeakRiskPoint.time, this.focusPeakRiskPoint.value],
            value: this.formatNumber(this.focusPeakRiskPoint.value, 4)
          }
        ];
      },
      focusRawOption() {
        const points = this.focusAnalysis?.points || [];
        const data = points.map((point) => [point.timestamp, point.value]);
        return {
        tooltip: {
          trigger: 'axis',
          confine: true,
          position: (pt) => [pt[0], '10%'],
          formatter: (params) => {
            if (!params || params.length === 0) {
              return '';
            }
            const point = params[0].value;
            return [
              `<strong>${this.t('rawSeries')}</strong>`,
              new Date(point[0]).toLocaleString(),
              `${Number(point[1]).toFixed(5)}`
            ].join('<br/>');
          }
        },
        toolbox: {
          right: 18,
          top: 8,
          feature: {
            dataZoom: { yAxisIndex: 'none' },
            restore: {},
            saveAsImage: {}
          }
        },
        grid: { top: 48, left: 56, right: 26, bottom: 58 },
	        xAxis: {
	          type: 'time',
	          name: this.t('timeAxis'),
	          boundaryGap: false
	        },
	        yAxis: {
	          type: 'value',
	          name: this.t('vibrationAmplitudeUnit'),
	          scale: true
	        },
        dataZoom: [
          {
            type: 'inside',
            start: 0,
            end: 100
          },
          {
            start: 0,
            end: 100,
            height: 24
          }
        ],
        series: [
          {
            name: this.t('rawSeries'),
            type: 'line',
            smooth: false,
            symbol: 'none',
            sampling: 'lttb',
            areaStyle: {
              color: 'rgba(0, 44, 95, 0.12)'
            },
            lineStyle: {
              color: '#002c5f',
              width: 1.4
            },
              markArea: {
                silent: true,
                data: this.focusRiskMarkAreas
              },
              markLine: {
                silent: true,
                symbol: 'none',
                label: { color: '#445064' },
                lineStyle: { color: '#002c5f', type: 'dashed', width: 1 },
                data: this.focusRiskMarkLines
              },
              data
          }
        ]
      };
    },
    focusFftOption() {
      const fft = this.focusSelection?.fft;
      const data = fft && fft.frequencies
        ? fft.frequencies.map((frequency, index) => [frequency, fft.magnitudes[index] || 0])
        : [];
      return {
        tooltip: {
          trigger: 'axis',
          confine: true,
          formatter: (params) => {
            if (!params || params.length === 0) {
              return '';
            }
            const point = params[0].value;
            return [
              `<strong>${this.t('selectedFft')}</strong>`,
              `${Number(point[0]).toFixed(2)} Hz`,
              `${Number(point[1]).toExponential(3)}`
            ].join('<br/>');
          }
        },
        grid: { top: 30, left: 54, right: 26, bottom: 44 },
	        xAxis: { type: 'value', name: this.t('frequencyAxis'), min: 0 },
	        yAxis: { type: 'value', name: this.t('magnitudeAxis'), scale: true },
        dataZoom: [{ type: 'inside' }, { height: 20 }],
        series: [
          {
            name: this.t('selectedFft'),
            type: 'line',
            symbol: 'none',
            smooth: false,
            lineStyle: { color: '#6254a8', width: 1.4 },
            data
          }
        ]
      };
    },
    focusFeatureTrendOption() {
      const rows = this.focusAnalysis?.analysisTrend || [];
      return {
        tooltip: { trigger: 'axis', confine: true },
        legend: {
          top: 2,
          data: [this.t('rms'), this.t('peakToPeak'), this.t('anomalyScore')]
        },
        grid: { top: 44, left: 56, right: 28, bottom: 44 },
	        xAxis: { type: 'time', name: this.t('timeAxis'), boundaryGap: false },
	        yAxis: { type: 'value', name: this.t('featureAxis'), scale: true },
        dataZoom: [{ type: 'inside' }, { height: 20 }],
        series: [
          {
            name: this.t('rms'),
            type: 'line',
            smooth: false,
            symbol: 'circle',
            symbolSize: 4,
            data: rows.map((row) => [this.toTime(row.measuredAt), row.rms])
          },
          {
            name: this.t('peakToPeak'),
            type: 'line',
            smooth: false,
            symbol: 'none',
            data: rows.map((row) => [this.toTime(row.measuredAt), row.peakToPeak]),
            lineStyle: { color: '#f6a609' }
          },
            {
              name: this.t('anomalyScore'),
              type: 'line',
              smooth: false,
              symbol: 'none',
              data: rows.map((row) => [this.toTime(row.measuredAt), row.anomalyScore]),
              lineStyle: { color: '#d64545' },
              markArea: {
                silent: true,
                data: this.focusRiskMarkAreas
              },
              markPoint: {
                symbol: 'pin',
                symbolSize: 46,
                itemStyle: { color: '#d64545' },
                label: { formatter: '{b}' },
                data: this.focusPeakRiskMarkPoint
              }
            }
          ]
      };
    }
  },
  async mounted() {
    await this.loadDashboard();
    this.startRefreshTimer();
  },
  beforeDestroy() {
    this.stopRefreshTimer();
    if (this.focusSelectionTimer) {
      window.clearTimeout(this.focusSelectionTimer);
    }
  },
  methods: {
    t(key) {
      return messages[this.language][key] || messages.ko[key] || key;
    },
    tLevel(level) {
      return this.t(level || 'normal');
    },
    tModelStatus(status) {
      const statusKey = {
        loaded: 'modelLoaded',
        missing: 'modelMissing',
        error: 'modelError',
        unavailable: 'modelUnavailable'
      }[status || 'unavailable'];
      return this.t(statusKey || 'modelUnavailable');
    },
    displayEquipmentName(equipment) {
      const code = equipment?.equipmentCode || '';
      const name = equipment?.equipmentName || '';
      if (!name || this.looksBrokenText(name)) {
        return this.defaultEquipmentName(code);
      }
      return name;
    },
    defaultEquipmentName(equipmentCode) {
      const koreanNames = {
        MOTOR_001: '1번 모터',
        MOTOR_002: '2번 모터',
        MOTOR_003: '3번 모터'
      };
      const englishNames = {
        MOTOR_001: 'Motor 1',
        MOTOR_002: 'Motor 2',
        MOTOR_003: 'Motor 3'
      };
      const names = this.language === 'ko' ? koreanNames : englishNames;
      return names[equipmentCode] || equipmentCode || '-';
    },
    looksBrokenText(value) {
      return /[ÃÂâêëìíîïð�]/.test(String(value));
    },
    normalizePrediction(prediction) {
      return String(prediction || '').trim().toLowerCase().replace(/[\s-]+/g, '_');
    },
    formatPrediction(prediction) {
      if (!prediction) {
        return '-';
      }
      const normalized = this.normalizePrediction(prediction);
      const predictionKey = {
        normal: 'predictionNormal',
        healthy: 'predictionNormal',
        h: 'predictionNormal',
        bearing: 'predictionBearing',
        bearing_fault: 'predictionBearing',
        b: 'predictionBall',
        ball: 'predictionBall',
        bf: 'predictionBall',
        ball_fault: 'predictionBall',
        ir: 'predictionInnerRace',
        inner: 'predictionInnerRace',
        inner_race: 'predictionInnerRace',
        inner_race_fault: 'predictionInnerRace',
        inner_raceway: 'predictionInnerRace',
        or: 'predictionOuterRace',
        outer: 'predictionOuterRace',
        outer_race: 'predictionOuterRace',
        outer_race_fault: 'predictionOuterRace',
        outer_raceway: 'predictionOuterRace',
        looseness: 'predictionLooseness',
        loose: 'predictionLooseness',
        l: 'predictionLooseness',
        misalignment: 'predictionMisalignment',
        misaligned: 'predictionMisalignment',
        m: 'predictionMisalignment',
        unbalance: 'predictionUnbalance',
        imbalance: 'predictionUnbalance',
        u: 'predictionUnbalance',
        not_trained: 'predictionUnknown',
        prediction_error: 'predictionUnknown'
      }[normalized];
      return predictionKey ? this.t(predictionKey) : prediction;
    },
    async loadDashboard() {
      if (this.isRefreshing) {
        return;
      }
      this.isRefreshing = true;
      try {
        const [status, equipments, summary] = await Promise.all([
          fetchDatabaseStatus(),
          fetchEquipments(),
          fetchDashboardSummary()
        ]);
        this.dbStatus = status;
        this.equipments = equipments;
        this.summary = summary;

        if (!this.selectedEquipment && equipments.length > 0) {
          this.selectedEquipment = equipments[0].equipmentCode;
        }

        const equipmentCode = this.selectedEquipment || 'MOTOR_001';
        const equipmentLatestPromise = Promise.all(
          equipments.map(async (equipment) => {
            try {
              const rows = await fetchAnalysisResults(equipment.equipmentCode, 1);
              return [equipment.equipmentCode, rows[0] || {}];
            } catch (error) {
              return [equipment.equipmentCode, {}];
            }
          })
        );
        const [rawWindow, rawSeries, analysisResults, alarms, equipmentLatestEntries] = await Promise.all([
          fetchLatestRawWindow(equipmentCode),
          fetchRawVibrationSeries(equipmentCode, this.rawSeriesWindowLimit, this.rawSeriesMaxPoints),
          fetchAnalysisResults(equipmentCode, this.analysisResultLimit),
          fetchAlarms(this.alarmLimit),
          equipmentLatestPromise
        ]);

        this.latestRaw = rawWindow;
        this.rawSeries = rawSeries;
        this.analysisResults = analysisResults;
        this.alarms = alarms;
        this.equipmentLatestMap = Object.fromEntries(equipmentLatestEntries);
        this.lastUpdatedAt = new Date();
      } catch (error) {
        console.warn('Failed to load dashboard data', error);
      } finally {
        this.isRefreshing = false;
        this.resizeChartsSoon();
      }
    },
    toggleAutoRefresh() {
      this.autoRefresh = !this.autoRefresh;
      if (this.autoRefresh) {
        this.startRefreshTimer();
      } else {
        this.stopRefreshTimer();
      }
    },
    startRefreshTimer() {
      this.stopRefreshTimer();
      this.refreshTimer = window.setInterval(() => {
        if (this.autoRefresh) {
          this.loadDashboard();
        }
      }, this.refreshIntervalMs);
    },
    stopRefreshTimer() {
      if (this.refreshTimer) {
        window.clearInterval(this.refreshTimer);
        this.refreshTimer = null;
      }
    },
    showMetricTooltip(metricKey, event) {
      this.metricHover = {
        visible: true,
        key: metricKey,
        x: event.clientX,
        y: event.clientY
      };
    },
    moveMetricTooltip(event) {
      if (!this.metricHover.visible) {
        return;
      }
      this.metricHover = {
        ...this.metricHover,
        x: event.clientX,
        y: event.clientY
      };
    },
    hideMetricTooltip() {
      this.metricHover = {
        ...this.metricHover,
        visible: false
      };
    },
    selectNav(item) {
      this.activeNav = item.key;
      this.setActiveSegment(item.segment, false);
    },
	    selectEquipment(equipmentCode, targetSegment = null) {
	      this.selectedEquipment = equipmentCode;
	      if (targetSegment) {
	        this.setActiveSegment(targetSegment, true);
	      }
	      this.loadDashboard();
	    },
    selectMetric(metricKey) {
      this.selectedMetricKey = metricKey;
      this.showMetricDetail = true;
      this.hideMetricTooltip();
      this.activeSegment = 'fft';
      this.activeNav = 'fft';
      this.resizeChartsSoon();
      this.$nextTick(() => {
        window.setTimeout(() => {
          window.dispatchEvent(new Event('resize'));
        }, 50);
      });
    },
      closeMetricDetail() {
        this.showMetricDetail = false;
        this.resizeChartsSoon();
      },
      async openCurrentFocusAnalysis() {
        const focus = this.buildCurrentFocusAnalysis();
        if (!focus) {
          return;
        }
        this.activeNav = 'fft';
        this.activeSegment = 'fft';
        this.focusMode = 'current';
        this.focusAlarm = null;
        this.focusAnalysis = focus;
        this.focusSelection = null;
        this.focusSelectionRange = null;
        this.focusLoading = false;
        this.showFocusModal = true;
        const initialRange = this.defaultFocusSelectionRange(focus);
        if (initialRange) {
          await this.loadFocusSelection(initialRange.start, initialRange.end);
        }
        this.resizeChartsSoon();
      },
      async openFocusAnalysis(alarm) {
        if (!alarm || !alarm.id) {
          return;
        }
        this.activeNav = 'fft';
        this.activeSegment = 'fft';
        this.focusMode = 'alarm';
        this.focusAlarm = alarm;
        this.focusAnalysis = null;
      this.focusSelection = null;
      this.focusSelectionRange = null;
      this.focusLoading = true;
      this.showFocusModal = true;

      try {
        const response = await fetchAlarmFocusAnalysis(alarm.id, 10, 40000);
        this.focusAnalysis = response;
        const initialRange = this.defaultFocusSelectionRange(response);
        if (initialRange) {
          await this.loadFocusSelection(initialRange.start, initialRange.end);
        }
      } catch (error) {
        console.warn('Failed to load alarm focus analysis', error);
      } finally {
        this.focusLoading = false;
        this.resizeChartsSoon();
      }
    },
    closeFocusModal() {
      this.showFocusModal = false;
      if (this.focusSelectionTimer) {
        window.clearTimeout(this.focusSelectionTimer);
        this.focusSelectionTimer = null;
      }
    },
    defaultFocusSelectionRange(focusAnalysis) {
      if (!focusAnalysis || !focusAnalysis.points || focusAnalysis.points.length === 0) {
        return null;
      }
	      const min = focusAnalysis.points[0].timestamp;
	      const max = focusAnalysis.points[focusAnalysis.points.length - 1].timestamp;
	      if (this.focusMode === 'current') {
	        const span = Math.max(1000, Math.min(3000, (max - min) * 0.25));
	        return { start: Math.max(min, max - span), end: max };
	      }
	      const occurred = this.toTime(focusAnalysis.occurredAt);
      const ended = this.toTime(focusAnalysis.endedAt || focusAnalysis.occurredAt);
      const center = Number.isFinite(occurred) ? occurred : (min + max) / 2;
      const start = Math.max(min, center - 1000);
      const end = Math.min(max, Math.max(Number.isFinite(ended) ? ended + 1000 : center + 1000, start + 1000));
      return { start, end };
    },
    handleFocusRawZoom(params) {
      if (!this.focusAnalysis || !this.focusAnalysis.points || this.focusAnalysis.points.length < 2) {
        return;
      }
      const action = (params.batch || [params])[0] || {};
      const points = this.focusAnalysis.points;
      const min = points[0].timestamp;
      const max = points[points.length - 1].timestamp;
      let start = action.startValue;
      let end = action.endValue;

      if (start === undefined || end === undefined) {
        const startPercent = action.start === undefined ? 0 : action.start;
        const endPercent = action.end === undefined ? 100 : action.end;
        start = min + (max - min) * (startPercent / 100);
        end = min + (max - min) * (endPercent / 100);
      }

      start = Math.max(min, Number(start));
      end = Math.min(max, Number(end));
      if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
        return;
      }

      if (this.focusSelectionTimer) {
        window.clearTimeout(this.focusSelectionTimer);
      }
      this.focusSelectionTimer = window.setTimeout(() => {
        this.loadFocusSelection(start, end);
      }, 450);
    },
      async loadFocusSelection(startMillis, endMillis) {
        if (!this.focusAnalysis) {
          return;
        }
        this.focusSelectionRange = { start: startMillis, end: endMillis };
        this.focusSelectionLoading = true;
        try {
          if (this.focusMode === 'alarm' && this.focusAnalysis.alarmId) {
            this.focusSelection = await fetchAlarmFocusSelection(
              this.focusAnalysis.alarmId,
              Math.round(startMillis),
              Math.round(endMillis),
              64000
            );
          } else {
            this.focusSelection = this.buildLocalFocusSelection(startMillis, endMillis);
          }
        } catch (error) {
          console.warn('Failed to analyze focus selection', error);
        } finally {
        this.focusSelectionLoading = false;
        this.resizeChartsSoon();
      }
    },
    setActiveSegment(segment, syncNav) {
      this.activeSegment = segment;
      const matchingNav = this.navItems.find((item) => item.segment === segment);
      if (syncNav && matchingNav) {
        this.activeNav = matchingNav.key;
      }
      this.resizeChartsSoon();
    },
    isSegmentVisible(segment) {
      return this.activeSegment === segment;
    },
    resizeChartsSoon() {
      this.$nextTick(() => {
        window.setTimeout(() => {
          window.dispatchEvent(new Event('resize'));
        }, 0);
      });
    },
    metricValues(metricKey) {
      return this.ascendingAnalysis
        .map((row) => row[metricKey])
        .filter((value) => value !== null && value !== undefined)
        .map(Number)
        .filter((value) => Number.isFinite(value));
    },
    statsForMetric(metricKey, rows = this.ascendingAnalysis) {
      const values = rows
        .map((row) => row[metricKey])
        .filter((value) => value !== null && value !== undefined)
        .map(Number)
        .filter((value) => Number.isFinite(value));
      if (values.length === 0) {
        return { current: null, average: null, minimum: null, maximum: null };
      }
      const sum = values.reduce((total, value) => total + value, 0);
      return {
        current: values[values.length - 1],
        average: sum / values.length,
        minimum: Math.min(...values),
        maximum: Math.max(...values)
      };
    },
    metricVisualPieces(metricKey, values) {
      const colorSet = ['#93CE07', '#FBDB0F', '#FC7D02', '#FD0100', '#AA069F', '#AC3B2A'];

      if (metricKey === 'anomalyScore') {
        return [
          { gt: 0, lte: 0.25, color: colorSet[0], label: `${this.t('normalRange')} <= 0.25` },
          { gt: 0.25, lte: 0.45, color: colorSet[1], label: `0.25 - 0.45` },
          { gt: 0.45, lte: 0.6, color: colorSet[2], label: `0.45 - 0.60` },
          { gt: 0.6, lte: 0.7, color: colorSet[3], label: `0.60 - 0.70` },
          { gt: 0.7, lte: 0.85, color: colorSet[4], label: `0.70 - 0.85` },
          { gt: 0.85, color: colorSet[5], label: `> 0.85` }
        ];
      }

      if (metricKey === 'crestFactor') {
        return [
          { gt: 0, lte: 3, color: colorSet[0], label: `${this.t('normalRange')} <= 3` },
          { gt: 3, lte: 4, color: colorSet[1], label: `3 - 4` },
          { gt: 4, lte: 5, color: colorSet[2], label: `4 - 5` },
          { gt: 5, lte: 6, color: colorSet[3], label: `5 - 6` },
          { gt: 6, lte: 8, color: colorSet[4], label: `6 - 8` },
          { gt: 8, color: colorSet[5], label: `> 8` }
        ];
      }

      if (metricKey === 'kurtosis') {
        return [
          { gt: 0, lte: 3.5, color: colorSet[0], label: `${this.t('normalRange')} <= 3.5` },
          { gt: 3.5, lte: 4.5, color: colorSet[1], label: `3.5 - 4.5` },
          { gt: 4.5, lte: 6, color: colorSet[2], label: `4.5 - 6` },
          { gt: 6, lte: 8, color: colorSet[3], label: `6 - 8` },
          { gt: 8, lte: 10, color: colorSet[4], label: `8 - 10` },
          { gt: 10, color: colorSet[5], label: `> 10` }
        ];
      }

      return this.dataDrivenPieces(values, colorSet);
    },
    dataDrivenPieces(values, colors) {
      if (values.length === 0) {
        return [{ gt: 0, color: colors[0], label: this.t('normalRange') }];
      }

      const sorted = [...values].sort((a, b) => a - b);
      const min = sorted[0];
      const max = sorted[sorted.length - 1];

      if (min === max) {
        const step = Math.max(Math.abs(min) * 0.05, 0.0001);
        return [
          { lte: min - step, color: colors[0], label: `< ${this.formatNumber(min - step, this.selectedMetricSpec.decimals)}` },
          { gt: min - step, lte: min + step, color: colors[1], label: `${this.t('normalRange')}` },
          { gt: min + step, color: colors[3], label: `> ${this.formatNumber(min + step, this.selectedMetricSpec.decimals)}` }
        ];
      }

      const p20 = this.percentile(sorted, 0.2);
      const p40 = this.percentile(sorted, 0.4);
      const p60 = this.percentile(sorted, 0.6);
      const p80 = this.percentile(sorted, 0.8);
      const p95 = this.percentile(sorted, 0.95);

      return [
        { lte: p20, color: colors[0], label: `<= ${this.formatNumber(p20, this.selectedMetricSpec.decimals)}` },
        { gt: p20, lte: p40, color: colors[1], label: `${this.formatNumber(p20, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p40, this.selectedMetricSpec.decimals)}` },
        { gt: p40, lte: p60, color: colors[2], label: `${this.formatNumber(p40, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p60, this.selectedMetricSpec.decimals)}` },
        { gt: p60, lte: p80, color: colors[3], label: `${this.formatNumber(p60, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p80, this.selectedMetricSpec.decimals)}` },
        { gt: p80, lte: p95, color: colors[4], label: `${this.formatNumber(p80, this.selectedMetricSpec.decimals)} - ${this.formatNumber(p95, this.selectedMetricSpec.decimals)}` },
        { gt: p95, color: colors[5], label: `> ${this.formatNumber(p95, this.selectedMetricSpec.decimals)}` }
      ];
    },
    percentile(sortedValues, ratio) {
      if (sortedValues.length === 1) {
        return sortedValues[0];
      }
      const index = (sortedValues.length - 1) * ratio;
      const lower = Math.floor(index);
      const upper = Math.ceil(index);
      if (lower === upper) {
        return sortedValues[lower];
      }
      const weight = index - lower;
      return sortedValues[lower] * (1 - weight) + sortedValues[upper] * weight;
    },
      buildRawSignalData() {
        if (this.rawSeries.points && this.rawSeries.points.length > 0) {
          return this.rawSeries.points.map((point) => [point.timestamp, point.value]);
        }

        const values = this.latestRaw.values || [];
        const samplingRate = Number(this.latestRaw.samplingRate || 1);
        const base = this.firstFiniteTime(this.latestRaw.measuredAt, this.latestRaw.timestamp, Date.now());
        const intervalMillis = samplingRate > 0 ? 1000 / samplingRate : 1;
        return values.map((value, index) => [base + index * intervalMillis, value]);
      },
      buildCurrentFocusAnalysis() {
        const signalData = this.buildRawSignalData();
        if (!signalData.length) {
          return null;
        }
        const points = signalData.map(([timestamp, value]) => ({
          timestamp,
          value,
          windowIndex: null
        }));
        if (this.rawSeries.points && this.rawSeries.points.length > 0) {
          points.forEach((point, index) => {
            point.windowIndex = this.rawSeries.points[index]?.windowIndex ?? null;
          });
        } else {
          points.forEach((point) => {
            point.windowIndex = this.latestRaw.windowIndex ?? null;
          });
        }
        const first = points[0].timestamp;
        const last = points[points.length - 1].timestamp;
        return {
          alarmId: null,
          equipmentCode: this.selectedEquipment,
          alarmLevel: this.latestAnalysis.alarmLevel || 'normal',
          status: 'current',
          occurredAt: null,
          endedAt: null,
          rangeStart: new Date(first).toISOString(),
          rangeEnd: new Date(last).toISOString(),
          samplingRate: this.rawSeries.samplingRate || this.latestRaw.samplingRate || null,
          windowCount: this.rawSeries.windowCount || 1,
          sampleCount: points.length,
          originalSampleCount: this.rawSeries.originalSampleCount || points.length,
          downsampled: Boolean(this.rawSeries.downsampled),
          firstWindowIndex: this.rawSeries.firstWindowIndex ?? this.latestRaw.windowIndex ?? null,
          lastWindowIndex: this.rawSeries.lastWindowIndex ?? this.latestRaw.windowIndex ?? null,
          points,
          analysisTrend: this.currentReplayAnalysis.map((row) => ({
            analysisResultId: row.id,
            vibrationWindowId: row.vibrationWindowId,
            measuredAt: row.measuredAt || row.createdAt,
            windowIndex: row.windowIndex,
            rms: row.rms,
            peakFrequency: row.peakFrequency,
            peakToPeak: row.peakToPeak,
            crestFactor: row.crestFactor,
            kurtosis: row.kurtosis,
            anomalyScore: row.anomalyScore,
            alarmLevel: row.alarmLevel,
            prediction: row.prediction,
            confidence: row.confidence
          }))
        };
      },
      buildLocalFocusSelection(startMillis, endMillis) {
        const points = (this.focusAnalysis?.points || [])
          .filter((point) => point.timestamp >= startMillis && point.timestamp <= endMillis);
        const selectedPoints = points.length > 0 ? points : this.focusAnalysis.points || [];
        const values = selectedPoints.map((point) => Number(point.value)).filter(Number.isFinite);
        const samplingRate = this.inferCurrentSamplingRate(selectedPoints);
        const features = this.computeSignalFeatures(values, samplingRate);
        const fft = this.buildFftSummary(values, samplingRate);
        const rows = this.focusAnalysis?.analysisTrend || [];
        const candidateRows = rows.filter((row) => {
          const time = this.toTime(row.measuredAt || row.createdAt);
          return Number.isFinite(time) && time >= startMillis && time <= endMillis;
        });
        const representative = candidateRows[candidateRows.length - 1] || rows[rows.length - 1] || this.latestAnalysis;
        const maxScore = candidateRows.reduce((max, row) => {
          const value = Number(row.anomalyScore);
          return Number.isFinite(value) ? Math.max(max, value) : max;
        }, Number(representative?.anomalyScore || 0));

        return {
          alarmId: null,
          equipmentCode: this.selectedEquipment,
          selectedStart: new Date(startMillis).toISOString(),
          selectedEnd: new Date(endMillis).toISOString(),
          sampleCount: values.length,
          originalSampleCount: values.length,
          downsampled: false,
          samplingRate,
          features,
          fft,
          anomalyScore: maxScore,
          alarmLevel: representative?.alarmLevel || this.latestAnalysis.alarmLevel || 'normal',
          prediction: representative?.prediction || this.latestAnalysis.prediction,
          confidence: representative?.confidence ?? this.latestAnalysis.confidence,
          modelVersion: representative?.modelVersion || this.latestAnalysis.modelVersion,
          modelInputType: representative?.modelInputType || this.latestAnalysis.modelInputType,
          modelInputSize: values.length,
          modelExpectedInputSize: representative?.modelExpectedInputSize || this.latestAnalysis.modelExpectedInputSize,
          modelInputStrategy: representative?.modelInputStrategy || this.latestAnalysis.modelInputStrategy,
          modelStatus: representative?.modelStatus || this.latestAnalysis.modelStatus
        };
      },
      computeSignalFeatures(values, samplingRate) {
        if (!values.length) {
          return {};
        }
        const count = values.length;
        const mean = values.reduce((sum, value) => sum + value, 0) / count;
        const squareMean = values.reduce((sum, value) => sum + value * value, 0) / count;
        const rms = Math.sqrt(squareMean);
        const max = Math.max(...values);
        const min = Math.min(...values);
        const peak = Math.max(Math.abs(max), Math.abs(min));
        const peakToPeak = max - min;
        const variance = values.reduce((sum, value) => sum + (value - mean) ** 2, 0) / count;
        const fourthMoment = values.reduce((sum, value) => sum + (value - mean) ** 4, 0) / count;
        const kurtosis = variance > 0 ? fourthMoment / (variance ** 2) : 0;
        const crestFactor = rms > 0 ? peak / rms : 0;
        const fft = this.buildFftSummary(values, samplingRate);
        let peakFrequency = 0;
        let peakMagnitude = -Infinity;
        (fft.frequencies || []).forEach((frequency, index) => {
          if (index === 0) {
            return;
          }
          const magnitude = fft.magnitudes[index] || 0;
          if (magnitude > peakMagnitude) {
            peakMagnitude = magnitude;
            peakFrequency = frequency;
          }
        });
        return { rms, peakFrequency, peakToPeak, crestFactor, kurtosis };
      },
      buildFftSummary(values, samplingRate) {
        if (!values.length || !samplingRate) {
          return { frequencyResolution: 0, binCount: 0, frequencies: [], magnitudes: [] };
        }
        const sampleValues = values.length > 2048 ? this.evenlySampleValues(values, 2048) : values;
        const n = sampleValues.length;
        if (n < 2) {
          return { frequencyResolution: 0, binCount: 0, frequencies: [], magnitudes: [] };
        }
        const mean = sampleValues.reduce((sum, value) => sum + value, 0) / n;
        const centered = sampleValues.map((value) => value - mean);
        const maxBin = Math.floor(n / 2);
        const binStep = Math.max(1, Math.ceil(maxBin / 900));
        const frequencies = [];
        const magnitudes = [];
        for (let k = 0; k <= maxBin; k += binStep) {
          let real = 0;
          let imag = 0;
          for (let i = 0; i < n; i += 1) {
            const angle = (2 * Math.PI * k * i) / n;
            real += centered[i] * Math.cos(angle);
            imag -= centered[i] * Math.sin(angle);
          }
          frequencies.push((k * samplingRate) / n);
          magnitudes.push(Math.sqrt(real * real + imag * imag) / n);
        }
        return {
          frequencyResolution: samplingRate / n,
          binCount: frequencies.length,
          frequencies,
          magnitudes
        };
      },
      evenlySampleValues(values, maxSamples) {
        if (values.length <= maxSamples) {
          return values;
        }
        const step = (values.length - 1) / (maxSamples - 1);
        return Array.from({ length: maxSamples }, (_, index) => values[Math.round(index * step)]);
      },
      inferCurrentSamplingRate(points) {
        const configured = Number(this.focusAnalysis?.samplingRate || this.rawSeries.samplingRate || this.latestRaw.samplingRate);
        if (configured > 0) {
          return configured;
        }
        if (!points || points.length < 2) {
          return 1;
        }
        const intervals = [];
        for (let index = 1; index < Math.min(points.length, 128); index += 1) {
          const diff = points[index].timestamp - points[index - 1].timestamp;
          if (diff > 0) {
            intervals.push(diff);
          }
        }
        if (!intervals.length) {
          return 1;
        }
        intervals.sort((left, right) => left - right);
        const median = intervals[Math.floor(intervals.length / 2)];
        return median > 0 ? Math.round(1000 / median) : 1;
      },
      focusWindowDurationMillis() {
        const samplingRate = Number(this.focusAnalysis?.samplingRate || this.latestRaw.samplingRate);
        const windowSize = Number(this.latestRaw.windowSize || 2048);
        if (samplingRate > 0 && windowSize > 0) {
          return (windowSize / samplingRate) * 1000;
        }
        return 1000;
      },
      metricCard(rank, label, key, decimals, unit = '') {
      const value = this.latestAnalysis[key];
      const series = this.ascendingAnalysis.map((row, index) => [index, row[key] || 0]);
      return {
        rank,
        key,
        label,
        value: value === undefined || value === null ? '-' : `${Number(value).toFixed(decimals)}${unit ? ` ${unit}` : ''}`,
        option: this.sparklineOption(series)
      };
    },
    sparklineOption(data) {
      return {
        grid: { top: 4, left: 0, right: 0, bottom: 2 },
        xAxis: { type: 'value', show: false },
        yAxis: { type: 'value', show: false },
        series: [
          {
            type: 'line',
            smooth: false,
            symbol: 'none',
            areaStyle: { color: 'rgba(0,44,95,0.10)' },
            lineStyle: { color: '#002c5f', width: 1.2 },
            data
          }
        ]
      };
    },
    buildDonutOption(title, rows) {
      const normalizedRows = rows && rows.length > 0 ? rows : [{ name: 'unknown', value: 0 }];
      const total = normalizedRows.reduce((sum, row) => sum + Number(row.value || 0), 0);
      const chartRows = total > 0 ? normalizedRows : [{ name: 'unknown', value: 1, color: alarmColor.unknown }];
      return {
        title: {
          text: `${total}`,
          subtext: title,
          left: 'center',
          top: '43%',
          textStyle: {
            color: '#111827',
            fontSize: 24,
            fontWeight: 700
          },
          subtextStyle: {
            color: '#6b7280',
            fontSize: 11
          }
        },
        tooltip: {
          trigger: 'item',
          formatter: (params) => `${this.tLevel(params.name)}: ${params.value} (${params.percent}%)`
        },
        legend: {
          bottom: 4,
          left: 'center',
          formatter: (name) => this.tLevel(name)
        },
        series: [
          {
            name: title,
            type: 'pie',
            radius: ['42%', '70%'],
            center: ['50%', '48%'],
            avoidLabelOverlap: false,
            minAngle: 4,
            itemStyle: {
              borderRadius: 8,
              borderColor: '#fff',
              borderWidth: 2
            },
            label: { show: false, position: 'center' },
            emphasis: {
              label: {
                show: true,
                fontSize: 22,
                fontWeight: 'bold',
                formatter: (params) => this.tLevel(params.name)
              }
            },
            labelLine: { show: false },
            data: chartRows.map((row) => ({
              name: row.name,
              value: row.value,
              itemStyle: { color: alarmColor[row.name] || row.color || '#002c5f' }
            }))
          }
        ]
      };
    },
    buildAlarmDistribution() {
      const counts = { normal: 0, warning: 0, danger: 0 };
      this.analysisResults.forEach((row) => {
        counts[row.alarmLevel || 'normal'] = (counts[row.alarmLevel || 'normal'] || 0) + 1;
      });
      return Object.keys(counts).map((name) => ({ name, value: counts[name] }));
    },
	    buildStatusDistribution() {
	      const counts = { normal: 0, warning: 0, danger: 0 };
	      this.equipmentHealthRows.forEach((row) => {
	        const level = row.alarmLevel || 'normal';
	        counts[level] = (counts[level] || 0) + 1;
	      });
	      return Object.keys(counts).map((name) => ({ name, value: counts[name] }));
	    },
    formatNumber(value, decimals) {
      if (value === undefined || value === null) {
        return '-';
      }
      return Number(value).toFixed(decimals);
    },
    formatPercent(value) {
      if (value === undefined || value === null) {
        return '-';
      }
      return `${(Number(value) * 100).toFixed(1)}%`;
    },
    formatDateTime(value) {
      if (!value) {
        return '-';
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return '-';
      }
      return date.toLocaleString(this.language === 'ko' ? 'ko-KR' : 'en-US');
    },
    formatTimeOnly(value) {
      if (!value) {
        return '-';
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return '-';
      }
      return date.toLocaleTimeString(this.language === 'ko' ? 'ko-KR' : 'en-US');
    },
    formatFocusRange(focusAnalysis) {
      if (!focusAnalysis) {
        return '-';
      }
      return `${this.formatDateTime(focusAnalysis.rangeStart)} - ${this.formatDateTime(focusAnalysis.rangeEnd)}`;
    },
    formatDuration(seconds) {
      if (seconds === undefined || seconds === null) {
        return '-';
      }
      const totalSeconds = Math.max(0, Number(seconds));
      const minutes = Math.floor(totalSeconds / 60);
      const remainder = Math.floor(totalSeconds % 60);
      if (minutes <= 0) {
        return `${remainder}s`;
      }
      return `${minutes}m ${remainder}s`;
    },
    buildAlarmDisplayMessage(alarm) {
      const level = this.tLevel(alarm.alarmLevel);
      const status = this.t(alarm.status || 'open');
      const duration = this.formatDuration(alarm.durationSeconds);
      const prediction = this.formatPrediction(alarm.prediction);
      const score = this.formatNumber(alarm.anomalyScore, 4);
      const rms = this.formatNumber(alarm.rms, 5);
      const peakToPeak = this.formatNumber(alarm.peakToPeak, 5);
      const kurtosis = this.formatNumber(alarm.kurtosis, 3);
      if (this.language === 'ko') {
        return `${alarm.equipmentCode} ${level} ${status}: 지속 ${duration}, AI ${prediction}, 이상점수 ${score}, RMS ${rms}, P2P ${peakToPeak}, Kurtosis ${kurtosis}`;
      }
      return `${alarm.equipmentCode} ${level} ${status}: duration ${duration}, AI ${prediction}, score ${score}, RMS ${rms}, P2P ${peakToPeak}, kurtosis ${kurtosis}`;
    },
      toTime(value) {
        if (typeof value === 'number') {
          return value;
        }
        return new Date(value).getTime();
      },
      firstFiniteTime(...values) {
        for (const value of values) {
          const time = this.toTime(value);
          if (Number.isFinite(time)) {
            return time;
          }
        }
        return NaN;
      }
    }
  };
</script>
