# Validation

## 검수 결과 (2026-09-09)

현재 패키지에서 실제로 확인한 항목입니다.

- `.env.example` 구문/중복 검사: **PASS (25 keys)**
- dummy local/deploy ENV 계약 검사: **PASS**
- 저장소 구조 검증: **PASS (69 checks)**
- Java main source 존재/구조 검사: **116 source files 확인**
- 카메라 runtime 경로 scan: **0건**
- LangChain runtime/dependency scan: **0건**
- Python compile: **PASS**
- Python unit tests: **18/18 PASS**
- TypeScript 5.8.3 typecheck: **PASS**
- TypeScript browser bundle build: **PASS**
- 생성 bundle ↔ Spring static bundle 비교: **PASS**
- `Hub.code-workspace` JSON/Task 구조: **PASS**
- `app.js` Node syntax check: **PASS**
- FastAPI 실제 mock 기동: **PASS**
  - `/health` 200
  - `/api/v1/embed` 768 dimensions
  - `/api/v1/ocr` 200, `uploaded_document_ocr` metadata
  - `/api/v1/stt` 200
  - `/api/v1/analyze` 200

## 구조 검증 내용

`HUB.bat check` 또는 VS Code의 `Hub: 구조 검수` 작업은 다음을 확인합니다.

- `.env.example`만 Git/배포 템플릿으로 사용하고 로컬 `.env`는 허용하되 Git에서 제외
- Spring `application.yml` 하나
- root에는 Compose YAML 없음, 선택 배포용 `deploy/compose.yml` 하나
- 검색 기본 규칙 YAML 한 곳
- 웹 검색/회의 녹음/TODO/Connector/Admin UI 존재
- 카메라 UI/API/source type 제거
- LangChain dependency/loader 제거
- 핵심 Search/RAG/Evidence/Auth 파일 보존
- 안정된 backend JAR 이름과 CI 경로 일치

## 현재 환경에서 못 한 항목

Spring `gradle clean test bootJar`는 이 실행 환경에 Gradle CLI가 없고 `services.gradle.org` DNS 조회가 차단되어 실제 실행하지 못했습니다. Java 21은 확인했습니다. GitHub Actions의 backend job은 Java 21 + Gradle 8.14.3으로 같은 빌드를 수행하도록 남겨 두었습니다.

실제 Gemini, Google Drive, Slack, GitHub, Notion과 local E5/PaddleOCR 모델 다운로드/실데이터 추론은 사용자의 네트워크와 Secret이 필요한 E2E 항목입니다.
