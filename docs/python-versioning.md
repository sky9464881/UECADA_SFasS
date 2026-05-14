# Python Versioning

현재 Python 기준은 `3.12.10`입니다.

```text
.python-version
ai-api/.python-version
ai-api/Dockerfile
ai-api/.venv
```

위 기준이 서로 어긋나면 안 됩니다.

## Files

```text
ai-api/requirements.in        직접 사용하는 패키지 목록
ai-api/requirements.txt       직접 의존성 exact pin
ai-api/requirements.lock.txt  Python 3.12 설치용 전체 pin
```

## Ubuntu Setup

먼저 Python `3.12.10`이 설치되어 있어야 합니다.

```bash
python3.12 --version
```

이 명령이 실패하거나 `Python 3.12.10`이 아니면 Python `3.12.10`을 먼저 설치합니다.

이 PC처럼 Ubuntu apt의 `python3.12`가 다른 patch 버전이면 `uv`로 정확한 런타임을 받습니다.

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
~/.local/bin/uv python install 3.12.10
```

이 Ubuntu 환경은 `/usr/bin/python3`를 직접 바꾸지 않고, 사용자 PATH의 `~/.local/bin`에서 `python`, `python3`, `python3.12`가 `uv`로 설치한 `3.12.10`을 가리키게 맞춥니다. Ubuntu 시스템 패키지용 `/usr/bin/python3`는 건드리지 않습니다.

```bash
python --version
python3 --version
python3.12 --version
```

```bash
bash scripts/setup_ai_api_ubuntu.sh
source ai-api/.venv/bin/activate
python --version
```

## Updating Dependencies

의존성을 바꿀 때는 순서를 지킵니다.

```text
1. requirements.in 수정
2. requirements.txt exact pin 수정
3. Python 3.12.10 .venv 재설치 또는 업데이트
4. pip freeze > requirements.lock.txt
5. python -m pip check
```

Docker는 `python:3.12.10-slim`과 `requirements.lock.txt`를 설치 기준으로 사용합니다.
