import { apiGet, apiPost, apiPut } from '@/api/client';
import type { Project, ProjectMember } from '@/api/types';

export const projectsApi = {
  list: () => apiGet<Project[]>('/api/projects'),
  create: (name: string, description?: string) => apiPost<Project>('/api/projects', { name, description }),
  rename: (projectId: number, name: string, description?: string) =>
    apiPut<{ status: string }>(`/api/projects/${projectId}`, { name, description }),
  members: (projectId: number) => apiGet<ProjectMember[]>(`/api/projects/${projectId}/members`),
};
