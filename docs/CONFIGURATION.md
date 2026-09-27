# Configuration

## 1. 원칙

Hub 저장소에서 직접 관리하는 환경 템플릿은 루트 `.env.example`입니다. 실제 `.env`와 Secret은 Git에 커밋하지 않습니다.

Spring 설정은 `backend/src/main/resources/application.yml`, AI 기본값은 `ai-service/app/config.py`가 기준입니다.

운영 배포 설정은 Hub 저장소가 아니라 **`chl4890620123-collab/Server` 저장소와 운영 서버의 런타임 `.env`**가 최종 기준입니다.

## 2. 로컬 `.env`

대표 설정:

```env
HUB_PORT=8080
HUB_BIND_ADDRESS=localhost
HUB_STORAGE_ROOT=./data/storage
HUB_AI_BASE_URL=http://localhost:8000

HUB_JWT_SECRET=<32자 이상>

HUB_AI_MODE=gemini
GEMINI_API_KEY=<secret>
GEMINI_MODEL=gemini-2.5-flash
GEMINI_FALLBACK_MODEL=gemini-3.6-flash
GEMINI_TRANSCRIBE_MODEL=gemini-2.5-flash
HUB_EMBED_MODE=e5
```

실제 기본 모델 값은 `ai-service/app/config.py`와 `.env.example`을 기준으로 확인합니다. 서버 런타임 `.env`에 같은 변수가 있으면 그 값이 우선합니다.

OCR은 실제 모드에서 local PaddleOCR, STT는 Gemini를 사용합니다. 빠른 smoke run에서는 `HUB_AI_MODE=mock`, `HUB_EMBED_MODE=hash` 조합을 사용할 수 있습니다.

텍스트 분석·RAG에서 기본 Gemini 모델이 재시도 후 429를 반환하면 fallback 모델을 사용할 수 있습니다. STT 모델은 별도 설정입니다.

## 3. 최초 관리자

관리자 가입은 자가 승인되지 않습니다.

관리자가 0명인 최초 설치에서는 운영자가 `HUB_BOOTSTRAP_ADMIN_PASSWORD`를 서버의 비공개 런타임 환경에 설정하면 `BootstrapService`가 최초 관리자 계정을 만들 수 있습니다. 최초 로그인 뒤 비밀번호 변경이 강제됩니다.

이미 관리자가 존재하면 bootstrap 값은 관리자 추가 수단으로 사용되지 않습니다.

## 4. DB

일반 `HUB.bat start`는 H2 file DB를 사용합니다.

PostgreSQL 배포에서는 다음 값이 필요합니다.

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`

운영은 PostgreSQL + pgvector를 사용합니다.

## 5. Connector

필요한 서비스만 설정합니다.

- GitHub: token 또는 OAuth client
- Google Drive: Client ID / Client Secret / Refresh Token 권장
- Slack: token 또는 OAuth client
- Notion: token 또는 OAuth client

Access Token은 짧게 만료될 수 있으므로 Google Drive는 refresh token 기반 구성을 권장합니다.

`HUB_ALLOW_SHARED_CONNECTOR_FALLBACK=false`가 기본입니다. 실제 다중 사용자 환경에서는 개인 연결 정보를 우선하고 공유 credential fallback은 켜지 않는 것이 원칙입니다.

## 6. React API origin

현재 프로덕션 구조는 React SPA를 Spring Boot가 직접 정적 파일로 서비스하므로 API도 같은 origin을 사용합니다.

따라서 현재 배포에서는 `VITE_API_BASE_URL`을 비워 두는 것이 기본입니다.

로컬 Vite 개발 서버에서는 `/api`를 `http://localhost:8080`으로 프록시해 브라우저 기준 same-origin처럼 동작시킵니다.

별도 frontend/backend origin으로 분리하려면 CORS·cookie·CSRF 구성을 별도로 설계해야 하며 현재 운영 기본 구조가 아닙니다.

## 7. CI/CD

Hub 저장소의 `.github/workflows/ci.yml`은 **빌드/테스트만 수행하고 운영 배포는 하지 않습니다.**

검사 항목:

- 저장소/환경 구조
- Flyway migration 버전 충돌
- React/TypeScript build
- AI compile/test
- Spring test/bootJar

운영 배포 SSH Secret은 Hub 저장소가 아니라 `Server` 저장소에 있습니다.

현재 중앙 배포가 사용하는 Secret 이름:

- `SERVER_HOST`
- `SERVER_USER`
- `SERVER_PORT`
- `SERVER_SSH_KEY`
- `SERVER_PASSWORD`

키 방식 또는 비밀번호 방식 중 운영 환경에 설정된 인증 수단을 사용합니다.

`GEMINI_API_KEY`, DB 비밀번호, Connector credential 같은 앱 런타임 값은 Hub Actions에 넣는 구조가 아니라 서버의 비공개 런타임 `.env`에서 관리합니다.

자세한 흐름은 `docs/DEPLOYMENT.md`를 참고하세요.

## 8. 고급 튜닝

검색 크기, timeout, E5 batch size, Gemini retry 같은 자주 바꾸지 않는 값은 `application.yml` 또는 `ai-service/app/config.py` 기본값에 둡니다. 일반 운영자의 `.env`에는 실제로 조정할 값만 노출합니다.
