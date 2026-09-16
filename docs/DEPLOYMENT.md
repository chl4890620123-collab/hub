# 배포

Hub의 실제 운영 배포는 이 저장소가 아니라 **`chl4890620123-collab/Server`** 저장소가 중앙에서
관리합니다. 여러 앱(maple, aitm, restok, hub)이 같은 Windows 미니PC 한 대를 함께 쓰기 때문에,
Docker Compose/Caddy/배포 스크립트/서버 SSH 접속 정보를 앱마다 따로 두지 않고 `Server` 저장소
한 곳에서 관리합니다. 이 문서는 그 시스템 안에서 Hub가 어떻게 동작하는지 설명합니다. 독립적으로
혼자 셀프호스팅하고 싶을 때 쓰는 방법은 맨 아래 "이 저장소만 단독으로 배포하기"를 참고하세요.

## 1. 전체 흐름

```text
git push (main, 이 저장소)
    -> hub-ci (테스트만, 배포 없음)

Server 저장소의 .ops-trigger/hub.txt 를 건드리는 push (또는 workflow_dispatch target=hub)
    -> Server: "Central production deployment" 워크플로
    -> Hub 저장소를 미니PC에서 새로 clone/체크아웃
    -> hub-production-ai / hub-production-backend 이미지를 그 자리에서 빌드
    -> deploy/scripts/deploy-hub.ps1 실행
       - D:\server-data\hub\runtime\.env 없으면 무작위 비밀값으로 자동 생성
       - Postgres 기존 데이터 있으면 배포 전 자동 백업
       - docker compose up (hub-db + ai + backend + caddy, 호스트 9070 포트)
       - /actuator/health, / 확인될 때까지 대기
       - HUB_OUTER_CADDY_AUTO_CONFIGURE=true면 공유 MOVEAI Caddy에
         yellow.it.kr -> 이 포트 라우트를 자동 등록
    -> 배포 결과 확인 (공개 URL + 정상 응답)
```

즉 **이 저장소를 push한다고 자동으로 배포되지 않습니다.** Hub 코드가 바뀐 뒤 실제로 배포하려면
`Server` 저장소의 `.ops-trigger/hub.txt`를 한 줄 바꿔서 push하거나(그러면 이 파일이 트리거가 되어
자동 배포됨), `Server` 저장소에서 "Central production deployment" 워크플로를 `target: hub`로
수동 실행하면 됩니다.

## 2. Hub 전용 설정 파일 (모두 `Server` 저장소 안에 있음)

- `deploy/compose/hub.yml` - Postgres(pgvector) + AI + Backend + Caddy
- `deploy/caddy/hub.Caddyfile` - 내부 리버스 프록시 (`:80 -> backend:8080`)
- `deploy/scripts/deploy-hub.ps1` - 실제 배포 스크립트 (다른 앱들과 동일한 패턴)
- `deploy/scripts/ensure-public-route.ps1` - 공유 MOVEAI Caddy에 새 도메인을 등록/관리하는 범용 스크립트
- `deploy/runtime/hub.env.example` - 서버 런타임 `.env`의 예시 (실제 값은 서버에만 존재)

Hub는 다른 세 앱과 달리 PostgreSQL(pgvector)을 쓰고, backend/Dockerfile이 저장소 **루트**를
빌드 컨텍스트로 쓴다는 점(프론트엔드와 백엔드를 함께 COPY하기 때문)이 다릅니다.

## 3. GitHub Secrets - 이 저장소(hub)에는 배포 시크릿이 하나도 없습니다

배포 SSH 접속 정보(`SERVER_HOST`/`SERVER_USER`/`SERVER_PORT`/`SERVER_SSH_KEY`/`SERVER_PASSWORD`)는
전부 `Server` 저장소에만 등록되어 있습니다. `deploy-service.ps1`이 매번 Hub 저장소를 새로
clone/fetch하기 때문에, Hub 저장소 자체는 어떤 배포 시크릿도 가질 필요가 없습니다.

(과거에 이 저장소 자체에 SSH로 직접 배포하는 방식을 잠깐 만들었다가, 이미 여러 앱을 함께 운영하는
중앙 배포 시스템이 있다는 걸 알게 되어 그쪽으로 옮겼습니다 - 지금은 그 흔적이 남아있지 않습니다.)

## 4. 서버 런타임 `.env`에서 액션이 필요한 값

`D:\server-data\hub\runtime\.env`는 첫 배포 때 `deploy-hub.ps1`이 무작위 비밀값으로 자동
생성합니다 (`DB_PASSWORD`, `HUB_JWT_SECRET`, `HUB_ADMIN_SETUP_KEY`, `HUB_STT_PII_HASH_KEY`).
사람이 직접 채워야 하는 값은 이것뿐입니다:

| 변수 | 어떻게 채우나 |
|---|---|
| `GEMINI_API_KEY` | Google AI Studio에서 발급 |
| `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`/`GOOGLE_REFRESH_TOKEN` | Google Drive 연동을 쓸 경우만 |
| `GITHUB_TOKEN` 또는 `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET` | GitHub 연동을 쓸 경우만 |
| `SLACK_TOKEN` 또는 `SLACK_CLIENT_ID`/`SLACK_CLIENT_SECRET` | Slack 연동을 쓸 경우만 |
| `NOTION_TOKEN` 또는 `NOTION_CLIENT_ID`/`NOTION_CLIENT_SECRET` | Notion 연동을 쓸 경우만 |

## 5. 도메인과 HTTPS

Hub는 **`yellow.it.kr`(apex 도메인)**을 그대로 씁니다. 이 도메인은 원래 다른 서비스(Dahum/닿음)가
쓰고 있었지만, `Server` 저장소의 `deploy/runtime/hub.env.example`에 기록된 대로 Hub로 이관하기로
결정되어 2026-09-16 첫 프로덕션 배포 때 `HUB_ALLOW_DOMAIN_TAKEOVER=true` 설정을 통해
`ensure-public-route.ps1`이 공유 MOVEAI Caddy에서 Dahum의 라우트 블록을 제거하고 Hub의 블록으로
교체했습니다. Dahum은 더 이상 이 도메인에서 서비스되지 않습니다. (`hub.yellow.it.kr` 서브도메인은
검토만 됐을 뿐 DNS 등록이 된 적이 없고, 실제로는 쓰이지 않습니다.)

공유 중인 MOVEAI Caddy(호스트 80/443 소유)가 `yellow.it.kr` 요청을 받아 Hub 컨테이너로 넘겨주고
Let's Encrypt 인증서를 자동으로 받아옵니다.

1. 가비아 DNS의 `yellow.it.kr` A 레코드는 이미 이 서버의 공인 IP를 가리키고 있음 (Dahum 때부터
   등록되어 있던 레코드를 그대로 재사용)
2. `D:\server-data\hub\runtime\.env`의 `HUB_OUTER_CADDY_AUTO_CONFIGURE=true`(기본값)면 배포할 때마다
   자동으로 공유 Caddy에 라우트가 등록/갱신됨. `HUB_ALLOW_DOMAIN_TAKEOVER=true`는 이관 이후에도
   그대로 둬도 무해합니다(도메인이 이미 Hub 소유이므로) - 만약 이후 다른 앱이 실수로 같은 도메인을
   요청했을 때 자동으로 뺏기지 않고 배포가 실패하도록 막고 싶다면 `false`로 바꾸세요.
3. 로그인 쿠키가 있으므로 `HUB_COOKIE_SECURE=true`, `HUB_ENFORCE_SECURE_CONFIG=true`로
   바꾸는 것을 권장 - 단, `yellow.it.kr`으로 실제 접속이 되는 것을 먼저 확인한 뒤에
   바꾸세요 (그전에 켜면 평문 HTTP로만 접근 가능한 상태에서 쿠키가 거부되어 로그인이 막힙니다).

## 6. 최초 관리자 계정 만들기

가입 화면의 "관리자 가입"에서 "관리자 설정 키"에 `D:\server-data\hub\runtime\.env`의
`HUB_ADMIN_SETUP_KEY` 값을 넣어야 최초 관리자 계정이 그 자리에서 만들어집니다. 이미 관리자가
있으면 이 값과 무관하게 항상 기존 관리자 승인 대기 상태로 접수됩니다.

## 7. 배포 상태 확인

```bash
gh run list --repo chl4890620123-collab/Server --limit 5
gh run view <run-id> --repo chl4890620123-collab/Server
```

## 이 저장소만 단독으로 배포하기 (Server 중앙 시스템을 쓰지 않을 때)

`Server` 저장소의 중앙 배포 시스템 없이 이 저장소 하나만 독립적으로 Docker로 띄우고 싶다면
(다른 서버, 다른 도메인 등) 이 저장소에 있는 `deploy/compose.yml` + `deploy/Caddyfile`을 그대로
씁니다 - PostgreSQL, AI, Backend, (선택) 공인 도메인용 Caddy를 이 저장소 안에서 전부 빌드합니다.

```bash
docker compose --env-file .env -f deploy/compose.yml up -d --build
# 공인 도메인 HTTPS까지 (Caddy):
docker compose --env-file .env -f deploy/compose.yml --profile https up -d --build
```

필요한 `.env` 값은 `.env.example`에 전부 설명되어 있습니다 (`HUB_JWT_SECRET`,
`HUB_ADMIN_SETUP_KEY`, `HUB_TLS_DOMAIN`, `DB_PASSWORD` 등).
