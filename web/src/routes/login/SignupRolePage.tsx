import { Link } from 'react-router-dom';
import { Card } from '@/components/ui/card';

export function SignupRolePage() {
  return (
    <div>
      <h2 className="mb-1 text-subhead font-semibold text-ink-900">회원가입</h2>
      <p className="mb-6 text-sm text-ink-500">가입 유형을 선택해 주세요.</p>

      <div className="flex flex-col gap-3">
        <Link to="/signup/member">
          <Card className="p-4 transition-colors hover:border-accent-500">
            <p className="font-medium text-ink-900">일반 사용자</p>
            <p className="text-sm text-ink-500">프로젝트에 소속된 팀원으로 가입합니다. 관리자 승인이 필요합니다.</p>
          </Card>
        </Link>
        <Link to="/signup/admin">
          <Card className="p-4 transition-colors hover:border-accent-500">
            <p className="font-medium text-ink-900">관리자</p>
            <p className="text-sm text-ink-500">
              회사를 관리하는 관리자로 가입합니다. 첫 설치라면 즉시 승인되고, 이후에는 기존 관리자 승인이 필요합니다.
            </p>
          </Card>
        </Link>
      </div>

      <p className="mt-6 text-center text-sm text-ink-500">
        이미 계정이 있으신가요? <Link to="/login" className="font-medium text-accent-600 hover:underline">로그인</Link>
      </p>
    </div>
  );
}
