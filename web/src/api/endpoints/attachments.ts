import { apiDelete, apiDownload, apiGet, apiPatch, apiUpload } from '@/api/client';
import type { AdminSubmission, AttachmentView, FileTransferRecipient } from '@/api/types';

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

  adminSubmissions: (projectId: number) => apiGet<AdminSubmission[]>(`/api/projects/${projectId}/admin-submissions`),
  submitToAdmin: (projectId: number, title: string, url?: string, note?: string, file?: File | null) => {
    const form = new FormData();
    form.append('title', title);
    if (url?.trim()) form.append('url', url.trim());
    if (note?.trim()) form.append('note', note.trim());
    if (file) form.append('file', file);
    return apiUpload<{ id: number; status: string }>(`/api/projects/${projectId}/admin-submissions`, form);
  },
  downloadAdminSubmission: (submissionId: number) => apiDownload(`/api/admin-submissions/${submissionId}/download`),
  deleteAdminSubmission: (submissionId: number) => apiDelete<{ status: string }>(`/api/admin-submissions/${submissionId}`),

  download: (attachmentId: number) => apiDownload(`/api/attachments/${attachmentId}/download`),
  update: (attachmentId: number, fileName: string, note?: string) => apiPatch<{ status: string }>(`/api/attachments/${attachmentId}`, { fileName, note: note ?? null }),
  hideFromInbox: (attachmentId: number) => apiDelete<{ status: string }>(`/api/attachments/${attachmentId}/inbox`),
  delete: (attachmentId: number) => apiDelete<{ status: string }>(`/api/attachments/${attachmentId}`),
};
