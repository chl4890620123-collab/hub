from __future__ import annotations
import re
from datetime import date, timedelta
from app.models.schemas import AnalyzeResponse, TodoProposal, DecisionProposal
from app.services.text_utils import sentences, parse_source_date, next_weekday
from app.providers.gemini import GeminiProvider
from app import config
import asyncio

TODO_STRONG_MARKERS = ("해주세요", "해 주세요", "해야", "필요합니다", "필요해", "할 일")
ACTION_VERBS = ("수정", "확인", "검토", "작성", "전달", "테스트", "연결", "구현", "배포", "준비", "정리", "업데이트", "추가", "삭제", "설정", "처리", "진행")
PAST_STATUS_MARKERS = ("완료했습니다", "완료됐다", "완료되었습니다", "발생했습니다", "발생했다", "종료했습니다")
DECISION_MARKERS = ("하기로", "결정", "확정", "사용한다", "사용하기로", "진행하기로", "로 가겠습니다", "로 진행")
WEEKDAY = {"월요일":0,"화요일":1,"수요일":2,"목요일":3,"금요일":4,"토요일":5,"일요일":6}

class Analyzer:
    def __init__(self, gemini: GeminiProvider | None = None) -> None:
        self.gemini = gemini or GeminiProvider()

    async def analyze(self, text: str, source_date: str | None) -> AnalyzeResponse:
        if not self.gemini.enabled:
            return self._mock(text, source_date)
        normalized = (text or "").strip()
        if len(normalized) <= config.ANALYZE_CHUNK_CHARS:
            return await self._gemini(normalized, source_date)
        chunks = self._analysis_chunks(normalized)[:config.ANALYZE_MAX_CHUNKS]
        semaphore = asyncio.Semaphore(config.ANALYZE_CONCURRENCY)

        async def run(chunk: str) -> AnalyzeResponse:
            async with semaphore:
                return await self._gemini(chunk, source_date)

        parts = await asyncio.gather(*(run(chunk) for chunk in chunks))
        return self._merge(parts)

    def _mock(self, text: str, source_date: str | None) -> AnalyzeResponse:
        sents = sentences(text)
        summary = " ".join(sents[:5])[:1600] if sents else text[:1600]
        todos: list[TodoProposal] = []
        decisions: list[DecisionProposal] = []
        base = parse_source_date(source_date)
        for sentence in sents:
            if self._is_todo(sentence):
                assignee = self._assignee(sentence)
                due, suggestion = self._due(sentence, base)
                title = re.sub(r"^[^:]{0,20}:\s*", "", sentence)[:90]
                todos.append(TodoProposal(
                    title=title, description=sentence,
                    assignee_text=assignee if self._explicit_assignee(sentence) else None,
                    assignee_suggestion_text=None if self._explicit_assignee(sentence) else assignee,
                    due_date=due, due_date_suggestion=suggestion,
                    confidence="HIGH" if due or self._explicit_assignee(sentence) else "MEDIUM",
                    evidence_quote=sentence,
                ))
            if any(m in sentence for m in DECISION_MARKERS):
                decisions.append(DecisionProposal(statement=sentence[:220], confidence="MEDIUM", evidence_quote=sentence))
        return AnalyzeResponse(summary=summary, todos=todos[:30], decisions=decisions[:20])

    def _analysis_chunks(self, text: str) -> list[str]:
        paragraphs = [part.strip() for part in re.split(r"\n\s*\n", text) if part.strip()]
        chunks: list[str] = []
        current: list[str] = []
        size = 0
        for paragraph in paragraphs or [text]:
            if current and size + len(paragraph) + 2 > config.ANALYZE_CHUNK_CHARS:
                chunks.append("\n\n".join(current))
                current = []
                size = 0
            if len(paragraph) > config.ANALYZE_CHUNK_CHARS:
                for start in range(0, len(paragraph), config.ANALYZE_CHUNK_CHARS):
                    if current:
                        chunks.append("\n\n".join(current)); current=[]; size=0
                    chunks.append(paragraph[start:start + config.ANALYZE_CHUNK_CHARS])
                continue
            current.append(paragraph); size += len(paragraph) + 2
        if current:
            chunks.append("\n\n".join(current))
        return chunks or [text[:config.ANALYZE_CHUNK_CHARS]]

    @staticmethod
    def _merge(parts: list[AnalyzeResponse]) -> AnalyzeResponse:
        summaries: list[str] = []
        todos: list[TodoProposal] = []
        decisions: list[DecisionProposal] = []
        todo_seen: set[tuple[str, str]] = set()
        decision_seen: set[str] = set()
        for part in parts:
            if part.summary and part.summary not in summaries:
                summaries.append(part.summary)
            for todo in part.todos:
                key = ((todo.title or "").strip().lower(), (todo.evidence_quote or "").strip())
                if key not in todo_seen:
                    todo_seen.add(key); todos.append(todo)
            for decision in part.decisions:
                key = (decision.evidence_quote or decision.statement or "").strip()
                if key and key not in decision_seen:
                    decision_seen.add(key); decisions.append(decision)
        return AnalyzeResponse(summary=" ".join(summaries)[:4000], todos=todos[:30], decisions=decisions[:20])


    def _is_todo(self, sentence: str) -> bool:
        text=sentence.strip()
        if not text or text.endswith("?") or text.endswith("？"):
            return False
        strong=any(m in text for m in TODO_STRONG_MARKERS)
        if any(m in text for m in PAST_STATUS_MARKERS) and not strong:
            return False
        if strong:
            return True
        has_action=any(v in text for v in ACTION_VERBS)
        has_commitment=("까지" in text or "담당" in text or self._explicit_assignee(text))
        return has_action and has_commitment

    def _assignee(self, sentence: str) -> str | None:
        patterns = [r"([가-힣A-Za-z]{2,12}(?:대리|과장|팀장|님|씨))", r"([가-힣]{2,4})\s*(?:이|가|은|는)\s*(?:담당|진행|수정|확인)"]
        for p in patterns:
            m=re.search(p,sentence)
            if m: return m.group(1).removesuffix("님").removesuffix("씨")
        return None

    def _explicit_assignee(self, sentence: str) -> bool:
        return bool(re.search(r"[가-힣A-Za-z]{2,12}(?:님|씨|대리|과장|팀장)(?:이|가|은|는|에게|께서)?", sentence))

    def _due(self, sentence: str, base):
        full=re.search(r"(20\d{2})[./년 -]\s*(\d{1,2})[./월 -]\s*(\d{1,2})일?", sentence)
        if full:
            try:
                parsed=date(int(full.group(1)),int(full.group(2)),int(full.group(3)))
                return parsed.isoformat(), None
            except ValueError:
                pass
        md=re.search(r"(\d{1,2})월\s*(\d{1,2})일", sentence)
        if md:
            try:
                parsed=date(base.year,int(md.group(1)),int(md.group(2)))
                return parsed.isoformat(), None
            except ValueError:
                pass
        for name,wd in WEEKDAY.items():
            if name in sentence:
                d=next_weekday(base,wd,force_next="다음 주" in sentence)
                return None,d.isoformat()
        m=re.search(r"(\d+)일\s*(?:안|내|이내)",sentence)
        if m: return None,(base+timedelta(days=int(m.group(1)))).isoformat()
        return None,None

    async def _gemini(self, text: str, source_date: str | None) -> AnalyzeResponse:
        prompt=f'''당신은 조직 업무 기록 추출기다. 근거 없는 값을 절대 만들지 않는다.
기준일: {source_date or '미지정'}
규칙:
1) TODO와 결정 후보는 반드시 원문에서 그대로 복사한 evidence_quote를 포함한다.
2) 담당자/기한이 명시적이면 assignee_text/due_date에 넣는다.
3) 문맥상 추천 가능하지만 확정할 수 없으면 assignee_suggestion_text/due_date_suggestion에만 넣는다.
4) 이 결과는 TODO 후보일 뿐이다. 실제 담당자/마감일/업무 확정은 ADMIN이 수행한다.
5) 정보가 없으면 null이다.
5) 날짜는 YYYY-MM-DD. 모호한 '빨리/나중에'는 null이다.
JSON 형식: {{"summary":"...","todos":[{{"title":"","description":"","assignee_text":null,"assignee_suggestion_text":null,"due_date":null,"due_date_suggestion":null,"confidence":"HIGH|MEDIUM|LOW","evidence_quote":"원문 그대로"}}],"decisions":[{{"statement":"","confidence":"HIGH|MEDIUM|LOW","evidence_quote":"원문 그대로"}}]}}
원문:\n{text[:config.ANALYZE_CHUNK_CHARS]}'''
        raw=await self.gemini.json_generate(prompt)
        return AnalyzeResponse.model_validate(raw)
