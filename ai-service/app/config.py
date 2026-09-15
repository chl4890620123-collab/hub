# Python performance and provider selection are configured in one place.
import importlib.util
import os
import sys


def env(name: str, default: str = "") -> str:
    value = os.getenv(name)
    return value.strip() if value and value.strip() else default


def env_int(name: str, default: int, minimum: int = 1, maximum: int | None = None) -> int:
    raw = env(name, str(default))
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be an integer") from exc
    if value < minimum or (maximum is not None and value > maximum):
        bound = f"{minimum}..{maximum}" if maximum is not None else f">={minimum}"
        raise RuntimeError(f"{name} must be {bound}")
    return value


def env_float(name: str, default: float, minimum: float = 0.0, maximum: float | None = None) -> float:
    raw = env(name, str(default))
    try:
        value = float(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be a number") from exc
    if value < minimum or (maximum is not None and value > maximum):
        bound = f"{minimum}..{maximum}" if maximum is not None else f">={minimum}"
        raise RuntimeError(f"{name} must be {bound}")
    return value


def env_bool(name: str, default: bool) -> bool:
    raw = env(name, "true" if default else "false").lower()
    if raw in {"1", "true", "yes", "on"}:
        return True
    if raw in {"0", "false", "no", "off"}:
        return False
    raise RuntimeError(f"{name} must be true/false")


AI_MODE = env("HUB_AI_MODE", "mock").lower()
GEMINI_API_KEY = env("GEMINI_API_KEY")
GEMINI_MODEL = env("GEMINI_MODEL", "gemini-3.8-flash")
GEMINI_TRANSCRIBE_MODEL = env("GEMINI_TRANSCRIBE_MODEL", "gemini-3.5-transcribe")
GEMINI_CONNECT_TIMEOUT_SECONDS = env_int("HUB_GEMINI_CONNECT_TIMEOUT_SECONDS", 5, 1, 30)
GEMINI_READ_TIMEOUT_SECONDS = env_int("HUB_GEMINI_READ_TIMEOUT_SECONDS", 60, 5, 300)
GEMINI_MAX_CONNECTIONS = env_int("HUB_GEMINI_MAX_CONNECTIONS", 20, 1, 100)
GEMINI_MAX_KEEPALIVE_CONNECTIONS = env_int("HUB_GEMINI_MAX_KEEPALIVE_CONNECTIONS", 10, 1, 100)
GEMINI_RETRIES = env_int("HUB_GEMINI_RETRIES", 3, 0, 5)
# A 429 is answered with the delay the API asks for, capped so a job cannot stall behind one quota wait.
GEMINI_MAX_RETRY_DELAY_SECONDS = env_int("HUB_GEMINI_MAX_RETRY_DELAY_SECONDS", 20, 1, 120)

# OCR is fixed to local PaddleOCR in real mode and mock OCR in smoke-test mode.
# These tuning values are advanced defaults; normal operators do not need them in .env.
OCR_PROVIDER = "mock" if AI_MODE == "mock" else "local"
OCR_LOCAL_ENGINE = "paddleocr"
OCR_LANGUAGE = env("OCR_LANGUAGE", "korean")
OCR_DEVICE = env("OCR_DEVICE", "cpu").lower()
OCR_DETECTION_MODEL = env("OCR_DETECTION_MODEL", "PP-OCRv5_mobile_det")
OCR_MIN_SCORE = env_float("OCR_MIN_SCORE", 0.45, 0.0, 1.0)
OCR_WARMUP = env_bool("OCR_WARMUP", False)

# STT reuses the existing Gemini key, so no separate CLOVA Speech credentials are required.
STT_PROVIDER = "mock" if AI_MODE == "mock" else "gemini"
STT_LANGUAGE = env("STT_LANGUAGE", "ko-KR")
STT_DIARIZATION = env_bool("STT_DIARIZATION", True)
STT_WORD_TIMESTAMPS = env_bool("STT_WORD_TIMESTAMPS", True)
STT_FILE_READY_TIMEOUT_SECONDS = env_int("STT_FILE_READY_TIMEOUT_SECONDS", 60, 5, 300)
STT_FILE_POLL_INTERVAL_MS = env_int("STT_FILE_POLL_INTERVAL_MS", 500, 100, 5000)

# Local E5 is the fixed semantic-search provider. Query/passages are encoded with E5 prefixes.
EMBED_MODE = env("HUB_EMBED_MODE", "e5").lower()
EMBED_MODEL = env("HUB_EMBED_MODEL", "intfloat/multilingual-e5-base")
EMBED_DEVICE = env("HUB_EMBED_DEVICE", "auto").lower()
EMBED_BATCH_SIZE = env_int("HUB_EMBED_BATCH_SIZE", 32, 1, 128)
EMBED_MAX_SEQ_LENGTH = env_int("HUB_EMBED_MAX_SEQ_LENGTH", 512, 64, 1024)
EMBED_QUERY_CACHE_SIZE = env_int("HUB_EMBED_QUERY_CACHE_SIZE", 512, 1, 10000)
EMBED_WARMUP = env_bool("HUB_EMBED_WARMUP", True)
EMBED_TORCH_THREADS = env_int("HUB_EMBED_TORCH_THREADS", 2, 1, 32)

# Long document analysis is split only when needed. Small requests still use one LLM call.
ANALYZE_CHUNK_CHARS = env_int("HUB_ANALYZE_CHUNK_CHARS", 18000, 4000, 40000)
ANALYZE_MAX_CHUNKS = env_int("HUB_ANALYZE_MAX_CHUNKS", 8, 1, 20)
ANALYZE_CONCURRENCY = env_int("HUB_ANALYZE_CONCURRENCY", 2, 1, 4)
RAG_MAX_CHUNKS = env_int("HUB_RAG_MAX_CHUNKS", 16, 1, 32)
RAG_MAX_CONTEXT_CHARS = env_int("HUB_RAG_MAX_CONTEXT_CHARS", 30000, 2000, 60000)


def validate() -> None:
    if AI_MODE not in {"mock", "gemini"}:
        raise RuntimeError("HUB_AI_MODE must be 'mock' or 'gemini'")
    if AI_MODE == "gemini" and not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY is required when HUB_AI_MODE=gemini")

    if OCR_PROVIDER == "local":
        missing = [name for name in ("paddle", "paddleocr") if importlib.util.find_spec(name) is None]
        if missing:
            # PaddleOcrProvider already loads its engine lazily on first use (see paddle_ocr.py),
            # so a missing local install should only fail an actual OCR request, not block startup
            # for installs that never upload scanned images/PDFs.
            print(
                "WARNING: Local OCR requires PaddlePaddle/PaddleOCR (missing: "
                + ", ".join(missing)
                + "). OCR requests will fail until ai-service/requirements-local.txt is installed.",
                file=sys.stderr,
            )

    if STT_PROVIDER not in {"mock", "gemini"}:
        raise RuntimeError("STT_PROVIDER must be mock|gemini")
    if STT_PROVIDER == "gemini" and not GEMINI_API_KEY:
        raise RuntimeError("Gemini STT requires GEMINI_API_KEY")

    if EMBED_MODE not in {"hash", "e5"}:
        raise RuntimeError("HUB_EMBED_MODE must be 'hash' or 'e5'")
    if EMBED_DEVICE not in {"auto", "cpu", "cuda", "mps"}:
        raise RuntimeError("HUB_EMBED_DEVICE must be auto|cpu|cuda|mps")
    if EMBED_MODE == "e5" and importlib.util.find_spec("sentence_transformers") is None:
        raise RuntimeError(
            "sentence-transformers is required when HUB_EMBED_MODE=e5. "
            "Install ai-service/requirements-local.txt."
        )


validate()
