# 배포

Hub의 실제 운영 배포는 **`chl4890620123-collab/Server` 저장소**가 중앙 관리합니다.

Hub 저장소의 CI와 운영 배포는 분리되어 있습니다.

## 1. 전체 흐름

```text
Hub main
  → hub-ci
     - config/structure 검사
     - React build
     - AI tests
     - Spring tests + bootJar

Server 저장소
  → .ops-trigger/hub.txt 갱신 또는 workflow_dispatch
  → Central production deployment
  → 운영 Windows 서버에 SSH
  → Hub main을 새로 fetch/checkout
  → AI / Backend Docker 이미지 빌드
  → deploy-hub.ps1
     - PostgreSQL 백업
     - hub-db / hub-ai / hub-backend / hub-caddy 기동
     - 로컬 /actuator/health + / 검사
  → 공개 URL / health 검증
```

**Hub 저장소에 push했다고 운영 배포가 자동 완료되는 것은 아닙니다.**

실제 배포는 Server 저장소의 중앙 워크플로를 통해 별도로 실행합니다.

## 2. Server 저장소의 Hub 배포 파일

현재 중앙 배포의 기준 파일:

- `deploy/compose/hub.yml`
- `deploy/caddy/hub.Caddyfile`
- `deploy/scripts/deploy-service.ps1`
- `deploy/scripts/deploy-hub.ps1`
- `.github/workflows/_deploy-service.yml`
- `.ops-trigger/hub.txt`

운영 런타임 데이터는 소스 체크아웃과 분리해 `D:\server-data\hub` 아래에 보존합니다.

대표 경로:

- `D:\server-data\hub\runtime\.env`
- `D:\server-data\hub\postgres`
- `D:\server-data\hub\storage`
- `D:\server-data\hub\backups`

## 3. 현재 컨테이너 구성

```text
hub-db       PostgreSQL + pgvector
hub-ai       FastAPI AI/STT/embedding/OCR
hub-backend  Spring Boot + React static bundle
hub-caddy    Hub 전용 gateway
```

현재 Server compose는 Hub 전용 Caddy를 호스트 포트 `9070`에 바인딩하고 backend로 프록시합니다.

이전 문서에 있던 **공유 MOVEAI Caddy 자동 등록 / `HUB_OUTER_CADDY_AUTO_CONFIGURE` / `HUB_ALLOW_DOMAIN_TAKEOVER` 흐름은 현재 Hub 배포 스크립트가 사용하지 않습니다.**

외부 HTTPS·라우터·포트 포워딩 같은 네트워크 경로는 Hub 저장소가 아니라 Server/운영 네트워크 구성이 소유합니다. 중앙 배포 워크플로는 배포 스크립트가 출력한 실제 public URL에서 루트와 `/actuator/health`가 모두 응답하는지 마지막에 검증합니다.

## 4. 현재 public base 설정

현재 `deploy-hub.ps1`은 운영 런타임에 다음 보안 기준을 강제합니다.

- `HUB_PUBLIC_BASE_URL=https://yellow.it.kr`
- `HUB_COOKIE_SECURE=true`
- `HUB_ENFORCE_SECURE_CONFIG=true`

따라서 외부 네트워크 경로도 실제로 해당 HTTPS 주소가 Hub까지 도달하도록 구성되어 있어야 합니다.

이 값이나 공개 경로를 바꿀 때는 Hub 문서만 수정하지 말고 **Server 저장소의 배포 스크립트·런타임 예시·공개 검증 로직을 함께 맞춰야 합니다.**

## 5. 운영 Secret

배포 SSH 정보는 Server 저장소에서 관리합니다.

- `SERVER_HOST`
- `SERVER_USER`
- `SERVER_PORT`
- `SERVER_SSH_KEY`
- `SERVER_PASSWORD`

앱 런타임 Secret은 운영 서버의 `D:\server-data\hub\runtime\.env`에 둡니다.

대표 값:

- `DB_PASSWORD`
- `HUB_JWT_SECRET`
- `HUB_ADMIN_SETUP_KEY`
- `HUB_STT_PII_HASH_KEY`
- `GEMINI_API_KEY`
- Connector credential

실제 값을 Git에 커밋하지 않습니다.

## 6. 배포 전 DB 백업

기존 `hub-db`가 실행 중이면 배포 전에 PostgreSQL dump를 생성해 backup 디렉터리에 보관합니다.

배포 실패가 애플리케이션 시작 단계에서 발생하더라도 기존 PostgreSQL 데이터 디렉터리는 소스와 분리되어 유지됩니다.

## 7. Flyway 주의사항

운영 backend는 `common + postgresql` migration을 함께 읽습니다.

동일한 Flyway 버전이 두 디렉터리에 동시에 있으면 Spring Boot가 시작되지 않습니다.

예:

```text
common/V25__something.sql
postgresql/V25__other.sql
```

이런 충돌은 `scripts/verify.py`가 CI에서 검사합니다. 이미 운영 DB에 적용된 migration의 번호를 임의 변경하지 말고, 새 migration은 사용하지 않은 다음 버전을 사용해야 합니다.

## 8. 배포 상태 확인

GitHub CLI 예시:

```bash
gh run list --repo chl4890620123-collab/Server --limit 5
gh run view <run-id> --repo chl4890620123-collab/Server
```

성공 판정은 다음이 모두 끝나야 합니다.

1. Hub CI 성공
2. Server 중앙 배포 성공
3. 서버 로컬 health/root 성공
4. 공개 URL root/health 성공

단순히 `main`에 merge됐거나 Docker 이미지가 build됐다는 이유만으로 배포 완료로 보지 않습니다.

## 9. 장애 진단

Server 저장소에는 read-only 로그 진단 워크플로가 있습니다.

주요 확인 대상:

- `hub-backend` 상태/로그
- `hub-db` 상태/로그
- 디스크/메모리
- Docker 상태

예를 들어 backend가 restart loop에 들어가면 먼저 Flyway, 환경 변수, DB 연결 오류를 확인합니다.

## 10. 이 저장소만 단독 배포

Server 중앙 시스템 없이 별도 서버에서 Hub 저장소만 실행할 때는 저장소 자체의 `deploy/compose.yml`과 `deploy/Caddyfile`을 사용합니다.

```bash
docker compose --env-file .env -f deploy/compose.yml up -d --build

# 별도 공인 도메인 HTTPS profile
docker compose --env-file .env -f deploy/compose.yml --profile https up -d --build
```

이 경로는 중앙 운영 서버 배포와 별개인 **독립 셀프호스팅 옵션**입니다.
