from app.services.embedding import EmbeddingService


def test_query_and_passage_contract_are_distinct_in_fallback_mode():
    service = EmbeddingService()
    query = service.embed(["로그인 회의록"], "query")
    passage = service.embed(["로그인 회의록"], "passage")
    assert query.dimensions == 768
    assert passage.dimensions == 768
    assert query.vectors[0] != passage.vectors[0]


def test_duplicate_passages_keep_output_order_and_shape():
    service = EmbeddingService()
    result = service.embed(["같은 문장", "같은 문장", "다른 문장"], "passage")
    assert len(result.vectors) == 3
    assert result.vectors[0] == result.vectors[1]
    assert result.vectors[0] != result.vectors[2]
