<template>
  <div ref="chart" class="echart-panel"></div>
</template>

<script>
import * as echarts from 'echarts';

export default {
  name: 'EChartPanel',
  props: {
    option: {
      type: Object,
      required: true
    }
  },
  data() {
    return {
      chart: null,
      dataZoomState: null,
      restoringZoom: false
    };
  },
  watch: {
    option: {
      deep: true,
      handler() {
        this.renderChart();
      }
    }
  },
  mounted() {
    this.chart = echarts.init(this.$refs.chart);
    this.chart.on('datazoom', this.rememberDataZoom);
    this.chart.on('restore', this.clearDataZoomState);
    this.renderChart();
    this.scheduleResize();
    window.addEventListener('resize', this.resizeChart);
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart);
    if (this.chart) {
      this.chart.off('datazoom', this.rememberDataZoom);
      this.chart.off('restore', this.clearDataZoomState);
      this.chart.dispose();
    }
  },
  methods: {
    renderChart() {
      if (!this.chart) {
        return;
      }
      const zoomState = this.dataZoomState;
      const stableOption = {
        animation: false,
        animationDuration: 0,
        animationDurationUpdate: 0,
        animationEasing: 'linear',
        animationEasingUpdate: 'linear',
        ...this.option
      };
      this.chart.setOption(stableOption, {
        notMerge: false,
        lazyUpdate: true
      });
      if (zoomState) {
        this.restoreDataZoom(zoomState);
      }
      this.scheduleResize();
    },
    resizeChart() {
      if (this.chart) {
        this.chart.resize();
      }
    },
    scheduleResize() {
      this.$nextTick(() => {
        window.setTimeout(() => {
          this.resizeChart();
        }, 30);
      });
    },
    rememberDataZoom(params) {
      if (this.restoringZoom) {
        return;
      }

      this.$emit('datazoom', params);
      const actions = params.batch || [params];
      this.dataZoomState = actions
        .map((action) => ({
          dataZoomIndex: action.dataZoomIndex,
          dataZoomId: action.dataZoomId,
          start: action.start,
          end: action.end
        }))
        .filter((action) => action.start !== undefined || action.end !== undefined);
    },
    restoreDataZoom(zoomState) {
      this.restoringZoom = true;
      window.setTimeout(() => {
        zoomState.forEach((state) => {
          this.chart.dispatchAction({
            type: 'dataZoom',
            ...state
          });
        });
        this.restoringZoom = false;
      }, 0);
    },
    clearDataZoomState() {
      this.dataZoomState = null;
    }
  }
};
</script>
