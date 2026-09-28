# Architecture

## 1. 제품 기준

Hub는 **사내 자료 검색과 실제 업무 흐름을 연결하는 시스템**입니다. 문서·회의·Connector 자료를 검색 가능한 형태로 모으고, RAG/Evidence로 근거를 확인한 뒤 TODO·결정·변경·일정과 관리자 승인 흐름으로 연결합니다.

AI 결과는 공식 업무가 아니라 후보입니다. 담당자 배정·확정과 완료 승인·보류 같은 결정은 관리자가 수행합니다.

## 2. 실행 구조

```text
Browser
  │
  ▼
React + TypeScript SPA
  │  same origin
  ▼
Spring Boot / Java 21
  ├─ Auth / ADMIN·MEMBER / signup approval
  ├─ Projects / documents / meetings / attachments
  ├─ Search Router / hybrid ranking / RAG orchestration
  ├─ TODO / decisions / changes / timeline / notifications
  ├─ Admin review / reassignment / organization / policy
  ├─ Evidence / revision / audit
  └─ Connector import
        │
        ├─ H2(local) or PostgreSQL + pgvector
        └─ FastAPI AI service
             ├─ Gemini LLM
             ├─ Gemini STT
             ├─ multilingual-e5-base
             └─ PaddleOCR
```

프로덕션 빌드에서는 React 결과물이 Spring Boot 정적 리소스로 포함되어 같은 origin에서 서비스됩니다. 인증, 권한, DB, 검색 오케스트레이션, Evidence 검증, 업무 상태는 Spring이 소유합니다.

## 3. 역할 구조

### MEMBER

- 프로젝트 참여자
- 할 일 담당자 후보
- 자기 할 일 상태 변경
- 도움 요청/해결
- 완료 파일 또는 URL 제출
- 할 일과 무관한 자료를 관리자 제출함으로 전송
- 프로젝트 참여자끼리 직접 파일 전송

### ADMIN

- 할 일 담당자가 아님
- AI 후보 검토·확정·반려
- 완료 승인·보류
- 삭제·복원·영구삭제
- 가입 승인, 사용자/조직/재배정/보안/검색 정책 관리
- 프로젝트 관리자 제출함 전체 조회

관리자 권한은 프로젝트 생성자나 일반 사용자별 confirm 플래그가 아니라 현재 전역 `ADMIN` 역할에 의해 결정됩니다.

## 4. 웹 UI

현재 웹 앱에 포함되는 주요 기능:

- 로그인/가입/관리자 승인
- 대시보드
- 자료 찾기 / RAG / Evidence / 원문 열기
- 관련 업무 모아보기
- 문서·이미지 업로드 / 직접 입력
- 브라우저 회의 녹음 / 음성 파일 업로드·드래그앤드롭 / STT
- TODO 목록 / 달력 / 진행 상태
- 관리자 AI 검토함
- 자료표
- Connector
- 알림
- 관리자 화면

별도 모바일 앱이나 모바일 전용 API는 없습니다. 다만 현재 React UI는 좁은 화면에서 사이드바를 off-canvas drawer로 전환하는 등 **반응형 레이아웃을 지원**합니다.

## 5. 검색 파이프라인

```text
Query
 → SearchQueryRouter
 → exact filename / metadata / lexical / semantic / connector candidates
 → weighted reciprocal-rank fusion
 → deterministic rerank
 → document context expansion
 → selected chunks/documents
 → RAG
 → returned Evidence verification
 → answer + source + open-original
```

명시적 파일명은 정확 일치를 강하게 우선합니다. 작성자·날짜·자료 종류 힌트는 구조화 검색에 사용합니다. AI가 반환한 Evidence가 실제 검색 결과와 매핑되지 않으면 근거 있는 답변으로 확정하지 않습니다.

프로젝트 관리자가 설정한 활성 검색 규칙이 질문에 일치하면 AI 답변의 근거는 그 규칙의 파일명 패턴, 명시적으로 요청된 원본 파일, 또는 지정된 원본 양식과 유사한 문서로 제한합니다. 이 범위에 근거가 없으면 답변을 보류합니다. 일반 자료 검색은 규칙 밖의 관련 문서도 계속 표시합니다. 내장 YAML 기본 규칙은 검색 우선순위만 조정하며 답변 범위를 제한하지 않습니다.

## 6. 입력·비동기 처리

- 일반 문서: Apache Tika 등 parser로 텍스트 추출
- 이미지/스캔 문서: 텍스트가 부족하면 local PaddleOCR fallback
- 회의: 웹 녹음 또는 음성 파일 → Gemini STT → transcript → 요약/TODO/결정 후보
- Connector: 외부 자료 import → Hub 검색 인덱스 반영
- 긴 분석/STT/대량 import: processing job과 백그라운드 처리 사용
- provider 목록/import: 페이지 단위 수집 지원

실패한 processing job 이력은 별도로 삭제할 수 있으며 원본 자료 삭제와는 구분됩니다.

## 7. 업무·제출 흐름

```text
AI 후보
 → ADMIN 검토·담당자 지정
 → MEMBER 수행
 → 파일/URL 완료 제출
 → 승인 대기 중 증빙 잠금
 → ADMIN 승인 또는 반려
```

특정 할 일과 관계없는 출장·외근·참고자료는 프로젝트 단위 관리자 제출함으로 보냅니다. 이 제출함은 특정 관리자 개인에게 귀속되지 않아 관리자 교체 후에도 유지됩니다.

## 8. 데이터·근거 원칙

- 문서는 version 단위로 보존합니다.
- Evidence는 원문 snapshot과 content hash를 유지합니다.
- 기존 TODO의 근거는 새 문서 버전으로 자동 이동하지 않습니다.
- AI 결과는 관리자 확인 전 공식 업무가 아닙니다.
- 완료 승인 대기 중에는 제출 URL/첨부 및 작업 상태를 변경하지 못하게 서버에서 제한합니다.

## 9. 저장소

- 로컬: H2 file DB + `./data/storage`
- 운영: PostgreSQL + pgvector + 서버 storage
- 스키마 변경: Flyway

Flyway는 `common + 현재 DB profile`을 함께 읽으므로 동일 버전 migration이 두 위치에 중복되면 서버가 시작되지 않습니다. `scripts/verify.py`가 이 충돌을 CI에서 검사합니다.

## 10. 보안

JWT stateless, access/refresh token 분리, BCrypt, CSRF cookie token, ADMIN/MEMBER 역할 분리, 가입 승인, 프로젝트 접근 제어를 사용합니다. 실제 Secret은 Git이 아니라 로컬 `.env` 또는 중앙 배포 서버의 런타임 환경에만 둡니다.
