import { useJobPolling } from '@/hooks/useJobPolling';
import { Spinner } from '@/components/ui/spinner';
import { Badge } from '@/components/ui/badge';

const JOB_STATUS_LABELS: Record<string, string> = { PENDING: '준비 중', RUNNING: '처리 중', SUCCESS: '완료', FAILED: '처리 실패' };

export function JobStatusPanel({ jobId, label = 'AI가 자료를 확인하고 있습니다' }: { jobId: number | null; label?: string }) {
  const { data: job } = useJobPolling(jobId);
  if (!jobId || !job) return null;

  const isTerminal = job.status === 'SUCCESS' || job.status === 'FAILED';

  return (
    <div className="flex flex-col gap-1 rounded-md border border-ink-200 bg-white px-3 py-2 text-sm dark:bg-ink-100">
      <div className="flex items-center gap-2">
        {job.status === 'SUCCESS' ? (
          <Badge variant="accent">완료</Badge>
        ) : job.status === 'FAILED' ? (
          <Badge variant="danger">실패</Badge>
        ) : (
          <Spinner className="h-3.5 w-3.5" />
        )}
        <span className="font-medium text-ink-700">{label}</span>
        <span className="text-ink-400">
          {JOB_STATUS_LABELS[job.status] ?? job.status} · {job.progress || 0}%
        </span>
      </div>
      {!isTerminal && (
        <div className="h-1 overflow-hidden rounded-full bg-ink-100">
          <div className="h-full rounded-full bg-accent-500 transition-all" style={{ width: `${job.progress || 0}%` }} />
        </div>
      )}
      {job.status === 'FAILED' && job.errorMessage && <span className="text-red-500">처리 중 문제가 생겼습니다. {job.errorMessage}</span>}
    </div>
  );
}
