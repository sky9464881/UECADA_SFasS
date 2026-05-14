# DAS common sensor simulator

## Local total_DAS wiring

This Compose project keeps Mosquitto and the simulator on a DAS-internal
network, and attaches only `das-node-red` to the shared `total-das-net` network
for X_DAS.

Create the shared network once before starting:

```powershell
docker network create total-das-net
```

The Node-RED editor is exposed on <http://localhost:1888>. The OPC UA endpoint
is `opc.tcp://localhost:53880/UA/DAS/` from the host and
`opc.tcp://das-node-red:53880/UA/DAS/` from X_DAS.

The simulator resolves `BearingType_DeepGrooveBall` from `data/raw_mat` when it
is available, and Docker Compose mounts that dataset into the simulator
container. If the dataset is not present, the simulator falls back to a
deterministic synthetic vibration waveform so the Docker stack can still run.

Python simulator for the common sensor/DAQ side before data is sent to Node-RED DAS.

## Line Model

- 3 lines: `LINE-01`, `LINE-02`, `LINE-03`
- Each line has 9 equipment instances:
  - `CAST-01`
  - `CNC-01`, `CNC-02`, `CNC-03`
  - `WASH-01`
  - `ASSY-01`, `ASSY-02`
  - `TEST-01`, `TEST-02`
- Total equipment count: 27
- MQTT topic: `das/simulator/{line_id}/{equipment_id}/window`
- Equipment instance ID inside the payload: `{line_id}-{equipment_id}`, for example `LINE-01-CAST-01`

## Sampling Rule

- Non-vibration sensors publish one scalar sample every second.
- Only the vibration sensor publishes a raw waveform window.
- The simulator payload only includes common sensors for this role:
  - vibration
  - current
  - voltage
  - equipment temperature
- The vibration source selects true normal bearing files from `BearingType_DeepGrooveBall/**/H_H_*.mat`.
- `NORMAL` health windows rotate through multiple clean 600 RPM normal templates so the current v2 AI model returns `prediction=normal` without repeating one identical waveform.
- The vibration RMS is scaled to the equipment state range: `NORMAL`, `WARNING`, or `DANGER`.
- Default vibration model input:
  - `sampling_rate`: `16000` Hz
  - `window_seconds`: `2.0`
  - `window_size`: `32000` samples
  - `stride`: `16000` samples
  - raw input key: `sensors[0].values.vibration_raw`

## Node-RED MQTT Routing

The Docker Node-RED flow has separate tabs by line, with 9 equipment inputs per line:

- `LINE-01 equipment`
- `LINE-02 equipment`
- `LINE-03 equipment`

The simulator publishes raw equipment messages to:

```text
das/simulator/{line_id}/{equipment_id}/window
```

Node-RED republishes common sensor messages for other flows to subscribe:

```text
das/common/{line_id}/{equipment_id}/vibration/window
das/common/{line_id}/{equipment_id}/current
das/common/{line_id}/{equipment_id}/voltage
das/common/{line_id}/{equipment_id}/temperature
```

Examples:

```text
das/common/LINE-01/CAST-01/vibration/window
das/common/LINE-02/CNC-03/current
das/common/LINE-03/WASH-01/temperature
```

The Node-RED debug nodes are disabled by default. Enable only the equipment instance you want to inspect. The full `vibration_raw` array is published on the `vibration/window` MQTT topic unless the simulator is run with `--compact`.

The `DAS Collector OPC UA` Node-RED tab subscribes to `das/common/#`, keeps the latest values for all 27 equipment instances, and exposes scalar DAS values through:

```text
opc.tcp://localhost:53880/UA/DAS/
```

## Docker on Ubuntu/WSL

Run Mosquitto, Node-RED, and the simulator together:

```bash
cd /mnt/c/Users/hwapyeong/Desktop/DAS
.\up.ps1
```

Open Node-RED:

```text
http://localhost:1888
```

The Node-RED runtime data directory is mounted from:

```text
node-red/data
```

Docker services:

- `mosquitto`: MQTT broker on `localhost:1883`
- `node-red`: Node-RED on `localhost:1888`, with the DAS OPC UA server on `localhost:53880`
- `simulator`: publishes 3 lines and 27 equipment payloads to MQTT

Useful Docker commands:

```bash
docker compose logs -f simulator
docker compose logs -f node-red
.\up.ps1 down
```

If port `1883` is already used by a local Mosquitto service:

```bash
sudo service mosquitto stop
.\up.ps1
```

Dry-run inside Docker:

```bash
docker compose run --rm simulator python run_simulator.py --dry-run --once --equipment LINE-01-CAST-01 --compact --pretty
```

## Local Install

```powershell
py -3 -m pip install -r requirements.txt
```

Ubuntu local Python:

```bash
python3 -m pip install -r requirements.txt
```

## Dry Run

Generate one compact payload per equipment for all 3 lines:

```powershell
py -3 run_simulator.py --dry-run --once --compact --pretty
```

Generate only one line:

```powershell
py -3 run_simulator.py --dry-run --once --equipment LINE-01 --compact --pretty
```

Generate all `CAST-01` machines across 3 lines:

```powershell
py -3 run_simulator.py --dry-run --once --equipment CAST-01 --compact --pretty
```

Generate one specific equipment instance with vibration waveform arrays:

```powershell
py -3 run_simulator.py --dry-run --once --equipment LINE-01-CAST-01
```

## MQTT Publish

```powershell
py -3 run_simulator.py --mqtt-host localhost --mqtt-port 1883
```

Force abnormal states:

```powershell
py -3 run_simulator.py --health-state WARNING
py -3 run_simulator.py --health-state DANGER
```

Mix abnormal states automatically:

```powershell
py -3 run_simulator.py --health-state AUTO --warning-rate 0.03 --danger-rate 0.005
```

## Payload Shape

Top-level `sample` is the 1 Hz scalar sample envelope. The vibration sensor has its own `window` object. Process tags, humidity, ambient temperature, and illuminance are intentionally not included in this common-sensor simulator.

```json
{
  "schema_version": "1.0",
  "timestamp": "2026-05-12T00:00:00.000Z",
  "line": {
    "line_id": "LINE-01",
    "line_index": 1,
    "line_count": 3
  },
  "equipment": {
    "instance_id": "LINE-01-CAST-01",
    "equipment_id": "CAST-01",
    "equipment_type": "CAST"
  },
  "sample": {
    "seq": 0,
    "period_sec": 1.0,
    "timestamp": "2026-05-12T00:00:00.000Z"
  },
  "tags": {
    "current_a": 44.2,
    "voltage_v": 381.5,
    "equipment_temperature_c": 66.1,
    "vibration_rms_mm_s": 1.82
  },
  "sensors": [
    {
      "sensor_id": "VIB-01",
      "window": {
        "period_sec": 2.0,
        "window_seconds": 2.0,
        "sample_rate_hz": 16000,
        "sample_count": 32000,
        "window_size": 32000,
        "stride": 16000,
        "model_input": {
          "input_key": "values.vibration_raw",
          "model_version": "spectrogram-pca-rf-v2",
          "preprocessing_version": "raw-stft-64x64-maxnorm-v2"
        }
      },
      "values": {
        "vibration_rms": 1.82,
        "vibration_raw": []
      }
    },
    {
      "sensor_id": "CUR-01",
      "sample": {
        "period_sec": 1.0
      },
      "values": {
        "current": 44.2
      }
    }
  ]
}
```
