# Architecture

```text
data/raw_mat
-> scripts/convert_mat_to_jsonl.py
-> data/jsonl
-> Node-RED
-> Mosquitto
-> Spring Boot backend
-> FastAPI ai-api
-> MySQL
-> Vue frontend
```

## Service Roles

- `node-red`: JSONL window replay and MQTT publish
- `mqtt`: message broker
- `backend`: MQTT subscribe, persistence, FastAPI client, REST API
- `ai-api`: FFT/statistical feature extraction and model inference
- `database`: schema and seed data
- `frontend`: dashboard UI

## Configuration Rule

서비스 주소는 코드에 직접 쓰지 않고 설정 파일이나 환경변수로 관리합니다.

로컬 직접 실행에서는 `localhost`를 사용하고, Docker Compose 내부 통신에서는 service name을 사용합니다.
