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

관리자 가입은 최초 관리자 계정을 바로 생성하고, 이후 관리자 가입은 기존 관리자의 승인 대기 상태로 처리합니다. 관리자 설정 키를 사용자에게 전달하거나 노출하지 않습니다.

## 3. DB

기본 `HUB.bat start`는 H2를 사용하므로 DB 설치가 필요 없습니다. PostgreSQL 배포 때만 `DB_URL`, `DB_USER`, `DB_PASSWORD`를 사용합니다.

## 4. Google Drive

권장 값은 Client ID + Client Secret + Refresh Token입니다. Access Token은 짧게 만료되므로 일반적으로 비워두고 Hub가 refresh token으로 새 access token을 발급받습니다.

## 5. CI/CD

`.env`는 저장소에 커밋하지 않습니다. GitHub Actions에서는 `.env.example`과 같은 변수 이름을 Secrets/Variables에 등록해 주입합니다. Hub용 GitHub PAT는 Actions 기본 `GITHUB_TOKEN`과 구분하기 위해 배포 Secret 이름을 `HUB_GITHUB_TOKEN`으로 두고 runtime `GITHUB_TOKEN`에 매핑하는 방식을 권장합니다.

## 6. 고급 튜닝

검색 크기, timeout, E5 batch size 같은 자주 바꾸지 않는 값은 `application.yml` 또는 `ai-service/app/config.py`의 기본값으로 둡니다. 일반 운영자의 `.env`에 수십 개 변수를 노출하지 않습니다.
