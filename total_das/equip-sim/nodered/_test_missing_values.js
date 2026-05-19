// Verify EMIT fills missing tags with null and PUBLISH coerces null -> defaults.
// Extract codes from a built flow and execute them with mock buffers.
const fs = require('fs');
const path = require('path');
const flow = JSON.parse(fs.readFileSync(
  path.join(__dirname, 'flows_das_LINE-01.json'), 'utf8'));

function findFn(name) {
  const n = flow.find(x => x.type === 'function' && x.name === name);
  if (!n) throw new Error('fn not found: ' + name);
  return n.func;
}

const emitCode = findFn('EMIT PAYLOAD');
const publishCode = findFn('PUBLISH -> OPC UA Server');

// Mock Node-RED flow context with one partial equipment slot (TEST-02 missing all tags).
const ctx = {};
const flowCtx = {
  get: (k) => ctx[k],
  set: (k, v) => { ctx[k] = v; },
};
ctx['equipments'] = {
  'CAST-01': { data: { power: true, billet_temp: 700 }, last_update_ms: Date.now() },
  // TEST-02 absent on purpose
};

// Run EMIT
const emitFn = new Function('msg', 'flow', 'node', emitCode + '\nreturn { payload: payload };');
// EMIT code already does `return { payload: payload }` — we need to actually wrap in fn:
const runEmit = new Function('msg', 'flow', 'node',
  '"use strict";\n' + emitCode);
const msg = {};
const out = runEmit(msg, flowCtx, { warn: () => {} });
if (!out || !out.payload) throw new Error('EMIT returned no payload');

const p = out.payload;
console.log('EMIT line_id =', p.line_id);
console.log('EMIT equipments count =', Object.keys(p.equipments).length);
// TEST-02 should be present with all expected tags as null (or defaults via EXPECTED_TAGS)
const test02 = p.equipments['TEST-02'];
if (!test02) throw new Error('TEST-02 missing entirely');
const keys = Object.keys(test02.data);
console.log('TEST-02 data keys =', keys);
if (keys.length === 0) throw new Error('TEST-02.data is empty (expected filled with null)');
// hole_dimension should be present
if (!('hole_dimension' in test02.data)) {
  throw new Error('hole_dimension not filled in TEST-02.data');
}
console.log('TEST-02.hole_dimension =', test02.data.hole_dimension);

// Now run PUBLISH with EMIT output. The flow intentionally sends addVariable
// first, skips one tick, then sends Variable updates asynchronously.
const publishFn = new Function('msg', 'flow', 'node',
  '"use strict";\n' + publishCode);
const ctx2 = {};
const flowCtx2 = { get: k => ctx2[k], set: (k,v) => { ctx2[k] = v; } };
const sent = [];
const nodeMock = { warn: () => {}, send: (m) => sent.push(m) };

publishFn({ payload: p }, flowCtx2, nodeMock);
publishFn({ payload: p }, flowCtx2, nodeMock);
publishFn({ payload: p }, flowCtx2, nodeMock);

setTimeout(() => {
  const addMsgs = sent
    .filter(Array.isArray)
    .flatMap(outputs => Array.isArray(outputs[0]) ? outputs[0] : outputs);
  const variableMsgs = sent
    .filter(m => !Array.isArray(m) && Array.isArray(m.payload))
    .flatMap(m => m.payload);

  console.log('PUBLISH addVariable messages =', addMsgs.length);
  console.log('PUBLISH variable updates =', variableMsgs.length);

  // Verify that hole_dimension was sent (with a non-null coerced value)
  const hd = variableMsgs.find(m => m.variableName === 'LINE-01.TEST-02.data.hole_dimension');
  if (!hd) {
    console.log('Sent variable names (first 20):',
      variableMsgs.map(m => m.variableName).slice(0, 20));
    throw new Error('hole_dimension publish message not found');
  }
  console.log('hole_dimension publish payload =',
    JSON.stringify({ name: hd.variableName, val: hd.variableValue, dt: hd.datatype }));
  if (hd.variableValue === null || hd.variableValue === undefined) {
    throw new Error('hole_dimension value not coerced from null');
  }

  // addVariable should also be present (first tick)
  const hdAdd = addMsgs.find(m => m.topic && m.topic.includes('s=LINE-01.TEST-02.data.hole_dimension;') &&
                                  m.payload && m.payload.opcuaCommand === 'addVariable');
  if (!hdAdd) throw new Error('addVariable for hole_dimension missing');
  console.log('addVariable topic =', hdAdd.topic);

  console.log('\nALL ASSERTIONS PASSED');
}, 1000);
