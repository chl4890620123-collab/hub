import { NavLink, Outlet } from 'react-router-dom';
import { PageHeader } from '@/components/layout/PageHeader';
import { cn } from '@/lib/cn';

const TABS = [
  { to: '/admin/members', label: '가입 승인' },
  { to: '/admin/reassign', label: '재배정' },
  { to: '/admin/users', label: '사용자 관리' },
  { to: '/admin/search', label: '검색 규칙' },
  { to: '/admin/security', label: '보안' },
  { to: '/admin/history', label: '이력' },
];

export function AdminLayout() {
  return (
    <div>
      <PageHeader title="회사 관리" description="가입 승인, 사용자, 검색 규칙, 보안 정책을 관리합니다." />
      <div className="mb-5 inline-flex items-center gap-1 rounded-md bg-ink-100 p-1">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) =>
              cn(
                'rounded-sm px-3 py-1.5 text-sm font-medium transition-colors',
                isActive ? 'bg-white text-ink-900 shadow-sm dark:bg-ink-200' : 'text-ink-500 hover:text-ink-700',
              )
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </div>
      <Outlet />
    </div>
  );
}
