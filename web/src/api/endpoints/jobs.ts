import { apiGet } from '@/api/client';
import type { ProcessingJob } from '@/api/types';

export const jobsApi = {
  get: (jobId: number) => apiGet<ProcessingJob>(`/api/jobs/${jobId}`),
  recent: (projectId: number) => apiGet<ProcessingJob[]>(`/api/projects/${projectId}/jobs`),
};
