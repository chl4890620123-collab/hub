from __future__ import annotations

from app import config
from app.providers.paddle_ocr import PaddleOcrProvider


class MockOcrProvider:
    async def startup(self) -> None:
        return None

    async def close(self) -> None:
        return None

    def info(self) -> dict[str, object]:
        return {"provider": "mock", "engine": "mock", "loaded": True}

    async def extract(self, file_name: str, content_type: str, data: bytes) -> str:
        return (
            f"[Mock OCR] {file_name} 업로드 문서에서 추출된 텍스트입니다. "
            "김대리님은 금요일까지 OCR 결과를 검토해 주세요."
        )


def create_ocr_provider():
    # Intranet real mode uses local PaddleOCR. No external OCR provider or OCR API key is required.
    return MockOcrProvider() if config.AI_MODE == "mock" else PaddleOcrProvider()
