export class ApiError extends Error {
  status: number;
  code: string;

  constructor(message: string, status: number, code: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }

  static async fromResponse(res: Response): Promise<ApiError> {
    try {
      const body = (await res.clone().json()) as { error?: string; message?: string };
      return new ApiError(body.message ?? res.statusText, res.status, body.error ?? 'UNKNOWN');
    } catch {
      return new ApiError(res.statusText || '요청을 처리하지 못했습니다.', res.status, 'UNKNOWN');
    }
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return '알 수 없는 오류가 발생했습니다.';
}
