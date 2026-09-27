# Hub v2.34

**Hub**는 사내 문서·회의·연결 서비스·업무 기록을 한곳에 모으고,  
검색 → 근거 확인 → AI 정리 → 담당자 업무 → 관리자 검토까지 이어 주는 **사내 업무 AI 허브**입니다.

처음 온 구성원도 기존 자료와 근거를 빠르게 찾고, 해야 할 일을 놓치지 않도록 만드는 것이 핵심 목표입니다.

---

## 핵심 구조

Hub는 단순 챗봇이 아니라 **회사 자료와 실제 업무 흐름을 연결하는 시스템**입니다.

```text
문서 / 회의 / GitHub / Google Drive / Slack / Notion
                    ↓
              통합 수집·색인
                    ↓
          검색 / AI 질문 / 근거 확인
                    ↓
          TODO / 결정 / 변경 / 일정
                    ↓
       일반 사용자 수행·제출
                    ↓
          관리자 검토·승인·반려
```

AI가 만든 결과는 바로 공식 업무가 되지 않습니다.  
**관리자가 원문과 근거를 확인한 뒤 담당자를 지정하고 확정**합니다.

---

## 사용자 역할

현재 역할 기준은 명확하게 분리되어 있습니다.

| 역할 | 주요 권한 |
|---|---|
| **일반 사용자 (MEMBER)** | 배정받은 업무 수행, 상태 변경, 도움 요청, 근거 확인, 완료 자료 제출, 일반 자료 전송 |
| **관리자 (ADMIN)** | AI 검토, 담당자 결정, 완료 승인·반려, 삭제·복원·영구삭제, 가입 승인, 사용자/조직/보안/검색 정책 관리 |

### 담당자 기준

- **할 일 담당자는 일반 사용자만 가능합니다.**
- 관리자는 담당자가 아니라 **결정권자/검토자**입니다.
- 일반 사용자는 다른 사람의 개인 업무 상태를 대신 변경할 수 없습니다.
- 완료 승인·반려·삭제는 백엔드에서도 관리자만 가능합니다.

### 관리자 교체

관리자는 프로젝트 생성자에 고정하지 않습니다.

- 관리자 역할은 **회사 관리 > 사용자 관리**에서 변경할 수 있습니다.
- 관리자가 교체되어도 관리자용 제출 자료는 특정 관리자 개인에게 묶이지 않습니다.
- 관리자 제출물은 **프로젝트 단위 관리자 제출함**에 남아 새 관리자가 이어서 확인할 수 있습니다.

---

## 할 일·일정 흐름

확정된 할 일은 다음 상태를 사용합니다.

- **시작 전**
- **진행 중**
- **보류**
- **도움 필요**
- **반려**
- **완료**

기본 흐름:

```text
AI 후보 생성
  → 관리자 검토
  → 담당자 지정·확정
  → 시작 전
  → 진행 중
  → 완료 제출
  → 관리자 검토
  ├─ 승인 → 완료
  └─ 반려 → 담당자 수정 후 재제출
```

담당자는 자기 할 일에서만 진행/보류/도움 요청/완료 제출을 할 수 있습니다.

### 완료 제출

완료 요청 시 다음 중 하나 이상의 근거가 필요합니다.

- 제출 파일 첨부
- `http://` 또는 `https://` URL 제출

파일과 URL이 모두 없으면 완료 제출할 수 없습니다.

완료 승인 대기 중에는 제출 근거가 바뀌지 않도록 서버에서 다음 동작을 잠급니다.

- 상태 변경
- 보류
- 도움 요청
- 제출 첨부파일 추가
- 제출 첨부파일 이름/메모 수정
- 제출 첨부파일 삭제

관리자는 제출 URL·첨부파일·업무 근거를 확인한 뒤 승인하거나 반려합니다.

---

## 할 일과 무관한 자료 제출

출장, 외근, 현장 확인, 참고 링크처럼 **등록된 할 일과 직접 연결되지 않는 자료**도 보낼 수 있습니다.

일반 사용자는 **자료 보내기**에서 관리자 제출함으로 다음 자료를 임의 제출할 수 있습니다.

- 파일
- URL
- 제목
- 설명/메모

관리자 제출함은 프로젝트에 귀속되므로 관리자 교체 후에도 기존 제출물을 이어서 볼 수 있습니다.

일반 사용자끼리 특정 상대에게 직접 보내는 파일 전송도 별도로 유지됩니다.

---

## 주요 화면

현재 React 웹 앱의 주요 메뉴는 다음과 같습니다.

- **대시보드**
- **자료 찾기**
- **AI에게 묻기**
- **관련 업무 모아보기**
- **할 일·일정**
- **AI 검토함** — 관리자 전용
- **문서 요약**
- **회의 녹음**
- **공유 자료표**
- **연결 서비스**
- **알림**
- **내 정보**
- **회사 관리** — 관리자 전용

회사 관리에서는 다음을 관리합니다.

- 가입 승인
- 업무 재배정
- 사용자 관리
- 부서·팀
- 검색 규칙
- 보안
- 관리 이력

---

## 문서·검색·AI

Hub의 검색은 단순 키워드 검색만 사용하지 않습니다.

```text
질문
 → SearchQueryRouter
 → 파일명 / 메타데이터 / lexical / semantic 후보
 → RRF 기반 결합
 → deterministic rerank
 → 문맥 확장
 → RAG
 → Evidence 검증
 → 답변 + 근거 + 원문 열기
```

주요 원칙:

- 파일명이 명확하면 정확 일치를 강하게 우선합니다.
- 문서·회의·첨부파일은 Hub 자료로 함께 검색합니다.
- Evidence는 원문 snapshot과 content hash를 유지합니다.
- AI가 반환한 근거가 실제 검색 결과와 연결되지 않으면 근거 있는 답변으로 확정하지 않습니다.
- 문서 버전이 바뀌어도 기존 TODO의 근거가 자동으로 다른 버전으로 바뀌지 않습니다.

---

## 회의

회의는 다음 방식으로 입력할 수 있습니다.

- 브라우저 녹음
- 음성 파일 선택
- 음성 파일 드래그앤드롭

처리 흐름:

```text
음성
 → STT
 → 회의록
 → 요약
 → TODO / 결정 후보
 → 관리자 AI 검토함
```

실패한 분석 작업 기록은 별도로 삭제할 수 있습니다.

---

## 연결 서비스

지원 대상:

- **GitHub**
- **Google Drive**
- **Slack**
- **Notion**

연결 서비스에서 가져온 자료도 Hub 검색 인덱스에 포함됩니다.

백엔드는 대용량 가져오기를 한 번에 화면 요청에 묶지 않고 **백그라운드 작업과 페이지 단위 수집**을 지원합니다.

실제 계정별 토큰은 사용자 단위로 분리하는 것이 기본이며, 공유 토큰 fallback은 명시적으로 켠 경우에만 사용합니다.

---

## 기술 구성

```text
Browser
   ↓
React + TypeScript
   ↓
Spring Boot / Java 21
   ├─ 인증 / 권한 / 관리자 승인
   ├─ 프로젝트 / 문서 / 회의
   ├─ 검색 / RAG / Evidence
   ├─ TODO / 결정 / 변경 / 알림
   ├─ 관리자 기능
   └─ Connector
          ↓
FastAPI
   ├─ Gemini LLM
   ├─ Gemini STT
   ├─ multilingual E5
   └─ PaddleOCR
```

데이터 저장:

- 로컬 개발: **H2 file DB**
- 운영: **PostgreSQL + pgvector**
- 스키마 변경: **Flyway**
- 파일 저장소: 로컬/서버 storage 디렉터리

Flyway는 `common + 현재 DB profile` 조합에서 버전이 중복되면 애플리케이션이 시작되지 않으므로, 저장소 검수 스크립트가 중복 버전을 CI에서 검사합니다.

---

## 보안 기준

- JWT stateless 인증
- Access / Refresh Token 분리
- BCrypt 비밀번호 해시
- CSRF cookie token
- ADMIN / MEMBER 권한 분리
- 가입 승인
- 비활성/정지 계정 제어
- 프로젝트 접근 제어
- 실제 Secret은 Git에 저장하지 않음
- 회의 STT의 민감정보는 저장 전 비식별 처리 경로 사용

---

## 로컬 실행

권장 환경:

- Java 21
- Python 3.9~3.13 x64
- Node.js 22+
- VS Code

### 프로젝트 열기

`Hub.code-workspace`를 VS Code로 엽니다.

### 환경 점검

```bat
HUB.bat doctor
```

### 로컬 시작

```bat
HUB.bat start
```

기본 주소:

```text
http://localhost:8080/login
```

`.env`가 없으면 빠른 개발 확인을 위해 H2 + mock/hash 기반으로 시작할 수 있습니다.

실제 AI/Connector를 붙일 때:

```bat
copy .env.example .env
```

실제 API 키와 비밀번호는 로컬 `.env` 또는 서버 Secret에만 저장합니다.

---

## 자주 쓰는 명령

```bat
HUB.bat doctor
HUB.bat start
HUB.bat status
HUB.bat check
HUB.bat test
HUB.bat build
HUB.bat stop
```

VS Code Task에서도 동일한 작업을 실행할 수 있습니다.

---

## CI

Hub 저장소의 `.github/workflows/ci.yml`에서 다음을 검증합니다.

- 저장소/환경 설정 검수
- React TypeScript typecheck 및 build
- AI 서비스 테스트
- Spring Boot 단위 테스트
- 배포용 JAR 생성
- Flyway migration 버전 중복 검사

최종 JAR:

```text
backend/build/libs/hub-backend.jar
```

---

## 운영 배포

운영 배포는 Hub 저장소에서 직접 SSH 배포하지 않습니다.

```text
Hub main
  → Hub CI
  → chl4890620123-collab/Server
  → Central production deployment
  → 서버에서 Hub 소스 체크아웃
  → AI / Backend Docker 이미지 빌드
  → PostgreSQL + AI + Backend + Caddy 기동
  → 로컬 health 확인
  → 공개 배포 검증
```

즉 **Hub의 main push와 실제 운영 배포는 분리**되어 있습니다.

운영 배포 트리거와 서버 SSH/Compose 설정은  
`chl4890620123-collab/Server` 저장소가 중앙 관리합니다.

현재 운영용 public base URL 설정은 다음 주소를 기준으로 사용합니다.

```text
https://yellow.it.kr
```

운영 상태는 README에 고정 기록하지 않고 배포 워크플로의 실제 검증 결과를 기준으로 판단합니다.

---

## 저장소 구성

```text
hub/
├─ web/          React + TypeScript 현재 웹 UI
├─ backend/      Spring Boot 핵심 애플리케이션
├─ ai-service/   FastAPI AI/STT/Embedding/OCR
├─ frontend/     기존 런타임/호환 자산
├─ scripts/      로컬 실행·검수 도구
├─ deploy/       독립 배포용 Compose/Caddy
├─ docs/         아키텍처·설정·API·운영 문서
├─ HUB.bat
└─ Hub.code-workspace
```

---

## 상세 문서

- `docs/ARCHITECTURE.md`
- `docs/API_CONTRACT.md`
- `docs/CONFIGURATION.md`
- `docs/OPERATIONS.md`
- `docs/VALIDATION.md`
- `docs/DEPLOYMENT.md`

---

## 제품 방향

Hub가 지향하는 흐름은 다음과 같습니다.

> 흩어진 사내 자료를 찾기 쉽게 만들고,  
> AI가 근거를 바탕으로 정리하며,  
> 일반 사용자는 맡은 일을 수행하고,  
> 관리자는 판단과 승인에 집중한다.

검색만 잘하는 도구가 아니라 **회사 안의 기록·근거·업무·판단을 한 흐름으로 연결하는 것**이 Hub의 목표입니다.
