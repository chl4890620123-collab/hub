import pytest
from app.services.rag import RagService
from app.models.schemas import RagRequest,RagChunk
@pytest.mark.asyncio
async def test_rag_returns_evidence():
    req=RagRequest(question="출시일 변경",chunks=[RagChunk(id=1,text="출시일은 9월 24일로 변경되었습니다."),RagChunk(id=2,text="점심 메뉴는 김밥입니다.")])
    res=await RagService().answer(req)
    assert res.evidence and res.evidence[0].id==1

@pytest.mark.asyncio
async def test_rag_refuses_unrelated_context():
    req=RagRequest(question="출시일이 언제야?",chunks=[RagChunk(id=7,text="점심 메뉴는 김치찌개입니다.")])
    res=await RagService().answer(req)
    assert not res.evidence
    assert "확인하지 못했습니다" in res.answer
