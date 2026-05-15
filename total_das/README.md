# total_DAS local Docker runbook

This workspace has three independent Docker Compose projects:

- `DAS`: common external sensor simulator -> MQTT -> Node-RED OPC UA server
- `equip-sim`: line equipment PLC simulators -> line Node-RED OPC UA servers
- `X_DAS`: OPC UA client bridge that subscribes to `DAS` and `equip-sim`, normalizes tags, exposes an `X_DAS` OPC UA endpoint, and can optionally write to a backend OPC UA endpoint

All projects stay separate, but their Node-RED containers share one Docker network:

```powershell
docker network create total-das-net
```

It is OK if Docker says the network already exists.

## Start order

Open each folder separately and run these commands.

### 1. DAS

```powershell
cd .\DAS
.\up.ps1
```

- Node-RED UI: <http://localhost:1888>
- Sensor OPC UA endpoint inside Docker: `opc.tcp://das-node-red:53880/UA/DAS/`
- Sensor OPC UA endpoint from host: `opc.tcp://localhost:53880/UA/DAS/`

### 2. equip-sim

Run one Compose project per line:

```powershell
cd .\equip-sim
.\scripts\up.ps1 LINE-01
.\scripts\up.ps1 LINE-02
.\scripts\up.ps1 LINE-03
```

The wrapper rebuilds the correct Node-RED flow for each line and then runs Docker Compose with the matching `.env.lineNN`.

- LINE-01 UI: <http://localhost:1880>, endpoint: `opc.tcp://equip-sim-line01-nodered:4870/line-das/LINE-01/`
- LINE-02 UI: <http://localhost:1881>, endpoint: `opc.tcp://equip-sim-line02-nodered:4970/line-das/LINE-02/`
- LINE-03 UI: <http://localhost:1882>, endpoint: `opc.tcp://equip-sim-line03-nodered:5070/line-das/LINE-03/`

### 3. X_DAS

```powershell
cd .\X_DAS
.\up.ps1
```

- Node-RED UI: <http://localhost:1890>
- Source endpoints are configured in `X_DAS\.env`
- X_DAS OPC UA endpoint from host: `opc.tcp://localhost:54880/UA/X_DAS/`
- Backend write is disabled by default. Set `BACKEND_OPCUA_WRITE_ENABLED=true` and `BACKEND_OPCUA_ENDPOINT=<backend opcua endpoint>` in `X_DAS\.env` when a real backend OPC UA server is ready.

## Stop

Run `.\up.ps1 down` in `DAS` and `X_DAS`.

For `equip-sim`, stop each line:

```powershell
cd .\equip-sim
.\scripts\up.ps1 LINE-01 down
.\scripts\up.ps1 LINE-02 down
.\scripts\up.ps1 LINE-03 down
```
