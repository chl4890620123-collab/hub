import pytest
from app.services.analyzer import Analyzer

@pytest.mark.asyncio
async def test_explicit_todo_and_evidence():
    result=await Analyzer().analyze("김대리님은 9월 10일까지 QA 계획서를 수정해주세요. PostgreSQL을 사용하기로 결정했습니다.","2026-09-04")
    assert result.todos
    assert result.todos[0].due_date=="2026-09-10"
    assert "QA 계획서" in result.todos[0].evidence_quote
    assert result.decisions

@pytest.mark.asyncio
async def test_ambiguous_due_is_suggestion_not_confirmed():
    result=await Analyzer().analyze("김대리님은 다음 주 금요일까지 일정표를 수정해주세요.","2026-09-04")
    todo=result.todos[0]
    assert todo.due_date is None
    assert todo.due_date_suggestion is not None

@pytest.mark.asyncio
async def test_invalid_calendar_date_is_not_emitted():
    result=await Analyzer().analyze("김대리님은 13월 99일까지 QA 계획서를 수정해주세요.","2026-09-04")
    todo=result.todos[0]
    assert todo.due_date is None

@pytest.mark.asyncio
async def test_discussion_or_completed_status_is_not_todo():
    result=await Analyzer().analyze("API 작업 관련 논의를 했습니다. DB 연결은 완료했습니다.","2026-09-04")
    assert not result.todos

@pytest.mark.asyncio
async def test_action_with_owner_and_deadline_is_todo():
    result=await Analyzer().analyze("민규님이 금요일까지 로그인 오류를 수정합니다.","2026-09-04")
    assert result.todos
