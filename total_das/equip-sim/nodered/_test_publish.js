const fs = require('fs');
const path = require('path');

const flowPath = path.join(__dirname, 'flows_das_LINE-01.json');
const data = JSON.parse(fs.readFileSync(flowPath, 'utf8'));
const publishNode = data.find((n) => n.type === 'function' && /^PUBLISH\b/.test(n.name));
if (!publishNode) {
  console.error('no PUBLISH node');
  process.exit(1);
}

const samplePayload = {
  ts: '2026-05-13T05:50:12.345Z',
  line_id: 'LINE-01',
  schema_version: '1.0',
  equipments: {
    'CAST-01': {
      status: 'RUN',
      ts: '2026-05-13T05:50:12.300Z',
      quality: 'GOOD',
      data: {
        power: true,
        progress: 0.0168,
        cycle_time: 59.8,
        injection_pressure: 65.4,
        mold_temperature: 215.3,
        spindle_speed: 5200,
      },
    },
    'TEST-01': {
      status: 'RUN',
      ts: '2026-05-13T05:50:12.300Z',
      quality: 'GOOD',
      data: { power: true, result_ok: false, bore_dimension: 40.001, cycle_time: 120 },
    },
  },
};

const flowCtx = {};
const flow = {
  get: (k) => flowCtx[k],
  set: (k, v) => {
    flowCtx[k] = v;
  },
};

const assert = (condition, message) => {
  if (!condition) {
    console.error('FAIL', message);
    process.exit(1);
  }
};

function flattenSendArg(arg) {
  if (!arg) return [];
  if (Array.isArray(arg)) return arg.flat(Infinity).filter(Boolean);
  return [arg];
}

function runOnce() {
  const sent = [];
  const node = { send: (arg) => sent.push(...flattenSendArg(arg)), status: () => {} };
  const immediateTimeout = (fn) => fn();
  const fn = new Function('msg', 'flow', 'node', 'setTimeout', publishNode.func);
  const result = fn({ payload: samplePayload }, flow, node, immediateTimeout);
  sent.push(...flattenSendArg(result));
  return sent;
}

const first = runOnce();
const adds1 = first.filter((m) => m.payload && m.payload.opcuaCommand === 'addVariable');
const vars1 = first.flatMap((m) => Array.isArray(m.payload) ? m.payload : [])
  .filter((p) => p.messageType === 'Variable');
console.log(`first run: addVariable=${adds1.length}, Variable=${vars1.length}`);
assert(adds1.length > 0, 'first run registers variables');
assert(vars1.length === 0, 'first run skips updates while variables are pending');

for (const m of adds1) {
  assert(/^ns=2;s=[^;]+;datatype=(String|Boolean|Int32|Double|Float)$/.test(m.topic),
    `addVariable topic format: ${m.topic}`);
}

const second = runOnce();
const adds2 = second.filter((m) => m.payload && m.payload.opcuaCommand === 'addVariable');
const vars2 = second.flatMap((m) => Array.isArray(m.payload) ? m.payload : [])
  .filter((p) => p.messageType === 'Variable');
console.log(`second run: addVariable=${adds2.length}, Variable=${vars2.length}`);
assert(adds2.length === 0, 'second run does not register duplicates');
assert(vars2.length === 0, 'second run promotes pending variables to ready');

const third = runOnce();
const adds3 = third.filter((m) => m.payload && m.payload.opcuaCommand === 'addVariable');
const vars3 = third.flatMap((m) => Array.isArray(m.payload) ? m.payload : [])
  .filter((p) => p.messageType === 'Variable');
console.log(`third run: addVariable=${adds3.length}, Variable=${vars3.length}`);
assert(adds3.length === 0, 'third run has no duplicate registrations');
assert(vars3.length > 0, 'third run publishes variable updates');

const byName = Object.fromEntries(vars3.map((p) => [p.variableName, p]));
assert(byName['LINE-01.payload'].datatype === 'String', 'payload String');
assert(JSON.parse(byName['LINE-01.payload'].variableValue).line_id === 'LINE-01', 'payload roundtrip');
assert(byName['LINE-01.CAST-01.status'].datatype === 'String', 'CAST-01.status');
assert(byName['LINE-01.CAST-01.data.injection_pressure'].datatype === 'Double', 'injection_pressure Double');
assert(byName['LINE-01.CAST-01.data.cycle_time'].datatype === 'Double', 'cycle_time Double');
assert(byName['LINE-01.CAST-01.data.power'].datatype === 'Boolean', 'power Boolean');
assert(byName['LINE-01.TEST-01.data.result_ok'].datatype === 'Boolean', 'result_ok Boolean');

console.log('=== ALL ASSERTIONS PASSED ===');
