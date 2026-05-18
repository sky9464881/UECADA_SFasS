-- UECADA 고정 마스터 데이터 + 테스트 시드
-- 공장 / 라인 / 설비 (27대)

USE uecada;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- ─────────────────────────────────────────
-- 공장
-- ─────────────────────────────────────────
INSERT INTO factory (factory_id, factory_name) VALUES
('FACTORY-01', '스마트 공장');

-- ─────────────────────────────────────────
-- 라인
-- ─────────────────────────────────────────
INSERT INTO `line` (line_id, factory_id, line_name, line_status) VALUES
('LINE-01', 'FACTORY-01', '1라인', 'RUNNING'),
('LINE-02', 'FACTORY-01', '2라인', 'RUNNING'),
('LINE-03', 'FACTORY-01', '3라인', 'STANDBY');

-- ─────────────────────────────────────────
-- 설비 (라인별 9대 × 3 = 27대)
-- ─────────────────────────────────────────

-- LINE-01
INSERT INTO equipment (equipment_code, equipment_name, process_type, model, install_date, location, location_x, location_y) VALUES
('LINE-01_CAST-01',  '주조기 1',  '주조', 'CAST-A100', '2022-03-15', 'LINE-01', 1.0, 1.0),
('LINE-01_CNC-01',   '가공기 1',  '가공', 'CNC-V500',  '2022-03-15', 'LINE-01', 2.0, 1.0),
('LINE-01_CNC-02',   '가공기 2',  '가공', 'CNC-V500',  '2022-03-15', 'LINE-01', 3.0, 1.0),
('LINE-01_CNC-03',   '가공기 3',  '가공', 'CNC-V500',  '2022-04-01', 'LINE-01', 4.0, 1.0),
('LINE-01_WASH-01',  '세척기 1',  '세척', 'WASH-W200', '2022-04-01', 'LINE-01', 5.0, 1.0),
('LINE-01_ASSY-01',  '조립기 1',  '조립', 'ASSY-R300', '2022-04-01', 'LINE-01', 6.0, 1.0),
('LINE-01_ASSY-02',  '조립기 2',  '조립', 'ASSY-R300', '2022-04-01', 'LINE-01', 7.0, 1.0),
('LINE-01_TEST-01',  '검사기 1',  '검사', 'TEST-L400', '2022-05-01', 'LINE-01', 8.0, 1.0),
('LINE-01_TEST-02',  '검사기 2',  '검사', 'TEST-L400', '2022-05-01', 'LINE-01', 9.0, 1.0);

-- LINE-02
INSERT INTO equipment (equipment_code, equipment_name, process_type, model, install_date, location, location_x, location_y) VALUES
('LINE-02_CAST-01',  '주조기 1',  '주조', 'CAST-A100', '2022-06-01', 'LINE-02', 1.0, 2.0),
('LINE-02_CNC-01',   '가공기 1',  '가공', 'CNC-V500',  '2022-06-01', 'LINE-02', 2.0, 2.0),
('LINE-02_CNC-02',   '가공기 2',  '가공', 'CNC-V500',  '2022-06-01', 'LINE-02', 3.0, 2.0),
('LINE-02_CNC-03',   '가공기 3',  '가공', 'CNC-V500',  '2022-06-15', 'LINE-02', 4.0, 2.0),
('LINE-02_WASH-01',  '세척기 1',  '세척', 'WASH-W200', '2022-06-15', 'LINE-02', 5.0, 2.0),
('LINE-02_ASSY-01',  '조립기 1',  '조립', 'ASSY-R300', '2022-06-15', 'LINE-02', 6.0, 2.0),
('LINE-02_ASSY-02',  '조립기 2',  '조립', 'ASSY-R300', '2022-07-01', 'LINE-02', 7.0, 2.0),
('LINE-02_TEST-01',  '검사기 1',  '검사', 'TEST-L400', '2022-07-01', 'LINE-02', 8.0, 2.0),
('LINE-02_TEST-02',  '검사기 2',  '검사', 'TEST-L400', '2022-07-01', 'LINE-02', 9.0, 2.0);

-- LINE-03
INSERT INTO equipment (equipment_code, equipment_name, process_type, model, install_date, location, location_x, location_y) VALUES
('LINE-03_CAST-01',  '주조기 1',  '주조', 'CAST-B200', '2023-01-10', 'LINE-03', 1.0, 3.0),
('LINE-03_CNC-01',   '가공기 1',  '가공', 'CNC-V600',  '2023-01-10', 'LINE-03', 2.0, 3.0),
('LINE-03_CNC-02',   '가공기 2',  '가공', 'CNC-V600',  '2023-01-10', 'LINE-03', 3.0, 3.0),
('LINE-03_CNC-03',   '가공기 3',  '가공', 'CNC-V600',  '2023-02-01', 'LINE-03', 4.0, 3.0),
('LINE-03_WASH-01',  '세척기 1',  '세척', 'WASH-W300', '2023-02-01', 'LINE-03', 5.0, 3.0),
('LINE-03_ASSY-01',  '조립기 1',  '조립', 'ASSY-R400', '2023-02-01', 'LINE-03', 6.0, 3.0),
('LINE-03_ASSY-02',  '조립기 2',  '조립', 'ASSY-R400', '2023-02-15', 'LINE-03', 7.0, 3.0),
('LINE-03_TEST-01',  '검사기 1',  '검사', 'TEST-L500', '2023-02-15', 'LINE-03', 8.0, 3.0),
('LINE-03_TEST-02',  '검사기 2',  '검사', 'TEST-L500', '2023-02-15', 'LINE-03', 9.0, 3.0);

-- ─────────────────────────────────────────
-- 사용자
-- 아래 hash = BCrypt("secret", cost=10)
-- 로그인 테스트: loginId=admin, password=secret
-- ※ 로그인 실패 시 → POST /api/users 로 신규 생성하면 자동 BCrypt 해싱됨
--   (로그인 외 모든 API는 Spring Security 없이 인증 불필요)
-- ─────────────────────────────────────────
INSERT INTO users (user_id, login_id, password_hash, user_name, email, role_name) VALUES
('U001', 'admin',     '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '관리자',     'admin@uecada.com',    'ADMIN'),
('U002', 'manager1',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '이라인장',   'mgr1@uecada.com',     'MANAGER'),
('U003', 'manager2',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '이라인장2',  'mgr2@uecada.com',     'MANAGER'),
('U004', 'operator1', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '작업자1',    'op1@uecada.com',      'OPERATOR'),
('U005', 'operator2', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '작업자2',    'op2@uecada.com',      'OPERATOR'),
('U006', 'operator3', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '작업자3',    'op3@uecada.com',      'OPERATOR');

-- ─────────────────────────────────────────
-- 설비 상태코드 (RUNNING / STANDBY / ALARM / MAINTENANCE)
-- ─────────────────────────────────────────
INSERT INTO equipment_status (equip_id, status_code) VALUES
('LINE-01_CAST-01',  'RUNNING'),
('LINE-01_CNC-01',   'RUNNING'),
('LINE-01_CNC-02',   'ALARM'),
('LINE-01_CNC-03',   'RUNNING'),
('LINE-01_WASH-01',  'RUNNING'),
('LINE-01_ASSY-01',  'RUNNING'),
('LINE-01_ASSY-02',  'STANDBY'),
('LINE-01_TEST-01',  'RUNNING'),
('LINE-01_TEST-02',  'RUNNING'),
('LINE-02_CAST-01',  'RUNNING'),
('LINE-02_CNC-01',   'ALARM'),
('LINE-02_CNC-02',   'RUNNING'),
('LINE-02_CNC-03',   'MAINTENANCE'),
('LINE-02_WASH-01',  'RUNNING'),
('LINE-02_ASSY-01',  'RUNNING'),
('LINE-02_ASSY-02',  'RUNNING'),
('LINE-02_TEST-01',  'RUNNING'),
('LINE-02_TEST-02',  'STANDBY'),
('LINE-03_CAST-01',  'RUNNING'),
('LINE-03_CNC-01',   'RUNNING'),
('LINE-03_CNC-02',   'RUNNING'),
('LINE-03_CNC-03',   'RUNNING'),
('LINE-03_WASH-01',  'RUNNING'),
('LINE-03_ASSY-01',  'STANDBY'),
('LINE-03_ASSY-02',  'RUNNING'),
('LINE-03_TEST-01',  'RUNNING'),
('LINE-03_TEST-02',  'RUNNING');

-- ─────────────────────────────────────────
-- 알람 (alarm 테이블 — 임계값 기반)
-- ─────────────────────────────────────────
INSERT INTO alarm (equipment_code, alarm_code, alarm_type_name, alarm_category, severity, alarm_message, alarm_status, occurred_at, sensor_snapshot) VALUES
('LINE-01_CNC-02', 'VIB-001', '진동 이상', 'VIBRATION', 'CRITICAL',
 '주축 진동 RMS 임계 초과 (측정값: 12.3g, 기준: 8.0g)',
 'OPEN', '2026-05-14 08:23:11',
 '{"rms":12.3,"peak":18.7,"crest_factor":1.52,"timestamp":"2026-05-14T08:23:11"}'),

('LINE-01_CAST-01', 'TMP-001', '온도 이상', 'TEMPERATURE', 'WARNING',
 '용탕 온도 급변 감지 (Δ6.2°C/s, 기준: 5°C/s)',
 'OPEN', '2026-05-14 09:05:42',
 '{"temperature":742.3,"delta_per_sec":6.2,"timestamp":"2026-05-14T09:05:42"}'),

('LINE-02_CNC-01', 'VIB-002', '진동 이상', 'VIBRATION', 'WARNING',
 '주축 진동 RMS 임계 초과 (측정값: 9.1g, 기준: 8.0g)',
 'OPEN', '2026-05-14 10:11:30',
 '{"rms":9.1,"peak":13.4,"crest_factor":1.47,"timestamp":"2026-05-14T10:11:30"}'),

('LINE-01_TEST-02', 'LEAK-001', '리크 압력 이상', 'LEAK', 'CRITICAL',
 '리크 검사 연속 3회 Fail (압력 강하: 12Pa, 기준: 5Pa)',
 'RESOLVED', '2026-05-13 14:22:05',
 '{"leak_pressure_drop":12,"fail_count":3,"timestamp":"2026-05-13T14:22:05"}'),

('LINE-02_ASSY-01', 'TRQ-001', '토크 이상', 'TORQUE', 'WARNING',
 '체결 토크 분포 이상 (σ=4.2N·m, 기준σ=2.0N·m)',
 'RESOLVED', '2026-05-12 16:44:18',
 '{"torque_sigma":4.2,"sample_count":50,"timestamp":"2026-05-12T16:44:18"}'),

('LINE-02_CNC-03', 'MNT-001', '정기 점검', 'MAINTENANCE', 'INFO',
 '정기 점검 일정 도래 (최종 점검: 2026-02-14)',
 'OPEN', '2026-05-14 07:00:00',
 NULL);

-- RESOLVED 알람에 처리 정보 업데이트
UPDATE alarm SET
    resolved_by  = 'U004',
    resolved_at  = '2026-05-13 15:10:00',
    comment      = '리크 씰 교체 후 재검사 통과'
WHERE alarm_code = 'LEAK-001';

UPDATE alarm SET
    resolved_by  = 'U005',
    resolved_at  = '2026-05-12 17:30:00',
    comment      = '토크 렌치 교정 완료'
WHERE alarm_code = 'TRQ-001';

-- ─────────────────────────────────────────
-- 가동 이력 (equipment_operation_log)
-- ─────────────────────────────────────────
INSERT INTO equipment_operation_log (equipment_code, operation_status, start_at, end_at, duration_min, recorded_at) VALUES
('LINE-01_CNC-01', 'RUNNING',     '2026-05-14 06:00:00', '2026-05-14 12:00:00', 360.00, '2026-05-14 06:00:00'),
('LINE-01_CNC-01', 'STANDBY',     '2026-05-14 12:00:00', '2026-05-14 13:00:00',  60.00, '2026-05-14 12:00:00'),
('LINE-01_CNC-01', 'RUNNING',     '2026-05-14 13:00:00', NULL,                   NULL,  '2026-05-14 13:00:00'),

('LINE-01_CNC-02', 'RUNNING',     '2026-05-14 06:00:00', '2026-05-14 08:23:11', 143.18, '2026-05-14 06:00:00'),
('LINE-01_CNC-02', 'ALARM',       '2026-05-14 08:23:11', NULL,                   NULL,  '2026-05-14 08:23:11'),

('LINE-02_CNC-03', 'RUNNING',     '2026-05-13 06:00:00', '2026-05-13 16:00:00', 600.00, '2026-05-13 06:00:00'),
('LINE-02_CNC-03', 'MAINTENANCE', '2026-05-14 07:00:00', NULL,                   NULL,  '2026-05-14 07:00:00'),

('LINE-01_CAST-01','RUNNING',     '2026-05-14 06:00:00', NULL,                   NULL,  '2026-05-14 06:00:00'),
('LINE-01_ASSY-02','STANDBY',     '2026-05-14 06:00:00', NULL,                   NULL,  '2026-05-14 06:00:00'),

('LINE-03_ASSY-01','STANDBY',     '2026-05-14 06:00:00', NULL,                   NULL,  '2026-05-14 06:00:00'),
('LINE-03_ASSY-01','RUNNING',     '2026-05-13 06:00:00', '2026-05-13 18:00:00', 720.00, '2026-05-13 06:00:00');

-- ─────────────────────────────────────────
-- 게시판 (board_post)
-- ─────────────────────────────────────────
INSERT INTO board_post (author_user_id, post_title, post_content, post_type, is_notice, created_at) VALUES
('U001', '[공지] 5월 정기 점검 일정 안내',
 '안녕하세요.\n2호 라인 CNC-03 정기 점검이 5월 14일 진행됩니다.\n작업 중 해당 설비 접근을 삼가 주시기 바랍니다.',
 'NOTICE', 1, '2026-05-13 09:00:00'),

('U002', '1라인 가공기 2 진동 이상 관련 공유',
 '오늘 08:23 발생한 CNC-02 진동 경보 관련하여 현재 원인 파악 중입니다.\n필요 시 생산 일정 조정 예정입니다.',
 'GENERAL', 0, '2026-05-14 09:00:00'),

('U004', '세척기 세척수 교체 요청',
 '1라인 세척기 세척수 농도가 기준치 이하로 떨어졌습니다.\n금일 중 교체 요청드립니다.',
 'GENERAL', 0, '2026-05-14 10:30:00'),

('U003', '2라인 생산 목표 달성 공유',
 '2라인 이번 주 생산 목표 105% 달성했습니다. 수고하셨습니다!',
 'GENERAL', 0, '2026-05-13 17:50:00');

-- ─────────────────────────────────────────
-- 분석 결과 샘플 (analysis_result)
-- vibration_window_id 없이 저장 가능 (nullable FK)
-- ─────────────────────────────────────────
INSERT INTO analysis_result
    (equipment_code, analysis_type, rms, peak_frequency, peak_to_peak, crest_factor, kurtosis,
     prediction, confidence, anomaly_score, alarm_level, model_version, model_status, result_json)
VALUES
('LINE-01_CNC-01', 'vibration', 1.23, 58.3, 4.12, 3.35, 2.87,
 'normal', 0.94, 0.12, 'normal', 'v1.2.0', 'ok',
 '{"rms":1.23,"peak_frequency":58.3,"anomaly_score":0.12,"prediction":"normal"}'),

('LINE-01_CNC-02', 'vibration', 12.30, 112.5, 28.40, 2.31, 4.92,
 'bearing_fault', 0.87, 0.89, 'danger', 'v1.2.0', 'ok',
 '{"rms":12.30,"peak_frequency":112.5,"anomaly_score":0.89,"prediction":"bearing_fault"}'),

('LINE-02_CNC-01', 'vibration', 9.10, 98.2, 18.70, 2.05, 3.78,
 'imbalance', 0.82, 0.72, 'warning', 'v1.2.0', 'ok',
 '{"rms":9.10,"peak_frequency":98.2,"anomaly_score":0.72,"prediction":"imbalance"}'),

('LINE-01_CAST-01', 'temperature', NULL, NULL, NULL, NULL, NULL,
 'overheat_risk', 0.79, 0.65, 'warning', 'v1.0.0', 'ok',
 '{"temperature":742.3,"delta_per_sec":6.2,"anomaly_score":0.65,"prediction":"overheat_risk"}'),

('LINE-01_TEST-02', 'leak', NULL, NULL, NULL, NULL, NULL,
 'leak_detected', 0.96, 0.91, 'danger', 'v1.0.0', 'ok',
 '{"leak_pressure_drop":12,"fail_count":3,"anomaly_score":0.91,"prediction":"leak_detected"}');
