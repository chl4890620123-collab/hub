import { useQuery } from '@tanstack/react-query';
import { jobsApi } from '@/api/endpoints/jobs';
import type { ProcessingJob } from '@/api/types';

const DONE_STATUSES = new Set(['SUCCESS', 'FAILED']);

/** Polls a background AI/STT job (upload, meeting, manual analysis) until it finishes. */
export function useJobPolling(jobId: number | null) {
  return useQuery<ProcessingJob>({
    queryKey: ['job', jobId],
    queryFn: () => jobsApi.get(jobId as number),
    enabled: jobId != null,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && DONE_STATUSES.has(status) ? false : 1500;
    },
  });
}
