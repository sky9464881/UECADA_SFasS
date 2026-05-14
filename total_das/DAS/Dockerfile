FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY das_simulator ./das_simulator
COPY run_simulator.py .

CMD ["python", "run_simulator.py", "--mqtt-host", "mosquitto", "--mqtt-port", "1883", "--health-state", "AUTO"]
