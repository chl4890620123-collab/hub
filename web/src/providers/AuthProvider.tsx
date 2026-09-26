import { createContext, useContext, type ReactNode } from 'react';
import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { meApi } from '@/api/endpoints/auth';
import type { User } from '@/api/types';
import { LoadingBlock } from '@/components/ui/spinner';
import { getMockUser, mockMode } from '@/api/mockApi';

type AuthQuery = UseQueryResult<User, Error>;

const AuthContext = createContext<AuthQuery | null>(null);

/**
 * Owns the single ['me'] query observer for the whole app. Every screen that needs the current
 * user reads it through useCurrentUser() below instead of calling useQuery(['me']) itself -
 * mounting a second independent observer for this same query reliably drove TanStack Query into
 * a request storm here (each observer's mount kept re-triggering the other's fetch in a tight
 * loop with no backoff, verified with a Playwright trace: hundreds of requests/second). A single
 * shared observer sidesteps that entirely, and is the simpler design regardless.
 *
 * Also seeds the XSRF-TOKEN cookie on first load: there is no server-rendered page anymore to do
 * that for us, so without this first GET the very first login submission would have no CSRF token
 * to send and would fail before ever reaching AuthController.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const query = useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      if (mockMode) {
        if (localStorage.getItem('hub.mock.logged-out') === 'true') {
          throw new Error('로그인이 필요합니다.');
        }
        return getMockUser();
      }
      return meApi.get();
    },
    retry: false,
  });

  if (query.isLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-ink-50">
        <LoadingBlock label="Hub를 불러오는 중..." />
      </div>
    );
  }

  return <AuthContext.Provider value={query}>{children}</AuthContext.Provider>;
}

export function useCurrentUser(): AuthQuery {
  const query = useContext(AuthContext);
  if (!query) throw new Error('useCurrentUser must be used within AuthProvider');
  return query;
}
