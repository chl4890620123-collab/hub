import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input, Label, Textarea } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { authApi } from '@/api/endpoints/auth';
import { errorMessage } from '@/lib/errors';
import { LoginIdField, LOGIN_ID_PATTERN } from '@/features/login/LoginIdField';

interface FormValues {
  loginId: string;
  email: string;
  password: string;
  passwordConfirm: string;
  displayName: string;
  companyName: string;
  departmentId: string;
  teamId: string;
  jobTitle: string;
  signupNote: string;
  requestedProjectId: string;
  privacyConsent: boolean;
}

export function MemberSignupPage() {
  const { register, handleSubmit, watch, setValue, formState } = useForm<FormValues>({
    defaultValues: { requestedProjectId: '', departmentId: '', teamId: '' },
  });
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const { data: projects } = useQuery({ queryKey: ['signup-projects'], queryFn: authApi.signupProjects });
  const { data: organization } = useQuery({ queryKey: ['signup-organization'], queryFn: authApi.signupOrganization });

  const departmentId = watch('departmentId');
  const teams = (organization?.teams ?? []).filter((team) => String(team.departmentId) === departmentId);

  const signup = useMutation({
    mutationFn: (values: FormValues) =>
      authApi.signupMember({
        loginId: values.loginId,
        email: values.email,
        password: values.password,
        displayName: values.displayName,
        companyName: values.companyName || null,
        departmentName: null,
        teamName: null,
        departmentId: values.departmentId ? Number(values.departmentId) : null,
        teamId: values.teamId ? Number(values.teamId) : null,
        requestedProjectId: values.requestedProjectId ? Number(values.requestedProjectId) : null,
        jobTitle: values.jobTitle || null,
        signupNote: values.signupNote || null,
        privacyConsent: values.privacyConsent,
      }),
    onSuccess: (result) => setSuccessMessage(result.message),
    onError: (error) => setSubmitError(errorMessage(error)),
  });

  const onSubmit = handleSubmit((values) => {
    setSubmitError(null);
    if (values.password !== values.passwordConfirm) return setSubmitError('비밀번호가 일치하지 않습니다.');
    if (!values.privacyConsent) return setSubmitError('개인정보 수집·이용에 동의해야 가입할 수 있습니다.');
    if (organization?.departments.length && (!values.departmentId || !values.teamId)) {
      return setSubmitError('부서와 팀을 선택해 주세요.');
    }
    signup.mutate(values);
  });

  if (successMessage) {
    return <div><h2 className="mb-2 text-subhead font-semibold text-ink-900">가입 신청 완료</h2><p className="mb-6 text-sm text-ink-600">{successMessage}</p><Button onClick={() => navigate('/login')}>로그인 화면으로</Button></div>;
  }

  return (
    <div>
      <h2 className="mb-1 text-subhead font-semibold text-ink-900">일반 사용자 가입</h2>
      <p className="mb-6 text-sm text-ink-500">관리자가 만든 부서·팀을 선택하고 가입 신청하세요. 관리자 승인 후 로그인할 수 있습니다.</p>
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <LoginIdField value={watch('loginId') ?? ''} registerProps={register('loginId', { required: true, pattern: LOGIN_ID_PATTERN })} />
        <div><Label htmlFor="displayName">이름</Label><Input id="displayName" {...register('displayName', { required: true })} /></div>
        <div><Label htmlFor="email">이메일</Label><Input id="email" type="email" {...register('email', { required: true })} /></div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div><Label htmlFor="password">비밀번호</Label><Input id="password" type="password" minLength={12} {...register('password', { required: true, minLength: 12 })} /><p className="mt-1 text-xs text-ink-400">12자 이상, 영문과 숫자를 포함해 주세요.</p></div>
          <div><Label htmlFor="passwordConfirm">비밀번호 확인</Label><Input id="passwordConfirm" type="password" {...register('passwordConfirm', { required: true })} />{watch('passwordConfirm') && watch('passwordConfirm') !== watch('password') && <p className="mt-1 text-xs text-red-500">비밀번호가 일치하지 않습니다.</p>}</div>
        </div>
        <div><Label htmlFor="companyName">회사/법인 (선택)</Label><Input id="companyName" {...register('companyName')} placeholder="예: 본사" /></div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <Label>부서</Label>
            <Select value={departmentId} onValueChange={(value) => { setValue('departmentId', value, { shouldDirty: true }); setValue('teamId', '', { shouldDirty: true }); }}>
              <SelectTrigger><SelectValue placeholder={organization?.departments.length ? '부서 선택' : '관리자가 등록한 부서가 없습니다'} /></SelectTrigger>
              <SelectContent>{organization?.departments.map((department) => <SelectItem key={department.id} value={String(department.id)}>{department.name}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div>
            <Label>팀</Label>
            <Select value={watch('teamId')} onValueChange={(value) => setValue('teamId', value, { shouldDirty: true })} disabled={!departmentId}>
              <SelectTrigger><SelectValue placeholder={departmentId ? '팀 선택' : '부서를 먼저 선택하세요'} /></SelectTrigger>
              <SelectContent>{teams.map((team) => <SelectItem key={team.id} value={String(team.id)}>{team.name}</SelectItem>)}</SelectContent>
            </Select>
          </div>
        </div>
        <div>
          <Label>소속 프로젝트</Label>
          <Select value={watch('requestedProjectId')} onValueChange={(v) => setValue('requestedProjectId', v, { shouldDirty: true })}>
            <SelectTrigger><SelectValue placeholder="선택 안 함" /></SelectTrigger>
            <SelectContent>{projects?.map((p) => <SelectItem key={p.id} value={String(p.id)}>{[p.departmentName, p.teamName].filter(Boolean).join(' · ') ? `${[p.departmentName, p.teamName].filter(Boolean).join(' · ')} · ${p.name}` : p.name}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div><Label htmlFor="jobTitle">직급/직책 (선택)</Label><Input id="jobTitle" {...register('jobTitle')} /></div>
        <div><Label htmlFor="signupNote">가입 메모 (선택)</Label><Textarea id="signupNote" rows={3} {...register('signupNote')} /></div>
        <label className="flex items-center gap-2 text-sm text-ink-600"><input type="checkbox" {...register('privacyConsent')} />개인정보 수집·이용에 동의합니다.</label>
        {submitError && <p className="text-sm text-red-600">{submitError}</p>}
        <Button type="submit" size="lg" disabled={!formState.isDirty || signup.isPending} className="mt-2">{signup.isPending ? '신청 중...' : '가입 신청'}</Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-500"><Link to="/signup" className="font-medium text-accent-600 hover:underline">가입 유형 다시 선택</Link></p>
    </div>
  );
}
