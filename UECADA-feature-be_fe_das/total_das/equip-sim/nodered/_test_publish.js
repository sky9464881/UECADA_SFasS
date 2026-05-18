// PUBLISH → OPC UA Server function 코드 단위 테스트
//
// node-red-contrib-opcua OpcUa-Server 는 2-단계 프로토콜:
//   1) addVariable: msg.topic='ns=2;s=<name>;datatype=<DT>',
//                   msg.payload={opcuaCommand:'addVariable'}
//   2) Variable:    msg.payload={messageType,namespace,variableName,
//                                variableValue,datatype}
//
// 따라서 첫 호출에서는 (addVariable, Variable) 쌍이 발행되고
// 두 번째 호출에서는 Variable 만 발행되어야 한다.

const fs = require('fs');
const path = require('path');

const data = JSON.parse(fs.readFileSync(path.join(__dirname, 'flows_das.json')));
const node = data.find(n => n.type === 'function' && /^PUBLISH\b/.test(n.name));
if (!node) { console.error('no PUBLISH'); process.exit(1); }

const samplePayload = {
  ts: '2026-05-13T05:50:12.345Z',
  line_id: 'LINE-01',
  schema_version: '1.0',
  equipments: {
    'CAST-01': {
      status: 'RUN', ts: '2026-05-13T05:50:12.300Z', quality: 'GOOD',
      data: {
        power: true,
        progress: 0.0168,
        cycle_time: 59.8,
        injection_pressure: 65.4,
        mold_temperature: 215.3,
        spindle_speed: 5200,  // Int32
      },
    },
    'TEST-01': {
      status: 'RUN', ts: '2026-05-13T05:50:12.300Z', quality: 'GOOD',
      data: { power: true, result_ok: false, bore_dimension: 40.001 },
    },
  },
};

// flow context 시뮬레이터
const flowCtx = {};
const flow = {
  get: (k) => flowCtx[k],
  set: (k, v) => { flowCtx[k] = v; },
};
function runOnce() {
  const fn = new Function('msg', 'flow', node.func);
  const res = fn({ payload: samplePayload }, flow);
  return res[0];
}

const assert = (c, m) => { if (!c) { console.error('FAIL', m); process.exit(1); } };

// --- 1차 호출: addVariable + Variable 둘 다 발행되어야 ---
const first = runOnce();
const adds1 = first.filter(m => m.payload && m.payload.opcuaCommand === 'addVariable');
const vars1 = first.filter(m => m.payload && m.payload.messageType === 'Variable');
console.log(`first run: ${first.length} msgs (addVariable=${adds1.length}, Variable=${vars1.length})`);
assert(adds1.length === vars1.length, 'addVariable count == Variable count');
assert(adds1.length > 0, 'at least one addVariable on first run');

// addVariable 토픽 형식 검증
for (const m of adds1) {
  assert(/^ns=2;s=[^;]+;datatype=(String|Boolean|Int32|Double|Float)$/.test(m.topic),
    `addVariable topic format: ${m.topic}`);
}

// Variable 메시지 필수 필드 (서버가 검사하는 4개 + datatype)
for (const m of vars1) {
  const p = m.payload;
  assert(p.messageType === 'Variable',           `messageType for ${JSON.stringify(p)}`);
  assert(p.namespace === 2,                       `namespace for ${p.variableName}`);
  assert(typeof p.variableName === 'string' && p.variableName.length > 0,
                                                  `variableName missing`);
  assert('variableValue' in p,                    `variableValue missing for ${p.variableName}`);
  assert(typeof p.datatype === 'string',          `datatype missing for ${p.variableName}`);
}

// 변수 이름 모음
const names1 = new Set(vars1.map(m => m.payload.variableName));
assert(names1.has('LINE-01.payload'),                              'LINE-01.payload');
assert(names1.has('LINE-01.line_ts'),                              'line_ts');
assert(names1.has('LINE-01.schema_version'),                       'schema_version');
assert(names1.has('LINE-01.CAST-01.status'),                       'CAST-01.status');
assert(names1.has('LINE-01.CAST-01.data.injection_pressure'),      'injection_pressure');
assert(names1.has('LINE-01.CAST-01.data.progress'),                'progress');
assert(names1.has('LINE-01.CAST-01.data.cycle_time'),              'cycle_time');
assert(names1.has('LINE-01.TEST-01.data.result_ok'),               'result_ok');

// dtype 매핑 (float -> Double, bool, int32, string)
const byName = Object.fromEntries(vars1.map(m => [m.payload.variableName, m.payload]));
assert(byName['LINE-01.CAST-01.data.injection_pressure'].datatype === 'Double', 'inj_pressure Double');
assert(byName['LINE-01.TEST-01.data.result_ok'].datatype === 'Boolean'
       && byName['LINE-01.TEST-01.data.result_ok'].variableValue === false, 'result_ok Bool false');
assert(byName['LINE-01.CAST-01.data.spindle_speed'].datatype === 'Int32', 'spindle_speed Int32');
assert(byName['LINE-01.CAST-01.data.power'].datatype === 'Boolean'
       && byName['LINE-01.CAST-01.data.power'].variableValue === true, 'power Bool true');
assert(byName['LINE-01.payload'].datatype === 'String', 'payload String');
const jsonStr = byName['LINE-01.payload'].variableValue;
assert(JSON.parse(jsonStr).line_id === 'LINE-01', 'payload roundtrip');

// --- 2차 호출: 같은 이름은 addVariable 없이 Variable 만 ---
const second = runOnce();
const adds2 = second.filter(m => m.payload && m.payload.opcuaCommand === 'addVariable');
const vars2 = second.filter(m => m.payload && m.payload.messageType === 'Variable');
console.log(`second run: ${second.length} msgs (addVariable=${adds2.length}, Variable=${vars2.length})`);
assert(adds2.length === 0, 'no addVariable on second run');
assert(vars2.length === vars1.length, 'Variable count stable across runs');

console.log('=== ALL ASSERTIONS PASSED ===');
