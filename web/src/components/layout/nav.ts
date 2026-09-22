import type { LucideIcon } from 'lucide-react';
import {
  CalendarCheck,
  FileText,
  Home,
  Link2,
  ListChecks,
  Mic,
  Search,
  Settings,
  Sparkles,
  User as UserIcon,
} from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Only shown when the current project grants confirm permission (or the user is ADMIN). */
  requiresConfirm?: boolean;
  adminOnly?: boolean;
}

export const PRIMARY_NAV: NavItem[] = [
  { to: '/', label: '대시보드', icon: Home },
  { to: '/search', label: '자료 찾기', icon: Search },
  { to: '/ask', label: 'AI에게 묻기', icon: Sparkles },
  { to: '/context', label: '관련 업무 모아보기', icon: ListChecks },
  { to: '/todos', label: '할 일·일정', icon: CalendarCheck },
  { to: '/review', label: '담당자 배정', icon: ListChecks, requiresConfirm: true },
  { to: '/documents', label: '문서 요약', icon: FileText },
  { to: '/meetings', label: '회의 녹음', icon: Mic },
  { to: '/connectors', label: '연결 서비스', icon: Link2 },
];

export const SECONDARY_NAV: NavItem[] = [
  { to: '/account', label: '내 정보', icon: UserIcon },
  { to: '/admin', label: '회사 관리', icon: Settings, adminOnly: true },
];
