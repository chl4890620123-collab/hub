import { apiDelete, apiDownload, apiGet, apiUpload } from '@/api/client';
import type { AttachmentView } from '@/api/types';

export const attachmentsApi = {
  listForTodo: (todoId: number) => apiGet<AttachmentView[]>(`/api/todos/${todoId}/attachments`),
  attachToTodo: (todoId: number, file: File, note?: string) => {
    const form = new FormData();
    form.append('file', file);
    return apiUpload<{ id: number; status: string }>(
      `/api/todos/${todoId}/attachments${note ? `?note=${encodeURIComponent(note)}` : ''}`,
      form,
    );
  },

  inbox: (projectId: number) => apiGet<AttachmentView[]>(`/api/projects/${projectId}/file-transfers`),
  send: (projectId: number, recipientId: number, file: File, note?: string) => {
    const form = new FormData();
    form.append('file', file);
    const params = new URLSearchParams({ recipientId: String(recipientId) });
    if (note) params.set('note', note);
    return apiUpload<{ id: number; status: string }>(`/api/projects/${projectId}/file-transfers?${params.toString()}`, form);
  },

  download: (attachmentId: number) => apiDownload(`/api/attachments/${attachmentId}/download`),
  delete: (attachmentId: number) => apiDelete<{ status: string }>(`/api/attachments/${attachmentId}`),
};
