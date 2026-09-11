# Architecture

## 1. 제품 기준

Hub의 본체는 **사내 통합 검색**입니다. 자료 입력과 회의 STT는 검색 가능한 데이터를 만들고, Connector는 범위를 넓히며, RAG/Evidence는 답변 신뢰도를 높이고, TODO/결정/변경은 검색 결과를 실제 업무로 연결합니다.

카메라 입력과 LangChain은 현재 범위에 없습니다.

## 2. 실행 구조

```text
Browser - one responsive web app
        │
        ▼
Spring Boot / Java 21
  ├─ Auth / ADMIN·MEMBER / approval
  ├─ Projects / documents / meetings
  ├─ Search Router / hybrid ranking / RAG orchestration
  ├─ TODO / decisions / changes / timeline
  ├─ Evidence / revision / audit
  └─ read-only connectors
        │
        ├── H2(local) or PostgreSQL + pgvector
        │
        └── FastAPI AI service
             ├─ Gemini LLM (external when enabled)
             ├─ Gemini STT (external when enabled)
             ├─ multilingual-e5-base (local)
             └─ PaddleOCR (local, uploaded scan/image fallback)
```

Spring이 인증, 권한, DB, 검색, RAG 오케스트레이션, Evidence 검증, 업무 상태를 소유합니다. FastAPI가 별도의 검색 저장소를 운영하거나 업무 규칙을 소유하지 않습니다.

## 3. 웹에 포함되는 기능

- 로그인/가입/관리자 승인
- 통합 검색 / RAG / Evidence / 원문 열기
- 문서·이미지 파일 업로드 / 직접 입력
- 브라우저 회의 녹음 / 음성 파일 업로드 / STT
- TODO 목록 / 월간 달력 / 진행 상태
- AI 후보 관리자 검토·수정·확정
- 계정 정지·복구 / 프로젝트 이동 / 미완료 TODO 재배정
- Slack / Notion / Google Drive / GitHub read-only Connector

별도 모바일 앱, 모바일 전용 API, 카메라 capture API는 없습니다. 좁은 화면에서도 동일한 웹 기능을 반응형으로 보여줍니다.

## 4. 검색 파이프라인

```text
Query
 → SearchQueryRouter
 → exact filename / metadata / lexical / semantic / connector candidates
 → weighted reciprocal-rank fusion
 → deterministic rerank
 → document context expansion
 → selected chunks/documents
 → RAG
 → returned evidence ID verification
 → answer + source + open-original
```

명시적 파일명은 정확 일치를 강하게 우선합니다. 작성자/날짜/자료 종류 힌트는 구조화된 검색에 사용합니다. AI가 반환한 Evidence ID가 실제 검색 소스와 매핑되지 않으면 근거 있는 답변으로 확정하지 않습니다.

## 5. 입력 처리

- 일반 문서: Apache Tika 등 기존 parser로 텍스트 추출
- 이미지/스캔 문서: 일반 파일 업로드 후 parser가 충분한 텍스트를 얻지 못하면 local PaddleOCR fallback
- 회의: 웹 녹음 또는 음성 파일 → Gemini STT → searchable transcript → 요약/TODO/결정 후보
- Connector: 읽기 전용 import → 같은 문서 검색 인덱스에 반영

## 6. 데이터·근거 원칙

- 문서는 version 단위로 보존합니다.
- Evidence는 원문 snapshot과 content hash를 유지합니다.
- 기존 TODO의 근거가 새 버전 문서로 자동 이동하지 않습니다.
- AI 결과는 후보이며 관리자 확인 전에는 공식 업무가 아닙니다.

## 7. 저장소

로컬은 H2 file DB + `./data/storage`, 인트라넷 서버는 PostgreSQL + pgvector를 사용합니다. 스키마 변경은 Flyway로 관리합니다.

## 8. 보안

JWT stateless, access/refresh token 분리, BCrypt, CSRF cookie token, ADMIN/MEMBER 권한, 가입 승인 흐름을 유지합니다. 실제 Secret은 `.env` 또는 배포 Secret store에만 둡니다.
