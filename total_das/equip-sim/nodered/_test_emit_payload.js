// EMIT_PAYLOAD 코드 단위 테스트
// build_flow_das.py 가 만든 flows_das.json 에서 EMIT PAYLOAD function 코드를
// 추출해 실제로 실행, 페이로드 구조 + cycle_time 산출 검증.
const fs = require('fs');
const path = require('path');

const file = path.join(__dirname, 'flows_das.json');
const data = JSON.parse(fs.readFileSync(file, 'utf8'));

const emitNode = data.find(n => n.type === 'function' && n.name === 'EMIT PAYLOAD');
if (!emitNode) { console.error('no EMIT PAYLOAD'); process.exit(1); }

// flow context 모의
const flowCtx = {};
const flow = {
  get: (k) => flowCtx[k],
  set: (k, v) => { flowCtx[k] = v; },
};

// 9개 설비에 대해 새 명세대로 가상 데이터 주입
const EQ = ['CAST-01', 'CNC-01', 'CNC-02', 'CNC-03', 'WASH-01',
            'ASSY-01', 'ASSY-02', 'TEST-01', 'TEST-02'];
const now = Date.now();
const buf = {};
for (const eq of EQ) {
  buf[eq] = {
    last_update_ms: now - 500,
    data: {
      power: true,
      progress: 1.0 / 60,    // 임의로 CAST-01 cycle 기준값
    },
  };
}
// 일부러 ASSY-02 는 오래된 데이터로 (UNCERTAIN)
buf['ASSY-02'].last_update_ms = now - 5000;
// TEST-02 는 power off
buf['TEST-02'].data = { power: false };
buf['TEST-02'].last_update_ms = now - 100;
// CNC-03 은 데이터 없음 (한 번도 받지 못한 경우)
delete buf['CNC-03'];

flowCtx.equipments = buf;

const code = emitNode.func;
const fn = new Function('msg', 'flow', code);

// 한 번 실행
const result = fn({}, flow);
const p = result.payload;
console.log(JSON.stringify(p, null, 2));

const assert = (cond, msg) => { if (!cond) { console.error('FAIL:', msg); process.exit(1); } };
assert(p.line_id === 'LINE-01', 'line_id');
assert(p.schema_version === '1.0', 'schema_version');
assert(p.equipments['CAST-01'].status === 'RUN', 'CAST-01 RUN');
assert(p.equipments['CAST-01'].quality === 'GOOD', 'CAST-01 GOOD');
assert(p.equipments['ASSY-02'].quality === 'UNCERTAIN', 'ASSY-02 UNCERTAIN');
assert(p.equipments['TEST-02'].status === 'OFF', 'TEST-02 OFF');
assert(p.equipments['CNC-03'].status === 'OFF', 'CNC-03 (no data) OFF');
assert(p.equipments['CNC-03'].quality === 'BAD', 'CNC-03 BAD');

// cycle_time 검증: progress 1/60 을 60번 누적시키면 1.0 도달 -> cycle_time 갱신
flowCtx.cycle_state = null;  // reset
for (let i = 0; i < 60; i++) {
  buf['CAST-01'].last_update_ms = Date.now();
  fn({}, flow);
}
const cyc = flowCtx.cycle_state['CAST-01'];
assert(cyc.last_cycle_time !== null, 'CAST-01 cycle_time set after 60 ticks');
console.log('CAST-01 cycle_time =', cyc.last_cycle_time, 's');

console.log('=== ALL ASSERTIONS PASSED ===');
