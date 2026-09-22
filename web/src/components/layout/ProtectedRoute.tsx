import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useCurrentUser } from '@/hooks/useAuth';
import { LoadingBlock } from '@/components/ui/spinner';

export function ProtectedRoute() {
  const { data: user, isLoading, isError } = useCurrentUser();
  const location = useLocation();

  if (isLoading) return <LoadingBlock label="세션 확인 중..." />;

  if (isError || !user) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }

  if (user.mustChangePassword && location.pathname !== '/account') {
    return <Navigate to="/account?forcePasswordChange=1" replace />;
  }

  return <Outlet />;
}

export function AdminRoute() {
  const { data: user, isLoading } = useCurrentUser();
  if (isLoading) return <LoadingBlock />;
  if (user?.globalRole !== 'ADMIN') return <Navigate to="/" replace />;
  return <Outlet />;
}
