# Scripts

데이터셋 확인과 변환을 위한 유틸리티 스크립트입니다.

```text
inspect_mat.py          MAT 내부 key/shape 확인
convert_mat_to_jsonl.py MAT Data 배열을 window 단위 JSONL로 변환
convert_sample_jsonl.sh 기본 샘플 MAT 파일을 짧은 JSONL로 변환
setup_ai_api_ubuntu.sh  Ubuntu에서 Python 3.12.10 가상환경 생성
```

Ubuntu 기준으로 `ai-api/.venv`를 활성화한 뒤 실행합니다.

```bash
source ai-api/.venv/bin/activate
python scripts/inspect_mat.py data/raw_mat/BearingType_DeepGrooveBall/SamplingRate_8000/RotatingSpeed_1200/H_H_8_6204_1200.mat
```

샘플 JSONL 변환은 긴 옵션을 매번 치지 않고 아래처럼 실행할 수 있습니다.

```bash
bash scripts/convert_sample_jsonl.sh
```

필요한 값만 환경변수로 바꿀 수 있습니다.

```bash
MAX_WINDOWS=20 OUTPUT=data/jsonl/MOTOR_001_sample_20.jsonl bash scripts/convert_sample_jsonl.sh
```
