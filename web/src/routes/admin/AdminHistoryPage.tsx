import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { useCurrentProject } from '@/hooks/useProjects';
import { adminHistoryApi } from '@/api/endpoints/admin';
import type { AuditLogRow, RevisionRow } from '@/api/types';
import { formatDateTime } from '@/lib/format';
import { usePagination, PaginationControls } from '@/components/layout/Pagination';

const EVENT_TYPE_LABELS: Record<string, string> = {
  DOCUMENT_IMPORTED: '자료 가져옴',
  DOCUMENT_UPDATED: '자료 새 내용 등록',
  DOCUMENT_ARCHIVE: '자료 보관',
  DOCUMENT_RESTORE: '자료 복원',
  DOCUMENT_DELETE: '자료 영구 삭제',
  DOCUMENT_CHANGED: '자료 변경 확인',
  MEETING_UPLOADED: '회의 녹음 등록',
  MEETING_TRANSCRIBED: '회의 음성을 글로 변환',
  TODO_CREATED: '할 일 후보 생성',
  TODO_CONFIRMED: '할 일 확정',
  TODO_STATUS: '할 일 상태 변경',
  TODO_DUPLICATE_MERGED: '비슷한 할 일 내용 합침',
  DECISION_CANDIDATE: '결정 후보 생성',
  DECISION_CONFIRMED: '결정 확정',
  CONNECTOR_IMPORT: '연결 서비스 자료 가져옴',
  LOCAL_PC_IMPORT: 'PC 자료 가져옴',
  PROJECT_MEMBER_JOIN: '프로젝트에 사람 추가',
  PROJECT_MEMBER_LEAVE: '프로젝트에서 사람 제외',
  PROJECT_MOVE: '프로젝트 이동',
  SEARCH_RULE_CREATE: '기준 자료 추가',
  SEARCH_RULE_UPDATE: '기준 자료 수정',
  SEARCH_RULE_DELETE: '기준 자료 삭제',
  USER_PROFILE_UPDATE: '프로필 수정',
  USER_ACCOUNT_STATUS: '사용 상태 변경',
  USER_ROLE_CHANGE: '관리자 여부 변경',
  TODO_COMPLETION_REQUESTED: '할 일 완료 요청',
  TODO_COMPLETION_REJECTED: '할 일 완료 반려',
  TODO_HELP_REQUESTED: '할 일 도움 요청',
  COMPLETION_APPROVED: '완료 승인',
  COMPLETION_REJECTED: '완료 반려',
};

const ENTITY_TYPE_LABELS: Record<string, string> = {
  TODO: '할 일',
  DECISION: '결정',
  CHANGE_ITEM: '변경 내용',
  DOCUMENT: '자료',
  DOCUMENT_VERSION: '자료 버전',
  USER: '사용자',
  PROJECT: '프로젝트',
  SEARCH_RULE: '기준 자료 설정',
  MEETING: '회의',
};

function eventTypeLabel(type: string | null): string {
  if (!type) return '기록';
  return EVENT_TYPE_LABELS[type] ?? '기타 활동';
}

function entityTypeLabel(type: string | null): string {
  if (!type) return '기록';
  return ENTITY_TYPE_LABELS[type] ?? '기록';
}

function RevisionItem({ row }: { row: RevisionRow }) {
  return (
    <li className="rounded-md border border-ink-100 px-3 py-2 text-sm">
      <p className="font-medium text-ink-800">
        {entityTypeLabel(row.entity_type)} · {eventTypeLabel(row.action)}
      </p>
      <p className="text-xs text-ink-400">
        {row.actor_name} · {formatDateTime(row.created_at)}
      </p>
      {(row.before_json || row.after_json) && (
        <details className="mt-1 text-xs">
          <summary className="cursor-pointer text-ink-500">자세한 변경 내용 보기</summary>
          <div className="mt-1 flex flex-col gap-1">
            {row.before_json && (
              <pre className="overflow-x-auto whitespace-pre-wrap rounded bg-ink-50 p-2 text-ink-600">변경 전{'\n'}{row.before_json}</pre>
            )}
            {row.after_json && (
              <pre className="overflow-x-auto whitespace-pre-wrap rounded bg-ink-50 p-2 text-ink-600">변경 후{'\n'}{row.after_json}</pre>
            )}
          </div>
        </details>
      )}
    </li>
  );
}

function AuditItem({ row }: { row: AuditLogRow }) {
  return (
    <li className="rounded-md border border-ink-100 px-3 py-2 text-sm">
      <p className="font-medium text-ink-800">
        {eventTypeLabel(row.action)} · {entityTypeLabel(row.target_type)}
      </p>
      <p className="text-xs text-ink-400">
        {row.display_name || '시스템 자동 처리'} · {row.project_name || '전체 조직'} · {formatDateTime(row.created_at)}
      </p>
    </li>
  );
}

export function AdminHistoryPage() {
  const { currentProject } = useCurrentProject();
  const { data: revisions, isLoading: loadingRevisions } = useQuery({
    queryKey: ['revisions', currentProject?.id],
    queryFn: () => adminHistoryApi.revisions(currentProject!.id),
    enabled: !!currentProject,
  });
  const { data: auditLog, isLoading: loadingAudit } = useQuery({ queryKey: ['audit-log'], queryFn: adminHistoryApi.audit });

  const revisionPages = usePagination(revisions ?? []);
  const auditPages = usePagination(auditLog ?? []);

  if (!currentProject) return <NoProjectState />;

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>수정 이력</CardTitle>
        </CardHeader>
        <CardContent>
          {loadingRevisions ? (
            <LoadingBlock />
          ) : !revisions || revisions.length === 0 ? (
            <EmptyState title="아직 수정 이력이 없습니다." />
          ) : (
            <>
              <ul className="flex flex-col gap-2">
                {revisionPages.pageItems.map((row) => (
                  <RevisionItem key={row.id} row={row} />
                ))}
              </ul>
              <PaginationControls
                page={revisionPages.page}
                totalPages={revisionPages.totalPages}
                pageSize={revisionPages.pageSize}
                onPageChange={revisionPages.setPage}
                onPageSizeChange={revisionPages.setPageSize}
                totalCount={revisions.length}
              />
            </>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>바뀐 내용 기록 (전체 회사)</CardTitle>
        </CardHeader>
        <CardContent>
          {loadingAudit ? (
            <LoadingBlock />
          ) : !auditLog || auditLog.length === 0 ? (
            <EmptyState title="아직 바뀐 내용 기록이 없습니다." />
          ) : (
            <>
              <ul className="flex flex-col gap-2">
                {auditPages.pageItems.map((row) => (
                  <AuditItem key={row.id} row={row} />
                ))}
              </ul>
              <PaginationControls
                page={auditPages.page}
                totalPages={auditPages.totalPages}
                pageSize={auditPages.pageSize}
                onPageChange={auditPages.setPage}
                onPageSizeChange={auditPages.setPageSize}
                totalCount={auditLog.length}
              />
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
