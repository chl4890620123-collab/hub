import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { NoProjectState } from '@/components/layout/NoProjectState';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input, Label, Textarea } from '@/components/ui/input';
import { LoadingBlock, EmptyState } from '@/components/ui/spinner';
import { Badge } from '@/components/ui/badge';
import { useCurrentProject } from '@/hooks/useProjects';
import { useCurrentUser, useIsAdmin } from '@/hooks/useAuth';
import { todosApi } from '@/api/endpoints/todos';
import { materialsApi } from '@/api/endpoints/materials';
import { documentsApi } from '@/api/endpoints/documents';
import { adminSignupApi, adminReassignmentApi, adminUsersApi } from '@/api/endpoints/admin';
import { connectorsApi } from '@/api/endpoints/connectors';
import { AnalysisResultPanel } from '@/features/jobs/AnalysisResultPanel';
import { AssigneeField } from '@/components/form/AssigneeField';
import { formatDate, formatDateTime, localMonth } from '@/lib/format';
import { toast } from '@/stores/toastStore';
import { errorMessage } from '@/lib/errors';

const TASK_STATUS_LABELS: Record<string, string> = { TODO: '시작 전', IN_PROGRESS: '진행 중', DONE: '완료', BLOCKED: '도움 필요' };
const TIMELINE_LABELS: Record<string, string> = {
  TODO_CREATED: '할 일 생성', TODO_CONFIRMED: '할 일 확정', TODO_STATUS: '상태 변경', TODO_COMPLETION_REQUESTED: '완료 승인 요청',
  TODO_COMPLETION_REJECTED: '완료 반려', TODO_HELP_REQUESTED: '도움 요청', TODO_DUPLICATE_MERGED: '중복 병합', DECISION_CONFIRMED: '결정 확정',
};

const CONNECTOR_LABELS: Record<string, string> = { GOOGLE_DRIVE: 'Google Drive', GITHUB: 'GitHub', SLACK: 'Slack', NOTION: 'Notion' };

function ConnectorSyncChips({ projectId }: { projectId: number }) {
  const { data: statuses } = useQuery({
    queryKey: ['connector-status', projectId],
    queryFn: () => connectorsApi.status(projectId),
  });
  const successes = (statuses ?? []).filter((s) => s.lastStatus === 'SUCCESS');

  if (successes.length === 0) return null;
  return (
    <div className="mb-4 flex flex-wrap gap-2">
      {successes.map((s) => (
        <span
          key={s.connectorType}
          className="rounded-full border border-ink-200 bg-white px-2.5 py-1 text-xs text-ink-500 dark:bg-ink-100"
        >
          {CONNECTOR_LABELS[s.connectorType] ?? s.connectorType} · 가져오기 완료
          {s.lastSyncedAt ? ` · 마지막 성공 ${formatDateTime(s.lastSyncedAt)}` : ''}
        </span>
      ))}
    </div>
  );
}

function AdminOverviewCards({ projectId }: { projectId: number }) {
  const { data: signups } = useQuery({ queryKey: ['admin-signups'], queryFn: adminSignupApi.list });
  const { data: reassignments } = useQuery({
    queryKey: ['admin-reassignments', projectId],
    queryFn: () => adminReassignmentApi.pending(projectId),
  });
  const { data: users } = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list });

  return (
    <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-3">
      <Card>
        <CardContent className="p-4">
          <p className="text-xs text-ink-500">대기 중인 가입 신청</p>
          <p className="mt-1 text-2xl font-bold text-ink-900">{signups?.length ?? '-'}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4">
          <p className="text-xs text-ink-500">대기 중인 재배정</p>
          <p className="mt-1 text-2xl font-bold text-ink-900">{reassignments?.length ?? '-'}</p>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-4">
          <p className="text-xs text-ink-500">전체 사용자 수</p>
          <p className="mt-1 text-2xl font-bold text-ink-900">{users?.length ?? '-'}</p>
        </CardContent>
      </Card>
    </div>
  );
}

function DashboardTodos({ projectId, userId }: { projectId: number; userId: number }) {
  const navigate = useNavigate();
  const now = new Date();
  const { data: todos, isLoading } = useQuery({
    queryKey: ['todos-month', projectId, now.getFullYear(), now.getMonth() + 1],
    queryFn: () => todosApi.month(projectId, now.getFullYear(), now.getMonth() + 1),
  });

  const todayKey = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
  const myTop5 = useMemo(() => {
    return (todos ?? [])
      .filter((t) => t.assigneeId === userId && t.taskStatus !== 'DONE' && t.dueDate != null && t.dueDate <= todayKey)
      .sort((a, b) => (a.dueDate ?? todayKey).localeCompare(b.dueDate ?? todayKey))
      .slice(0, 5);
  }, [todos, userId, todayKey]);

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>오늘 할 일</CardTitle>
          <Button variant="ghost" size="sm" onClick={() => navigate('/todos?view=calendar')}>
            캘린더에서 보기
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : myTop5.length === 0 ? (
          <EmptyState title="오늘까지 처리할 할 일이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-2">
            {myTop5.map((todo) => (
              <li key={todo.id} className="flex items-center justify-between gap-2 rounded-md border border-ink-100 px-3 py-2">
                <div>
                  <p className="text-sm font-medium text-ink-800">{todo.title}</p>
                  <p className="text-xs text-ink-400">{todo.dueDate && todo.dueDate < todayKey ? `기한 지남 · ${formatDate(todo.dueDate)}` : `오늘 · ${formatDate(todo.dueDate)}`}</p>
                </div>
                <Badge variant={todo.taskStatus === 'IN_PROGRESS' ? 'accent' : 'neutral'}>{TASK_STATUS_LABELS[todo.taskStatus] ?? todo.taskStatus}</Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function QuickManualNoteForm({ projectId }: { projectId: number }) {
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [assigneeId, setAssigneeId] = useState('');
  const [activeJobId, setActiveJobId] = useState<number | null>(null);
  const queryClient = useQueryClient();

  const submit = useMutation({
    mutationFn: () =>
      documentsApi.manual(projectId, {
        title,
        text,
        sourceDate: localMonth() + '-01',
        dueDate: dueDate || undefined,
        assigneeId: assigneeId ? Number(assigneeId) : undefined,
      }),
    onSuccess: (result) => {
      toast.success('회의 노트가 저장되었습니다. AI 분석이 진행됩니다.');
      setActiveJobId(result.jobId);
      setTitle('');
      setText('');
      setDueDate('');
      setAssigneeId('');
      queryClient.invalidateQueries({ queryKey: ['documents', projectId] });
    },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>빠른 회의 노트</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (!title.trim() || !text.trim()) return;
            submit.mutate();
          }}
        >
          <div>
            <Label htmlFor="quick-title">제목</Label>
            <Input id="quick-title" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 주간 스탠드업" />
          </div>
          <div>
            <Label htmlFor="quick-text">내용</Label>
            <Textarea
              id="quick-text"
              rows={4}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="회의 내용을 붙여넣으면 AI가 할 일/결정 사항을 자동으로 추출합니다."
            />
          </div>
          <div className="flex flex-wrap items-end gap-3">
            <div>
              <Label htmlFor="quick-due-date">완료 기한 (선택)</Label>
              <Input id="quick-due-date" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className="w-40" />
            </div>
            <AssigneeField projectId={projectId} value={assigneeId} onChange={setAssigneeId} />
          </div>
          <Button type="submit" disabled={submit.isPending} className="self-start">
            {submit.isPending ? '저장 중...' : '저장 및 분석 요청'}
          </Button>
        </form>
        {activeJobId && <AnalysisResultPanel jobId={activeJobId} projectId={projectId} />}
      </CardContent>
    </Card>
  );
}

function WorkflowTimeline({ projectId }: { projectId: number }) {
  const { data: events, isLoading } = useQuery({
    queryKey: ['timeline', projectId],
    queryFn: () => materialsApi.timeline(projectId),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>최근 활동</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : !events || events.length === 0 ? (
          <EmptyState title="아직 활동 기록이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-2">
            {events.slice(0, 10).map((event) => (
              <li key={event.id} className="flex items-start gap-3 border-b border-ink-100 pb-2 last:border-0">
                <Badge variant="outline" className="mt-0.5 shrink-0">
                  {TIMELINE_LABELS[event.eventType] ?? event.eventType}
                </Badge>
                <div className="min-w-0">
                  <p className="truncate text-sm text-ink-800">{event.title}</p>
                  <p className="text-xs text-ink-400">{formatDateTime(event.happenedAt)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const { currentProject } = useCurrentProject();
  const { data: user } = useCurrentUser();
  const isAdmin = useIsAdmin();

  if (!currentProject || !user) return <NoProjectState />;

  return (
    <div>
      <PageHeader title="대시보드" description={`${currentProject.name} 프로젝트 현황입니다.`} />
      <ConnectorSyncChips projectId={currentProject.id} />
      {isAdmin && <AdminOverviewCards projectId={currentProject.id} />}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <DashboardTodos projectId={currentProject.id} userId={user.id} />
        <QuickManualNoteForm projectId={currentProject.id} />
        <div className="lg:col-span-2">
          <WorkflowTimeline projectId={currentProject.id} />
        </div>
      </div>
    </div>
  );
}
