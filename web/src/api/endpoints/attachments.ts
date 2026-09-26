import { apiDelete, apiDownload, apiGet, apiPatch, apiUpload } from '@/api/client';
import type { AttachmentView, FileTransferRecipient } from '@/api/types';

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

  recipients: (projectId: number) => apiGet<FileTransferRecipient[]>(`/api/projects/${projectId}/file-transfer-recipients`),
  inbox: (projectId: number) => apiGet<AttachmentView[]>(`/api/projects/${projectId}/file-transfers`),
  send: (projectId: number, recipientId: number, file: File, note?: string) => {
    const form = new FormData();
    form.append('file', file);
    const params = new URLSearchParams({ recipientId: String(recipientId) });
    if (note) params.set('note', note);
    return apiUpload<{ id: number; status: string }>(`/api/projects/${projectId}/file-transfers?${params.toString()}`, form);
  },

  download: (attachmentId: number) => apiDownload(`/api/attachments/${attachmentId}/download`),
  update: (attachmentId: number, fileName: string, note?: string) => apiPatch<{ status: string }>(`/api/attachments/${attachmentId}`, { fileName, note: note ?? null }),
  delete: (attachmentId: number) => apiDelete<{ status: string }>(`/api/attachments/${attachmentId}`),
};
