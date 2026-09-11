from __future__ import annotations

from app import config
from app.providers.gemini_speech import GeminiSpeechProvider


class MockSpeechProvider:
    async def startup(self) -> None:
        return None

    async def close(self) -> None:
        return None

    def info(self) -> dict[str, object]:
        return {"provider": "mock", "model": "mock"}

    async def transcribe(self, file_name: str, content_type: str, data: bytes) -> dict:
        text = (
            "박팀장: 김대리님은 금요일까지 QA 계획서를 수정해 주세요. "
            "이대리: 출시일은 9월 24일로 변경하는 것으로 하겠습니다."
        )
        return {
            "text": text,
            "segments": [
                {"start_ms": 0, "end_ms": 5000, "speaker": "박팀장", "text": "김대리님은 금요일까지 QA 계획서를 수정해 주세요."},
                {"start_ms": 5000, "end_ms": 10000, "speaker": "이대리", "text": "출시일은 9월 24일로 변경하는 것으로 하겠습니다."},
            ],
            "raw": {"mock": True},
        }


def create_speech_provider():
    if config.STT_PROVIDER == "gemini":
        return GeminiSpeechProvider()
    return MockSpeechProvider()
