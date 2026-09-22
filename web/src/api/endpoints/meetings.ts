import { apiUpload } from '@/api/client';

export interface MeetingUploadResult {
  meetingId: number;
  jobId: number;
  status: string;
}

export const meetingsApi = {
  upload: (projectId: number, title: string, file: File | Blob, meetingAt?: string) => {
    const form = new FormData();
    form.append('file', file, file instanceof File ? file.name : 'recording.webm');
    const params = new URLSearchParams({ title });
    if (meetingAt) params.set('meetingAt', meetingAt);
    return apiUpload<MeetingUploadResult>(`/api/projects/${projectId}/meetings?${params.toString()}`, form);
  },
};
