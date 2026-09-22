import { apiGet, apiPost } from '@/api/client';
import type { ChangeCompareResponse, ChangeEvidenceView, ChangeItemRow, DecisionRow, EvidenceView } from '@/api/types';

export const decisionsApi = {
  pending: (projectId: number) => apiGet<DecisionRow[]>(`/api/projects/${projectId}/review/decisions`),
  evidence: (decisionId: number) => apiGet<EvidenceView[]>(`/api/decisions/${decisionId}/evidence`),
  confirm: (decisionId: number) => apiPost<{ status: string }>(`/api/decisions/${decisionId}/confirm`),
  reject: (decisionId: number) => apiPost<{ status: string }>(`/api/decisions/${decisionId}/reject`),
};

export const changesApi = {
  list: (projectId: number) => apiGet<ChangeItemRow[]>(`/api/projects/${projectId}/changes`),
  pending: (projectId: number) => apiGet<ChangeItemRow[]>(`/api/projects/${projectId}/changes/review`),
  compare: (projectId: number, beforeVersionId: number, afterVersionId: number) =>
    apiPost<ChangeCompareResponse>(`/api/projects/${projectId}/changes`, { beforeVersionId, afterVersionId }),
  evidence: (projectId: number, itemId: number) =>
    apiGet<ChangeEvidenceView[]>(`/api/projects/${projectId}/changes/items/${itemId}/evidence`),
  confirm: (projectId: number, itemId: number) =>
    apiPost<{ status: string }>(`/api/projects/${projectId}/changes/items/${itemId}/confirm`),
  reject: (projectId: number, itemId: number) =>
    apiPost<{ status: string }>(`/api/projects/${projectId}/changes/items/${itemId}/reject`),
};
