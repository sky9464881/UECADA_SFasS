-- ─────────────────────────────────────────────────────────────────────────────
-- 성능 보강용 추가 인덱스 (idempotent)
--
-- 기존 init.sql 이후 추가된 운영 보강 컬럼/인덱스를 idempotent 하게 적용한다.
-- MySQL 8.x 는 CREATE INDEX IF NOT EXISTS 를 지원하지 않으므로
-- information_schema 로 존재 여부를 체크한 뒤 동적 SQL 로 생성한다.
--
-- 적용 시점: docker-entrypoint-initdb.d 가 새 DB 초기화 시점에 02_seed 다음으로 실행.
-- 이미 운영 중인 DB 에는 이 파일을 수동으로 한 번 실행하면 됨.
-- ─────────────────────────────────────────────────────────────────────────────

DELIMITER //

DROP PROCEDURE IF EXISTS create_index_if_missing //
CREATE PROCEDURE create_index_if_missing(
    IN tbl VARCHAR(64),
    IN idx VARCHAR(64),
    IN cols VARCHAR(255)
)
BEGIN
    DECLARE cnt INT DEFAULT 0;
    SELECT COUNT(*) INTO cnt
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = tbl
       AND index_name = idx;
    IF cnt = 0 THEN
        SET @sql = CONCAT('CREATE INDEX ', idx, ' ON ', tbl, ' (', cols, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

DROP PROCEDURE IF EXISTS add_column_if_missing //
CREATE PROCEDURE add_column_if_missing(
    IN tbl VARCHAR(64),
    IN col VARCHAR(64),
    IN ddl VARCHAR(255)
)
BEGIN
    DECLARE cnt INT DEFAULT 0;
    SELECT COUNT(*) INTO cnt
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = tbl
       AND column_name = col;
    IF cnt = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', tbl, ' ADD COLUMN ', ddl);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

DELIMITER ;

-- users 테이블: 로그인 실패 누적과 계정 잠금 표시/관리.
CALL add_column_if_missing('users', 'failed_login_count', 'failed_login_count INT NOT NULL DEFAULT 0');
CALL add_column_if_missing('users', 'locked', 'locked TINYINT(1) NOT NULL DEFAULT 0');

-- alarm 테이블: 프론트 알람 조회/필터는 (status, occurred_at), (equipment_code, occurred_at) 패턴이 가장 잦음.
CALL create_index_if_missing('alarm', 'idx_alarm_status_occurred',     'alarm_status, occurred_at');
CALL create_index_if_missing('alarm', 'idx_alarm_equipment_occurred',  'equipment_code, occurred_at');
CALL create_index_if_missing('alarm', 'idx_alarm_occurred',            'occurred_at');

-- equipment 테이블: 라인별 설비 목록 조회.
CALL create_index_if_missing('equipment', 'idx_equipment_location',    'location');
CALL create_index_if_missing('equipment', 'idx_equipment_process_type','process_type');

-- analysis_result: equipment + created_at 은 이미 존재 (idx_analysis_result_equipment). 추가 단일 인덱스만 보강.
CALL create_index_if_missing('analysis_result', 'idx_analysis_created_at', 'created_at');

-- board_post: 최신순 정렬이 잦음.
CALL create_index_if_missing('board_post', 'idx_board_post_created_at', 'created_at');

DROP PROCEDURE IF EXISTS create_index_if_missing;
DROP PROCEDURE IF EXISTS add_column_if_missing;
