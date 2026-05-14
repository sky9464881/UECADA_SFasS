# AI Model Integration Guide

이 문서는 FastAPI 분석 서버에 fault classification 모델을 교체/추가할 때 맞춰야 하는 기준입니다.

## 현재 추천 모델

현재 프로젝트 설명 기준으로 가장 적합한 모델은 다음입니다.

```text
Spectrogram 64x64
-> Flatten 4096
-> StandardScaler
-> PCA(100)
-> RandomForestClassifier
```

이유:

- Random split 정확도보다 Leave-One-RPM-Out 성능이 더 중요합니다.
- Feature 모델 v4는 평균 74.24%이고, Spectrogram 모델은 평균 93.30%입니다.
- 현재 FastAPI는 raw 진동 window를 받기 때문에, 내부에서 STFT spectrogram을 만들어 모델 입력으로 변환합니다.

## 현재 모델 파일

- 파일 위치: `ai-api/app/models/model.pkl`
- 로더: `ai-api/app/services/predict_service.py`
- 모델 버전: `spectrogram-pca-rf-v1`
- 입력 타입: `spectrogram`
- 모델 입력 shape: `(1, 4096)`
- 의미: `64x64 spectrogram flatten`

확인된 클래스:

```text
bearing
looseness
misalignment
normal
unbalance
```

## 현재 MVP에서 주의할 점

현재 MQTT payload는 실제 센서처럼 raw vibration window만 보냅니다.

```json
{
  "equipmentId": "MOTOR_001",
  "samplingRate": 8000,
  "rpm": 1200,
  "windowSize": 4096,
  "values": [...]
}
```

FastAPI는 이 raw `values`에서 다음을 동시에 계산합니다.

- RMS
- Peak Frequency
- Peak-to-Peak
- Crest Factor
- Kurtosis
- FFT
- STFT Spectrogram 64x64
- AI Prediction

중요:

현재 제공된 모델은 설명상 subset 3, 30204 tapered roller bearing, 16 kHz 기준입니다.
현재 MVP 데이터 폴더는 6204 deep groove ball bearing 중심입니다.
따라서 모델 구조는 맞지만 데이터 도메인은 완전히 같지 않을 수 있습니다.

그 때문에 현재 운영 판정은 기존 signal feature 기반 `anomalyScore`와 `alarmLevel`을 유지하고, AI 모델 결과는 FFT 탭에서 참고 예측으로 표시합니다.

## 추천 학습 스펙

### 1순위: Spectrogram 모델

서비스와 모델을 가장 깔끔하게 맞추려면 다음 기준을 권장합니다.

입력 원천:

```text
raw vibration values
```

이 raw window는 센서 또는 Node-RED가 보내는 값입니다. 운영 payload에는 정답 라벨이나 feature를 넣지 않습니다. FastAPI가 raw window에서 feature, FFT, spectrogram을 모두 계산합니다.

전처리:

```text
raw window
-> STFT spectrogram
-> 64x64 resize
-> flatten
```

모델 입력:

```text
X.shape = (n_windows, 4096)
```

출력:

```text
y.shape = (n_windows,)
y class = normal | bearing | looseness | misalignment | unbalance
```

현재 `bearing`은 볼 결함, 내륜 결함, 외륜 결함을 하나로 합친 통합 라벨입니다. 따라서 이 모델은 `bearing`이라고 말할 수는 있지만, 그 안에서 `ball`, `inner_race`, `outer_race` 중 무엇인지는 구분할 수 없습니다.

세부 베어링 결함까지 화면에 표시하려면 다음 학습부터는 아래처럼 세부 라벨 모델로 학습하는 것을 권장합니다.

```text
y class = normal | ball | inner_race | outer_race | looseness | misalignment | unbalance
```

서비스 프론트엔드는 이미 `ball`, `B`, `inner_race`, `IR`, `outer_race`, `OR` 라벨을 한글 표시명으로 변환할 수 있습니다. 즉 모델만 세부 라벨을 출력하면 기존 API/DB의 `prediction` 문자열을 통해 바로 표시할 수 있습니다.

모델의 1차 출력은 분류 결과입니다.

```text
prediction: normal | bearing | looseness | misalignment | unbalance
confidence: 0.0 ~ 1.0
```

세부 라벨 모델로 교체한 뒤에는 `prediction` 범위를 다음처럼 보면 됩니다.

```text
prediction: normal | ball | inner_race | outer_race | looseness | misalignment | unbalance
confidence: 0.0 ~ 1.0
```

현재 서비스의 알람 레벨은 별도로 계산합니다.

```text
anomalyScore: FastAPI feature rule 기반 점수
alarmLevel: normal | warning | danger
```

즉 1차 안정화 기준에서는 AI 모델은 "고장 유형 추정"을 담당하고, 알람은 기존 feature 기반 점수로 유지합니다. 모델 검증이 충분해지면 `prediction`과 `confidence`까지 알람 판정에 반영할 수 있습니다.

권장 window:

```text
samplingRate = 16000 Hz
windowSeconds = 2.0 sec
windowSize = 32000 samples
stride = 16000 or 32000 samples
```

8 kHz 데이터만 쓸 경우:

```text
samplingRate = 8000 Hz
windowSeconds = 2.0 sec
windowSize = 16000 samples
```

핵심은 학습과 추론에서 `samplingRate`, `windowSeconds`, `STFT 파라미터`, `resize 방식`이 같아야 한다는 점입니다.

현재 2048 또는 4096 sample window는 빠른 MQTT 시연에는 좋지만, PHM 결함 분류 모델 학습 기준으로는 짧은 편입니다.

중요:

현재 모델처럼 MAT 파일 안의 `Spectrogram` 필드로 학습했다면, 운영 FastAPI가 raw 신호에서 만드는 STFT spectrogram과 완전히 같지 않을 수 있습니다. 센서 연동까지 생각하면 다음 학습부터는 `raw Data -> FastAPI와 동일한 STFT 전처리 -> 64x64` 방식으로 학습 데이터를 만드는 것을 권장합니다.

### 2순위: Feature 모델

설명 가능성과 운영 안정성을 우선하면 feature 모델도 좋습니다.

추천 feature:

```text
rms
peak
peak_to_peak
kurtosis
crest_factor
band_1x_energy
band_2x_energy
band_3x_energy
bearing_band_energy
dominant_frequency
```

추가 추천:

```text
rms_ratio_to_normal_baseline
band_1x_diff_to_normal_baseline
band_2x_diff_to_normal_baseline
band_3x_diff_to_normal_baseline
```

단, feature 모델은 FastAPI와 학습 코드가 같은 feature 추출 공식을 써야 합니다.

## pkl vs npz

### sklearn 모델은 pkl/joblib 추천

RandomForest, StandardScaler, PCA, Pipeline을 같이 저장하려면 `joblib` 기반 `pkl`이 가장 편합니다.

추천:

```text
model.pkl
```

저장 방식:

```python
import joblib

artifact = {
    "model": pipeline,
    "model_version": "spectrogram-pca-rf-v1",
    "input_type": "spectrogram",
    "sampling_rate": 16000,
    "window_seconds": 2.0,
    "window_size": 32000,
    "spectrogram_shape": [64, 64],
    "spectrogram_size": 64,
    "stft_params": {
        "window": "hann",
        "nperseg": 256,
        "noverlap": 128,
        "detrend": False,
        "scaling": "spectrum",
        "mode": "magnitude",
    },
    "class_names": list(pipeline.classes_),
    "sklearn_version": "1.8.0",
    "preprocessing_version": "raw-stft-64x64-v1",
}

joblib.dump(artifact, "model.pkl")
```

FastAPI는 위 artifact를 읽어서 다음 값을 자동으로 사용합니다.

```text
model
model_version
input_type
spectrogram_shape / spectrogram_size
stft_params
sampling_rate / window_size / window_seconds
class_names
```

`class_names`는 사람이 보기 위한 메타데이터이지만, 가능하면 `list(pipeline.classes_)`처럼 모델의 실제 클래스 순서와 같게 저장합니다. 특히 `predict_proba()` 확률을 클래스별로 풀어서 보여주려면 이 순서가 중요합니다.

따라서 나중에 모델을 바꿀 때는 같은 키를 가진 새 `model.pkl`로 교체하면 됩니다. 모델 내부가 RandomForest에서 SVM, XGBoost, MLP 등으로 바뀌어도 `predict()`와 가능하면 `predict_proba()`를 제공하면 현재 서비스와 연결됩니다.

### npz는 데이터/배열 저장에 적합

`npz`는 학습용 배열을 저장할 때 좋습니다.

예:

```text
X_spectrogram_64x64.npz
y_labels.npz
normal_baseline_features.npz
```

하지만 sklearn Pipeline 전체를 운영 API에 넣는 파일로는 `pkl/joblib`이 더 적합합니다.

정리:

```text
운영 모델 파일: pkl/joblib
학습 중간 데이터: npz
```

## FastAPI 출력 기준

FastAPI는 Spring Boot에 아래 값을 반환합니다.

```json
{
  "prediction": "bearing",
  "confidence": 0.87,
  "modelVersion": "spectrogram-pca-rf-v1",
  "modelInputType": "spectrogram",
  "modelInputSize": 32000,
  "modelExpectedInputSize": 4096,
  "modelInputStrategy": "stft_spectrogram_64x64_from_raw",
  "modelStatus": "loaded",
  "anomalyScore": 0.76,
  "alarmLevel": "warning"
}
```

현재는 `anomalyScore`와 `alarmLevel`을 signal feature 기반으로 유지합니다.
모델 성능이 검증되면 `prediction`과 `confidence`를 알람 판정에 함께 반영할 수 있습니다.
