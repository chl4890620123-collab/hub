# Operations

## 1. 로컬 개발

VS Code에서는 루트 `Hub.code-workspace`를 열고 Task 또는 다음 명령을 사용합니다.

```text
HUB.bat start
HUB.bat status
HUB.bat check
HUB.bat doctor
HUB.bat test
HUB.bat build
HUB.bat stop
```

`.env`가 없으면 빠른 확인을 위해 local H2 + mock/hash 구성을 사용할 수 있습니다. 실제 AI 설정을 쓰면 필요한 Python 패키지와 local model 구성이 추가로 필요합니다.

## 2. 사내 다른 PC에서 접속

서버 PC에서 `HUB_BIND_ADDRESS=0.0.0.0`을 사용하고 OS 방화벽에서 필요한 포트만 허용합니다.

브라우저 마이크 녹음은 secure context 제약이 있으므로 localhost가 아닌 다른 PC에서 사용할 때는 HTTPS 구성을 권장합니다.

## 3. 독립 Docker 실행

Hub 저장소 자체의 선택적 Docker 경로:

```text
docker compose --env-file .env -f deploy/compose.yml up -d --build
```

이 구성은 PostgreSQL/pgvector + AI + Backend를 함께 실행합니다. HTTPS가 필요하면 Caddy profile을 추가할 수 있습니다.

이 방식은 `Server` 저장소가 관리하는 실제 중앙 운영 배포와 별개입니다.

## 4. 데이터 위치

로컬:

- H2: `./data/hubdb*`
- 업로드 원본: `./data/storage`

Hub 저장소의 독립 Docker compose:

- PostgreSQL: named volume `hub_pg_data`
- 파일: named volume `hub_storage`

현재 중앙 운영 서버:

- PostgreSQL: `D:\server-data\hub\postgres`
- 파일: `D:\server-data\hub\storage`
- 백업: `D:\server-data\hub\backups`
- 런타임 설정: `D:\server-data\hub\runtime\.env`

원본 파일과 DB는 소스 checkout과 분리합니다.

## 5. 데이터 보존

문서·회의·Evidence 같은 핵심 자료와 로그성 데이터의 보존 정책은 동일하지 않습니다.

- 완료된 오래된 TODO는 영구 삭제 대신 먼저 복원 가능한 휴지통으로 이동할 수 있습니다.
- 세션/검색 로그/일부 import history처럼 로그 성격의 데이터는 retention 대상이 될 수 있습니다.
- 문서/회의/Evidence 원본을 retention job이 임의로 영구 삭제하는 구조로 취급하면 안 됩니다.

세부 기간은 관리자 설정과 `application.yml` 기본값을 확인합니다.

## 6. 장애 확인 순서 — 로컬

1. `HUB.bat status`
2. `.runtime/ai.err.log`
3. `.runtime/backend.err.log`
4. Secret 값 자체는 출력하지 말고 변수 이름과 에러 메시지만 확인
5. `HUB.bat check`
6. `HUB.bat test`

## 7. 장애 확인 순서 — 운영

1. Server 저장소의 최신 `Central production deployment` 결과 확인
2. 실패 step 확인
3. `hub-backend`, `hub-db`, `hub-ai`, `hub-caddy` 상태 확인
4. backend restart loop면 Spring/Flyway 로그 우선 확인
5. DB/AI가 healthy인데 root/health가 실패하면 backend 로그 확인
6. 로컬 health가 성공하고 공개 검증만 실패하면 외부 HTTPS/라우팅/포트 경로 확인

Server 저장소의 read-only 진단 워크플로를 이용해 backend/db 로그와 머신 상태를 수집할 수 있습니다.

## 8. 배포 완료 판정

다음이 모두 성공해야 운영 배포 완료입니다.

- Hub 최신 main CI
- Server 중앙 배포
- 서버 내부 `/actuator/health`
- 공개 URL root + `/actuator/health`

Merge나 이미지 build만 성공한 상태를 배포 완료라고 판단하지 않습니다.
