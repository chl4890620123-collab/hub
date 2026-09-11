# query embeddings are LRU-cached, duplicate texts are encoded once, and passages are batched.
from __future__ import annotations

import re
import threading
from collections import OrderedDict
from typing import Literal

from app import config
from app.models.schemas import EmbedResponse
from app.services.text_utils import hash_vector

VECTOR_DIMENSIONS = 768
InputType = Literal["query", "passage"]


class EmbeddingService:
    def __init__(self) -> None:
        self._model = None
        self._device = "hash"
        self._model_lock = threading.RLock()
        self._cache_lock = threading.RLock()
        self._query_cache: OrderedDict[str, list[float]] = OrderedDict()

    def warmup(self) -> None:
        """Load E5 once per process before user traffic reaches /embed."""
        if config.EMBED_MODE != "e5":
            return
        self._ensure_model()
        self.embed(["Hub semantic search warmup"], "query")

    def info(self) -> dict[str, object]:
        return {
            "mode": config.EMBED_MODE,
            "model": config.EMBED_MODEL if config.EMBED_MODE == "e5" else "hash-3gram-local",
            "device": self._device,
            "loaded": self._model is not None or config.EMBED_MODE != "e5",
            "query_cache_entries": len(self._query_cache),
            "batch_size": config.EMBED_BATCH_SIZE,
        }

    def embed(self, texts: list[str], input_type: InputType = "passage") -> EmbedResponse:
        if input_type not in {"query", "passage"}:
            raise ValueError("input_type must be query or passage")
        prepared = [self._prepare(text, input_type) for text in texts]
        if config.EMBED_MODE != "e5":
            values = [hash_vector(text, VECTOR_DIMENSIONS) for text in prepared]
            return EmbedResponse(model="hash-3gram-local", dimensions=VECTOR_DIMENSIONS, vectors=values)

        model = self._ensure_model()
        if input_type == "query":
            vectors = self._encode_queries(model, prepared)
        else:
            vectors = self._encode_passages(model, prepared)
        return EmbedResponse(model=config.EMBED_MODEL, dimensions=VECTOR_DIMENSIONS, vectors=vectors)

    def _ensure_model(self):
        if self._model is not None:
            return self._model
        with self._model_lock:
            if self._model is not None:
                return self._model
            import torch
            from sentence_transformers import SentenceTransformer

            torch.set_num_threads(config.EMBED_TORCH_THREADS)
            device = self._resolve_device(torch)
            model = SentenceTransformer(config.EMBED_MODEL, device=device)
            model.max_seq_length = config.EMBED_MAX_SEQ_LENGTH
            self._model = model
            self._device = device
            return model

    def _resolve_device(self, torch) -> str:
        if config.EMBED_DEVICE != "auto":
            return config.EMBED_DEVICE
        if torch.cuda.is_available():
            return "cuda"
        mps = getattr(torch.backends, "mps", None)
        if mps is not None and mps.is_available():
            return "mps"
        return "cpu"

    def _encode_queries(self, model, prepared: list[str]) -> list[list[float]]:
        output: list[list[float] | None] = [None] * len(prepared)
        missing: OrderedDict[str, list[int]] = OrderedDict()
        with self._cache_lock:
            for index, text in enumerate(prepared):
                cached = self._query_cache.get(text)
                if cached is not None:
                    self._query_cache.move_to_end(text)
                    output[index] = list(cached)
                else:
                    missing.setdefault(text, []).append(index)

        if missing:
            encoded = self._encode(model, list(missing.keys()))
            with self._cache_lock:
                for text, vector in zip(missing.keys(), encoded):
                    self._query_cache[text] = vector
                    self._query_cache.move_to_end(text)
                    while len(self._query_cache) > config.EMBED_QUERY_CACHE_SIZE:
                        self._query_cache.popitem(last=False)
                    for index in missing[text]:
                        output[index] = list(vector)

        return [vector if vector is not None else [0.0] * VECTOR_DIMENSIONS for vector in output]

    def _encode_passages(self, model, prepared: list[str]) -> list[list[float]]:
        # Repeated chunks can occur after connector retries or template-heavy documents. Encode each
        # distinct passage once inside a request, then expand back to the original ordering.
        unique: OrderedDict[str, list[int]] = OrderedDict()
        for index, text in enumerate(prepared):
            unique.setdefault(text, []).append(index)
        encoded = self._encode(model, list(unique.keys()))
        output: list[list[float] | None] = [None] * len(prepared)
        for text, vector in zip(unique.keys(), encoded):
            for index in unique[text]:
                output[index] = list(vector)
        return [vector if vector is not None else [0.0] * VECTOR_DIMENSIONS for vector in output]

    def _encode(self, model, values: list[str]) -> list[list[float]]:
        if not values:
            return []
        # SentenceTransformer/torch can oversubscribe CPU badly when several FastAPI threads encode
        # simultaneously. One model lock gives predictable latency on the target single-PC deployment.
        with self._model_lock:
            array = model.encode(
                values,
                batch_size=config.EMBED_BATCH_SIZE,
                show_progress_bar=False,
                normalize_embeddings=True,
                convert_to_numpy=True,
            )
        vectors = array.tolist()
        dimensions = len(vectors[0]) if vectors else 0
        if dimensions != VECTOR_DIMENSIONS:
            raise RuntimeError(
                f"Embedding model returned {dimensions} dimensions; Hub pgvector schema requires 768."
            )
        return vectors

    @staticmethod
    def _prepare(text: str, input_type: InputType) -> str:
        normalized = re.sub(r"\s+", " ", (text or "").strip())
        if not normalized:
            normalized = "empty"
        prefix = "query: " if input_type == "query" else "passage: "
        return prefix + normalized
