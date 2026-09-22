import { API_BASE, apiDelete, apiGet, apiPost, apiPut } from '@/api/client';
import type { ConnectorSyncState, ConnectorTargetsResponse, ConnectorType } from '@/api/types';

export const connectorsApi = {
  status: (projectId: number) => apiGet<ConnectorSyncState[]>(`/api/projects/${projectId}/connectors/status`),
  targets: (projectId: number, type: ConnectorType) =>
    apiGet<ConnectorTargetsResponse>(`/api/projects/${projectId}/connectors/${type.toLowerCase()}/targets`),
  disconnect: (projectId: number, type: ConnectorType) =>
    apiDelete<{ status: string }>(`/api/projects/${projectId}/connectors/${type.toLowerCase()}/link`),
  connect: (projectId: number, type: ConnectorType) =>
    apiPost<{ status: string }>(`/api/projects/${projectId}/connectors/${type.toLowerCase()}/link`),
  import: (projectId: number, type: ConnectorType, scope: string) =>
    apiPost<{ imported: number }>(`/api/projects/${projectId}/connectors/${type.toLowerCase()}/import`, { scope }),

  /** Server-initiated OAuth redirect - navigate the browser here directly, don't fetch() it. */
  authorizeUrl: (projectId: number, type: ConnectorType) =>
    type === 'GOOGLE_DRIVE'
      ? `${API_BASE}/api/projects/${projectId}/connectors/google/authorize`
      : `${API_BASE}/api/projects/${projectId}/connectors/${type.toLowerCase()}/authorize`,

  policy: () => apiGet<Record<ConnectorType, boolean>>('/api/connector-policy'),
  setPolicy: (type: ConnectorType, enabled: boolean) =>
    apiPut<{ status: string }>(`/api/connector-policy/${type}`, { enabled }),
};
