import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { useCurrentUser, useLogin } from '@/hooks/useAuth';
import { errorMessage } from '@/lib/errors';

interface LoginForm {
  identifier: string;
  password: string;
}

/** Only accept a same-origin path, never an absolute URL - an open redirect risk otherwise. */
function safeNext(raw: string | null): string {
  if (!raw) return '/';
  return raw.startsWith('/') && !raw.startsWith('//') ? raw : '/';
}

export function LoginPage() {
  const { register, handleSubmit, formState } = useForm<LoginForm>();
  const login = useLogin();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const next = safeNext(params.get('next'));

  // Shared single observer (see AuthProvider) - already resolved at app start, so reading it here
  // costs nothing extra. A visitor who is still logged in should never see the login form again.
  const { data: user } = useCurrentUser();
  if (user) return <Navigate to={next} replace />;

  const onSubmit = handleSubmit(({ identifier, password }) => {
    login.mutate(
      { identifier, password },
      { onSuccess: () => navigate(next, { replace: true }) },
    );
  });

  return (
    <div>
      <h2 className="mb-1 text-subhead font-semibold text-ink-900">로그인</h2>
      <p className="mb-6 text-sm text-ink-500">아이디와 비밀번호를 입력해 주세요.</p>

      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <div>
          <Label htmlFor="identifier">아이디 또는 이메일</Label>
          <Input id="identifier" autoFocus {...register('identifier', { required: true })} />
        </div>
        <div>
          <Label htmlFor="password">비밀번호</Label>
          <div className="relative">
            <Input id="password" type={showPassword ? 'text' : 'password'} {...register('password', { required: true })} />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute right-2 top-1/2 -translate-y-1/2 text-ink-400 hover:text-ink-600"
            >
              {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
            </button>
          </div>
        </div>

        {login.isError && <p className="text-sm text-red-600">{errorMessage(login.error)}</p>}

        <Button type="submit" size="lg" disabled={!formState.isDirty || login.isPending} className="mt-2">
          {login.isPending ? '로그인 중...' : '로그인'}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        계정이 없으신가요? <Link to="/signup" className="font-medium text-accent-600 hover:underline">회원가입</Link>
      </p>
    </div>
  );
}
