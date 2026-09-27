# Hub Web (hub-web)

Hub의 현재 React 웹 UI입니다.

- React 18
- TypeScript
- Vite
- Tailwind CSS 4
- React Router

## 요구 사항

`package.json` 기준 Node.js 20 이상이 필요합니다. GitHub Actions와 Docker production build는 Node.js 22 계열을 사용합니다.

## 로컬 실행

```bash
npm install
npm run dev
```

개발 서버:

```text
http://localhost:5173
```

로컬 개발에서는 Vite가 `/api` 요청을 `http://localhost:8080`으로 프록시합니다. 브라우저 기준 same-origin 흐름을 유지해 cookie/CSRF 처리를 단순하게 합니다.

## 스크립트

| 명령 | 설명 |
| --- | --- |
| `npm run dev` | 개발 서버 실행 |
| `npm run build` | 타입 체크 후 프로덕션 빌드 |
| `npm run preview` | 빌드 결과 미리보기 |
| `npm run typecheck` | 타입 체크 |
| `npm run lint` | ESLint 실행 |

## 프로덕션 구조

현재 Hub 프로덕션은 frontend를 별도 웹 서버에 배포하지 않습니다.

```text
web/
 → Vite build
 → backend Docker build가 dist/를 Spring static resource에 복사
 → Spring Boot가 React SPA와 /api를 같은 origin에서 제공
```

따라서 현재 운영 기본값은 `VITE_API_BASE_URL`을 **비워 두는 것**입니다.

## 환경 변수

`web/.env.example`의 핵심 값:

```env
VITE_API_BASE_URL=
```

로컬 Vite 개발에서도 비워 두면 proxy를 사용합니다. 현재 same-origin production에서도 비워 둡니다.

별도 frontend/backend origin 구성을 의도적으로 만들 경우 CORS뿐 아니라 cookie SameSite/Secure, CSRF, OAuth callback까지 함께 재설계해야 하므로 현재 기본 배포 방식으로 취급하지 않습니다.

백엔드 없이 UI만 확인하는 개발용 mock/bypass 설정은 로컬 전용 env에서 사용하고 Git에 커밋하지 않습니다.

## 디렉터리 구조

```text
src/
  api/          API 클라이언트, endpoint, 타입, mock API
  components/   레이아웃·UI 프리미티브·피드백 컴포넌트
  features/     검색·할 일·회의·Connector 등 도메인 컴포넌트
  hooks/        공용 훅
  lib/          포맷터, 쿠키, 에러 유틸
  providers/    인증·테마 provider
  routes/       페이지 및 router
  stores/       zustand 전역 상태
  styles/       전역 CSS
```

## 반응형 UI

별도 모바일 앱은 없지만 웹 UI 자체는 반응형입니다. 예를 들어 작은 화면에서는 sidebar가 off-canvas drawer로 전환됩니다.
