# API Contract Summary

이 문서는 웹 클라이언트가 사용하는 주요 경로만 요약합니다. 별도 모바일 API와 카메라 전용 API는 없습니다.

## Auth / account
- `/api/auth/*`
- `/api/me*`
- `/api/admin/*`

## Documents
- `POST /api/projects/{projectId}/documents/upload?sourceDate=YYYY-MM-DD`
- `POST /api/projects/{projectId}/documents/manual`
- `POST /api/projects/{projectId}/documents/{versionId}/analyze`

이미지 파일도 일반 `documents/upload`로 전송합니다. 서버가 필요할 때 local OCR fallback을 사용합니다.

## Meetings
- `POST /api/projects/{projectId}/meetings`

웹 녹음 Blob과 사용자가 선택한 음성 파일이 같은 endpoint를 사용합니다. STT 후 회의 transcript는 일반 통합 검색에 포함됩니다.

## Search / RAG
- `/api/projects/{projectId}/materials/search`
- `/api/projects/{projectId}/materials/ask`
- Evidence/open-original 관련 조회 API

## Work output
- TODO / decisions / changes / timeline / revisions / project memory

## Connectors
- Google Drive / Slack / GitHub / Notion read-only import

## Internal AI service
Spring → FastAPI 내부 호출:
- `/api/v1/analyze`
- `/api/v1/rag`
- `/api/v1/changes`
- `/api/v1/embed`
- `/api/v1/stt`
- `/api/v1/ocr` (업로드된 이미지/스캔 문서 fallback 전용)
