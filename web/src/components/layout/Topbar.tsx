import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { useMemo, useState } from 'react';
import { ChevronDown, LogOut, Menu, Monitor, Moon, Plus, Search, Sun, User as UserIcon } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useCurrentUser, useIsAdmin, useLogout } from '@/hooks/useAuth';
import { useCurrentProject } from '@/hooks/useProjects';
import { useGlobalSearchStore } from '@/stores/globalSearchStore';
import { useAppStore, type ThemeMode } from '@/stores/appStore';
import { CreateProjectDialog } from '@/features/projects/CreateProjectDialog';
import { cn } from '@/lib/cn';

const THEME_OPTIONS: { mode: ThemeMode; label: string; icon: typeof Sun }[] = [
  { mode: 'light', label: '라이트', icon: Sun },
  { mode: 'dark', label: '다크', icon: Moon },
  { mode: 'system', label: '시스템', icon: Monitor },
];

export function Topbar({ onOpenMobileNav }: { onOpenMobileNav: () => void }) {
  const { data: user } = useCurrentUser();
  const { currentProject, projects, setCurrentProjectId } = useCurrentProject();
  const isAdmin = useIsAdmin();
  const logout = useLogout();
  const navigate = useNavigate();
  const openGlobalSearch = useGlobalSearchStore((s) => s.open);
  const themeMode = useAppStore((s) => s.themeMode);
  const setThemeMode = useAppStore((s) => s.setThemeMode);
  const [createProjectOpen, setCreateProjectOpen] = useState(false);
  const searchShortcut = useMemo(() => (typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘K' : 'Ctrl+K'), []);
  const projectGroups = useMemo(() => {
    const groups = new Map<string, typeof projects>();
    projects.forEach((project) => {
      const label = [project.departmentName, project.teamName].filter(Boolean).join(' · ') || '회사 공용';
      const rows = groups.get(label) ?? [];
      rows.push(project);
      groups.set(label, rows);
    });
    return Array.from(groups.entries());
  }, [projects]);

  return (
    <header className="flex h-14 shrink-0 items-center justify-between gap-2 border-b border-ink-200 bg-white px-3 sm:px-5 dark:bg-ink-100">
      <div className="flex min-w-0 items-center gap-1 sm:gap-3">
        <button
          type="button"
          onClick={onOpenMobileNav}
          aria-label="메뉴 열기"
          className="shrink-0 rounded-md p-1.5 text-ink-600 hover:bg-ink-100 md:hidden"
        >
          <Menu size={19} />
        </button>
        {projects.length > 0 && (
          <DropdownMenu.Root>
            <DropdownMenu.Trigger className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm font-medium text-ink-700 hover:bg-ink-100">
              {currentProject?.name ?? '프로젝트 선택'}
              <ChevronDown size={14} className="text-ink-400" />
            </DropdownMenu.Trigger>
            <DropdownMenu.Portal>
              <DropdownMenu.Content
                align="start"
                className="z-50 min-w-[200px] rounded-md border border-ink-200 bg-white p-1 shadow-lg dark:bg-ink-100"
              >
                {projectGroups.map(([label, rows], groupIndex) => (
                  <div key={label}>
                    {groupIndex > 0 && <DropdownMenu.Separator className="my-1 h-px bg-ink-100" />}
                    <DropdownMenu.Label className="px-2 py-1 text-[11px] font-medium text-ink-400">{label}</DropdownMenu.Label>
                    {rows.map((p) => (
                      <DropdownMenu.Item
                        key={p.id}
                        onSelect={() => setCurrentProjectId(p.id)}
                        className={cn(
                          'cursor-pointer rounded-sm px-2 py-1.5 text-sm outline-none',
                          'data-[highlighted]:bg-accent-50 data-[highlighted]:text-accent-700',
                          p.id === currentProject?.id && 'font-semibold text-accent-600',
                        )}
                      >
                        {p.name}
                      </DropdownMenu.Item>
                    ))}
                  </div>
                ))}
              </DropdownMenu.Content>
            </DropdownMenu.Portal>
          </DropdownMenu.Root>
        )}
        {isAdmin && (
          <button
            type="button"
            onClick={() => setCreateProjectOpen(true)}
            aria-label="새 프로젝트 만들기"
            title="새 프로젝트 만들기"
            className="shrink-0 rounded-md p-1.5 text-ink-500 hover:bg-ink-100"
          >
            <Plus size={16} />
          </button>
        )}
      </div>
      <CreateProjectDialog open={createProjectOpen} onOpenChange={setCreateProjectOpen} />

      <div className="flex shrink-0 items-center gap-1 sm:gap-2">
        <button
          onClick={openGlobalSearch}
          aria-label="통합 검색"
          className="flex items-center gap-2 rounded-md border border-ink-200 px-2 py-1.5 text-sm text-ink-500 hover:bg-ink-50 sm:px-3"
        >
          <Search size={14} />
          <span className="hidden sm:inline">통합 검색</span>
          <kbd className="hidden rounded border border-ink-200 bg-ink-50 px-1 text-[10px] sm:inline">{searchShortcut}</kbd>
        </button>

        <DropdownMenu.Root>
          <DropdownMenu.Trigger className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-ink-700 hover:bg-ink-100">
            <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent-100 text-accent-700">
              <UserIcon size={14} />
            </span>
            <span className="hidden max-w-[8rem] truncate sm:inline">{user?.displayName}</span>
          </DropdownMenu.Trigger>
          <DropdownMenu.Portal>
            <DropdownMenu.Content align="end" className="z-50 min-w-[160px] rounded-md border border-ink-200 bg-white p-1 shadow-lg dark:bg-ink-100">
              <DropdownMenu.Label className="px-2 py-1 text-xs font-medium text-ink-400">테마</DropdownMenu.Label>
              <DropdownMenu.RadioGroup value={themeMode} onValueChange={(value) => setThemeMode(value as ThemeMode)}>
                {THEME_OPTIONS.map(({ mode, label, icon: Icon }) => (
                  <DropdownMenu.RadioItem
                    key={mode}
                    value={mode}
                    className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-ink-50 data-[state=checked]:font-semibold data-[state=checked]:text-accent-600"
                  >
                    <Icon size={14} />
                    {label}
                  </DropdownMenu.RadioItem>
                ))}
              </DropdownMenu.RadioGroup>
              <DropdownMenu.Separator className="my-1 h-px bg-ink-100" />
              <DropdownMenu.Item
                onSelect={() => navigate('/account')}
                className="cursor-pointer rounded-sm px-2 py-1.5 text-sm outline-none data-[highlighted]:bg-ink-50"
              >
                내 정보
              </DropdownMenu.Item>
              <DropdownMenu.Item
                onSelect={() => logout.mutate()}
                disabled={logout.isPending}
                className="flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm text-red-600 outline-none data-[highlighted]:bg-red-50"
              >
                <LogOut size={14} />
                로그아웃
              </DropdownMenu.Item>
            </DropdownMenu.Content>
          </DropdownMenu.Portal>
        </DropdownMenu.Root>
      </div>
    </header>
  );
}
