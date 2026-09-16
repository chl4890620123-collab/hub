# Configuration

## 1. 원칙

운영자가 직접 관리하는 파일은 루트 `.env` 하나입니다. Git에는 변수 이름만 있는 `.env.example`만 둡니다. Spring 설정은 `application.yml` 하나, 선택적 컨테이너 배포는 `deploy/compose.yml` 하나를 사용합니다.

## 2. 로컬 `.env`

```env
HUB_PORT=8080
HUB_BIND_ADDRESS=localhost
HUB_STORAGE_ROOT=./data/storage
HUB_AI_BASE_URL=http://localhost:8000

HUB_JWT_SECRET=<32자 이상>

HUB_AI_MODE=gemini
GEMINI_API_KEY=<secret>
GEMINI_MODEL=gemini-3.8-flash
GEMINI_TRANSCRIBE_MODEL=gemini-3.5-transcribe
HUB_EMBED_MODE=e5

GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_REFRESH_TOKEN=
GOOGLE_ACCESS_TOKEN=
SLACK_TOKEN=
NOTION_TOKEN=
GITHUB_TOKEN=
```

OCR/STT provider 선택용 ENV는 제거했습니다. 실제 모드에서 OCR은 local PaddleOCR, STT는 Gemini로 고정되어 운영자가 provider 변수를 중복 관리하지 않습니다. mock 모드는 `HUB_AI_MODE=mock` 하나로 전환합니다.

관리자 가입은 최초 계정을 포함해 항상 기존 관리자의 승인 대기 상태로 처리합니다(자가 승인 불가). 관리자가 0명인 첫 배포에서는 운영자가 `HUB_BOOTSTRAP_ADMIN_PASSWORD`를 서버의 `.env`에 직접 정해서 넣어야 `BootstrapService`가 그 값으로 관리자 계정 하나를 만듭니다(코드/마이그레이션에 비밀번호를 적어두지 않음). 최초 로그인 시 비밀번호 변경이 강제되며, 관리자가 이미 있으면 이 값은 무시됩니다.

## 3. DB

기본 `HUB.bat start`는 H2를 사용하므로 DB 설치가 필요 없습니다. PostgreSQL 배포 때만 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 사용합니다.

## 4. Google Drive

권장 값은 Client ID + Client Secret + Refresh Token입니다. Access Token은 짧게 만료되므로 일반적으로 비워두고 Hub가 refresh token으로 새 access token을 발급받습니다.

## 5. CI/CD

`.env`는 저장소에 커밋하지 않습니다. CI(`web-and-config`/`ai`/`backend` job)는 앱 시크릿 없이 빌드/테스트만 하므로 GitHub Actions Secrets에 커넥터 토큰류를 등록할 필요가 없습니다.

배포 서버에 SSH로 접속해 `git pull` + `docker compose up`을 실행하는 `deploy` job만 예외로, 이때 Actions Secrets에는 배포 접속 정보 4개(`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `DEPLOY_PATH`)만 등록합니다. `GEMINI_API_KEY`/`GITHUB_TOKEN`/`DB_PASSWORD` 같은 앱 런타임 값은 Actions를 거치지 않고 배포 서버의 로컬 `.env` 파일에만 있으면 됩니다 - 상세 절차는 `docs/DEPLOYMENT.md` 참고.

(참고: GitHub Actions는 `GITHUB_`로 시작하는 이름을 저장소 Secret으로 등록하지 못하게 막아둡니다. 위 방식에서는 해당하지 않지만, 혹시 커넥터 토큰을 Actions Secret으로 직접 등록해야 하는 다른 상황이 생기면 `HUB_GITHUB_TOKEN`처럼 접두사를 바꾼 이름으로 등록하고 워크플로에서 런타임 이름(`GITHUB_TOKEN`)으로 매핑해야 합니다.)

## 6. 고급 튜닝

검색 크기, timeout, E5 batch size 같은 자주 바꾸지 않는 값은 `application.yml` 또는 `ai-service/app/config.py`의 기본값으로 둡니다. 일반 운영자의 `.env`에 수십 개 변수를 노출하지 않습니다.
