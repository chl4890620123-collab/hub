# Context AI Hub v2.34

사내 자료를 한곳에서 검색하고, 근거가 있는 AI 답변과 TODO/결정/변경 기록으로 연결하는 **웹 전용 로컬 우선 인트라넷 AI**입니다.

## 이번 버전에서 확정한 범위

- 클라이언트는 **웹 하나**입니다. PC와 좁은 화면 모두 같은 반응형 웹을 사용합니다.
- **카메라 촬영 기능은 없습니다.** 이미지/스캔 문서는 일반 파일 업로드로만 처리합니다.
- **LangChain은 사용하지 않습니다.** 검색/권한/업무 흐름은 Spring Boot가 직접 담당합니다.
- FastAPI는 LLM, STT, Embedding, 업로드 문서 OCR 같은 AI 처리만 담당합니다.
- 기본 개발 DB는 **H2 파일 DB**라서 PostgreSQL/Docker 없이 로컬에서 먼저 구현할 수 있습니다.
- 실제 운영 값은 루트 `.env` 하나만 사용하고 Git에는 `.env.example`만 둡니다.
- VS Code에서는 `Hub.code-workspace` 하나로 프로젝트를 엽니다.

## 핵심 사용자 흐름

```text
로그인 / 관리자 승인
  → 프로젝트 선택
  → 자료 입력
     - 파일 업로드
     - 직접 텍스트 입력
     - 웹 회의 녹음 / 음성 파일
     - Google Drive / Slack / Notion / GitHub
  → 파싱 / 필요 시 업로드 이미지 OCR
  → Chunking / Embedding
  → 통합 검색
  → Query Router + RRF + deterministic rerank
  → 검색 결과 기반 RAG
  → Evidence / 원문 열기
  → TODO / 결정 / 변경 / 일정 / 관리자 검토
```

## VS Code에서 여는 방법

필수 권장 환경:

- Java 21
- Python 3.9~3.13 x64
- VS Code
- Node.js 22+는 TypeScript 수정/검증 때 필요

### 1. 프로젝트 열기

ZIP을 새 폴더에 푼 뒤 **`Hub.code-workspace`를 VS Code로 엽니다.**

VS Code가 추천 확장 설치를 물으면 다음 확장만 설치하면 됩니다.

- Extension Pack for Java
- Spring Boot Extension Pack 계열
- Python
- Pylance

### 2. 환경 점검

VS Code에서 `Ctrl + Shift + P` → `Tasks: Run Task` →

```text
Hub: 0. 환경 점검
```

을 실행합니다.

또는 터미널에서:

```bat
HUB.bat doctor
```

### 3. 가장 먼저 mock 모드로 실행

아직 `.env`가 없어도 됩니다.

```text
Tasks: Run Task
→ Hub: 1. 로컬 시작
```

또는:

```bat
HUB.bat start
```

정상 시작 주소:

```text
http://127.0.0.1:8080/login
```

`.env`가 없으면 다음 안전한 개발 모드로 시작합니다.

```text
H2 file DB
mock AI
hash embedding
```

### 4. 실제 AI/Connector를 붙일 때만 `.env` 생성

```bat
copy .env.example .env
```

그리고 필요한 값만 채웁니다. 실제 Secret은 Git에 올리지 않습니다.

예:

```env
HUB_AI_MODE=gemini
HUB_EMBED_MODE=e5
GEMINI_API_KEY=...

GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GOOGLE_REFRESH_TOKEN=...
GOOGLE_ACCESS_TOKEN=
```

Google Access Token은 비워두어도 Refresh Token으로 갱신할 수 있습니다.

## VS Code 작업 목록

`Tasks: Run Task`에서 다음 작업만 사용하면 됩니다.

```text
Hub: 0. 환경 점검
Hub: 1. 로컬 시작
Hub: 상태 확인
Hub: 구조 검수
Hub: 전체 테스트
Hub: 배포 JAR 빌드
Hub: 종료
```

같은 작업은 터미널에서도 가능합니다.

```bat
HUB.bat doctor
HUB.bat start
HUB.bat status
HUB.bat check
HUB.bat test
HUB.bat build
HUB.bat stop
```

## 설정 파일 정리 기준

실제 구현자가 자주 만질 설정은 최대한 줄였습니다.

```text
.env.example
  └─ 환경변수 이름과 안전한 기본값만 저장

.env
  └─ 실제 API 키/비밀번호. Git 제외

backend/src/main/resources/application.yml
  └─ Spring/H2/PostgreSQL 설정을 한 파일에서 profile로 관리

backend/src/main/resources/config/search-rules.yml
  └─ 검색 규칙 fallback 한 파일

.github/workflows/ci.yml
  └─ GitHub Actions CI 한 파일

deploy/compose.yml
  └─ 나중에 Docker/PostgreSQL로 인트라넷 배포할 때만 사용
```

로컬 구현 단계에서는 `deploy/compose.yml`을 사용할 필요가 없습니다.

## 로컬/인트라넷 구조

```text
Browser
   ↓
Spring Boot :8080
   ├─ 로그인 / 권한 / 관리자 승인
   ├─ 문서 / 회의 / TODO / 결정 / 변경
   ├─ 통합 검색 / Query Router / RRF / Evidence
   ├─ H2 또는 PostgreSQL
   └─ Connector
          ↓
FastAPI :8000
   ├─ Gemini LLM
   ├─ Gemini STT
   ├─ local E5
   └─ local PaddleOCR (업로드 스캔/이미지 fallback)
```

카메라 전용 UI/API나 LangChain runtime은 포함하지 않습니다.

## 인트라넷 공개

현재 기본 바인딩은 로컬 PC 전용입니다.

```env
HUB_BIND_ADDRESS=127.0.0.1
```

같은 사내망의 다른 PC에서도 접속시키려면:

```env
HUB_BIND_ADDRESS=0.0.0.0
```

으로 바꾸고 Windows 방화벽/사내 네트워크 정책을 맞춰야 합니다. 다른 PC 브라우저에서 마이크 녹음을 직접 사용할 경우 HTTPS가 필요할 수 있습니다.

## 검수/빌드

빠른 구조 검수:

```bat
HUB.bat check
```

전체 테스트:

```bat
HUB.bat test
```

배포 JAR 생성:

```bat
HUB.bat build
```

최종 JAR:

```text
backend/build/libs/hub-backend.jar
```

Gradle이 설치되어 있지 않으면 `scripts/bootstrap-gradle.ps1`이 공식 Gradle 8.14.3을 프로젝트의 `.tools`에 한 번 다운로드합니다.

## 선택적 Docker 배포

로컬 구현 단계에서는 필요 없습니다. 추후 PostgreSQL + pgvector로 인트라넷 서버에 올릴 때만 사용합니다.

```bat
docker compose --env-file .env -f deploy/compose.yml up -d --build
```

HTTPS profile까지 사용할 경우:

```bat
docker compose --env-file .env -f deploy/compose.yml --profile https up -d --build
```

## 상세 문서

- `docs/ARCHITECTURE.md`
- `docs/CONFIGURATION.md`
- `docs/API_CONTRACT.md`
- `docs/OPERATIONS.md`
- `docs/VALIDATION.md`
