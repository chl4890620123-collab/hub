import { Link, NavLink } from 'react-router-dom';
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { useCurrentUser } from '@/hooks/useAuth';
import { useCurrentProject } from '@/hooks/useProjects';
import { PRIMARY_NAV, SECONDARY_NAV, type NavItem } from '@/components/layout/nav';
import { useAppStore } from '@/stores/appStore';
import { cn } from '@/lib/cn';

function NavLinkItem({ item, collapsed, onNavigate }: { item: NavItem; collapsed: boolean; onNavigate?: () => void }) {
  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      title={collapsed ? item.label : undefined}
      onClick={onNavigate}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-colors',
          collapsed && 'md:justify-center',
          isActive ? 'bg-accent-50 text-accent-700' : 'text-ink-600 hover:bg-ink-200 hover:text-ink-900',
        )
      }
    >
      <item.icon size={17} />
      <span className={collapsed ? 'md:hidden' : undefined}>{item.label}</span>
    </NavLink>
  );
}

/** Below md, this becomes an off-canvas drawer (open/close controlled by AppLayout's hamburger
 * button) instead of pushing the content over - a always-visible 11-item nav column has no room on
 * a phone. At md and up it's the original in-flow, collapsible column. */
export function Sidebar({ mobileOpen, onCloseMobile }: { mobileOpen: boolean; onCloseMobile: () => void }) {
  const { data: user } = useCurrentUser();
  const { currentProject } = useCurrentProject();
  const isAdmin = user?.globalRole === 'ADMIN';
  const canConfirm = isAdmin || (currentProject?.canConfirm ?? false);
  const isCollapsed = useAppStore((state) => state.isSidebarCollapsed);
  const setSidebarCollapsed = useAppStore((state) => state.setSidebarCollapsed);

  return (
    <>
      {mobileOpen && (
        <div className="fixed inset-0 z-30 bg-shell-900/40 md:hidden" onClick={onCloseMobile} aria-hidden="true" />
      )}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex h-full w-64 flex-col border-r border-ink-200 bg-ink-100 px-3 py-4',
          'transition-transform duration-200 ease-out md:static md:z-auto md:translate-x-0 md:transition-[width]',
          mobileOpen ? 'translate-x-0 shadow-2xl' : '-translate-x-full',
          isCollapsed ? 'md:w-16' : 'md:w-60',
        )}
      >
        <div className={cn('mb-6 flex items-center gap-2 px-2', isCollapsed && 'md:justify-center md:px-0')}>
          <div className="group/toggle relative hidden md:block">
            <button
              type="button"
              onClick={() => setSidebarCollapsed(!isCollapsed)}
              aria-label={isCollapsed ? '사이드바 열기' : '사이드바 닫기'}
              className="flex shrink-0 items-center justify-center rounded-md p-1.5 text-ink-500 transition-colors hover:bg-ink-200 hover:text-ink-900"
            >
              {isCollapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
            </button>
            <span
              role="tooltip"
              className="pointer-events-none absolute left-full top-1/2 z-20 ml-2 -translate-y-1/2 whitespace-nowrap rounded-md bg-shell-900 px-2 py-1 text-xs font-medium text-white opacity-0 shadow-lg ring-1 ring-white/10 transition-opacity duration-150 group-hover/toggle:opacity-100"
            >
              {isCollapsed ? '사이드바 열기' : '사이드바 닫기'}
            </span>
          </div>
          {(!isCollapsed || mobileOpen) && (
            <Link to="/" className="rounded-md px-1 text-lg font-bold text-ink-900 hover:text-accent-700" aria-label="홈으로 이동" onClick={onCloseMobile}>
              Hub
            </Link>
          )}
        </div>
        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto">
          {PRIMARY_NAV.filter((item) => !item.requiresConfirm || canConfirm).map((item) => (
            <NavLinkItem key={item.to} item={item} collapsed={isCollapsed} onNavigate={onCloseMobile} />
          ))}
        </nav>
        <div className="mt-4 flex flex-col gap-1 border-t border-ink-200 pt-4">
          {SECONDARY_NAV.filter((item) => !item.adminOnly || isAdmin).map((item) => (
            <NavLinkItem key={item.to} item={item} collapsed={isCollapsed} onNavigate={onCloseMobile} />
          ))}
        </div>
      </aside>
    </>
  );
}
