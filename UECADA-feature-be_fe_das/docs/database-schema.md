# Database Schema

초기 SQL은 `database/init/01_schema.sql`에 있습니다.

핵심 테이블:

- `equipment`: 설비 마스터
- `vibration_window`: 원본 window 메타데이터와 raw file path
- `analysis_result`: FFT/AI 분석 결과
- `alarm_history`: warning/danger 이력

## Table Summary

```text
equipment
- id
- equipment_code
- equipment_name
- location
- created_at

vibration_window
- id
- equipment_code
- measured_at
- sampling_rate
- rpm
- window_size
- window_index
- raw_file_path
- created_at

analysis_result
- id
- vibration_window_id
- equipment_code
- rms
- peak_frequency
- peak_to_peak
- crest_factor
- kurtosis
- prediction
- confidence
- model_version
- model_input_type
- model_input_size
- model_expected_input_size
- model_input_strategy
- model_status
- anomaly_score
- alarm_level
- created_at

alarm_history
- id
- equipment_code
- analysis_result_id
- alarm_level
- status
- message
- occurred_at
- ended_at
- duration_seconds
- updated_at
```

## Storage Rule

원본 `values` 배열은 MySQL에 직접 저장하지 않습니다.

```text
values 원본 -> data/raw_windows/...json
MySQL -> raw_file_path와 메타데이터
```

## Spring Boot Mapping

JPA entity와 repository는 아래 패키지에 있습니다.

```text
equipment/entity/Equipment.java
equipment/repository/EquipmentRepository.java
vibration/entity/VibrationWindow.java
vibration/repository/VibrationWindowRepository.java
analysis/entity/AnalysisResult.java
analysis/repository/AnalysisResultRepository.java
alarm/entity/AlarmHistory.java
alarm/repository/AlarmHistoryRepository.java
```

Spring Boot는 `ddl-auto: validate`로 설정되어 있어서, 애플리케이션 시작 시 Entity와 실제 MySQL 테이블 구조가 맞는지 검증합니다.
