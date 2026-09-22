import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import type { UseFormRegisterReturn } from 'react-hook-form';
import { Input, Label } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { authApi } from '@/api/endpoints/auth';
import { errorMessage } from '@/lib/errors';

/** 4-40 chars, matching login.html's `pattern="[A-Za-z0-9_.\-]{4,40}"`. */
export const LOGIN_ID_PATTERN = /^[A-Za-z0-9_.-]{4,40}$/;

export function LoginIdField({
  value,
  onCheckedChange,
  registerProps,
}: {
  value: string;
  onCheckedChange?: (available: boolean | null) => void;
  registerProps: UseFormRegisterReturn<'loginId'>;
}) {
  const [result, setResult] = useState<{ available: boolean; message: string } | null>(null);

  const check = useMutation({
    mutationFn: () => authApi.checkLoginId(value),
    onSuccess: (data) => {
      setResult({ available: data.available, message: data.message });
      onCheckedChange?.(data.available);
    },
    onError: (error) => {
      setResult(null);
      onCheckedChange?.(null);
      alert(errorMessage(error));
    },
  });

  return (
    <div>
      <Label htmlFor="loginId">아이디</Label>
      <div className="flex gap-2">
        <Input
          id="loginId"
          {...registerProps}
          onChange={(e) => {
            registerProps.onChange(e);
            setResult(null);
            onCheckedChange?.(null);
          }}
          minLength={4}
          maxLength={40}
          pattern="[A-Za-z0-9_.-]{4,40}"
          className="flex-1"
        />
        <Button
          type="button"
          variant="outline"
          disabled={!LOGIN_ID_PATTERN.test(value) || check.isPending}
          onClick={() => check.mutate()}
        >
          중복확인
        </Button>
      </div>
      <p className="mt-1 text-xs text-ink-400">4~40자, 영문/숫자/._- 만 사용할 수 있습니다.</p>
      {result && <p className={`mt-1 text-xs ${result.available ? 'text-accent-600' : 'text-red-500'}`}>{result.message}</p>}
    </div>
  );
}
