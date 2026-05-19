# Node-RED Docker Flow

Docker runs Node-RED with `node-red/data` mounted as `/data`.

The active runtime flow file is:

```text
node-red/data/flows.json
```

`das_simulator_import_flow.json` is kept as an importable template copy.

The flow subscribes to raw simulator messages:

```text
das/simulator/+/+/window
```

It then routes messages by equipment instance into separate line tabs:

- `LINE-01 equipment`
- `LINE-02 equipment`
- `LINE-03 equipment`

Each line tab has 9 equipment inputs:

- `CAST-01`
- `CNC-01`, `CNC-02`, `CNC-03`
- `WASH-01`
- `ASSY-01`, `ASSY-02`
- `TEST-01`, `TEST-02`

Each equipment tab republishes the four common sensor streams:

```text
das/common/{line_id}/{equipment_id}/vibration/window
das/common/{line_id}/{equipment_id}/current
das/common/{line_id}/{equipment_id}/voltage
das/common/{line_id}/{equipment_id}/temperature
```

Other Node-RED flows should subscribe to these `das/common/...` topics.

## DAS Collector OPC UA Flow

The import flow also includes `DAS Collector OPC UA`.

This tab subscribes to:

```text
das/common/#
```

It keeps the latest scalar values for all 27 equipment instances and exposes them through the embedded OPC UA server:

```text
opc.tcp://localhost:53880/UA/DAS/
```

Example OPC UA variable names:

```text
ns=1;s=DAS_LINE_01_CAST_01_vibration_rms
ns=1;s=DAS_LINE_01_CAST_01_current_a
ns=1;s=DAS_LINE_01_CAST_01_voltage_v
ns=1;s=DAS_LINE_01_CAST_01_equipment_temperature_c
```

Raw vibration arrays are received by MQTT but are not exposed to OPC UA by default because each window is 32,000 samples. In the `collect DAS and update OPC UA` function, set `SEND_RAW_VIBRATION_TO_OPCUA` to `true` if a `Double Array` OPC UA value is required.

Expected simulator command:

```powershell
py -3 run_simulator.py --mqtt-host localhost --mqtt-port 1883
```

Dry-run is useful before MQTT publishing:

```powershell
py -3 run_simulator.py --dry-run --once --equipment LINE-01 --compact --pretty
```

Each equipment input has two debug styles:

- `{INSTANCE} debug`: disabled by default, shows compact summaries.
- `{INSTANCE} raw MQTT payload`: disabled by default, shows the exact payload being published to MQTT.

Vibration arrays are large, so enable debug only for the equipment instance you are checking. The MQTT out node always publishes the full vibration window.

Default Docker MQTT broker config:

```text
mosquitto:1883
```

When Node-RED is running directly on Ubuntu instead of Docker, change the broker host to `localhost`.
