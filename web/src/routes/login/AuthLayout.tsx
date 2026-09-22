import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import { LoadingBlock } from '@/components/ui/spinner';

export function AuthLayout() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-shell-900 px-4">
      <div className="grid w-full max-w-4xl grid-cols-1 overflow-hidden rounded-xl bg-white shadow-2xl dark:bg-ink-100 md:grid-cols-2">
        <div className="hidden flex-col justify-center bg-gradient-to-br from-shell-900 to-accent-700 p-10 text-white md:flex">
          <h1 className="mb-3 text-headline">Hub</h1>
          <p className="text-sm text-white/80">
            사내 자료 통합검색, RAG 기반 질의응답, 할 일·결정·변경 이력을 한 곳에서 관리하는 인트라넷 업무 어시스턴트입니다.
          </p>
        </div>
        <div className="p-8 md:p-10">
          <Suspense fallback={<LoadingBlock />}>
            <Outlet />
          </Suspense>
        </div>
      </div>
    </div>
  );
}
