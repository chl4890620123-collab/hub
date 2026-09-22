import { Suspense, useEffect } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Sidebar } from '@/components/layout/Sidebar';
import { Topbar } from '@/components/layout/Topbar';
import { Toaster } from '@/components/feedback/Toaster';
import { CommandPaletteSearch } from '@/features/global-search/CommandPaletteSearch';
import { EvidenceViewerDialog } from '@/features/evidence/EvidenceViewerDialog';
import { AUTH_EXPIRED_EVENT } from '@/api/client';
import { LoadingBlock } from '@/components/ui/spinner';

export function AppLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  useEffect(() => {
    function onAuthExpired() {
      queryClient.clear();
      navigate('/login', { replace: true });
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, onAuthExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, onAuthExpired);
  }, [navigate, queryClient]);

  return (
    <div className="flex h-screen w-screen overflow-hidden">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <main className="flex-1 overflow-y-auto bg-ink-50 p-6">
          <Suspense fallback={<LoadingBlock />}>
            <Outlet />
          </Suspense>
        </main>
      </div>
      <Toaster />
      <CommandPaletteSearch />
      <EvidenceViewerDialog />
    </div>
  );
}
