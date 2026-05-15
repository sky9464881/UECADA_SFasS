# X_DAS

## Local total_DAS wiring

The Compose file joins the external Docker network `total-das-net`.
Create it once before starting the projects:

```powershell
docker network create total-das-net
```

In this workspace `X_DAS/.env` is already wired to the other two Compose
projects by Docker DNS aliases:

- `opc.tcp://das-node-red:53880/UA/DAS/`
- `opc.tcp://equip-sim-line01-nodered:4870/line-das/LINE-01/`
- `opc.tcp://equip-sim-line02-nodered:4970/line-das/LINE-02/`
- `opc.tcp://equip-sim-line03-nodered:5070/line-das/LINE-03/`

The X_DAS Node-RED editor is exposed on <http://localhost:1890>.

Docker 기반 Node-RED로 OPC UA 데이터를 수집하고 Spring Boot 백엔드 OPC UA 서버로 전달하는 DAS 구성입니다.

## 구조

```text
PLC LINE1 OPC UA ┐
PLC LINE2 OPC UA ├─> Node-RED OPC UA subscribe -> normalize -> OPC UA write -> Spring Boot
PLC LINE3 OPC UA ┤
Sensor DAS OPC UA┘
```

Node-RED는 OPC UA client로 각 설비 서버를 구독하고, 백엔드가 제공하는 OPC UA endpoint에 값을 씁니다. Spring Boot가 OPC UA client로 데이터를 읽는 구조라면 Node-RED 쪽에 OPC UA server flow를 추가하면 됩니다.

## 실행

1. `.env`의 OPC UA endpoint를 실제 주소로 바꿉니다.
2. 컨테이너를 빌드하고 실행합니다.

```powershell
.\up.ps1
```

3. Node-RED 편집기는 `.env`의 `NODE_RED_PORT` 값으로 열립니다. 현재 로컬 기본값은 `1890`입니다.

```text
http://localhost:1890
```

이미 `1890` 포트를 쓰는 서비스가 있으면 `.env`의 `NODE_RED_PORT`를 다른 값으로 바꾸고 다시 실행합니다.

로그 확인:

```powershell
docker compose logs -f x-das-node-red
```

실시간 구독량이 많아 Node-RED debug sidebar가 메시지를 계속 보관하면 메모리를 크게 씁니다. 운영/연동 확인 시에는 X_DAS flow의 debug 노드를 꺼두고, Compose 기본값처럼 `NODE_OPTIONS=--max-old-space-size=4096`을 사용합니다.

중지:

```powershell
.\up.ps1 down
```

## 설정 파일

- `docker-compose.yml`: Node-RED 컨테이너, 포트, 볼륨, 환경변수 연결
- `.env`: PLC 3라인, 외부 Sensor DAS, Spring Boot OPC UA endpoint
- `nodered/data/flows.json`: Node-RED 플로우
- `nodered/data/settings.js`: Node-RED 런타임 설정
- `docker/node-red/Dockerfile`: OPC UA 노드 패키지 설치

## 태그 변경

Node-RED에서 `Build source subscriptions` function 노드의 `sources` 객체를 실제 NodeId에 맞게 수정합니다.

각 항목은 다음 형식입니다.

```javascript
{
  name: "machineState",
  sourceNodeId: "ns=2;s=Line1.MachineState",
  targetNodeId: "ns=2;s=X_DAS.LINE1.MachineState",
  datatype: "Int32"
}
```

- `sourceNodeId`: PLC 또는 Sensor DAS에서 읽을 NodeId
- `targetNodeId`: Spring Boot OPC UA 서버에 쓸 NodeId
- `datatype`: OPC UA datatype

The flow also forwards the aggregated line payload variable from each PLC line as `ns=2;s=X_DAS.<LINE>.payload` so equipment-level structure is preserved as a string.

Backend-facing node mapping is documented in
[`docs/backend_opcua_mapping.md`](docs/backend_opcua_mapping.md). X_DAS now
publishes both raw `X_DAS.LINE_*` nodes and backend schema nodes such as
`ns=2;s=LINE01.CAST01.Temperature`, plus the LINE-01 compatibility alias
`ns=2;s=CAST01.Temperature`.

## 보안/인증

현재 기본값은 개발 편의를 위해 `SecurityPolicy=None`, `SecurityMode=None`, anonymous 연결 기준입니다. 현장 서버가 사용자/암호 또는 인증서를 요구하면 Node-RED의 각 `OPC UA Endpoint` 설정에서 보안 정책, 모드, credentials를 지정하세요. 인증서 파일은 `nodered/certs`에 두면 컨테이너에서 `/certs`로 읽을 수 있습니다.
