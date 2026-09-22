import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from '@/api/client';
import type { DocumentRow, DocumentVersionRow } from '@/api/types';

export interface UploadResult {
  versionId: number;
  jobId: number;
  status: string;
  todoId?: number;
  documentId?: number;
}

export const documentsApi = {
  list: (projectId: number) => apiGet<DocumentRow[]>(`/api/projects/${projectId}/documents`),
  versions: (documentId: number) => apiGet<DocumentVersionRow[]>(`/api/documents/${documentId}/versions`),
  version: (versionId: number) => apiGet<Record<string, unknown>>(`/api/versions/${versionId}`),
  chunk: (chunkId: number) => apiGet<Record<string, unknown>>(`/api/chunks/${chunkId}`),
  archive: (documentId: number) => apiDelete<{ status: string }>(`/api/documents/${documentId}`),

  upload: (
    projectId: number,
    file: File,
    options?: { sourceDate?: string; dueDate?: string; assigneeId?: number },
  ) => {
    const form = new FormData();
    form.append('file', file);
    const params = new URLSearchParams();
    if (options?.sourceDate) params.set('sourceDate', options.sourceDate);
    if (options?.dueDate) params.set('dueDate', options.dueDate);
    if (options?.assigneeId) params.set('assigneeId', String(options.assigneeId));
    const query = params.toString();
    return apiUpload<UploadResult>(`/api/projects/${projectId}/documents/upload${query ? `?${query}` : ''}`, form);
  },

  manual: (
    projectId: number,
    payload: { title: string; text: string; sourceDate?: string; dueDate?: string; assigneeId?: number },
  ) => apiPost<UploadResult>(`/api/projects/${projectId}/documents/manual`, payload),

  editManual: (projectId: number, documentId: number, title: string, text: string) =>
    apiPut<UploadResult>(`/api/projects/${projectId}/documents/${documentId}`, { title, text }),

  /** Read-only: proposes a revision from a meeting-transcript document, saves nothing. */
  reviseDraft: (projectId: number, documentId: number, meetingDocumentId: number) =>
    apiPost<{ revisedText: string }>(`/api/projects/${projectId}/documents/${documentId}/revise-draft`, {
      meetingDocumentId,
    }),

  analyze: (projectId: number, versionId: number, sourceDate?: string) =>
    apiPost<UploadResult>(
      `/api/projects/${projectId}/documents/${versionId}/analyze${sourceDate ? `?sourceDate=${sourceDate}` : ''}`,
    ),
};
