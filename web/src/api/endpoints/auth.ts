import { apiGet, apiPatch, apiPost } from '@/api/client';
import type { GlobalRole, OrganizationDepartment, OrganizationTeam, User } from '@/api/types';

export interface LoginResponse {
  user: User;
  accessExpiresAt: string;
}

export const authApi = {
  login: (identifier: string, password: string) =>
    apiPost<LoginResponse>('/api/auth/login', { identifier, password }),

  logout: () => apiPost<{ status: string }>('/api/auth/logout'),

  changePassword: (currentPassword: string, newPassword: string) =>
    apiPost<{ status: string; message: string }>('/api/auth/password', { currentPassword, newPassword }),

  checkLoginId: (loginId: string) =>
    apiGet<{ loginId: string; available: boolean; message: string }>(
      `/api/auth/check-login-id?loginId=${encodeURIComponent(loginId)}`,
    ),

  signupOrganization: () => apiGet<{ departments: OrganizationDepartment[]; teams: OrganizationTeam[] }>('/api/auth/signup/organization'),

  signupProjects: () =>
    apiGet<{ id: number; name: string; departmentName: string | null; teamName: string | null }[]>('/api/auth/signup/projects'),

  signupMember: (payload: {
    loginId: string;
    email: string;
    password: string;
    displayName: string;
    companyName: string | null;
    departmentName: string | null;
    teamName: string | null;
    departmentId?: number | null;
    teamId?: number | null;
    requestedProjectId: number | null;
    jobTitle: string | null;
    signupNote: string | null;
    privacyConsent: boolean;
  }) => apiPost<{ status: string; requestedRole: GlobalRole; reopened: boolean; message: string }>('/api/auth/signup/member', payload),

  signupAdmin: (payload: {
    loginId: string;
    email: string;
    password: string;
    displayName: string;
    companyName: string | null;
    departmentName: string | null;
    teamName: string | null;
    departmentId?: number | null;
    teamId?: number | null;
    privacyConsent: boolean;
  }) =>
    apiPost<{ status: string; requestedRole: GlobalRole; reopened: boolean; firstAdminCreated: boolean; message: string }>(
      '/api/auth/signup/admin',
      payload,
    ),
};

export const meApi = {
  get: () => apiGet<User>('/api/me'),
  updateProfile: (payload: { jobTitle?: string | null }) =>
    apiPatch<User>('/api/me/profile', payload),
  withdraw: (currentPassword: string, reason?: string) =>
    apiPost<{ status: string; reassignmentCount: number }>('/api/me/withdraw', { currentPassword, reason }),
};
