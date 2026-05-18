FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py /app/main.py
COPY sim /app/sim
COPY configs /app/configs

# 기본값. docker-compose 에서 SIM_CONFIG / LINE_ID 로 덮어쓴다.
ENV SIM_CONFIG=/app/configs/CAST-01.json
ENV LINE_ID=LINE-01

# exec form -> PID 1 이 SIGINT/SIGTERM 을 정확히 받음
CMD ["python", "-u", "main.py"]
