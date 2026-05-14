# Docker Strategy

이 프로젝트는 Ubuntu 로컬 개발과 Docker 실행을 분리해서 관리합니다.

## FastAPI

Python runtime:

```text
3.12.10
```

Ubuntu 로컬 개발:

```bash
bash scripts/setup_ai_api_ubuntu.sh
source ai-api/.venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

Docker:

```bash
docker compose up --build ai-api
```

원칙:

- `.venv`는 로컬 개발용입니다.
- `.venv`는 Git과 Docker image에 넣지 않습니다.
- Dockerfile은 `requirements.lock.txt`만 보고 Python 패키지를 설치합니다.
- `requirements.in`은 직접 쓰는 패키지 목록입니다.
- `requirements.txt`는 직접 의존성의 exact pin 파일입니다.
- `requirements.lock.txt`는 Python 3.12 설치용 전체 pin 파일입니다.

## Spring Boot

Spring Boot는 venv를 쓰지 않고 Gradle로 의존성을 관리합니다.

설정은 profile로 분리합니다.

```text
application-local.yml   localhost 기준
application-docker.yml  compose service name 기준
```

나중에 backend scaffold 후 Dockerfile은 다음 흐름으로 둡니다.

```text
Gradle build stage -> bootJar -> JRE runtime image
```

현재 backend Dockerfile은 이 흐름으로 구성되어 있고, MQTT subscribe 로그 확인은 아래처럼 실행합니다.

```bash
docker compose up --build backend
```

Docker profile에서는 compose service name을 사용합니다.

```text
MySQL: mysql:3306
Mosquitto: mosquitto:1883
FastAPI: http://ai-api:8001
```

## Vue

Vue는 `package.json`과 lock file 기준으로 관리합니다.

나중에 frontend scaffold 후 Dockerfile은 다음 흐름으로 둡니다.

```text
npm ci -> npm run build -> nginx static serving
```

현재 frontend Dockerfile은 이 방식으로 구성되어 있습니다. Nginx는 `/api/*` 요청을 `http://backend:8080/api/*`로 proxy합니다.

## Node-RED, Mosquitto, MySQL

- Node-RED: 공식 image + `node-red/` volume
- Mosquitto: 공식 image + `mqtt/mosquitto.conf`
- MySQL: 공식 image + `database/init/*.sql`

Node-RED Docker flow는 컨테이너 경로와 service name을 사용합니다.

```text
JSONL: /project-data/jsonl/*.jsonl
MQTT broker: mosquitto
Reset API: http://backend:8080/api/debug/reset-data
```

## Docker Compose Service Names

컨테이너 안에서 `localhost`는 자기 자신입니다. 서비스 간 통신은 compose service name을 사용합니다.

```text
backend -> ai-api: http://ai-api:8001
backend -> mysql: jdbc:mysql://mysql:3306/smart_factory
backend -> mosquitto: mosquitto:1883
```

Ubuntu에서 직접 실행할 때는 기존처럼 `localhost`를 사용합니다.

## Full Compose Run

전체 서비스를 Docker로 실행:

```bash
docker compose up --build
```

브라우저:

```text
Vue dashboard: http://localhost:5173
Spring Boot:   http://localhost:8080
FastAPI docs:  http://localhost:8001/docs
Node-RED:      http://localhost:1880
MySQL:         localhost:3306
Mosquitto:     localhost:1883
```

DB 스키마 변경 후 named volume까지 초기화하려면:

```bash
docker compose down -v
docker compose up --build
```

주의: `docker compose up -v`가 아니라, 볼륨 삭제는 `down -v`에서 합니다.
