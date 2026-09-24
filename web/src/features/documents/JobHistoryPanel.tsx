import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, LoadingBlock } from '@/components/ui/spinner';
import { jobsApi } from '@/api/endpoints/jobs';
import { formatDateTime } from '@/lib/format';

const JOB_TYPE_LABELS: Record<string, string> = {
  DOCUMENT_ANALYSIS: '문서 정리',
  MEETING_STT_ANALYSIS: '회의 음성 정리',
  CONNECTOR_IMPORT: '연결 서비스 자료 가져오기',
};

const JOB_STATUS_LABELS: Record<string, string> = {
  PENDING: '준비 중',
  RUNNING: '처리 중',
  SUCCESS: '완료',
  FAILED: '처리 실패',
};

export function JobHistoryPanel({ projectId }: { projectId: number }) {
  const { data: jobs, isLoading } = useQuery({
    queryKey: ['jobs-recent', projectId],
    queryFn: () => jobsApi.recent(projectId),
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>최근 AI 처리 기록</CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <LoadingBlock />
        ) : !jobs || jobs.length === 0 ? (
          <EmptyState title="최근 AI 처리 기록이 없습니다." />
        ) : (
          <ul className="flex flex-col gap-2">
            {jobs.slice(0, 20).map((job) => (
              <li key={job.id} className="rounded-md border border-ink-100 px-3 py-2 text-sm">
                <p className="font-medium text-ink-800">
                  {JOB_TYPE_LABELS[job.jobType] ?? 'AI 처리'} · {JOB_STATUS_LABELS[job.status] ?? '처리 중'}
                </p>
                <p className="text-xs text-ink-400">
                  진행 {job.progress || 0}% · {formatDateTime(job.updatedAt)}
                </p>
                {job.errorMessage && <p className="text-xs text-red-600">{job.errorMessage}</p>}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
