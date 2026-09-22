import { useMutation, useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/api/endpoints/auth';
import { useCurrentUser } from '@/providers/AuthProvider';

export { useCurrentUser };

export function useIsAdmin(): boolean {
  const { data } = useCurrentUser();
  return data?.globalRole === 'ADMIN';
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ identifier, password }: { identifier: string; password: string }) =>
      authApi.login(identifier, password),
    onSuccess: (data) => {
      if (mockMode()) localStorage.removeItem('hub.mock.logged-out');
      queryClient.setQueryData(['me'], data.user);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      if (mockMode()) localStorage.setItem('hub.mock.logged-out', 'true');
      queryClient.clear();
      window.location.replace('/login');
    },
  });
}

function mockMode(): boolean {
  return import.meta.env.DEV && import.meta.env.VITE_DEV_AUTH_BYPASS === 'true';
}
