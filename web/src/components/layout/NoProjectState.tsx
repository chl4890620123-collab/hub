export function NoProjectState() {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-ink-300 py-16 text-center">
      <p className="text-sm font-medium text-ink-600">소속된 프로젝트가 없습니다.</p>
      <p className="mt-1 text-xs text-ink-400">관리자에게 프로젝트 배정을 요청해 주세요.</p>
    </div>
  );
}
