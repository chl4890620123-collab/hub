import type {
  AuditLogRow,
  ChangeItemRow,
  ConnectorSyncState,
  ConnectorTargetsResponse,
  DecisionRow,
  DocumentRow,
  DocumentVersionRow,
  EvidenceView,
  FileTransferRecipient,
  MaterialAskResponse,
  MaterialHit,
  ProcessingJob,
  Project,
  ProjectMember,
  ReassignmentRequest,
  RevisionRow,
  SearchRule,
  SensitiveTerm,
  SignupApplication,
  TimelineEvent,
  TodoItem,
  User,
} from '@/api/types';

export const mockMode = import.meta.env.DEV && import.meta.env.VITE_DEV_AUTH_BYPASS === 'true';

const now = new Date();
const iso = (daysAgo = 0) => new Date(now.getTime() - daysAgo * 86_400_000).toISOString();
const date = (daysFromNow: number) => new Date(now.getTime() + daysFromNow * 86_400_000).toISOString().slice(0, 10);

export const mockUser: User = {
  id: 1,
  loginId: 'dev-user',
  email: 'dev@example.com',
  displayName: '이승현',
  companyName: 'Hub Demo',
  departmentName: '프로덕트팀',
  teamName: '플랫폼 스쿼드',
  jobTitle: '프로덕트 매니저',
  globalRole: 'ADMIN',
  accountStatus: 'ACTIVE',
  mustChangePassword: false,
  approvalStatus: 'APPROVED',
};

const projects: Project[] = [
  { id: 101, name: 'Atlas 리뉴얼', description: '고객용 업무 허브 리뉴얼 프로젝트', createdBy: 1, projectRole: 'ADMIN', canConfirm: true },
  { id: 202, name: 'Northstar 런칭', description: '신규 분석 기능의 베타 런칭 준비', createdBy: 1, projectRole: 'ADMIN', canConfirm: true },
];

const members: ProjectMember[] = [
  { id: 1, displayName: '이승현', loginId: 'dev-user', jobTitle: '프로덕트 매니저', projectRole: 'ADMIN', canConfirm: true },
  { id: 2, displayName: '이서윤', loginId: 'seoyun', jobTitle: '디자이너', projectRole: 'MEMBER', canConfirm: false },
  { id: 3, displayName: '박준호', loginId: 'junho', jobTitle: '프론트엔드 엔지니어', projectRole: 'MEMBER', canConfirm: false },
];

const todos: TodoItem[] = [
  { id: 1001, projectId: 101, title: '대시보드 빈 상태 문구 확정', description: '첫 방문 사용자의 다음 행동을 명확하게 안내한다.', assigneeId: 1, assigneeText: '이승현', assigneeSuggestionId: null, assigneeSuggestionText: null, dueDate: date(2), dueDateSuggestion: null, confidence: 'HIGH', reviewStatus: 'CONFIRMED', taskStatus: 'IN_PROGRESS', assignmentStatus: 'ACTIVE', possibleDuplicateOfId: null, duplicateReason: null, createdAt: iso(4), updatedAt: iso(1), googleCalendarEventId: null, pendingApproval: true, statusNote: null },
  { id: 1002, projectId: 101, title: '검색 결과 카드에 출처 배지 추가', description: '원문 유형과 최신 동기화 시간을 함께 노출한다.', assigneeId: 3, assigneeText: '박준호', assigneeSuggestionId: null, assigneeSuggestionText: null, dueDate: date(5), dueDateSuggestion: null, confidence: 'HIGH', reviewStatus: 'CONFIRMED', taskStatus: 'BLOCKED', assignmentStatus: 'ACTIVE', possibleDuplicateOfId: null, duplicateReason: null, createdAt: iso(3), updatedAt: iso(2), googleCalendarEventId: null, pendingApproval: false, statusNote: '디자인 시스템 배지 컴포넌트 확정이 필요합니다.' },
  { id: 1003, projectId: 101, title: '온보딩 인터뷰 3건 예약', description: '신규 팀원과 기존 사용자 그룹을 나눠 진행한다.', assigneeId: 1, assigneeText: '이승현', assigneeSuggestionId: null, assigneeSuggestionText: null, dueDate: null, dueDateSuggestion: null, confidence: 'MEDIUM', reviewStatus: 'CONFIRMED', taskStatus: 'TODO', assignmentStatus: 'ACTIVE', possibleDuplicateOfId: null, duplicateReason: null, createdAt: iso(6), updatedAt: iso(6), googleCalendarEventId: null, pendingApproval: false, statusNote: null },
  { id: 1004, projectId: 101, title: '회의록에서 추출된 예산 검토', description: '2분기 인프라 비용안을 재무팀과 확인한다.', assigneeId: null, assigneeText: null, assigneeSuggestionId: null, assigneeSuggestionText: '이승현', dueDate: date(8), dueDateSuggestion: date(8), confidence: 'MEDIUM', reviewStatus: 'AI_GENERATED', taskStatus: 'TODO', assignmentStatus: 'ACTIVE', possibleDuplicateOfId: null, duplicateReason: null, createdAt: iso(1), updatedAt: iso(1), googleCalendarEventId: null, pendingApproval: false, statusNote: null },
  { id: 2001, projectId: 202, title: '베타 사용자 초대 리스트 정리', description: '초기 사용성 테스트 그룹 20명을 확정한다.', assigneeId: 2, assigneeText: '이서윤', assigneeSuggestionId: null, assigneeSuggestionText: null, dueDate: date(3), dueDateSuggestion: null, confidence: 'HIGH', reviewStatus: 'CONFIRMED', taskStatus: 'IN_PROGRESS', assignmentStatus: 'ACTIVE', possibleDuplicateOfId: null, duplicateReason: null, createdAt: iso(5), updatedAt: iso(1), googleCalendarEventId: null, pendingApproval: false, statusNote: null },
  { id: 2002, projectId: 202, title: '런칭 체크리스트 최종 리뷰', description: '마케팅, CS, 기술 운영 항목을 한 번에 점검한다.', assigneeId: 1, assigneeText: '이승현', assigneeSuggestionId: null, assigneeSuggestionText: null, dueDate: date(7), dueDateSuggestion: null, confidence: 'HIGH', reviewStatus: 'CONFIRMED', taskStatus: 'TODO', assignmentStatus: 'ACTIVE', possibleDuplicateOfId: null, duplicateReason: null, createdAt: iso(2), updatedAt: iso(2), googleCalendarEventId: null, pendingApproval: false, statusNote: null },
];

const documents: DocumentRow[] = [
  { id: 301, original_name: '주간 제품 회의록 - 09월 2주차.md', source_type: 'MANUAL', source_identifier: null, archived: false, source_deleted: false, created_at: iso(1), latest_version: 2, has_original: true },
  { id: 302, original_name: 'Atlas 사용자 인터뷰 요약.pdf', source_type: 'GOOGLE_DRIVE', source_identifier: 'drive://atlas/interviews', archived: false, source_deleted: false, created_at: iso(4), latest_version: 1, has_original: true },
  { id: 303, original_name: 'Northstar 베타 런칭 플랜.docx', source_type: 'MANUAL', source_identifier: null, archived: false, source_deleted: false, created_at: iso(2), latest_version: 1, has_original: true },
  { id: 304, original_name: '온보딩 가이드 초안', source_type: 'MANUAL_TEXT', source_identifier: null, archived: false, source_deleted: false, created_at: iso(3), latest_version: 1, has_original: false },
  { id: 305, original_name: '09월 3주차 스프린트 회의', source_type: 'MEETING_TRANSCRIPT', source_identifier: null, archived: false, source_deleted: false, created_at: iso(1), latest_version: 1, has_original: false },
];

const hits: MaterialHit[] = [
  { evidenceId: 401, sourceType: 'HUB', sourceLabel: 'Hub 회의록', itemType: 'DOCUMENT', title: '주간 제품 회의록 - 09월 2주차', location: '2페이지', snippet: '검색 결과와 원문 연결 경험을 첫 번째 베타 범위에 포함한다.', author: '이승현', sourceUrl: null, sourceCreatedAt: iso(1), recommendationRank: 1, matchType: 'SEMANTIC', recommendationReason: '현재 프로젝트의 핵심 결정과 직접 연결됩니다.' },
  { evidenceId: 402, sourceType: 'GOOGLE_DRIVE', sourceLabel: 'Google Drive', itemType: 'DOCUMENT', title: 'Atlas 사용자 인터뷰 요약', location: '인사이트 04', snippet: '팀은 최신 상태와 다음 액션을 한 화면에서 보고 싶어 한다.', author: '이서윤', sourceUrl: 'https://drive.google.com', sourceCreatedAt: iso(4), recommendationRank: 2, matchType: 'KEYWORD', recommendationReason: '관련 키워드가 반복해서 등장합니다.' },
  { evidenceId: 403, sourceType: 'HUB', sourceLabel: 'Hub 문서', itemType: 'DOCUMENT', title: 'Northstar 베타 런칭 플랜', location: '문서 원문', snippet: '마케팅, CS, 기술 운영 항목을 한 번에 점검하는 런칭 계획입니다.', author: '이승현', sourceUrl: null, sourceCreatedAt: iso(2), recommendationRank: 3, matchType: 'KEYWORD', recommendationReason: '프로젝트 문서에서 일치하는 내용입니다.' },
];

const timeline: TimelineEvent[] = [
  { id: 501, projectId: 101, eventType: 'DOCUMENT_ADDED', title: '주간 제품 회의록이 추가되었습니다.', description: 'AI가 4개의 할 일 후보와 2개의 결정 사항을 발견했습니다.', happenedAt: iso(1), sourceType: 'HUB', sourceId: 301 },
  { id: 502, projectId: 101, eventType: 'TODO_CONFIRMED', title: '할 일 2건이 확정되었습니다.', description: '담당자와 기한이 지정되었습니다.', happenedAt: iso(2), sourceType: 'HUB', sourceId: 1001 },
  { id: 503, projectId: 202, eventType: 'CONNECTOR_SYNCED', title: 'GitHub 자료가 동기화되었습니다.', description: '최근 변경 18건을 가져왔습니다.', happenedAt: iso(1), sourceType: 'GITHUB', sourceId: null },
];

const reviewDecisions: DecisionRow[] = [
  { id: 601, statement: '첫 베타는 초대된 팀만 접근할 수 있도록 제한한다.', confidence: 'HIGH', review_status: 'AI_GENERATED', created_at: iso(1) },
  { id: 602, statement: '검색 결과에는 원문으로 이동할 수 있는 근거 링크를 제공한다.', confidence: 'MEDIUM', review_status: 'AI_GENERATED', created_at: iso(2) },
];

const reviewChanges: ChangeItemRow[] = [
  { id: 701, category: '범위', before_text: '전체 고객 대상 공개', after_text: '초대 팀 대상 베타 공개', reason: '운영 리스크를 줄이기 위해 단계적 공개로 변경', review_status: 'REVIEWING', created_at: iso(1) },
  { id: 702, category: '일정', before_text: '9월 20일', after_text: '9월 27일', reason: '사용성 테스트 일정을 반영', review_status: 'AI_GENERATED', created_at: iso(2) },
];

const users: User[] = [mockUser, { ...mockUser, id: 2, loginId: 'seoyun', email: 'seoyun@example.com', displayName: '이서윤', globalRole: 'MEMBER' }, { ...mockUser, id: 3, loginId: 'junho', email: 'junho@example.com', displayName: '박준호', globalRole: 'MEMBER' }];
const rules: SearchRule[] = [
  { id: 801, projectId: 101, name: '릴리즈 일정', aliases: ['출시', '런칭', '배포'], patterns: ['릴리즈', '일정'], targetFile: null, mode: 'SMART', priority: 100, active: true, managed: true },
  { id: 802, projectId: 202, name: '베타 피드백', aliases: ['사용성', '피드백'], patterns: ['베타', '리서치'], targetFile: null, mode: 'SMART', priority: 100, active: true, managed: true },
];
const terms: SensitiveTerm[] = [{ id: 901, term: '개인정보' }, { id: 902, term: '계약 금액' }];
const applications: SignupApplication[] = [{ id: 10001, loginId: 'minji', email: 'minji@example.com', displayName: '최민지', companyName: 'Hub Demo', departmentName: 'CS팀', teamName: null, jobTitle: 'CS 매니저', signupNote: '프로젝트 자료를 함께 검토하고 싶습니다.', requestedProjectId: 101, requestedProjectName: 'Atlas 리뉴얼', requestedRole: 'MEMBER', approvalStatus: 'PENDING', rejectionReason: null, createdAt: iso(1) }];

const projectIdFrom = (path: string) => Number(path.match(/projects\/(\d+)/)?.[1] ?? 101);
const projectTodos = (projectId: number) => todos.filter((todo) => todo.projectId === projectId);
const jsonBody = (opts: RequestInit) => typeof opts.body === 'string' ? JSON.parse(opts.body) as Record<string, unknown> : {};
const result = <T>(value: T) => Promise.resolve(value);

export function mockApiFetch<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const url = new URL(path, 'http://mock.local');
  const { pathname, searchParams } = url;
  const method = (opts.method ?? 'GET').toUpperCase();
  const projectId = projectIdFrom(path);

  if (pathname === '/api/me') return result(mockUser as T);
  if (pathname === '/api/projects' && method === 'GET') return result(projects as T);
  if (pathname === '/api/auth/signup/projects') return result(projects.map(({ id, name }) => ({ id, name })) as T);
  if (pathname.includes('/members') && method === 'GET') return result(members as T);
  if (pathname.endsWith('/review/todos')) return result(projectTodos(projectId).filter((todo) => todo.reviewStatus === 'AI_GENERATED') as T);
  if (pathname.endsWith('/review/decisions')) return result(reviewDecisions as T);
  if (pathname.endsWith('/changes/review')) return result(reviewChanges as T);
  if (pathname.includes('/todos/due-through')) {
    const through = searchParams.get('date') ?? '9999-12-31';
    return result(projectTodos(projectId).filter(
      (todo) => todo.reviewStatus === 'CONFIRMED' && todo.taskStatus !== 'DONE' && !!todo.dueDate && todo.dueDate <= through,
    ) as T);
  }
  if (pathname.includes('/todos/undated')) return result(projectTodos(projectId).filter((todo) => !todo.dueDate) as T);
  if (pathname.match(/\/todos$/) && method === 'GET') return result(projectTodos(projectId).filter((todo) => !todo.dueDate || todo.dueDate.startsWith(`${searchParams.get('year')}-${String(searchParams.get('month')).padStart(2, '0')}`)) as T);
  if (pathname.endsWith('/changes')) return result(reviewChanges as T);
  if (pathname.match(/\/documents$/)) return result(documents.filter((doc) => projectId === 101 ? doc.id !== 303 : doc.id === 303) as T);
  if (pathname.match(/\/documents\/\d+\/versions/)) return result([{ id: 3021, version_no: 2, sha256: 'mock-sha-3021', parse_status: 'DONE', summary: '사용자 인터뷰 핵심 인사이트와 후속 액션을 정리한 버전입니다.', created_at: iso(1) }, { id: 3020, version_no: 1, sha256: 'mock-sha-3020', parse_status: 'DONE', summary: '초기 인터뷰 메모입니다.', created_at: iso(4) }] as DocumentVersionRow[] as T);
  if (pathname.endsWith('/timeline')) return result(timeline.filter((event) => event.projectId === projectId) as T);
  if (pathname.endsWith('/materials/search')) {
    const query = (searchParams.get('q') ?? '').toLowerCase();
    const projectHits = projectId === 101 ? hits.filter((hit) => hit.evidenceId !== 403) : hits.filter((hit) => hit.evidenceId === 403);
    return result(projectHits.filter((hit) => `${hit.title} ${hit.snippet} ${hit.author ?? ''}`.toLowerCase().includes(query)) as T);
  }
  if (pathname.endsWith('/search/top')) return result([{ query_text: '베타 일정', search_count: 12 }, { query_text: '사용자 피드백', search_count: 8 }] as T);
  if (pathname.endsWith('/materials/ask')) return result({ answer: '현재 프로젝트에서는 초대 팀 대상 베타를 먼저 진행하고, 검색 결과와 원문 근거를 함께 제공하는 방향으로 정리되어 있습니다.', sources: hits } as MaterialAskResponse as T);
  if (pathname.endsWith('/context')) return result({ query: searchParams.get('q') ?? '', summary: '최근 회의와 문서에서 확인된 프로젝트의 핵심 맥락입니다. 베타 범위, 검색 근거, 사용자 인터뷰 후속 작업이 연결되어 있습니다.', sources: hits, todos: projectTodos(projectId), decisions: reviewDecisions, changes: reviewChanges, timeline } as T);
  if (pathname.endsWith('/review/todos') || pathname.includes('/evidence')) return result([{ id: 4101, versionId: 3021, chunkId: 1, quote: '다음 베타에서는 초대 팀을 대상으로 검색 근거 연결 경험을 검증한다.', contentHash: 'mock-hash', documentName: '주간 제품 회의록 - 09월 2주차.md', paragraphRef: 'p.2', pageNo: 2, meetingTitle: null, startMs: null, endMs: null, speaker: '이승현' }] as EvidenceView[] as T);
  if (pathname.endsWith('/connectors/status')) {
    const linked = JSON.parse(localStorage.getItem('hub.mock.connectors') ?? '[]') as string[];
    return result((['GITHUB', 'GOOGLE_DRIVE', 'SLACK', 'NOTION'] as const).map((connectorType, index): ConnectorSyncState => {
      const isLinked = linked.includes(connectorType) || index < 2;
      return { connectorType, externalScope: index === 0 ? 'hub-front' : '', lastSyncedAt: isLinked ? iso(index + 1) : null, lastStatus: isLinked ? 'SUCCESS' : null, lastError: null, lastImportedCount: isLinked ? (index === 0 ? 18 : 7) : 0 };
    }) as T);
  }
  if (pathname.endsWith('/connector-policy')) return result({ GITHUB: true, GOOGLE_DRIVE: true, SLACK: true, NOTION: true } as T);
  if (pathname.endsWith('/targets')) return result({ connected: true, linkedByUser: true, account: 'demo@hub.local', targets: [{ id: 'repo-hub-front', name: 'hub-front', description: '제품 허브 프론트엔드 저장소', url: 'https://github.com' }, { id: 'repo-design-system', name: 'design-system', description: '공용 컴포넌트와 토큰', url: 'https://github.com' }] } as ConnectorTargetsResponse as T);
  if (pathname.endsWith('/admin/users')) return result(users as T);
  if (pathname.endsWith('/signup-applications')) return result(applications as T);
  if (pathname.endsWith('/reassignments')) return result([{ id: 1101, project_id: projectId, old_assignee_id: 3, reason: '팀 이동으로 담당자 변경 필요', created_at: iso(2) }] as ReassignmentRequest[] as T);
  if (pathname.endsWith('/sensitive-terms')) return result(terms as T);
  if (pathname.endsWith('/search/rules')) return result(rules.filter((rule) => rule.projectId === projectId) as T);
  if (pathname.endsWith('/embedding-status')) return result({ status: 'READY', indexed: projectTodos(projectId).length + documents.length, pending: 0, lastIndexedAt: iso(1) } as T);
  if (pathname.endsWith('/admin/audit'))
    return result([
      { id: 1201, user_id: 1, display_name: mockUser.displayName, project_id: projectId, project_name: '데모 프로젝트', action: 'TODO_CONFIRMED', target_type: 'TODO', target_id: 1001, detail_json: null, created_at: iso(1) },
    ] as AuditLogRow[] as T);
  if (pathname.endsWith('/revisions'))
    return result([
      { id: 1301, entity_type: 'DOCUMENT', entity_id: 301, action: 'DOCUMENT_UPDATED', before_json: null, after_json: null, actor_name: mockUser.displayName, created_at: iso(1) },
    ] as RevisionRow[] as T);
  if (pathname.endsWith('/jobs')) return result([{ id: 1401, projectId, jobType: 'DOCUMENT_ANALYSIS', targetType: 'DOCUMENT', targetId: 301, status: 'SUCCESS', progress: 100, errorCode: null, errorMessage: null, resultJson: null, createdAt: iso(1), updatedAt: iso(1) }] as ProcessingJob[] as T);
  if (pathname.match(/\/jobs\/\d+$/)) return result({ id: 1401, projectId, jobType: 'DOCUMENT_ANALYSIS', targetType: 'DOCUMENT', targetId: 301, status: 'SUCCESS', progress: 100, errorCode: null, errorMessage: null, resultJson: null, createdAt: iso(1), updatedAt: iso(1) } as ProcessingJob as T);
  if (pathname.endsWith('/file-transfer-recipients')) {
    const recipients: FileTransferRecipient[] = users.map((user) => ({
      id: user.id,
      displayName: user.displayName,
      loginId: user.loginId,
      admin: user.globalRole === 'ADMIN',
    }));
    return result(recipients as T);
  }
  if (pathname.endsWith('/file-transfers')) return result([] as T);
  if (pathname.endsWith('/connector-policy')) return result({ GITHUB: true, GOOGLE_DRIVE: true, SLACK: true, NOTION: true } as T);
  if (pathname.endsWith('/check-login-id')) return result({ loginId: searchParams.get('loginId') ?? '', available: true, message: '사용할 수 있는 아이디입니다.' } as T);

  if (method !== 'GET') {
    const body = jsonBody(opts);
    const documentMatch = pathname.match(/^\/api\/documents\/(\d+)(?:\/(restore|permanent))?$/);
    if (documentMatch) {
      const documentId = Number(documentMatch[1]);
      const action = documentMatch[2];
      const index = documents.findIndex((doc) => doc.id === documentId);
      if (index >= 0) {
        if (action === 'permanent' && method === 'DELETE') {
          documents.splice(index, 1);
          return result({ status: 'DELETED' } as T);
        }
        if (action === 'restore' && method === 'POST') {
          documents[index] = { ...documents[index], archived: false };
          return result({ status: 'ACTIVE' } as T);
        }
        if (!action && method === 'DELETE') {
          documents[index] = { ...documents[index], archived: true };
          return result({ status: 'ARCHIVED' } as T);
        }
      }
    }
    if (pathname.includes('/connectors/') && pathname.endsWith('/link')) {
      const connectorType = pathname.split('/').at(-2)?.toUpperCase() ?? '';
      const linked = JSON.parse(localStorage.getItem('hub.mock.connectors') ?? '[]') as string[];
      const next = method === 'DELETE' ? linked.filter((item) => item !== connectorType) : [...new Set([...linked, connectorType])];
      localStorage.setItem('hub.mock.connectors', JSON.stringify(next));
      return result({ status: 'SUCCESS' } as T);
    }
    if (pathname.includes('/documents/manual')) return result({ versionId: 3021, documentId: 301, jobId: 1401, status: 'SUCCESS' } as T);
    if (pathname.includes('/revise-draft')) return result({ revisedText: '목데이터 기준 - 회의 내용을 반영한 초안입니다.' } as T);
    if (pathname.includes('/documents/upload') || pathname.includes('/meetings')) return result({ versionId: 3021, meetingId: 501, documentId: 301, jobId: 1401, status: 'SUCCESS' } as T);
    if (pathname.endsWith('/materials/ask')) return result({ answer: '목데이터 기준으로 연결된 답변입니다.', sources: hits } as T);
    if (pathname.includes('/search/test')) return result({ query: searchParams.get('q') ?? '', matchedRule: rules[0], results: hits } as T);
    if (pathname.includes('/signup') || pathname.includes('/login') || pathname.includes('/logout')) return result({ status: 'SUCCESS', message: '목데이터 작업이 완료되었습니다.', user: mockUser } as T);
    if (pathname.includes('/version') || pathname.includes('/embedding')) return result({ status: 'SUCCESS', reindexed: 12 } as T);
    void body;
    return result({ status: 'SUCCESS', imported: 6, reassignmentCount: 0, results: {} } as T);
  }

  return result([] as T);
}
