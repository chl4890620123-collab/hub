import { apiDelete, apiDownload, apiGet, apiPost, apiPut, apiUpload } from '@/api/client';
import type { DocumentRow, DocumentVersionRow } from '@/api/types';

export interface UploadResult {
  versionId: number;
  jobId?: number;
  status: string;
  todoId?: number;
  documentId?: number;
}

export const documentsApi = {
  list: (projectId: number) => apiGet<DocumentRow[]>(`/api/projects/${projectId}/documents`),
  meetingTranscripts: (projectId: number) => apiGet<DocumentRow[]>(`/api/projects/${projectId}/documents/meeting-transcripts`),
  versions: (documentId: number) => apiGet<DocumentVersionRow[]>(`/api/documents/${documentId}/versions`),
  version: (versionId: number) => apiGet<Record<string, unknown>>(`/api/versions/${versionId}`),
  chunk: (chunkId: number) => apiGet<Record<string, unknown>>(`/api/chunks/${chunkId}`),
  archive: (documentId: number) => apiDelete<{ status: string }>(`/api/documents/${documentId}`),
  restore: (documentId: number) => apiPost<{ status: string }>(`/api/documents/${documentId}/restore`),
  deletePermanently: (documentId: number) => apiDelete<{ status: string }>(`/api/documents/${documentId}/permanent`),
  download: (documentId: number) => apiDownload(`/api/documents/${documentId}/download`),

  upload: (projectId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return apiUpload<UploadResult>(`/api/projects/${projectId}/documents/upload`, form);
  },

  manual: (
    projectId: number,
    payload: { title: string; text: string; sourceDate?: string; dueDate?: string; assigneeId?: number },
  ) => apiPost<UploadResult>(`/api/projects/${projectId}/documents/manual`, payload),

  edit: (projectId: number, documentId: number, title: string, text: string) =>
    apiPut<UploadResult>(`/api/projects/${projectId}/documents/${documentId}`, { title, text }),

  /** Read-only: proposes a revision from a meeting-transcript document, saves nothing. */
  reviseDraft: (projectId: number, documentId: number, meetingDocumentId: number) =>
    apiPost<{ revisedText: string }>(`/api/projects/${projectId}/documents/${documentId}/revise-draft`, {
      meetingDocumentId,
    }),
  reviseDraftJob: (projectId: number, documentId: number, meetingDocumentId: number) =>
    apiPost<{ jobId: number; status: string }>(`/api/projects/${projectId}/documents/${documentId}/revise-draft-job`, {
      meetingDocumentId,
    }),

  analyze: (projectId: number, versionId: number, sourceDate?: string, force = false) => {
    const params = new URLSearchParams();
    if (sourceDate) params.set('sourceDate', sourceDate);
    if (force) params.set('force', 'true');
    const suffix = params.toString() ? `?${params.toString()}` : '';
    return apiPost<UploadResult>(`/api/projects/${projectId}/documents/${versionId}/analyze${suffix}`);
  },
};
