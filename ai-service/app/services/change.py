import difflib

from app.models.schemas import ChangeItem, ChangeResponse
from app.providers.gemini import GeminiProvider


class ChangeService:
    def __init__(self, gemini: GeminiProvider | None = None) -> None:
        self.gemini = gemini or GeminiProvider()

    async def compare(self, before: str, after: str) -> ChangeResponse:
        if before == after:
            return ChangeResponse(changes=[])

        diff = list(difflib.ndiff(before.splitlines(), after.splitlines()))
        removed = [line[2:].strip() for line in diff if line.startswith("- ") and line[2:].strip()]
        added = [line[2:].strip() for line in diff if line.startswith("+ ") and line[2:].strip()]

        if self.gemini.enabled:
            raw = await self.gemini.json_generate(
                f"""You classify business meaning from a text diff.
Rules:
1. category must be SCHEDULE|BUDGET|ASSIGNEE|FEATURE|CONTRACT|CONTENT.
2. before must be an exact unchanged string copied from the deleted list, or an empty string.
3. after must be an exact unchanged string copied from the added list, or an empty string.
4. Never invent a reason that is not supported by the diff.
JSON: {{"changes":[{{"category":"","before":"","after":"","reason":""}}]}}
Deleted: {removed[:80]}
Added: {added[:80]}"""
            )
            return ChangeResponse.model_validate(raw)

        changes = []
        for index in range(max(len(removed), len(added))):
            before_line = removed[index] if index < len(removed) else ""
            after_line = added[index] if index < len(added) else ""
            combined = before_line + after_line
            if any(keyword in combined for keyword in ("일정", "출시", "마감", "날짜")):
                category = "SCHEDULE"
            elif any(keyword in combined for keyword in ("예산", "원", "비용")):
                category = "BUDGET"
            elif any(keyword in combined for keyword in ("담당", "팀", "대리")):
                category = "ASSIGNEE"
            else:
                category = "CONTENT"
            changes.append(
                ChangeItem(
                    category=category,
                    before=before_line,
                    after=after_line,
                    reason="문서의 변경된 부분에서 확인했습니다.",
                )
            )
        return ChangeResponse(changes=changes[:30])
