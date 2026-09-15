# 배포 (yellow.it.kr 서버 자동배포)

`main` 브랜치에 push되면 GitHub Actions가 테스트를 통과한 뒤 SSH로 서버에 접속해
`git pull` + `docker compose up -d --build`를 실행합니다. 워크플로 정의는
`.github/workflows/ci.yml`의 `deploy` job입니다.

## 1. 서버에서 한 번만 준비할 것

1. 저장소를 원하는 경로에 clone합니다. 예: `git clone https://github.com/chl4890620123-collab/hub.git /home/<user>/hub`
2. 같은 경로에 `.env` 파일을 직접 만듭니다 (`.env.example`을 복사해서 실제 값으로 채우기). 이 파일은
   git으로 관리되지 않으므로 GitHub Secret 이름 제약(`GITHUB_`로 시작 불가 등)과 무관합니다.
3. Docker / Docker Compose가 설치되어 있어야 합니다.
4. GitHub Actions가 접속할 SSH 키 쌍을 하나 새로 만듭니다 (사람이 로그인할 때 쓰는 키와 분리 권장):
   ```bash
   ssh-keygen -t ed25519 -C "github-actions-deploy" -f ./deploy_key -N ""
   ```
   - 공개키(`deploy_key.pub`) 내용을 서버 계정의 `~/.ssh/authorized_keys`에 추가합니다.
   - 개인키(`deploy_key`) 내용을 아래 GitHub Secret `DEPLOY_SSH_KEY`에 등록합니다. 등록 후 로컬의
     `deploy_key` 파일은 지웁니다.

## 2. GitHub Actions Secrets (저장소 Settings → Secrets and variables → Actions)

`deploy` job이 필요로 하는 값은 이 4개뿐입니다 - 커넥터 토큰(`GEMINI_API_KEY`, `GITHUB_TOKEN` 등)은
여기 등록하지 않습니다, 서버의 `.env`에만 있으면 됩니다.

| Secret 이름 | 값 |
|---|---|
| `DEPLOY_HOST` | `yellow.it.kr` (또는 서버 IP) |
| `DEPLOY_USER` | SSH 접속 계정명 |
| `DEPLOY_SSH_KEY` | 위에서 만든 배포 전용 SSH **개인**키 전체 내용 |
| `DEPLOY_PATH` | 서버에 clone한 경로, 예: `/home/<user>/hub` |

## 3. 서버 `.env`에서 반드시 채워야 하는 값 (액션 필요)

`.env.example`에 있는 값 중 아래는 사람이 직접 값을 만들거나 발급받아야 하는 항목입니다. 나머지는
`.env.example`에 이미 적힌 예시값을 그대로 써도 됩니다.

| 변수 | 어떻게 채우나 |
|---|---|
| `HUB_JWT_SECRET` | 무작위 문자열 32자 이상. `openssl rand -base64 48` 로 생성 |
| `HUB_STT_PII_HASH_KEY` | 무작위 문자열. `openssl rand -base64 32` 로 생성 (재시작해도 같은 값 유지 - 5번 해시키 기능이 이 값을 씁니다) |
| `DB_PASSWORD` | PostgreSQL 비밀번호로 쓸 값 직접 정하기 |
| `GEMINI_API_KEY` | Google AI Studio에서 발급 |
| `HUB_PUBLIC_BASE_URL` | `https://yellow.it.kr` (실제 접속 도메인) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` / `GOOGLE_REFRESH_TOKEN` | Google Drive 연동을 쓸 경우만. OAuth 동의화면 redirect URI를 `HUB_PUBLIC_BASE_URL` 기준으로 등록해야 함 |
| `GITHUB_TOKEN` 또는 `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` | GitHub 연동을 쓸 경우만 |
| `SLACK_TOKEN` 또는 `SLACK_CLIENT_ID`/`SLACK_CLIENT_SECRET` | Slack 연동을 쓸 경우만 |
| `NOTION_TOKEN` 또는 `NOTION_CLIENT_ID`/`NOTION_CLIENT_SECRET` | Notion 연동을 쓸 경우만 |

커넥터(Google/GitHub/Slack/Notion) OAuth 앱을 등록할 때 콜백(redirect) 주소는 전부
`https://yellow.it.kr/...` 형태로 맞춰야 합니다 - `HUB_PUBLIC_BASE_URL`을 빈 값으로 두면 접속한
주소를 그대로 쓰므로, 공인 도메인으로 서비스할 때는 반드시 채워야 합니다.

## 4. PostgreSQL 설정

`deploy/compose.yml`은 `pgvector/pgvector:pg16` 이미지로 PostgreSQL을 함께 띄우므로, 기본값을
그대로 쓰면 별도 설치 없이 pgvector 확장까지 준비됩니다. `.env`에 `DB_USER`/`DB_PASSWORD`만
정하면 됩니다. 서버가 처음 뜰 때 Flyway가 `db/migration/common` + `db/migration/postgresql`의
마이그레이션을 자동 적용합니다 - 수동으로 스키마를 만들 필요가 없습니다.

이미 관리 중인 별도 PostgreSQL(관리형 DB 등)을 쓰고 싶다면:

1. DB와 계정을 만듭니다: `CREATE DATABASE hub; CREATE USER hub WITH PASSWORD '...'; GRANT ALL PRIVILEGES ON DATABASE hub TO hub;`
2. `pgvector` 확장을 활성화합니다 (DB에 접속해서 1회): `CREATE EXTENSION IF NOT EXISTS vector;`
3. `.env`의 `DB_URL`을 그 서버 주소로 바꿉니다: `jdbc:postgresql://<host>:5432/hub`
4. `deploy/compose.yml`에서 `postgres` 서비스와 `backend`의 `depends_on: postgres` 항목을 지우고,
   `docker compose ... up -d --build`로 backend/ai만 띄웁니다.
5. 나머지는 동일 - 앱이 처음 뜰 때 Flyway가 스키마를 자동으로 만듭니다.

## 5. 참고 - 아직 안 한 것

- **공인 도메인용 HTTPS**: `deploy/compose.yml`의 `caddy` 서비스(`--profile https`)는 현재
  `tls internal`(사내망용 자체서명 인증서)로 되어 있어 공인 도메인(yellow.it.kr)에 그대로 쓰면
  브라우저 경고가 뜹니다. 실제 도메인 인증서(Let's Encrypt 자동발급)가 필요하면 `deploy/Caddyfile`을
  `https://yellow.it.kr { reverse_proxy backend:8080 ... }` 형태로 바꾸고 서버 80/443 포트를 열어야
  합니다 - 필요하시면 이어서 설정해 드리겠습니다.
