# UECADA

스마트 팩토리 설비 통합 관제 시스템 관련 저장소입니다.

## 프로젝트 구성

| 경로 | 설명 |
|------|------|
| `db/init.sql` | MySQL 8.0 초기 스키마 (Docker 초기화용) |
| `docker-compose.yml` | MySQL 컨테이너 정의 |
| `dist/` | Vue/Vite 빌드 산출물 (정적 배포) |
| `package.json` | 프론트엔드 의존성 및 스크립트 |

로컬 개발 시 의존성 설치: `npm install`

---

## 변경 이력

### 2026-05-12

- 저장소 루트 구조 정리 및 재현 가능한 의존성 관리
  - `.gitignore` 추가 (`node_modules/`, Vite 캐시, 로그, `.env` 등)
  - `package.json` 추가 (Vue 3.5.x, Vite 5.4.x, `@vitejs/plugin-vue`, `lucide-vue-next`)
  - `package-lock.json` 생성으로 동일 버전 재설치 가능
- 빈 중첩 폴더 `UECADA/` 제거 권장 (파일 잠금 시 수동 삭제)
- 아직 저장소에 Vue 소스(`src/`, `vite.config` 등)는 포함되지 않으며, 현재 프론트는 `dist/` 빌드 결과물 기준입니다.
