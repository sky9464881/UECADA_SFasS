# X_DAS Backend OPC UA Mapping

X_DAS receives:

- PLC/line data from `equip-sim` line OPC UA servers.
- Common external sensor data from `DAS`.

X_DAS then publishes both as one equipment-centered OPC UA surface:

```text
opc.tcp://localhost:54880/UA/X_DAS/
```

If `BACKEND_OPCUA_WRITE_ENABLED=true`, X_DAS also writes the same backend-schema
nodes to `BACKEND_OPCUA_ENDPOINT`. Raw `X_DAS.LINE_*` nodes are kept internally
but are not written to the backend server.

## Node ID Convention

All lines are published with a line prefix to prevent collisions:

```text
ns=2;s=LINE01.CAST01.InjectionPressure
ns=2;s=LINE02.CNC01.SpindleSpeed
ns=2;s=LINE03.WASH01.CleaningTemperature
```

For compatibility with the BE table, LINE-01 is also published without the line
prefix:

```text
ns=2;s=CAST01.InjectionPressure
```

Each equipment also has a combined JSON snapshot:

```text
ns=2;s=LINE01.CAST01.Payload
ns=2;s=CAST01.Payload
```

The payload contains `line_id`, `equipment_id`, `equipment_code`, `plc`,
`sensor`, and `updated_at`.

## Status Code

| Source status | Backend status code |
|---|---:|
| `OFF`, `STOP` | 0 |
| `RUN`, `ON`, `GOOD` | 1 |
| `WARNING`, `UNCERTAIN` | 2 |
| `DANGER`, `ALARM`, `BAD` | 3 |
| unknown | -1 |

`AlarmCode` is derived as `0` while the simulators do not emit alarm events.

## PLC Mapping

Use `{LINE}` as `LINE01`, `LINE02`, or `LINE03`. LINE-01 also has the alias
without `{LINE}.`.

| Process | Equipment | Backend Node ID | Type | Source line tag | BE handling |
|---|---|---|---|---|---|
| Casting | CAST-01 | `ns=2;s={LINE}.CAST01.InjectionPressure` | Double | `injection_pressure` | Buffer: `CAST01:injection_pressure` |
| Casting | CAST-01 | `ns=2;s={LINE}.CAST01.MoldTemperature` | Double | `mold_temperature` | Buffer: `CAST01:mold_temperature` |
| Casting | CAST-01 | `ns=2;s={LINE}.CAST01.CoolingFlow` | Double | `cooling_flow` | Buffer: `CAST01:cooling_flow` |
| Casting | CAST-01 | `ns=2;s={LINE}.CAST01.CycleTime` | Double | `cycle_time` | Buffer: `CAST01:cycle_time` |
| Casting | CAST-01 | `ns=2;s={LINE}.CAST01.Status` | Int32 | `status` | `equipment_status` UPSERT |
| Casting | CAST-01 | `ns=2;s={LINE}.CAST01.AlarmCode` | Int32 | derived `0` | `alarms` INSERT when non-zero |
| Machining | CNC-01/02/03 | `ns=2;s={LINE}.CNC01.SpindleSpeed` | Int32 | `spindle_speed` | Buffer: `CNC01:spindle_speed` |
| Machining | CNC-01/02/03 | `ns=2;s={LINE}.CNC01.ToolUsage` | Double | `tool_usage` | Buffer: `CNC01:tool_usage` |
| Machining | CNC-01/02/03 | `ns=2;s={LINE}.CNC01.CoolantFlow` | Double | `coolant_flow` | Buffer: `CNC01:coolant_flow` |
| Machining | CNC-01/02/03 | `ns=2;s={LINE}.CNC01.CycleTime` | Double | `cycle_time` | Buffer |
| Machining | CNC-01/02/03 | `ns=2;s={LINE}.CNC01.Status` | Int32 | `status` | `equipment_status` UPSERT |
| Machining | CNC-01/02/03 | `ns=2;s={LINE}.CNC01.AlarmCode` | Int32 | derived `0` | `alarms` INSERT when non-zero |
| Washing | WASH-01 | `ns=2;s={LINE}.WASH01.CleaningConcentration` | Double | `cleaning_concentration` | Buffer: `WASH01:cleaning_concentration` |
| Washing | WASH-01 | `ns=2;s={LINE}.WASH01.CleaningTemperature` | Double | `cleaning_temperature` | Buffer: `WASH01:cleaning_temperature` |
| Washing | WASH-01 | `ns=2;s={LINE}.WASH01.CleaningPressure` | Double | `cleaning_pressure` | Buffer: `WASH01:cleaning_pressure` |
| Washing | WASH-01 | `ns=2;s={LINE}.WASH01.CycleTime` | Double | `cycle_time` | Buffer |
| Washing | WASH-01 | `ns=2;s={LINE}.WASH01.Status` | Int32 | `status` | `equipment_status` UPSERT |
| Assembly | ASSY-01/02 | `ns=2;s={LINE}.ASSY01.TighteningTorque` | Double | `tightening_torque` | Buffer: `ASSY01:tightening_torque` |
| Assembly | ASSY-01/02 | `ns=2;s={LINE}.ASSY01.TighteningAngle` | Double | `tightening_angle` | Buffer: `ASSY01:tightening_angle` |
| Assembly | ASSY-01/02 | `ns=2;s={LINE}.ASSY01.PressForce` | Double | `press_force` | Buffer: `ASSY01:press_force` |
| Assembly | ASSY-01/02 | `ns=2;s={LINE}.ASSY01.CycleTime` | Double | `cycle_time` | Buffer |
| Assembly | ASSY-01/02 | `ns=2;s={LINE}.ASSY01.Status` | Int32 | `status` | `equipment_status` UPSERT |
| Assembly | ASSY-01/02 | `ns=2;s={LINE}.ASSY01.AlarmCode` | Int32 | derived `0` | `alarms` INSERT when non-zero |
| Test | TEST-01/02 | `ns=2;s={LINE}.TEST01.BoreDimension` | Double | `bore_dimension` | Buffer: `TEST01:bore_dimension` |
| Test | TEST-01/02 | `ns=2;s={LINE}.TEST01.HoleDimension` | Double | `hole_dimension` | Buffer: `TEST01:hole_dimension` |
| Test | TEST-01/02 | `ns=2;s={LINE}.TEST01.ResultOk` | Boolean | `result_ok` | Buffer: `TEST01:result_ok` |
| Test | TEST-01/02 | `ns=2;s={LINE}.TEST01.CycleTime` | Double | `cycle_time` | Buffer |
| Test | TEST-01/02 | `ns=2;s={LINE}.TEST01.Status` | Int32 | `status` | `equipment_status` UPSERT |

For `CNC-02`, `CNC-03`, `ASSY-02`, and `TEST-02`, the equipment code changes
to `CNC02`, `CNC03`, `ASSY02`, and `TEST02`.

## Common Sensor Mapping

The same four sensor fields are attached to every equipment payload.
These are equipment-scoped DAS values. There are no backend `ENV:*`,
`vibration_x`, `vibration_y`, or `vibration_z` buffer keys in the current
specification.

| Backend Node ID | Type | Sensor DAS source tag |
|---|---|---|
| `ns=2;s={LINE}.{EQUIP}.SensorVibration` | Double | `vibration_rms` |
| `ns=2;s={LINE}.{EQUIP}.SensorCurrent` | Double | `current_a` |
| `ns=2;s={LINE}.{EQUIP}.SensorVoltage` | Double | `voltage_v` |
| `ns=2;s={LINE}.{EQUIP}.SensorTemperature` | Double | `equipment_temperature_c` |
| `ns=2;s={LINE}.{EQUIP}.Payload` | String JSON | PLC + sensor merged snapshot |

Example:

```text
ns=2;s=LINE01.CAST01.SensorVibration
ns=2;s=LINE01.CAST01.Payload
```
