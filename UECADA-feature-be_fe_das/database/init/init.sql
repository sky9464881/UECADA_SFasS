-- UECADA Smart Factory DB Schema
-- MySQL 8.0 기준

CREATE DATABASE IF NOT EXISTS uecada
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE uecada;
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS board_post;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_room;
DROP TABLE IF EXISTS alarm_history;
DROP TABLE IF EXISTS alarm;
DROP TABLE IF EXISTS analysis_result;
DROP TABLE IF EXISTS vibration_window;
DROP TABLE IF EXISTS equipment_kpi_log;
DROP TABLE IF EXISTS line_kpi_log;
DROP TABLE IF EXISTS factory_kpi_log;
DROP TABLE IF EXISTS equipment_operation_log;
DROP TABLE IF EXISTS equipment_status;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS `line`;
DROP TABLE IF EXISTS factory;

SET FOREIGN_KEY_CHECKS = 1;

-- ─────────────────────────────────────────
-- 공장 / 라인
-- ─────────────────────────────────────────

CREATE TABLE factory (
    factory_id   VARCHAR(20)  NOT NULL,
    factory_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `line` (
    line_id     VARCHAR(20)  NOT NULL,
    factory_id  VARCHAR(20)  NOT NULL,
    line_name   VARCHAR(100) NOT NULL,
    line_status VARCHAR(30)  DEFAULT NULL,
    PRIMARY KEY (line_id),
    CONSTRAINT fk_line_factory FOREIGN KEY (factory_id) REFERENCES factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────
-- 설비
-- ─────────────────────────────────────────

CREATE TABLE equipment (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    equipment_code VARCHAR(50)   NOT NULL,
    equipment_name VARCHAR(100)  NOT NULL,
    process_type   VARCHAR(50)   DEFAULT NULL,
    model          VARCHAR(100)  DEFAULT NULL,
    install_date   DATE          DEFAULT NULL,
    location       VARCHAR(100)  DEFAULT NULL,
    location_x     DECIMAL(10,4) DEFAULT NULL,
    location_y     DECIMAL(10,4) DEFAULT NULL,
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_code (equipment_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────
-- 설비 상태코드 (UPSERT — 설비당 1행)
-- ─────────────────────────────────────────

CREATE TABLE equipment_status (
    equip_id    VARCHAR(50) NOT NULL,
    status_code VARCHAR(30) NOT NULL,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (equip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────
-- 사용자
-- ─────────────────────────────────────────

CREATE TABLE users (
    user_id       VARCHAR(20)  NOT NULL,
    line_id       VARCHAR(20)  DEFAULT NULL,
    login_id      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_name     VARCHAR(50)  NOT NULL,
    email         VARCHAR(100) DEFAULT NULL,
    role_name     VARCHAR(30)  NOT NULL,
    last_login_at DATETIME     DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_login_id (login_id),
    CONSTRAINT fk_users_line FOREIGN KEY (line_id) REFERENCES `line`(line_id)
        ON UPDATE CASCADE ON DELETE SET NULL,
    CONSTRAINT chk_users_role_name CHECK (role_name IN ('ADMIN', 'OPERATOR', 'MANAGER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────
-- KPI 집계
-- ─────────────────────────────────────────

CREATE TABLE equipment_operation_log (
    operation_log_id BIGINT        NOT NULL AUTO_INCREMENT,
    equipment_code   VARCHAR(50)   NOT NULL,
    operation_status VARCHAR(30)   NOT NULL,
    start_at         DATETIME      DEFAULT NULL,
    end_at           DATETIME      DEFAULT NULL,
    duration_min     DECIMAL(10,2) DEFAULT NULL,
    cycle_time       DECIMAL(10,2) DEFAULT NULL,
    ok_count         INT           NOT NULL DEFAULT 0,
    ng_count         INT           NOT NULL DEFAULT 0,
    operation_rate   DECIMAL(5,2)  DEFAULT NULL,
    recorded_at      DATETIME      NOT NULL,
    PRIMARY KEY (operation_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE factory_kpi_log (
    factory_kpi_id        BIGINT        NOT NULL AUTO_INCREMENT,
    factory_id            VARCHAR(20)   NOT NULL,
    factory_oee           DECIMAL(5,2)  DEFAULT NULL,
    factory_availability  DECIMAL(5,2)  DEFAULT NULL,
    factory_performance   DECIMAL(5,2)  DEFAULT NULL,
    factory_quality       DECIMAL(5,2)  DEFAULT NULL,
    factory_uph           DECIMAL(10,2) DEFAULT NULL,
    factory_upmh          DECIMAL(10,2) DEFAULT NULL,
    avg_line_balance_rate DECIMAL(5,2)  DEFAULT NULL,
    recorded_at           DATETIME      NOT NULL,
    PRIMARY KEY (factory_kpi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE line_kpi_log (
    line_kpi_id       BIGINT        NOT NULL AUTO_INCREMENT,
    line_id           VARCHAR(20)   NOT NULL,
    line_oee          DECIMAL(5,2)  DEFAULT NULL,
    line_availability DECIMAL(5,2)  DEFAULT NULL,
    line_performance  DECIMAL(5,2)  DEFAULT NULL,
    line_quality      DECIMAL(5,2)  DEFAULT NULL,
    line_uph          DECIMAL(10,2) DEFAULT NULL,
    line_upmh         DECIMAL(10,2) DEFAULT NULL,
    line_balance_rate DECIMAL(5,2)  DEFAULT NULL,
    takt_time         DECIMAL(10,2) DEFAULT NULL,
    avg_cycle_time    DECIMAL(10,2) DEFAULT NULL,
    recorded_at       DATETIME      NOT NULL,
    PRIMARY KEY (line_kpi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equipment_kpi_log (
    equipment_kpi_id       BIGINT        NOT NULL AUTO_INCREMENT,
    equipment_code         VARCHAR(50)   NOT NULL,
    equipment_oee          DECIMAL(5,2)  DEFAULT NULL,
    equipment_availability DECIMAL(5,2)  DEFAULT NULL,
    equipment_performance  DECIMAL(5,2)  DEFAULT NULL,
    equipment_quality      DECIMAL(5,2)  DEFAULT NULL,
    equipment_uph          DECIMAL(10,2) DEFAULT NULL,
    recorded_at            DATETIME      NOT NULL,
    PRIMARY KEY (equipment_kpi_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────
-- 진동 분석
-- ─────────────────────────────────────────

CREATE TABLE vibration_window (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_code VARCHAR(50)  NOT NULL,
    measured_at    DATETIME     NOT NULL,
    sampling_rate  INT          NOT NULL,
    rpm            INT          DEFAULT NULL,
    window_size    INT          NOT NULL,
    window_index   BIGINT       NOT NULL,
    values_json    LONGTEXT     DEFAULT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_vibration_window_equipment (equipment_code, measured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='진동 윈도우 메타데이터';

CREATE TABLE analysis_result (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    vibration_window_id       BIGINT       DEFAULT NULL,
    equipment_code            VARCHAR(50)  NOT NULL,
    analysis_type             VARCHAR(50)  DEFAULT NULL,
    rms                       DOUBLE       DEFAULT NULL,
    peak_frequency            DOUBLE       DEFAULT NULL,
    peak_to_peak              DOUBLE       DEFAULT NULL,
    crest_factor              DOUBLE       DEFAULT NULL,
    kurtosis                  DOUBLE       DEFAULT NULL,
    prediction                VARCHAR(50)  DEFAULT NULL,
    confidence                DOUBLE       DEFAULT NULL,
    model_version             VARCHAR(100) DEFAULT NULL,
    model_input_type          VARCHAR(50)  DEFAULT NULL,
    model_input_size          INT          DEFAULT NULL,
    model_expected_input_size INT          DEFAULT NULL,
    model_input_strategy      VARCHAR(150) DEFAULT NULL,
    model_status              VARCHAR(50)  DEFAULT NULL,
    anomaly_score             DOUBLE       DEFAULT NULL,
    alarm_level               VARCHAR(20)  DEFAULT NULL,
    result_json               JSON         DEFAULT NULL,
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_analysis_result_equipment (equipment_code, created_at),
    CONSTRAINT fk_analysis_result_window
        FOREIGN KEY (vibration_window_id) REFERENCES vibration_window(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 진동 분석 결과';

-- ─────────────────────────────────────────
-- 알람
-- ─────────────────────────────────────────

CREATE TABLE alarm (
    alarm_id          BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_code    VARCHAR(50)  NOT NULL,
    alarm_code        VARCHAR(50)  DEFAULT NULL,
    alarm_type_name   VARCHAR(100) DEFAULT NULL,
    alarm_category    VARCHAR(50)  DEFAULT NULL,
    severity          VARCHAR(20)  NOT NULL,
    alarm_message     VARCHAR(255) DEFAULT NULL,
    alarm_status      VARCHAR(30)  NOT NULL,
    occurred_at       DATETIME     NOT NULL,
    sensor_snapshot   JSON         DEFAULT NULL,
    resolved_by       VARCHAR(20)  DEFAULT NULL,
    resolved_at       DATETIME     DEFAULT NULL,
    comment           VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (alarm_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alarm_history (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    equipment_code     VARCHAR(50)  NOT NULL,
    analysis_result_id BIGINT       NOT NULL,
    alarm_level        VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    message            VARCHAR(255) NOT NULL,
    occurred_at        DATETIME     NOT NULL,
    ended_at           DATETIME     DEFAULT NULL,
    duration_seconds   BIGINT       DEFAULT NULL,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_alarm_history_equipment (equipment_code, occurred_at),
    CONSTRAINT fk_alarm_history_analysis
        FOREIGN KEY (analysis_result_id) REFERENCES analysis_result(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 분석 기반 알람 이력';

-- ─────────────────────────────────────────
-- 채팅 / 게시판
-- ─────────────────────────────────────────

CREATE TABLE chat_room (
    chat_room_id BIGINT       NOT NULL AUTO_INCREMENT,
    line_id      VARCHAR(20)  NOT NULL,
    room_name    VARCHAR(100) NOT NULL,
    created_at   DATETIME     NOT NULL,
    PRIMARY KEY (chat_room_id),
    CONSTRAINT fk_chat_room_line FOREIGN KEY (line_id) REFERENCES `line`(line_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    message_id      BIGINT      NOT NULL AUTO_INCREMENT,
    chat_room_id    BIGINT      NOT NULL,
    sender_user_id  VARCHAR(20) NOT NULL,
    message_content TEXT        NOT NULL,
    is_deleted      TINYINT(1)  NOT NULL DEFAULT 0,
    sent_at         DATETIME    NOT NULL,
    PRIMARY KEY (message_id),
    CONSTRAINT fk_chat_message_room   FOREIGN KEY (chat_room_id)   REFERENCES chat_room(chat_room_id),
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE board_post (
    post_id        BIGINT       NOT NULL AUTO_INCREMENT,
    author_user_id VARCHAR(20)  NOT NULL,
    post_title     VARCHAR(200) NOT NULL,
    post_content   TEXT         NOT NULL,
    post_type      VARCHAR(30)  DEFAULT NULL,
    is_notice      TINYINT(1)   NOT NULL DEFAULT 0,
    is_deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_board_post_author FOREIGN KEY (author_user_id) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
