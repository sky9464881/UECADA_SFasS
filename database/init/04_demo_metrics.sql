-- 라인/설비 데모 KPI (화면 수치용)
USE uecada;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS line_station_balance (
    line_id           VARCHAR(20)  NOT NULL,
    station_no        INT          NOT NULL,
    utilization_pct   DECIMAL(5,2) NOT NULL,
    recorded_at       DATETIME     NOT NULL,
    PRIMARY KEY (line_id, station_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS equipment_runtime_demo (
    equipment_code    VARCHAR(50)  NOT NULL,
    utilization_rate  DECIMAL(5,2) NOT NULL,
    defect_count      INT          NOT NULL DEFAULT 0,
    operator_name     VARCHAR(50)  NOT NULL,
    cycle_time_sec    DECIMAL(10,2) NOT NULL,
    current_amp       DECIMAL(8,2) DEFAULT NULL,
    temperature_c     DECIMAL(8,2) DEFAULT NULL,
    humidity_pct      DECIMAL(5,2) DEFAULT NULL,
    vibration_mm_s    DECIMAL(8,3) DEFAULT NULL,
    recorded_at       DATETIME     NOT NULL,
    PRIMARY KEY (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO line_kpi_log (line_id, line_oee, line_availability, line_performance, line_quality, line_uph, line_upmh, line_balance_rate, takt_time, avg_cycle_time, recorded_at) VALUES
('LINE-01', 70.0, 92.0, 88.0, 96.5, 520.0, 1240.0, 88.0, 58.0, 62.5, NOW()),
('LINE-02', 68.0, 90.0, 85.0, 94.0, 480.0, 1180.0, 82.0, 62.0, 65.0, NOW()),
('LINE-03', 65.0, 88.0, 82.0, 93.0, 445.0, 1100.0, 79.0, 65.0, 68.0, NOW());

INSERT INTO line_station_balance (line_id, station_no, utilization_pct, recorded_at) VALUES
('LINE-01', 1, 72.0, NOW()), ('LINE-01', 2, 85.0, NOW()), ('LINE-01', 3, 78.0, NOW()),
('LINE-01', 4, 90.0, NOW()), ('LINE-01', 5, 82.0, NOW()), ('LINE-01', 6, 88.0, NOW()),
('LINE-02', 1, 70.0, NOW()), ('LINE-02', 2, 80.0, NOW()), ('LINE-02', 3, 75.0, NOW()),
('LINE-02', 4, 86.0, NOW()), ('LINE-02', 5, 78.0, NOW()), ('LINE-02', 6, 84.0, NOW()),
('LINE-03', 1, 68.0, NOW()), ('LINE-03', 2, 76.0, NOW()), ('LINE-03', 3, 72.0, NOW()),
('LINE-03', 4, 82.0, NOW()), ('LINE-03', 5, 74.0, NOW()), ('LINE-03', 6, 79.0, NOW());

INSERT INTO factory_kpi_log (factory_id, factory_oee, factory_availability, factory_performance, factory_quality, factory_uph, factory_upmh, avg_line_balance_rate, recorded_at) VALUES
('FACTORY-01', 68.5, 90.0, 85.0, 94.5, 482.0, 1175.0, 83.0, NOW());

INSERT INTO equipment_runtime_demo (equipment_code, utilization_rate, defect_count, operator_name, cycle_time_sec, current_amp, temperature_c, humidity_pct, vibration_mm_s, recorded_at) VALUES
('LINE-01_CAST-01',  87.5, 2, '김주조', 58.2, 42.3, 211.0, 48.0, 1.23, NOW()),
('LINE-01_CNC-01',   91.2, 1, '이가공', 45.0, 38.5, 45.0,  42.0, 1.18, NOW()),
('LINE-01_CNC-02',   62.0, 8, '박가공', 52.0, 55.1, 48.0,  40.0, 12.30, NOW()),
('LINE-01_CNC-03',   89.0, 0, '최가공', 44.5, 36.2, 44.0,  41.0, 1.05, NOW()),
('LINE-01_WASH-01',  85.0, 1, '정세척', 38.0, 22.0, 35.0,  55.0, 0.85, NOW()),
('LINE-01_ASSY-01',  88.0, 2, '한조립', 72.0, 28.5, 32.0,  45.0, 0.92, NOW()),
('LINE-01_ASSY-02',  55.0, 0, '윤조립',  0.0,  0.0,  25.0,  44.0, 0.10, NOW()),
('LINE-01_TEST-01',  93.0, 0, '서검사', 28.0, 15.0, 28.0,  50.0, 0.45, NOW()),
('LINE-01_TEST-02',  90.5, 1, '강검사', 30.0, 16.2, 29.0,  49.0, 0.52, NOW()),
('LINE-02_CAST-01',  86.0, 1, '김주조', 60.0, 41.0, 208.0, 47.0, 1.15, NOW()),
('LINE-02_CNC-01',   64.0, 5, '이가공', 48.0, 52.0, 46.0,  41.0, 9.10, NOW()),
('LINE-02_CNC-02',   88.0, 1, '박가공', 46.0, 37.0, 43.0,  40.0, 1.08, NOW()),
('LINE-02_CNC-03',   0.0,  0, '최가공',  0.0,  0.0,  22.0,  38.0, 0.05, NOW()),
('LINE-02_WASH-01',  84.0, 2, '정세척', 39.0, 21.5, 34.0,  54.0, 0.88, NOW()),
('LINE-02_ASSY-01',  87.0, 3, '한조립', 70.0, 27.0, 31.0,  46.0, 0.95, NOW()),
('LINE-02_ASSY-02',  89.0, 0, '윤조립', 71.0, 26.5, 30.0,  45.0, 0.90, NOW()),
('LINE-02_TEST-01',  92.0, 0, '서검사', 27.0, 14.5, 27.0,  48.0, 0.42, NOW()),
('LINE-02_TEST-02',  58.0, 0, '강검사',  0.0,  0.0,  26.0,  48.0, 0.08, NOW()),
('LINE-03_CAST-01',  82.0, 1, '오주조', 62.0, 40.0, 205.0, 46.0, 1.10, NOW()),
('LINE-03_CNC-01',   85.0, 0, '신가공', 47.0, 35.0, 42.0,  39.0, 1.02, NOW()),
('LINE-03_CNC-02',   86.0, 1, '유가공', 46.5, 34.5, 41.0,  39.0, 0.98, NOW()),
('LINE-03_CNC-03',   84.0, 2, '임가공', 48.0, 36.0, 43.0,  40.0, 1.12, NOW()),
('LINE-03_WASH-01',  83.0, 1, '배세척', 40.0, 20.5, 33.0,  53.0, 0.82, NOW()),
('LINE-03_ASSY-01',  52.0, 0, '조조립',  0.0,  0.0,  24.0,  43.0, 0.06, NOW()),
('LINE-03_ASSY-02',   88.0, 1, '홍조립', 73.0, 29.0, 32.0,  44.0, 0.94, NOW()),
('LINE-03_TEST-01',  91.0, 0, '문검사', 29.0, 15.5, 28.0,  47.0, 0.48, NOW()),
('LINE-03_TEST-02',  90.0, 0, '양검사', 28.5, 15.0, 27.5, 47.0, 0.46, NOW());
