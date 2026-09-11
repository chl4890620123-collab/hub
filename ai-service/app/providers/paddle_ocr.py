from __future__ import annotations

import asyncio
import tempfile
import threading
from pathlib import Path
from typing import Any

from app import config


class PaddleOcrProvider:
    """Local Korean OCR using lightweight PaddleOCR models.

    The model is loaded lazily so normal search/RAG startup does not pay OCR memory
    cost until an uploaded image or scanned document needs OCR. Recognition runs in a worker
    thread because Paddle inference is synchronous and CPU-bound.
    """

    def __init__(self) -> None:
        self._engine: Any | None = None
        self._lock = threading.Lock()

    async def startup(self) -> None:
        if config.OCR_WARMUP:
            await asyncio.to_thread(self._get_engine)

    async def close(self) -> None:
        # PaddleOCR has no async close hook. Drop the reference on shutdown.
        self._engine = None

    def info(self) -> dict[str, object]:
        return {
            "provider": "local",
            "engine": "paddleocr",
            "language": config.OCR_LANGUAGE,
            "device": config.OCR_DEVICE,
            "detection_model": config.OCR_DETECTION_MODEL,
            "loaded": self._engine is not None,
        }

    async def extract(self, file_name: str, content_type: str, data: bytes) -> str:
        if not data:
            raise RuntimeError("OCR image is empty")
        return await asyncio.to_thread(self._extract_sync, file_name, data)

    def _get_engine(self):
        if self._engine is not None:
            return self._engine
        with self._lock:
            if self._engine is not None:
                return self._engine
            try:
                from paddleocr import PaddleOCR
            except ImportError as exc:
                raise RuntimeError(
                    "Local OCR requires PaddleOCR. Install ai-service/requirements-local.txt."
                ) from exc

            self._engine = PaddleOCR(
                lang=config.OCR_LANGUAGE,
                use_doc_orientation_classify=False,
                use_doc_unwarping=False,
                use_textline_orientation=False,
                text_detection_model_name=config.OCR_DETECTION_MODEL,
                engine="paddle",
                device=config.OCR_DEVICE,
            )
            return self._engine

    def _extract_sync(self, file_name: str, data: bytes) -> str:
        engine = self._get_engine()
        suffix = Path(file_name).suffix.lower()
        if suffix not in {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}:
            suffix = ".jpg"
        temp_path: str | None = None
        try:
            with tempfile.NamedTemporaryFile(prefix="hub-ocr-", suffix=suffix, delete=False) as tmp:
                tmp.write(data)
                temp_path = tmp.name
            result = engine.predict(temp_path)
            texts: list[str] = []
            for item in result or []:
                payload = self._payload(item)
                rec_texts = payload.get("rec_texts")
                rec_scores = payload.get("rec_scores")
                if rec_texts is None:
                    rec_texts = []
                if rec_scores is None:
                    rec_scores = []
                for index, text in enumerate(rec_texts):
                    clean = str(text or "").strip()
                    if not clean:
                        continue
                    score = self._score_at(rec_scores, index)
                    if score is None or score >= config.OCR_MIN_SCORE:
                        texts.append(clean)
            return "\n".join(texts).strip()
        finally:
            if temp_path:
                Path(temp_path).unlink(missing_ok=True)

    @staticmethod
    def _payload(item: Any) -> dict[str, Any]:
        # PaddleOCR 3.x Result objects expose mapping-like get/items. Some versions
        # wrap the payload under `res`, so accept both shapes for forward compatibility.
        if hasattr(item, "get"):
            nested = item.get("res")
            if hasattr(nested, "get"):
                return nested
            return item
        json_value = getattr(item, "json", None)
        if callable(json_value):
            json_value = json_value()
        if isinstance(json_value, dict):
            nested = json_value.get("res")
            return nested if isinstance(nested, dict) else json_value
        return {}

    @staticmethod
    def _score_at(scores: Any, index: int) -> float | None:
        try:
            return float(scores[index])
        except (IndexError, KeyError, TypeError, ValueError):
            return None
