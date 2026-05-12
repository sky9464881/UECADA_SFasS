-- init.sql
-- UECADA Smart Factory DB Schema
-- MySQL 8.0 기준

CREATE DATABASE IF NOT EXISTS uecada
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE uecada;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS board_post;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_room;
DROP TABLE IF EXISTS alarm;
DROP TABLE IF EXISTS equipment_kpi_log;
DROP TABLE IF EXISTS line_kpi_log;
DROP TABLE IF EXISTS factory_kpi_log;
DROP TABLE IF EXISTS equipment_operation_log;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS `line`;
DROP TABLE IF EXISTS factory;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE factory (
    factory_id VARCHAR(20) NOT NULL COMMENT '공장 ID',
    factory_name VARCHAR(100) NOT NULL COMMENT '공장명',
    PRIMARY KEY (factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공장 정보';

CREATE TABLE `line` (
    line_id VARCHAR(20) NOT NULL COMMENT '라인 ID',
    factory_id VARCHAR(20) NOT NULL COMMENT '소속 공장 ID',
    line_name VARCHAR(100) NOT NULL COMMENT '라인명',
    line_status VARCHAR(30) DEFAULT NULL COMMENT '라인 상태',
    PRIMARY KEY (line_id),
    CONSTRAINT fk_line_factory
        FOREIGN KEY (factory_id)
        REFERENCES factory(factory_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라인 정보';

CREATE TABLE equipment (
    equipment_id VARCHAR(20) NOT NULL COMMENT '설비 ID',
    line_id VARCHAR(20) NOT NULL COMMENT '소속 라인 ID',
    equipment_name VARCHAR(100) NOT NULL COMMENT '설비명',
    equipment_type VARCHAR(30) NOT NULL COMMENT '설비 유형',
    PRIMARY KEY (equipment_id),
    CONSTRAINT fk_equipment_line
        FOREIGN KEY (line_id)
        REFERENCES `line`(line_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT chk_equipment_type
        CHECK (equipment_type IN ('CASTING', 'MACHINING', 'WASHING', 'ASSEMBLY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='설비 기본 정보';

-- MySQL 시스템 테이블 user와 혼동을 피하기 위해 물리 테이블명은 users 사용
CREATE TABLE users (
    user_id VARCHAR(20) NOT NULL COMMENT '사용자 ID',
    line_id VARCHAR(20) DEFAULT NULL COMMENT '소속 라인 ID',
    login_id VARCHAR(50) NOT NULL COMMENT '로그인 ID',
    password_hash VARCHAR(255) NOT NULL COMMENT '암호화 비밀번호',
    user_name VARCHAR(50) NOT NULL COMMENT '사용자명',
    role_name VARCHAR(30) NOT NULL COMMENT '권한명',
    last_login_at DATETIME DEFAULT NULL COMMENT '최종 로그인 시간',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_login_id (login_id),
    CONSTRAINT fk_users_line
        FOREIGN KEY (line_id)
        REFERENCES `line`(line_id)
        ON UPDATE CASCADE
        ON DELETE SET NULL,
    CONSTRAINT chk_users_role_name
        CHECK (role_name IN ('ADMIN', 'OPERATOR', 'MANAGER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='시스템 사용자';

CREATE TABLE equipment_operation_log (
    operation_log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '설비 운영 로그 ID',
    equipment_id VARCHAR(20) NOT NULL COMMENT '설비 ID',
    operation_status VARCHAR(30) NOT NULL COMMENT '운전 상태',
    cycle_time DECIMAL(10,2) DEFAULT NULL COMMENT '사이클 타임',
    ok_count INT NOT NULL DEFAULT 0 COMMENT '양품 수량',
    ng_count INT NOT NULL DEFAULT 0 COMMENT '불량 수량',
    operation_rate DECIMAL(5,2) DEFAULT NULL COMMENT '가동률',
    recorded_at DATETIME NOT NULL COMMENT '기록 시간',
    PRIMARY KEY (operation_log_id),
    CONSTRAINT fk_operation_log_equipment
        FOREIGN KEY (equipment_id)
        REFERENCES equipment(equipment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_operation_status
        CHECK (operation_status IN ('RUNNING', 'STOPPED', 'IDLE')),
    CONSTRAINT chk_operation_counts
        CHECK (ok_count >= 0 AND ng_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='설비 운전 상태 및 생산 결과 로그';

CREATE TABLE factory_kpi_log (
    factory_kpi_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '공장 KPI 로그 ID',
    factory_id VARCHAR(20) NOT NULL COMMENT '공장 ID',
    factory_oee DECIMAL(5,2) DEFAULT NULL COMMENT '전체 공장 OEE',
    factory_availability DECIMAL(5,2) DEFAULT NULL COMMENT '전체 공장 시간가동률',
    factory_performance DECIMAL(5,2) DEFAULT NULL COMMENT '전체 공장 성능률',
    factory_quality DECIMAL(5,2) DEFAULT NULL COMMENT '전체 공장 품질률',
    factory_uph DECIMAL(10,2) DEFAULT NULL COMMENT '전체 공장 시간당 생산량',
    factory_upmh DECIMAL(10,2) DEFAULT NULL COMMENT '전체 공장 인당 시간당 생산량',
    avg_line_balance_rate DECIMAL(5,2) DEFAULT NULL COMMENT '전체 라인 평균 밸런싱률',
    recorded_at DATETIME NOT NULL COMMENT '집계 시간',
    PRIMARY KEY (factory_kpi_id),
    CONSTRAINT fk_factory_kpi_factory
        FOREIGN KEY (factory_id)
        REFERENCES factory(factory_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공장 전체 KPI 집계 데이터';

CREATE TABLE line_kpi_log (
    line_kpi_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '라인 KPI 로그 ID',
    line_id VARCHAR(20) NOT NULL COMMENT '라인 ID',
    line_oee DECIMAL(5,2) DEFAULT NULL COMMENT '라인 OEE',
    line_availability DECIMAL(5,2) DEFAULT NULL COMMENT '라인 시간가동률',
    line_performance DECIMAL(5,2) DEFAULT NULL COMMENT '라인 성능률',
    line_quality DECIMAL(5,2) DEFAULT NULL COMMENT '라인 품질률',
    line_uph DECIMAL(10,2) DEFAULT NULL COMMENT '라인 시간당 생산량',
    line_upmh DECIMAL(10,2) DEFAULT NULL COMMENT '라인 인당 시간당 생산량',
    line_balance_rate DECIMAL(5,2) DEFAULT NULL COMMENT '라인 밸런싱률',
    takt_time DECIMAL(10,2) DEFAULT NULL COMMENT '택트 타임',
    avg_cycle_time DECIMAL(10,2) DEFAULT NULL COMMENT '평균 사이클 타임',
    recorded_at DATETIME NOT NULL COMMENT '집계 시간',
    PRIMARY KEY (line_kpi_id),
    CONSTRAINT fk_line_kpi_line
        FOREIGN KEY (line_id)
        REFERENCES `line`(line_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라인 단위 KPI 집계 데이터';

CREATE TABLE equipment_kpi_log (
    equipment_kpi_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '설비 KPI 로그 ID',
    equipment_id VARCHAR(20) NOT NULL COMMENT '설비 ID',
    equipment_oee DECIMAL(5,2) DEFAULT NULL COMMENT '설비 OEE',
    equipment_availability DECIMAL(5,2) DEFAULT NULL COMMENT '설비 시간가동률',
    equipment_performance DECIMAL(5,2) DEFAULT NULL COMMENT '설비 성능률',
    equipment_quality DECIMAL(5,2) DEFAULT NULL COMMENT '설비 품질률',
    equipment_uph DECIMAL(10,2) DEFAULT NULL COMMENT '설비 시간당 생산량',
    recorded_at DATETIME NOT NULL COMMENT '집계 시간',
    PRIMARY KEY (equipment_kpi_id),
    CONSTRAINT fk_equipment_kpi_equipment
        FOREIGN KEY (equipment_id)
        REFERENCES equipment(equipment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='설비 단위 KPI 집계 데이터';

CREATE TABLE alarm (
    alarm_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '알람 ID',
    equipment_id VARCHAR(20) NOT NULL COMMENT '알람 발생 설비 ID',
    alarm_code VARCHAR(50) DEFAULT NULL COMMENT '알람 코드',
    alarm_type_name VARCHAR(100) DEFAULT NULL COMMENT '알람 유형명',
    alarm_category VARCHAR(50) DEFAULT NULL COMMENT '알람 카테고리',
    severity VARCHAR(20) NOT NULL COMMENT '심각도',
    alarm_message VARCHAR(255) DEFAULT NULL COMMENT '알람 메시지',
    alarm_status VARCHAR(30) NOT NULL COMMENT '처리 상태',
    occurred_at DATETIME NOT NULL COMMENT '발생 시간',
    PRIMARY KEY (alarm_id),
    CONSTRAINT fk_alarm_equipment
        FOREIGN KEY (equipment_id)
        REFERENCES equipment(equipment_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT chk_alarm_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL', 'EMERGENCY')),
    CONSTRAINT chk_alarm_status
        CHECK (alarm_status IN ('OCCURRED', 'CHECKING', 'RESOLVED', 'IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='설비 알람 이력';

CREATE TABLE chat_room (
    chat_room_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '채팅방 ID',
    line_id VARCHAR(20) NOT NULL COMMENT '라인 ID',
    room_name VARCHAR(100) NOT NULL COMMENT '채팅방명',
    created_at DATETIME NOT NULL COMMENT '생성 시간',
    PRIMARY KEY (chat_room_id),
    CONSTRAINT fk_chat_room_line
        FOREIGN KEY (line_id)
        REFERENCES `line`(line_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='라인별 채팅방';

CREATE TABLE chat_message (
    message_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '메시지 ID',
    chat_room_id BIGINT NOT NULL COMMENT '채팅방 ID',
    sender_user_id VARCHAR(20) NOT NULL COMMENT '발신자 사용자 ID',
    message_content TEXT NOT NULL COMMENT '메시지 내용',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '삭제 여부',
    sent_at DATETIME NOT NULL COMMENT '전송 시간',
    PRIMARY KEY (message_id),
    CONSTRAINT fk_chat_message_room
        FOREIGN KEY (chat_room_id)
        REFERENCES chat_room(chat_room_id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_sender
        FOREIGN KEY (sender_user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='채팅 메시지';

CREATE TABLE board_post (
    post_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '게시글 ID',
    author_user_id VARCHAR(20) NOT NULL COMMENT '작성자 사용자 ID',
    post_title VARCHAR(200) NOT NULL COMMENT '게시글 제목',
    post_content TEXT NOT NULL COMMENT '게시글 내용',
    post_type VARCHAR(30) DEFAULT NULL COMMENT '게시글 유형',
    is_notice TINYINT(1) NOT NULL DEFAULT 0 COMMENT '공지 여부',
    created_at DATETIME NOT NULL COMMENT '작성 시간',
    PRIMARY KEY (post_id),
    CONSTRAINT fk_board_post_author
        FOREIGN KEY (author_user_id)
        REFERENCES users(user_id)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='관리자 게시글 및 공지';

CREATE INDEX idx_line_factory_id ON `line` (factory_id);
CREATE INDEX idx_equipment_line_id ON equipment (line_id);
CREATE INDEX idx_users_line_id ON users (line_id);
CREATE INDEX idx_operation_log_equipment_recorded ON equipment_operation_log (equipment_id, recorded_at);
CREATE INDEX idx_factory_kpi_factory_recorded ON factory_kpi_log (factory_id, recorded_at);
CREATE INDEX idx_line_kpi_line_recorded ON line_kpi_log (line_id, recorded_at);
CREATE INDEX idx_equipment_kpi_equipment_recorded ON equipment_kpi_log (equipment_id, recorded_at);
CREATE INDEX idx_alarm_equipment_occurred ON alarm (equipment_id, occurred_at);
CREATE INDEX idx_alarm_status_severity ON alarm (alarm_status, severity);
CREATE INDEX idx_chat_room_line_id ON chat_room (line_id);
CREATE INDEX idx_chat_message_room_sent ON chat_message (chat_room_id, sent_at);
CREATE INDEX idx_board_post_created_at ON board_post (created_at);
CREATE INDEX idx_board_post_author ON board_post (author_user_id);
