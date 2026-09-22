import pytest
from app.services.revise import ReviseService
from app.models.schemas import ReviseRequest


@pytest.mark.asyncio
async def test_revise_mock_mode_keeps_original_and_appends_meeting():
    req = ReviseRequest(original_text="회의 시간은 매주 월요일 오전 10시입니다.", meeting_text="회의 시간을 화요일로 옮기기로 했습니다.")
    res = await ReviseService().revise(req)
    assert req.original_text in res.revised_text
    assert "화요일" in res.revised_text
