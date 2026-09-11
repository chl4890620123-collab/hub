# with bounded retries for transient 429/5xx responses and no API key in the URL.
import asyncio
import json
import re

import httpx

from app import config
from app.providers.http_client_pool import LazyAsyncClient


class GeminiProvider:
    def __init__(self) -> None:
        self.enabled = bool(config.GEMINI_API_KEY) and config.AI_MODE == "gemini"
        timeout = httpx.Timeout(
            connect=config.GEMINI_CONNECT_TIMEOUT_SECONDS,
            read=config.GEMINI_READ_TIMEOUT_SECONDS,
            write=30.0,
            pool=5.0,
        )
        limits = httpx.Limits(
            max_connections=config.GEMINI_MAX_CONNECTIONS,
            max_keepalive_connections=config.GEMINI_MAX_KEEPALIVE_CONNECTIONS,
            keepalive_expiry=30.0,
        )
        self._pool = LazyAsyncClient(timeout=timeout, limits=limits, http2=False)

    @staticmethod
    def request_body(prompt: str) -> dict:
        return {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {"responseMimeType": "application/json"},
        }

    async def startup(self) -> None:
        if self.enabled:
            await self._pool.get()

    async def close(self) -> None:
        await self._pool.close()

    async def json_generate(self, prompt: str) -> dict:
        if not self.enabled:
            raise RuntimeError("Gemini provider is not enabled")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{config.GEMINI_MODEL}:generateContent"
        headers = {"x-goog-api-key": config.GEMINI_API_KEY, "Content-Type": "application/json"}
        client = await self._pool.get()
        response: httpx.Response | None = None
        for attempt in range(config.GEMINI_RETRIES + 1):
            response = await client.post(url, headers=headers, json=self.request_body(prompt))
            if response.status_code not in {429, 500, 502, 503, 504} or attempt >= config.GEMINI_RETRIES:
                break
            await asyncio.sleep(min(2.0, 0.25 * (2**attempt)))
        assert response is not None
        response.raise_for_status()
        data = response.json()
        try:
            text = data["candidates"][0]["content"]["parts"][0]["text"]
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError("Gemini returned no JSON text candidate") from exc
        text = re.sub(r"^```(?:json)?\s*|\s*```$", "", text.strip())
        try:
            value = json.loads(text)
        except json.JSONDecodeError as exc:
            raise RuntimeError("Gemini returned invalid JSON") from exc
        if not isinstance(value, dict):
            raise RuntimeError("Gemini JSON response must be an object")
        return value
