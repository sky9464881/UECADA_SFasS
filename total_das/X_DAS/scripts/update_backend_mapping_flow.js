const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const flowPath = path.join(root, "nodered", "data", "flows.json");

function sanitizeJsonText(text) {
  let out = "";
  let inString = false;
  let escaped = false;
  for (const ch of text) {
    if (escaped) {
      out += ch;
      escaped = false;
      continue;
    }
    if (ch === "\\") {
      out += ch;
      escaped = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      out += ch;
      continue;
    }
    if (inString && ch === "\n") {
      out += "\\n";
      continue;
    }
    if (inString && ch === "\r") {
      continue;
    }
    out += ch;
  }
  return out;
}

const flow = JSON.parse(sanitizeJsonText(fs.readFileSync(flowPath, "utf8")));

const collectorId = "fn_collect_backend_format";
const publisherId = "fn_publish_xdas_backend";
const liveDebugId = "debug_xdas_live_sample";
const subscriptionId = "fn_build_subscriptions";
const normalizerIds = [
  "fn_normalize_line1",
  "fn_normalize_line2",
  "fn_normalize_line3",
  "fn_normalize_sensor_das",
];

const processTagsByEquipmentFunc = `const processTagsByEquipment = {
  'CAST-01': [
    ['power', 'Boolean'],
    ['injection_pressure_sp', 'Double'],
    ['mold_temperature_sp', 'Double'],
    ['cooling_flow_sp', 'Double'],
    ['injection_pressure', 'Double'],
    ['mold_temperature', 'Double'],
    ['cooling_flow', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'CNC-01': [
    ['power', 'Boolean'],
    ['spindle_speed_sp', 'Int32'],
    ['tool_usage_sp', 'Double'],
    ['coolant_flow_sp', 'Double'],
    ['spindle_speed', 'Int32'],
    ['tool_usage', 'Double'],
    ['coolant_flow', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'CNC-02': [
    ['power', 'Boolean'],
    ['spindle_speed_sp', 'Int32'],
    ['tool_usage_sp', 'Double'],
    ['coolant_flow_sp', 'Double'],
    ['spindle_speed', 'Int32'],
    ['tool_usage', 'Double'],
    ['coolant_flow', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'CNC-03': [
    ['power', 'Boolean'],
    ['spindle_speed_sp', 'Int32'],
    ['tool_usage_sp', 'Double'],
    ['coolant_flow_sp', 'Double'],
    ['spindle_speed', 'Int32'],
    ['tool_usage', 'Double'],
    ['coolant_flow', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'WASH-01': [
    ['power', 'Boolean'],
    ['cleaning_concentration_sp', 'Double'],
    ['cleaning_temperature_sp', 'Double'],
    ['cleaning_pressure_sp', 'Double'],
    ['cleaning_concentration', 'Double'],
    ['cleaning_temperature', 'Double'],
    ['cleaning_pressure', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'ASSY-01': [
    ['power', 'Boolean'],
    ['tightening_torque_sp', 'Double'],
    ['tightening_angle_sp', 'Double'],
    ['press_force_sp', 'Double'],
    ['tightening_torque', 'Double'],
    ['tightening_angle', 'Double'],
    ['press_force', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'ASSY-02': [
    ['power', 'Boolean'],
    ['tightening_torque_sp', 'Double'],
    ['tightening_angle_sp', 'Double'],
    ['press_force_sp', 'Double'],
    ['tightening_torque', 'Double'],
    ['tightening_angle', 'Double'],
    ['press_force', 'Double'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'TEST-01': [
    ['power', 'Boolean'],
    ['bore_dimension_sp', 'Double'],
    ['hole_dimension_sp', 'Double'],
    ['bore_dimension', 'Double'],
    ['hole_dimension', 'Double'],
    ['result_ok', 'Boolean'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ],
  'TEST-02': [
    ['power', 'Boolean'],
    ['bore_dimension_sp', 'Double'],
    ['hole_dimension_sp', 'Double'],
    ['bore_dimension', 'Double'],
    ['hole_dimension', 'Double'],
    ['result_ok', 'Boolean'],
    ['progress', 'Double'],
    ['cycle_time', 'Double']
  ]
};`;

const collectorFunc = `const original = RED.util.cloneMessage(msg);
original.backendWrite = false;

const tag = msg.xDas && msg.xDas.tag;
if (!tag) return [original, null];

const snapshots = flow.get('backend_equipment_snapshots') || {};
const cycleState = flow.get('backend_cycle_state') || {};
const out = [original];
const now = Date.now();
const nowIso = new Date(now).toISOString();

const EQUIPMENT_CODE = {
  'CAST-01': 'CAST01',
  'CNC-01': 'CNC01',
  'CNC-02': 'CNC02',
  'CNC-03': 'CNC03',
  'WASH-01': 'WASH01',
  'ASSY-01': 'ASSY01',
  'ASSY-02': 'ASSY02',
  'TEST-01': 'TEST01',
  'TEST-02': 'TEST02'
};

const PLC_MAP = {
  'CAST-01': {
    'data.injection_pressure': ['InjectionPressure', 'Double'],
    'data.mold_temperature': ['MoldTemperature', 'Double'],
    'data.cooling_flow': ['CoolingFlow', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'CNC-01': {
    'data.spindle_speed': ['SpindleSpeed', 'Int32'],
    'data.tool_usage': ['ToolUsage', 'Double'],
    'data.coolant_flow': ['CoolantFlow', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'CNC-02': {
    'data.spindle_speed': ['SpindleSpeed', 'Int32'],
    'data.tool_usage': ['ToolUsage', 'Double'],
    'data.coolant_flow': ['CoolantFlow', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'CNC-03': {
    'data.spindle_speed': ['SpindleSpeed', 'Int32'],
    'data.tool_usage': ['ToolUsage', 'Double'],
    'data.coolant_flow': ['CoolantFlow', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'WASH-01': {
    'data.cleaning_concentration': ['CleaningConcentration', 'Double'],
    'data.cleaning_temperature': ['CleaningTemperature', 'Double'],
    'data.cleaning_pressure': ['CleaningPressure', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'ASSY-01': {
    'data.tightening_torque': ['TighteningTorque', 'Double'],
    'data.tightening_angle': ['TighteningAngle', 'Double'],
    'data.press_force': ['PressForce', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'ASSY-02': {
    'data.tightening_torque': ['TighteningTorque', 'Double'],
    'data.tightening_angle': ['TighteningAngle', 'Double'],
    'data.press_force': ['PressForce', 'Double'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'TEST-01': {
    'data.bore_dimension': ['BoreDimension', 'Double'],
    'data.hole_dimension': ['HoleDimension', 'Double'],
    'data.result_ok': ['ResultOk', 'Boolean'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  },
  'TEST-02': {
    'data.bore_dimension': ['BoreDimension', 'Double'],
    'data.hole_dimension': ['HoleDimension', 'Double'],
    'data.result_ok': ['ResultOk', 'Boolean'],
    'data.cycle_time': ['CycleTime', 'Double'],
    'status': ['Status', 'Int32', 'statusCode']
  }
};

const SENSOR_MAP = {
  'vibration_rms': ['SensorVibration', 'Double', 'vibration'],
  'current_a': ['SensorCurrent', 'Double', 'current'],
  'voltage_v': ['SensorVoltage', 'Double', 'voltage'],
  'equipment_temperature_c': ['SensorTemperature', 'Double', 'temperature']
};

function parseTag(tagName) {
  const parts = String(tagName).split('.');
  if (parts.length < 2) return null;
  const lineId = parts[0];
  const equipmentId = parts[1];
  if (!EQUIPMENT_CODE[equipmentId]) return null;

  if (parts[2] === 'sensor') {
    return { lineId, equipmentId, kind: 'sensor', field: parts.slice(3).join('.') };
  }
  if (parts[2] === 'data') {
    return { lineId, equipmentId, kind: 'plc', field: 'data.' + parts.slice(3).join('.') };
  }
  return { lineId, equipmentId, kind: 'plc', field: parts.slice(2).join('.') };
}

function lineCode(lineId) {
  return String(lineId).replace('-', '');
}

function statusCode(value) {
  if (typeof value === 'number') return value;
  const v = String(value || '').toUpperCase();
  if (v === 'RUN' || v === 'ON' || v === 'GOOD') return 1;
  if (v === 'OFF' || v === 'STOP') return 0;
  if (v === 'WARNING' || v === 'UNCERTAIN') return 2;
  if (v === 'DANGER' || v === 'ALARM' || v === 'BAD') return 3;
  return -1;
}

function numeric(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function updateCycle(lineId, equipmentId, value, mode) {
  const key = lineId + '.' + equipmentId;
  const st = cycleState[key] || { acc: 0, count: 0, startedAt: now, lastCycleTime: 0 };
  st.acc += Math.max(0, numeric(value, 0));
  if (st.acc >= 1) {
    st.count += 1;
    st.lastCycleTime = Math.max(0, +((now - (st.startedAt || now)) / 1000).toFixed(3));
    st.acc = st.acc % 1;
    st.startedAt = now;
  }
  cycleState[key] = st;
  return mode === 'cycleCount' ? st.count : st.lastCycleTime;
}

function ensureSnapshot(lineId, equipmentId) {
  const key = lineId + '.' + equipmentId;
  const snap = snapshots[key] || {
    lineId,
    equipmentId,
    plc: {},
    sensor: {},
    updatedAt: nowIso
  };
  snap.updatedAt = nowIso;
  snapshots[key] = snap;
  return snap;
}

function typedMessage(nodeName, datatype, value, meta, backendWrite = true) {
  return {
    topic: 'ns=2;s=' + nodeName + ';datatype=' + datatype,
    payload: value,
    backendWrite,
    xDas: Object.assign({}, msg.xDas || {}, meta || {}, {
      backendNodeId: 'ns=2;s=' + nodeName,
      backendDatatype: datatype,
      backendWrite
    })
  };
}

function emitBackend(lineId, equipmentId, fieldName, datatype, value, meta) {
  const eqCode = EQUIPMENT_CODE[equipmentId];
  const scopedName = lineCode(lineId) + '.' + eqCode + '.' + fieldName;
  out.push(typedMessage(scopedName, datatype, value, meta, true));

  // Compatibility alias for the BE table supplied by the user. LINE-02/03 keep
  // the line prefix to avoid overwriting LINE-01 values.
  if (lineId === 'LINE-01') {
    out.push(typedMessage(eqCode + '.' + fieldName, datatype, value, meta, true));
  }
}

function emitSnapshot(lineId, equipmentId, snap) {
  const eqCode = EQUIPMENT_CODE[equipmentId];
  const payload = JSON.stringify({
    line_id: lineId,
    equipment_id: equipmentId,
    equipment_code: eqCode,
    plc: snap.plc,
    sensor: snap.sensor,
    updated_at: snap.updatedAt
  });
  emitBackend(lineId, equipmentId, 'Payload', 'String', payload, { backendField: 'Payload' });
}

const parsed = parseTag(tag);
if (!parsed) return [out, null];

const snap = ensureSnapshot(parsed.lineId, parsed.equipmentId);
let value = msg.payload;

if (parsed.kind === 'sensor') {
  const sensorTarget = SENSOR_MAP[parsed.field];
  if (sensorTarget) {
    const [backendField, datatype, snapField] = sensorTarget;
    value = numeric(value);
    snap.sensor[snapField] = value;
    snap.sensor.updated_at = nowIso;
    emitBackend(parsed.lineId, parsed.equipmentId, backendField, datatype, value, { backendField, sourceKind: 'sensor' });
    emitSnapshot(parsed.lineId, parsed.equipmentId, snap);
  }
} else {
  const plcTarget = PLC_MAP[parsed.equipmentId] && PLC_MAP[parsed.equipmentId][parsed.field];
  if (plcTarget) {
    const [backendField, datatype, transform] = plcTarget;
    if (transform === 'statusCode') value = statusCode(value);
    else if (transform === 'cycleTime') value = updateCycle(parsed.lineId, parsed.equipmentId, value, transform);
    else if (transform === 'cycleCount') value = updateCycle(parsed.lineId, parsed.equipmentId, value, transform);
    else if (datatype === 'Double') value = numeric(value);
    else if (datatype === 'Int32') value = Math.trunc(numeric(value));
    else if (datatype === 'Boolean') value = !!value;

    snap.plc[backendField] = value;
    snap.plc.updated_at = nowIso;
    emitBackend(parsed.lineId, parsed.equipmentId, backendField, datatype, value, { backendField, sourceKind: 'plc' });

    if (backendField === 'Status') {
      snap.plc.AlarmCode = snap.plc.AlarmCode || 0;
      emitBackend(parsed.lineId, parsed.equipmentId, 'AlarmCode', 'Int32', snap.plc.AlarmCode, { backendField: 'AlarmCode', sourceKind: 'derived' });
    }
    emitSnapshot(parsed.lineId, parsed.equipmentId, snap);
  }
}

flow.set('backend_equipment_snapshots', snapshots);
flow.set('backend_cycle_state', cycleState);
node.status({ fill: 'green', shape: 'dot', text: 'BE mapped ' + (out.length - 1) + ' node(s)' });

let debugMessage = null;
const lastDebugAt = Number(flow.get('xdas_live_debug_last_ms') || 0);
if (out.length > 1 && now - lastDebugAt >= 1000) {
  flow.set('xdas_live_debug_last_ms', now);
  debugMessage = {
    topic: parsed.lineId + '.' + parsed.equipmentId,
    payload: {
      received_at: nowIso,
      source: msg.xDas && msg.xDas.source,
      source_tag: tag,
      source_node_id: msg.xDas && msg.xDas.sourceNodeId,
      source_value: msg.payload,
      backend_nodes: out.slice(1).map((m) => ({
        node_id: m.xDas && m.xDas.backendNodeId,
        datatype: m.xDas && m.xDas.backendDatatype,
        field: m.xDas && m.xDas.backendField,
        value: m.payload
      })),
      snapshot: snap
    }
  };
}
return [out, debugMessage];`;

function findNode(id) {
  return flow.find((node) => node.id === id);
}

const subscription = findNode(subscriptionId);
if (!subscription) throw new Error(`Missing subscription builder node ${subscriptionId}`);
subscription.func = subscription.func.replace(
  /const processTagsByEquipment = \{[\s\S]*?\n\};\n\nconst sensorDasTags =/,
  `${processTagsByEquipmentFunc}\n\nconst sensorDasTags =`
);

for (const id of normalizerIds) {
  const node = findNode(id);
  if (!node) throw new Error(`Missing normalizer node ${id}`);
  node.wires = [[collectorId]];
}

let collector = findNode(collectorId);
if (!collector) {
  collector = {
    id: collectorId,
    type: "function",
    z: "tab_x_das_bridge",
    name: "Collect equipment snapshot / map BE schema",
    outputs: 2,
    timeout: 0,
    noerr: 0,
    initialize: "",
    finalize: "",
    libs: [],
    x: 1160,
    y: 300,
    wires: [[publisherId], [liveDebugId]],
  };
  flow.push(collector);
}
collector.func = collectorFunc;
collector.outputs = 2;
collector.wires = [[publisherId], [liveDebugId]];

let liveDebug = findNode(liveDebugId);
if (!liveDebug) {
  liveDebug = {
    id: liveDebugId,
    type: "debug",
    z: "tab_x_das_bridge",
    name: "X_DAS live receive sample",
    active: true,
    tosidebar: true,
    console: false,
    tostatus: false,
    complete: "payload",
    targetType: "msg",
    statusVal: "",
    statusType: "auto",
    x: 1450,
    y: 300,
    wires: [],
  };
  flow.push(liveDebug);
}
liveDebug.name = "X_DAS live receive sample";
liveDebug.active = true;
liveDebug.tosidebar = true;
liveDebug.console = false;
liveDebug.tostatus = false;
liveDebug.complete = "payload";
liveDebug.targetType = "msg";

const publisher = findNode(publisherId);
if (!publisher) throw new Error(`Missing publisher node ${publisherId}`);
publisher.func = `const enabled = /^(1|true|yes|on)$/i.test(String(env.get('BACKEND_OPCUA_WRITE_ENABLED') || 'false'));
const shouldWriteBackend = enabled && msg.backendWrite === true;
const topic = String(msg.topic || '');
const match = topic.match(/^ns=(\\d+);s=([^;]+);datatype=([^;]+)$/);
if (!match) {
  node.warn(\`Cannot publish X_DAS OPC UA value without typed topic: \${topic}\`);
  return [null, shouldWriteBackend ? msg : null];
}

const namespace = Number(match[1]);
const variableName = match[2];
const datatype = match[3];
const registered = flow.get('xdas_opcua_registered') || {};
const serverMessages = [];

if (!registered[variableName]) {
  serverMessages.push({
    topic: \`ns=\${namespace};s=\${variableName};datatype=\${datatype};browseName=\${variableName}\`,
    payload: { opcuaCommand: 'addVariable' }
  });
  registered[variableName] = true;
}

serverMessages.push({
  payload: {
    messageType: 'Variable',
    namespace,
    variableName,
    variableValue: msg.payload,
    datatype
  },
  xDas: msg.xDas
});

flow.set('xdas_opcua_registered', registered);
node.status({
  fill: shouldWriteBackend ? 'green' : 'blue',
  shape: 'dot',
  text: shouldWriteBackend ? 'server + backend write' : 'server publish only'
});
return [serverMessages, shouldWriteBackend ? msg : null];`;

const comment = findNode("comment_architecture");
if (comment) {
  comment.name = "Inputs -> X_DAS raw nodes + BE schema nodes";
  comment.info = "X_DAS keeps raw X_DAS.LINE_* nodes and also publishes BE schema nodes such as LINE01.CAST01.InjectionPressure and CAST01.InjectionPressure (LINE-01 alias).";
}

fs.writeFileSync(flowPath, JSON.stringify(flow, null, 2), "utf8");
console.log(`updated ${flowPath}`);
