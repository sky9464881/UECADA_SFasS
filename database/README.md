# Database

MySQL 초기 스키마와 seed 데이터를 관리합니다.

Docker Compose로 MySQL을 띄우면 `database/init` 아래 SQL이 초기 실행됩니다.

핵심 테이블:

- `equipment`
- `vibration_window`
- `analysis_result`
- `alarm_history`

로컬 MySQL에 직접 적용하려면:

```bash
mysql -u root -p < database/init/01_schema.sql
mysql -u root -p < database/init/02_seed.sql
```

`01_schema.sql`은 로컬 Spring Boot 접속용 계정도 함께 생성합니다.

```text
username: smart_factory
password: smart_factory
database: smart_factory
```

Spring Boot에서 DB 연결을 확인하는 endpoint:

```bash
curl http://localhost:8080/api/database/status
```
