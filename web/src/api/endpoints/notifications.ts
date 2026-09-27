import { apiGet } from '@/api/client';
import type { ProjectNotification } from '@/api/types';

export const notificationsApi = {
  list: (projectId: number) => apiGet<ProjectNotification[]>(`/api/projects/${projectId}/notifications`),
};
