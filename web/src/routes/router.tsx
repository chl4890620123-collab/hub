import { lazy } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { ProtectedRoute, AdminRoute } from '@/components/layout/ProtectedRoute';
import { AuthLayout } from '@/routes/login/AuthLayout';

// Route-level code splitting: each screen is its own chunk, loaded on first visit instead of
// bundled into one large initial download.
const LoginPage = lazy(() => import('@/routes/login/LoginPage').then((m) => ({ default: m.LoginPage })));
const SignupRolePage = lazy(() => import('@/routes/login/SignupRolePage').then((m) => ({ default: m.SignupRolePage })));
const MemberSignupPage = lazy(() => import('@/routes/login/MemberSignupPage').then((m) => ({ default: m.MemberSignupPage })));
const AdminSignupPage = lazy(() => import('@/routes/login/AdminSignupPage').then((m) => ({ default: m.AdminSignupPage })));
const DashboardPage = lazy(() => import('@/routes/dashboard/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const SearchPage = lazy(() => import('@/routes/search/SearchPage').then((m) => ({ default: m.SearchPage })));
const AskPage = lazy(() => import('@/routes/ask/AskPage').then((m) => ({ default: m.AskPage })));
const ContextPage = lazy(() => import('@/routes/context/ContextPage').then((m) => ({ default: m.ContextPage })));
const TodosPage = lazy(() => import('@/routes/todos/TodosPage').then((m) => ({ default: m.TodosPage })));
const ReviewPage = lazy(() => import('@/routes/review/ReviewPage').then((m) => ({ default: m.ReviewPage })));
const DocumentsPage = lazy(() => import('@/routes/documents/DocumentsPage').then((m) => ({ default: m.DocumentsPage })));
const MeetingsPage = lazy(() => import('@/routes/meetings/MeetingsPage').then((m) => ({ default: m.MeetingsPage })));
const ConnectorsPage = lazy(() => import('@/routes/connectors/ConnectorsPage').then((m) => ({ default: m.ConnectorsPage })));
const AccountPage = lazy(() => import('@/routes/account/AccountPage').then((m) => ({ default: m.AccountPage })));
const AdminLayout = lazy(() => import('@/routes/admin/AdminLayout').then((m) => ({ default: m.AdminLayout })));
const AdminMembersPage = lazy(() => import('@/routes/admin/AdminMembersPage').then((m) => ({ default: m.AdminMembersPage })));
const AdminReassignPage = lazy(() => import('@/routes/admin/AdminReassignPage').then((m) => ({ default: m.AdminReassignPage })));
const AdminUsersPage = lazy(() => import('@/routes/admin/AdminUsersPage').then((m) => ({ default: m.AdminUsersPage })));
const AdminSearchRulesPage = lazy(() =>
  import('@/routes/admin/AdminSearchRulesPage').then((m) => ({ default: m.AdminSearchRulesPage })),
);
const AdminSecurityPage = lazy(() => import('@/routes/admin/AdminSecurityPage').then((m) => ({ default: m.AdminSecurityPage })));
const AdminHistoryPage = lazy(() => import('@/routes/admin/AdminHistoryPage').then((m) => ({ default: m.AdminHistoryPage })));

export const router = createBrowserRouter([
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupRolePage /> },
      { path: '/signup/member', element: <MemberSignupPage /> },
      { path: '/signup/admin', element: <AdminSignupPage /> },
    ],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: '/', element: <DashboardPage /> },
          { path: '/search', element: <SearchPage /> },
          { path: '/ask', element: <AskPage /> },
          { path: '/context', element: <ContextPage /> },
          { path: '/todos', element: <TodosPage /> },
          { path: '/review', element: <ReviewPage /> },
          { path: '/documents', element: <DocumentsPage /> },
          { path: '/meetings', element: <MeetingsPage /> },
          { path: '/connectors', element: <ConnectorsPage /> },
          { path: '/account', element: <AccountPage /> },
          {
            path: '/admin',
            element: <AdminRoute />,
            children: [
              {
                element: <AdminLayout />,
                children: [
                  { index: true, element: <AdminMembersPage /> },
                  { path: 'members', element: <AdminMembersPage /> },
                  { path: 'reassign', element: <AdminReassignPage /> },
                  { path: 'users', element: <AdminUsersPage /> },
                  { path: 'search', element: <AdminSearchRulesPage /> },
                  { path: 'security', element: <AdminSecurityPage /> },
                  { path: 'history', element: <AdminHistoryPage /> },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
]);
