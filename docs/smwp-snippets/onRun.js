// SMWP "공정 라인" 페이지 — 실행 시 (OnRun)
// 우측 패널 [연결] 탭 → [실행 시] 슬롯에 그대로 붙여 넣기.
// 매 1초 호출되어 페이지 라벨/차트를 갱신.
// 자세한 설명: docs/smwp-data-binding.md 5절.

(function () {
  var UECADA = window.__uecada;
  if (!UECADA) return;

  window.__uecadaRunOnce = runOnce;

  function runOnce() {
    UECADA.fetchJson('/api/lines?factoryId=' + encodeURIComponent(UECADA.factoryId))
      .then(function (lines) {
        if (!lines) return;
        UECADA.lines = lines;

        var first = lines[0];
        if (first) {
          UECADA.write('LINE_A_OEE', first.latestOee != null ? Math.round(first.latestOee) : '--');
          UECADA.write('LINE_A_UPH', first.uph != null ? Math.round(first.uph) : '--');
          UECADA.write('LINE_A_BAL', first.balanceRate != null ? Math.round(first.balanceRate) : '--');
          UECADA.write('LINE_A_STATUS', first.lineStatus || 'UNKNOWN');
          UECADA.write('LINE_A_ALARMS', first.openAlarmCount || 0);
        }

        lines.forEach(function (ln, idx) {
          UECADA.write('LINE_' + (idx + 1) + '_NAME', ln.lineName);
          UECADA.write('LINE_' + (idx + 1) + '_OEE',  ln.latestOee != null ? Math.round(ln.latestOee) : '--');
        });
      });

    if (UECADA.equipIds && UECADA.equipIds.length) {
      UECADA.fetchJson('/api/equipment-status?equipIds=' + UECADA.equipIds.join(','))
        .then(function (rows) {
          if (!rows) return;
          var counts = { RUNNING: 0, STANDBY: 0, ALARM: 0, MAINTENANCE: 0 };
          rows.forEach(function (r) {
            counts[r.statusCode] = (counts[r.statusCode] || 0) + 1;
            UECADA.write('EQ_' + r.equipId + '_STATUS', r.statusCode);
          });
          UECADA.write('CNT_RUNNING',     counts.RUNNING);
          UECADA.write('CNT_STANDBY',     counts.STANDBY);
          UECADA.write('CNT_ALARM',       counts.ALARM);
          UECADA.write('CNT_MAINTENANCE', counts.MAINTENANCE);
        });
    }

    UECADA.fetchJson('/api/equipments?factoryId=' + encodeURIComponent(UECADA.factoryId))
      .then(function (equips) {
        if (!equips || !equips.length) return;
        var representative = equips.find(function (e) { return e.processType === '주조'; }) || equips[0];
        UECADA.write('PRIMARY_CYCLE_TIME', representative.cycleTimeSec    != null ? representative.cycleTimeSec.toFixed(1) + 's' : '--');
        UECADA.write('PRIMARY_TEMP',       representative.temperatureC    != null ? representative.temperatureC.toFixed(1) + '℃' : '--');
        UECADA.write('PRIMARY_CURRENT',    representative.currentAmp      != null ? representative.currentAmp.toFixed(2) + 'A' : '--');
        UECADA.write('PRIMARY_VIBRATION',  representative.vibrationMmS    != null ? representative.vibrationMmS.toFixed(2) + 'mm/s' : '--');
        UECADA.write('PRIMARY_DEFECT',     representative.defectCount     != null ? representative.defectCount : 0);
        UECADA.write('PRIMARY_UTIL',       representative.utilizationRate != null ? Math.round(representative.utilizationRate) + '%' : '--');
      });

    var firstEq = (UECADA.equipments || [])[0];
    if (firstEq) {
      UECADA.fetchJson('/api/equipments/' + encodeURIComponent(firstEq.equipmentCode)
                       + '/vibration-windows/raw-series?limit=5&maxPoints=400')
        .then(function (s) {
          if (!s || !s.points) return;
          try {
            var chart = (typeof FindObject === 'function') ? FindObject('chartVibration') : null;
            if (chart && chart.SetSeries) {
              chart.SetSeries('vib', s.points.map(function (p) { return [p.t, p.v]; }));
            } else {
              window.__uecadaVibrationSeries = s.points;
            }
          } catch (e) {}
        });
    }
  }

  runOnce();
})();
