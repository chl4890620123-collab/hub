import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { authApi } from '@/api/endpoints/auth';
import { errorMessage } from '@/lib/errors';

interface FormValues {
  loginId: string;
  email: string;
  password: string;
  passwordConfirm: string;
  displayName: string;
  privacyConsent: boolean;
}

export function AdminSignupPage() {
  const { register, handleSubmit, watch, formState } = useForm<FormValues>();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const signup = useMutation({
    mutationFn: (values: FormValues) =>
      authApi.signupAdmin({
        loginId: values.loginId,
        email: values.email,
        password: values.password,
        displayName: values.displayName,
        privacyConsent: values.privacyConsent,
      }),
    onSuccess: (result) => setSuccessMessage(result.message),
    onError: (error) => setSubmitError(errorMessage(error)),
  });

  const onSubmit = handleSubmit((values) => {
    setSubmitError(null);
    if (values.password !== values.passwordConfirm) {
      setSubmitError('비밀번호가 일치하지 않습니다.');
      return;
    }
    if (!values.privacyConsent) {
      setSubmitError('개인정보 수집·이용에 동의해야 가입할 수 있습니다.');
      return;
    }
    signup.mutate(values);
  });

  if (successMessage) {
    return (
      <div>
        <h2 className="mb-2 text-subhead font-semibold text-ink-900">가입 신청 완료</h2>
        <p className="mb-6 text-sm text-ink-600">{successMessage}</p>
        <Button onClick={() => navigate('/login')}>로그인 화면으로</Button>
      </div>
    );
  }

  return (
    <div>
      <h2 className="mb-1 text-subhead font-semibold text-ink-900">관리자 가입</h2>
      <p className="mb-6 text-sm text-ink-500">
        회사에 관리자가 한 명도 없다면 즉시 승인되어 바로 로그인할 수 있습니다.
      </p>

      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <div>
          <Label htmlFor="loginId">아이디</Label>
          <Input id="loginId" {...register('loginId', { required: true })} />
        </div>
        <div>
          <Label htmlFor="displayName">이름</Label>
          <Input id="displayName" {...register('displayName', { required: true })} />
        </div>
        <div>
          <Label htmlFor="email">이메일</Label>
          <Input id="email" type="email" {...register('email', { required: true })} />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <Label htmlFor="password">비밀번호</Label>
            <Input id="password" type="password" {...register('password', { required: true })} />
          </div>
          <div>
            <Label htmlFor="passwordConfirm">비밀번호 확인</Label>
            <Input id="passwordConfirm" type="password" {...register('passwordConfirm', { required: true })} />
            {watch('passwordConfirm') && watch('passwordConfirm') !== watch('password') && (
              <p className="mt-1 text-xs text-red-500">비밀번호가 일치하지 않습니다.</p>
            )}
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm text-ink-600">
          <input type="checkbox" {...register('privacyConsent')} />
          개인정보 수집·이용에 동의합니다.
        </label>

        {submitError && <p className="text-sm text-red-600">{submitError}</p>}

        <Button type="submit" size="lg" disabled={!formState.isDirty || signup.isPending} className="mt-2">
          {signup.isPending ? '신청 중...' : '가입 신청'}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-ink-500">
        <Link to="/signup" className="font-medium text-accent-600 hover:underline">
          가입 유형 다시 선택
        </Link>
      </p>
    </div>
  );
}
