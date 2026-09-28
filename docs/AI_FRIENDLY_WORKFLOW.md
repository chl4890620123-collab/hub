# AI 친화적 업무 흐름 기반

`GET /api/projects/{projectId}/workflow/todos/{todoId}`는 기존 Hub 업무 기록을 읽기 전용으로 연결합니다. 인증된 프로젝트 구성원과 관리자만 조회하며, 다른 프로젝트의 TODO나 휴지통 TODO는 반환하지 않습니다.

| 구조 | Hub의 원본 | 의미 |
| --- | --- | --- |
| Resource URI | 프로젝트별 TODO, 회의, 사용자, 문서 버전, 근거 | `hub://projects/1/todos/7`처럼 프로젝트 범위의 안정적 식별자 |
| Relation | TODO의 회의 출처, 확정 담당자, 연결 근거 | `DERIVED_FROM`, `ASSIGNED_TO`, `SUPPORTED_BY` |
| Event | TODO에 연결된 timeline 이벤트 | 확인, 제출, 승인 등의 발생 이력 (최근 200건) |
| Knowledge | TODO에 연결된 evidence의 저장된 인용문 | 원본 문서가 변경되어도 근거 스냅샷 유지 |
| Agent | 현재 공식 상태에서 계산한 다음 행동 | 제안 정보이며 쓰기 권한이 없음 |

회의 분석으로 나온 TODO 후보에는 `ADMIN_REVIEW`, 관리자 확정 직후에는 `ASSIGNEE_START`, 진행 중에는 `ASSIGNEE_WORK_AND_SUBMIT`, 담당자 제출 후에는 `ADMIN_REVIEW_COMPLETION`, 완료 후에는 `NONE`을 반환합니다. 보류·차단·재배정 대기는 각각 상태 해소를 먼저 안내합니다. `ASSIGNED_TO` 관계는 확정된 현재 담당자에만 표시합니다. 기존 `/api/todos/{id}/confirm`, `/status`, `/request-completion`, `/approve-completion` 경로만 상태를 변경합니다. AI 제안은 담당자를 확정하거나 승인하지 않습니다.

회의 출처나 근거가 삭제되면 해당 관계는 생략됩니다. 다른 프로젝트 자료를 추론해서 연결하지 않습니다. 이 API는 기존 데이터의 투영으로, AKB 코드나 프로토콜 구현을 포함하지 않습니다.
