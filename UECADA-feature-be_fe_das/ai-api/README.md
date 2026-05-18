# AI API

FastAPI 기반 진동 분석 서버 위치입니다.

역할:

- vibration window 수신
- RMS, FFT, peak frequency, peak-to-peak, crest factor, kurtosis 계산
- AI 모델 추론
- Spring Boot에 분석 결과 반환

## Ubuntu Setup

먼저 Python `3.12.10`이 설치되어 있어야 합니다.

```bash
python3.12 --version
```

가상환경 생성:

```bash
bash scripts/setup_ai_api_ubuntu.sh
source ai-api/.venv/bin/activate
python --version
```

실행:

```bash
cd /home/hwapyeong/smart_factory_vib_monitoring/ai-api
source .venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

분석 API:

```bash
curl -X POST http://localhost:8001/analyze \
  -H 'Content-Type: application/json' \
  -d '{
    "equipmentId":"MOTOR_001",
    "timestamp":"2026-05-06T12:00:00.000Z",
    "samplingRate":8000,
    "rpm":1200,
    "windowSize":3,
    "windowIndex":0,
    "values":[0.1,0.2,0.3]
  }'
```

## Docker

```bash
docker build -t smart-factory-ai-api .
docker run --rm -p 8001:8001 smart-factory-ai-api
```

원칙:

- 로컬 개발은 `.venv`를 사용합니다.
- `.venv`는 Git과 Docker image에 포함하지 않습니다.
- Docker image는 `requirements.lock.txt` 기준으로 의존성을 설치합니다.
- Python runtime은 `.python-version`의 `3.12.10`에 맞춥니다.
- 직접 의존성은 `requirements.in`, 직접 의존성 exact pin은 `requirements.txt`, 전체 설치 pin은 `requirements.lock.txt`로 관리합니다.
