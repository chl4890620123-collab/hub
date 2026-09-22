from app.models.schemas import ReviseRequest, ReviseResponse
from app.providers.gemini import GeminiProvider

MOCK_MARKER = "\n\n[회의 내용 반영 - mock 모드]\n"


class ReviseService:
    """Proposes a revised document, grounded in a meeting transcript. Never invents content the
    meeting did not actually mention - the prompt says so, and callers still review the draft before
    anything is saved (see DocumentService.reviseDraftFromMeeting on the Java side)."""

    def __init__(self, gemini: GeminiProvider | None = None) -> None:
        self.gemini = gemini or GeminiProvider()

    async def revise(self, req: ReviseRequest) -> ReviseResponse:
        if self.gemini.enabled:
            raw = await self.gemini.json_generate(
                f"""아래 원본 문서를 회의 내용에 실제로 언급된 변경 사항만 반영해 다시 작성하라.
회의에서 언급되지 않은 내용은 추측해서 새로 만들지 않는다.
문서 전체를 다시 작성해 반환한다 (일부 문단만 반환하지 않는다).
JSON: {{"revised_text":"..."}}

원본 문서:
{req.original_text}

회의 내용:
{req.meeting_text}"""
            )
            revised = str(raw.get("revised_text", "")).strip()
            return ReviseResponse(revised_text=revised or req.original_text)

        # Offline mock: deterministic, visible placeholder so the save/compare flow can be tested
        # end-to-end without a real LLM call.
        return ReviseResponse(revised_text=req.original_text + MOCK_MARKER + req.meeting_text[:300])
