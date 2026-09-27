# API Contract Summary

이 문서는 현재 React 웹 앱이 실제로 사용하는 주요 API와 권한 경계를 요약합니다. 별도 모바일 API나 카메라 전용 API는 없습니다.

## 인증·계정

- `/api/auth/*`
- `/api/me*`
- `/api/admin/*`

전역 역할은 `ADMIN`과 `MEMBER` 두 가지입니다.

- `MEMBER`: 프로젝트 참여자이자 할 일 담당자
- `ADMIN`: 검토·승인·조직 관리 담당자

관리자는 할 일 담당자로 배정하지 않습니다.

## 프로젝트·구성원

- `GET /api/projects`
- `GET /api/projects/{projectId}/members`
- 프로젝트 생성/수정 및 구성원 추가·이동·삭제 관련 API

현재 프로젝트 구성원 목록은 활성 `MEMBER`만 담당자 후보로 반환합니다. 관리자 권한은 프로젝트 생성자나 구성원별 confirm 플래그가 아니라 현재 전역 `ADMIN` 역할을 기준으로 판단합니다.

## 문서

- `POST /api/projects/{projectId}/documents/upload`
- `POST /api/projects/{projectId}/documents/manual`
- 문서 분석·목록·버전·원문 열기 관련 API

이미지 파일도 일반 문서 업로드 경로를 사용합니다. 텍스트 추출이 충분하지 않은 이미지/스캔 문서는 local PaddleOCR fallback을 사용할 수 있습니다.

## 회의

- `POST /api/projects/{projectId}/meetings`

브라우저 녹음 Blob과 사용자가 선택하거나 드래그앤드롭한 음성 파일이 같은 처리 흐름으로 들어갑니다. STT 결과와 회의 transcript는 프로젝트 검색 자료에 포함됩니다.

## 처리 작업

긴 문서 분석·STT처럼 즉시 끝나지 않는 작업은 processing job으로 관리합니다.

- `GET /api/jobs/{jobId}`
- `GET /api/projects/{projectId}/jobs`
- `DELETE /api/jobs/{jobId}` — `FAILED` 기록만 삭제

실패 작업 기록 삭제는 처리 이력 삭제이며 원본 문서·회의 자체를 자동 삭제하지 않습니다.

## 검색·RAG

- `/api/projects/{projectId}/materials/search`
- `/api/projects/{projectId}/materials/ask`
- Evidence 조회 / 원문 열기 관련 API

검색은 파일명·메타데이터·lexical·semantic 후보를 결합하고, RAG 응답에서 반환된 Evidence가 실제 검색 근거와 연결되는지 서버가 다시 검증합니다.

## 할 일·검토

조회:

- `GET /api/projects/{projectId}/todos`
- `GET /api/projects/{projectId}/todos/undated`
- `GET /api/projects/{projectId}/todos/due-through`
- `GET /api/projects/{projectId}/todos/trash`
- `GET /api/projects/{projectId}/review/todos` — 관리자 전용
- `GET /api/todos/{todoId}/evidence`

관리자 검토:

- `POST /api/todos/{todoId}/confirm`
- `POST /api/projects/{projectId}/review/todos/bulk-confirm`
- `PATCH /api/todos/{todoId}` — 후보 수정
- `POST /api/todos/{todoId}/reject`
- `POST /api/todos/{todoId}/merge-duplicate`

담당자 본인 작업:

- `PATCH /api/todos/{todoId}/status`
- `POST /api/todos/{todoId}/request-help`
- `POST /api/todos/{todoId}/resolve-help`
- `POST /api/todos/{todoId}/request-completion`

관리자 완료 검토:

- `POST /api/todos/{todoId}/approve-completion`
- `POST /api/todos/{todoId}/reject-completion`

삭제:

- `POST /api/todos/{todoId}/delete`
- `POST /api/todos/{todoId}/restore`
- `DELETE /api/todos/{todoId}/permanent`

상태 변경·도움 요청·완료 제출은 확정된 일반 사용자 담당자 본인만 할 수 있습니다. 완료 승인 대기 중에는 제출 근거와 작업 상태가 바뀌지 않도록 서버에서 변경을 잠급니다.

## 할 일 첨부·직접 파일 전송

- `GET /api/todos/{todoId}/attachments`
- `POST /api/todos/{todoId}/attachments`
- `GET /api/projects/{projectId}/file-transfer-recipients`
- `GET /api/projects/{projectId}/file-transfers`
- `POST /api/projects/{projectId}/file-transfers`
- `GET /api/attachments/{attachmentId}/download`
- `PATCH /api/attachments/{attachmentId}`
- `DELETE /api/attachments/{attachmentId}`
- `DELETE /api/attachments/{attachmentId}/inbox`

할 일 제출 첨부는 해당 담당자만 추가할 수 있고 관리자는 검토를 위해 볼 수 있습니다. 일반 사용자끼리의 직접 파일 전송은 관리자 제출함과 별도입니다.

## 관리자 제출함

출장·외근·참고자료처럼 특정 할 일과 연결되지 않는 자료를 프로젝트 관리자에게 전달하는 경로입니다.

- `POST /api/projects/{projectId}/admin-submissions`
- `GET /api/projects/{projectId}/admin-submissions`
- `GET /api/admin-submissions/{submissionId}/download`
- `DELETE /api/admin-submissions/{submissionId}`

일반 사용자는 파일 또는 `http(s)` URL 중 하나 이상을 제출해야 합니다. 일반 사용자는 자기 제출물을 보고, 현재 관리자는 프로젝트 전체 제출물을 봅니다. 제출물이 특정 관리자 사용자 ID가 아니라 프로젝트에 귀속되므로 관리자 교체 후에도 이어서 확인할 수 있습니다.

## 결정·변경·이력

TODO 외에도 decisions / changes / timeline / revisions가 존재합니다. 검토·확정 동작은 관리자 권한을 요구하고, 일반 구성원은 허용된 범위에서 근거와 확정 결과를 조회합니다.

## 알림

- `GET /api/projects/{projectId}/notifications`

## Connector

지원 대상:

- Google Drive
- Slack
- GitHub
- Notion

Connector import는 원본 서비스를 임의 수정하는 경로가 아니라 Hub 검색 자료를 수집하는 읽기 중심 흐름입니다. 대량 수집은 백그라운드 처리와 provider별 페이지 단위 수집을 사용합니다.

## 내부 AI 서비스

Spring → FastAPI 내부 호출:

- `/api/v1/analyze`
- `/api/v1/rag`
- `/api/v1/changes`
- `/api/v1/embed`
- `/api/v1/stt`
- `/api/v1/ocr`

FastAPI는 AI/STT/embedding/OCR 실행을 담당하고, 인증·프로젝트 권한·업무 상태·Evidence 검증은 Spring이 소유합니다.
