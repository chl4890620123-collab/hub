from app.models.schemas import RagRequest, RagResponse, RagEvidence
from app.services.text_utils import hash_vector, cosine
from app.providers.gemini import GeminiProvider
from app import config

NO_EVIDENCE = "관련 자료에서 해당 내용을 확인하지 못했습니다."


class RagService:
    def __init__(self, gemini: GeminiProvider | None = None):
        self.gemini = gemini or GeminiProvider()

    async def answer(self, req: RagRequest) -> RagResponse:
        if not req.chunks:
            return RagResponse(answer=NO_EVIDENCE)

        if self.gemini.enabled:
            # Java already orders vector candidates. Keep that order so semantic E5 retrieval is not
            # destroyed by a second lexical-only ranking step.
            candidates = req.chunks[:config.RAG_MAX_CHUNKS]
            context_parts: list[str] = []
            used = 0
            for c in candidates:
                piece = f"[chunk:{c.id}] {c.text}"
                remaining = config.RAG_MAX_CONTEXT_CHARS - used
                if remaining <= 0:
                    break
                context_parts.append(piece[:remaining])
                used += len(context_parts[-1]) + 2
            context = "\n\n".join(context_parts)
            raw = await self.gemini.json_generate(
                f'''아래 내부 자료만 사용해 질문에 답하라.
자료에 없는 내용은 만들지 말고 "{NO_EVIDENCE}"라고 답한다.
evidence_ids에는 답변에 실제 사용한 chunk id만 넣는다.
근거가 하나도 없으면 evidence_ids는 빈 배열이어야 한다.
JSON: {{"answer":"...","evidence_ids":[1,2]}}
질문:{req.question}
자료:{context}'''
            )
            valid = {c.id: c for c in candidates}
            ids = []
            for value in raw.get("evidence_ids", []):
                try:
                    chunk_id = int(value)
                except (TypeError, ValueError):
                    continue
                if chunk_id in valid and chunk_id not in ids:
                    ids.append(chunk_id)
            # Grounding is enforced in code, not only in the prompt.
            if not ids:
                return RagResponse(answer=NO_EVIDENCE)
            answer = str(raw.get("answer", "")).strip() or NO_EVIDENCE
            return RagResponse(
                answer=answer,
                evidence=[RagEvidence(id=i, quote=valid[i].text[:500]) for i in ids],
            )

        # Offline mock mode uses deterministic lexical similarity and refuses weak matches.
        qv = hash_vector(req.question)
        ranked = sorted(req.chunks, key=lambda c: cosine(qv, hash_vector(c.text)), reverse=True)[:5]
        if not ranked or cosine(qv, hash_vector(ranked[0].text)) < 0.05:
            return RagResponse(answer=NO_EVIDENCE)
        top = ranked[0]
        return RagResponse(
            answer=top.text[:700],
            evidence=[RagEvidence(id=top.id, quote=top.text[:500])],
        )
