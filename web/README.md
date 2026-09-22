# Hub Web (hub-web)

Context AI Hub 프론트엔드. React 18 + TypeScript + Vite + Tailwind CSS v4.

## 요구 사항

- Node.js 20 이상

## 실행

```bash
npm install
npm run dev
```

개발 서버: http://localhost:5173

`/api` 요청은 Vite 개발 프록시를 통해 `http://localhost:8080` (백엔드) 로 전달됩니다.
프록시 덕분에 브라우저 기준 same-origin 이 되어 쿠키 인증이 그대로 동작하고 CORS 설정이 필요 없습니다.
백엔드 주소가 다르면 `vite.config.ts` 의 `server.proxy` 를 수정하세요.

## 스크립트

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 실행 |
| `npm run build` | 타입 체크 후 프로덕션 빌드 (`dist/`) |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run typecheck` | 타입 체크만 수행 |
| `npm run lint` | ESLint 실행 |

## 환경 변수

저장소 정책상 실제 dotenv 파일은 커밋하지 않습니다 (`.env.example` 만 커밋). `web/.env.example` 을 복사해 사용하세요.

```bash
cp .env.example .env.development   # 개발: VITE_API_BASE_URL 를 비워 두면 개발 프록시 사용
cp .env.example .env.production    # 배포: 실제 백엔드 origin 입력
```

| 변수 | 설명 |
| --- | --- |
| `VITE_API_BASE_URL` | 백엔드 origin. 개발 시에는 비워 두어 Vite 프록시를 사용하고, 배포 시에는 실제 주소를 넣습니다. 백엔드의 `HUB_CORS_ALLOWED_ORIGINS` 에도 등록이 필요합니다. |
| `VITE_DEV_AUTH_BYPASS` | `.env.local` 에 `true` 로 두면 백엔드 없이 UI 만 확인할 수 있습니다. |

## 디렉터리 구조

```
src/
  api/          API 클라이언트, 엔드포인트별 함수, 타입, mock API
  components/   레이아웃 · UI 프리미티브 · 피드백 컴포넌트
  features/     도메인 기능 단위 컴포넌트 (검색, 할 일, 회의, 커넥터 등)
  hooks/        공용 훅
  lib/          포맷터, 쿠키, 에러 유틸
  providers/    인증 · 테마 프로바이더
  routes/       페이지 및 라우터 정의
  stores/       zustand 전역 상태
  styles/       전역 CSS
```
