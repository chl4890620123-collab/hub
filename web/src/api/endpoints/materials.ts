import { apiGet, apiPost } from '@/api/client';
import type { MaterialAskResponse, MaterialHit, TimelineEvent, WorkContextBundle } from '@/api/types';

export const materialsApi = {
  search: (projectId: number, q: string, offset = 0) =>
    apiGet<MaterialHit[]>(`/api/projects/${projectId}/materials/search?q=${encodeURIComponent(q)}&offset=${offset}`),
  ask: (projectId: number, question: string) =>
    apiPost<MaterialAskResponse>(`/api/projects/${projectId}/materials/ask`, { question }),
  topSearches: (projectId: number) =>
    apiGet<{ query_text: string; search_count: number }[]>(`/api/projects/${projectId}/search/top`),
  context: (projectId: number, q: string) =>
    apiGet<WorkContextBundle>(`/api/projects/${projectId}/context?q=${encodeURIComponent(q)}`),
  timeline: (projectId: number) => apiGet<TimelineEvent[]>(`/api/projects/${projectId}/timeline`),
};
