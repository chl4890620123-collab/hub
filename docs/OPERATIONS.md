# Operations

## 로컬 개발

VS Code에서는 루트 `Hub.code-workspace`를 열고 `Tasks: Run Task`에서 아래 명령과 같은 작업을 실행합니다.


```text
HUB.bat start   # 시작, 인자 없이 더블클릭해도 start
HUB.bat status  # 상태 확인
HUB.bat check   # 저장소/설정 구조 검수
HUB.bat doctor  # Java/Python/Node/VS Code 환경 점검
HUB.bat test    # TypeScript + Python + Spring 테스트
HUB.bat build   # 프론트 동기화 + Spring JAR 빌드
HUB.bat stop    # 종료
```

`.env`가 없으면 mock/hash 모드로 빠르게 시작합니다. 실제 AI 설정이 있으면 최초 1회 Python 기본 패키지와 local E5/PaddleOCR 묶음을 `.venv`에 설치합니다.

## 사내 다른 PC에서 접속

서버 PC의 `.env`에서 `HUB_BIND_ADDRESS=0.0.0.0`을 사용하고 OS 방화벽에서 필요한 포트만 사내망에 허용합니다. 브라우저 직접 마이크 녹음은 secure context가 필요하므로 다른 PC에서 녹음 기능까지 쓸 경우 HTTPS를 권장합니다.

## 선택적 Docker/PostgreSQL

```text
docker compose --env-file .env -f deploy/compose.yml up -d --build
```

이 방식은 PostgreSQL/pgvector + AI + Backend를 함께 띄웁니다. HTTPS가 필요할 때만 Caddy profile을 추가합니다. 로컬 개발은 Docker가 필수가 아닙니다.

## 데이터 보존

- H2: `./data/hubdb*`
- 업로드 원본: `./data/storage`
- PostgreSQL Docker: named volume `hub_pg_data`
- 원본 파일은 Hub가 임의로 수정/이동/이름 변경하지 않습니다.

## 장애 확인 순서

1. `HUB.bat status`
2. `.runtime/ai.err.log`
3. `.runtime/backend.err.log`
4. `.env` 값은 노출하지 말고 변수 이름/에러 메시지만 확인
5. `HUB.bat check`로 구조 회귀 확인
