import { useJobPolling } from '@/hooks/useJobPolling';
import { Spinner } from '@/components/ui/spinner';
import { Badge } from '@/components/ui/badge';

export function JobStatusPanel({ jobId }: { jobId: number | null }) {
  const { data: job } = useJobPolling(jobId);
  if (!jobId || !job) return null;

  return (
    <div className="flex items-center gap-2 rounded-md border border-ink-200 bg-white px-3 py-2 text-sm dark:bg-ink-100">
      {job.status === 'SUCCESS' ? (
        <Badge variant="accent">완료</Badge>
      ) : job.status === 'FAILED' ? (
        <Badge variant="danger">실패</Badge>
      ) : (
        <>
          <Spinner className="h-3.5 w-3.5" />
          <Badge variant="outline">{job.status}</Badge>
        </>
      )}
      <span className="text-ink-500">{job.jobType}</span>
      {job.status === 'FAILED' && job.errorMessage && <span className="text-red-500">{job.errorMessage}</span>}
    </div>
  );
}
