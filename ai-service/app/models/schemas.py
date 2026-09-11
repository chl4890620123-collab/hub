# semantics so search quality improves without introducing a second embedding model.
from __future__ import annotations
from typing import Literal, Optional
from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    text: str = Field(min_length=1)
    source_date: Optional[str] = None


class TodoProposal(BaseModel):
    title: str
    description: Optional[str] = None
    assignee_text: Optional[str] = None
    assignee_suggestion_text: Optional[str] = None
    due_date: Optional[str] = None
    due_date_suggestion: Optional[str] = None
    confidence: str = "LOW"
    evidence_quote: str


class DecisionProposal(BaseModel):
    statement: str
    confidence: str = "LOW"
    evidence_quote: str


class AnalyzeResponse(BaseModel):
    summary: str
    todos: list[TodoProposal] = Field(default_factory=list)
    decisions: list[DecisionProposal] = Field(default_factory=list)


class RagChunk(BaseModel):
    id: int
    text: str
    paragraph_ref: Optional[str] = None


class RagRequest(BaseModel):
    question: str = Field(min_length=1)
    chunks: list[RagChunk]


class RagEvidence(BaseModel):
    id: int
    quote: str


class RagResponse(BaseModel):
    answer: str
    evidence: list[RagEvidence] = Field(default_factory=list)


class ChangeRequest(BaseModel):
    before: str
    after: str


class ChangeItem(BaseModel):
    category: str = "CONTENT"
    before: str = ""
    after: str = ""
    reason: str = ""


class ChangeResponse(BaseModel):
    changes: list[ChangeItem] = Field(default_factory=list)


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=128)
    input_type: Literal["query", "passage"] = "passage"


class EmbedResponse(BaseModel):
    model: str
    dimensions: int
    vectors: list[list[float]]
