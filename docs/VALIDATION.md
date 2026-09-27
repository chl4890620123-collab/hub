# Validation

이 문서는 오래된 특정 날짜의 테스트 개수 스냅샷이 아니라 **현재 저장소가 무엇을 검증하는지**를 설명합니다. 실제 성공 여부는 최신 GitHub Actions run을 기준으로 판단합니다.

## 1. Hub CI

`.github/workflows/ci.yml`의 주요 job:

### web-and-config

- `.env.example` 형식 검사
- 저장소 구조 검사
- Flyway migration 버전 중복 검사
- legacy mobile/camera/LangChain 경로 회귀 검사
- `frontend/` TypeScript typecheck/build
- committed runtime bundle 일치 검사
- legacy static JS syntax 검사

### web-app

- 현재 React 앱 `web/` TypeScript typecheck
- Vite production build

### ai

- Python 3.13
- compileall
- mock/hash provider 기반 pytest
- CI에서는 외부 Gemini/Connector Secret 없이 실행

### backend

앞선 job이 모두 성공한 뒤:

- Java 21
- Gradle 8.14.3
- `gradle --no-daemon clean test bootJar`
- `backend/build/libs/hub-backend.jar` artifact 업로드

## 2. 구조 검증

`scripts/verify.py`는 현재 다음 종류의 회귀를 차단합니다.

- `.env.example` 외 루트 dotenv 템플릿 증가
- Spring 설정 파일 중복
- 배포 compose 위치 회귀
- 검색 규칙 파일 중복
- 카메라/PWA/mobile 전용 legacy 경로 재등장
- LangChain runtime 재도입
- 핵심 Search/RAG/Evidence/Auth 파일 누락
- backend JAR 이름/CI 경로 불일치
- `common + h2`, `common + postgresql` 조합의 Flyway 버전 충돌

Flyway 버전 충돌은 컴파일·단위 테스트가 성공해도 실제 프로덕션 DB profile 부팅을 막을 수 있으므로 별도로 검사합니다.

## 3. 역할·업무 검증에서 중요한 항목

최근 권한/업무 흐름에서 특히 확인해야 하는 경계:

- 할 일 담당자는 활성 `MEMBER`만 가능
- 관리자 검토/승인 API는 `ADMIN` 전용
- 일반 사용자는 자기 확정 할 일만 상태 변경
- 완료 제출은 파일 또는 `http(s)` URL 필요
- 완료 승인 대기 중 상태와 제출 증빙 변경 금지
- 관리자 제출함은 프로젝트 귀속
- 현재 관리자는 프로젝트 전체 관리자 제출물을 확인 가능
- 일반 사용자는 자기 관리자 제출물만 확인
- 실패 processing job 삭제는 `FAILED` 상태만 가능

관련 동작에는 Spring 단위 테스트가 포함되어 있습니다.

## 4. 배포 검증

Hub CI 성공과 운영 배포 성공은 별개입니다.

Server 중앙 배포에서 추가로 확인하는 항목:

1. 서버에서 최신 Hub main checkout
2. AI/Backend 이미지 revision 확인
3. PostgreSQL 사전 백업
4. Docker compose 기동
5. 로컬 `/actuator/health` + root
6. 공개 URL root + health

따라서 최종 배포 판정은 Server 저장소의 `Central production deployment` 결과까지 확인해야 합니다.

## 5. 실제 외부 서비스 E2E

다음은 CI의 mock/network-free 테스트만으로 완전 검증할 수 없습니다.

- 실제 Gemini quota/응답
- Google Drive OAuth/import
- Slack OAuth/import
- GitHub OAuth/import
- Notion OAuth/import
- 실제 DNS/HTTPS/포트 포워딩
- 운영 사용자 계정으로 전체 브라우저 시나리오

이 항목은 운영 Secret과 실제 외부 서비스가 연결된 환경에서 별도로 확인합니다.
