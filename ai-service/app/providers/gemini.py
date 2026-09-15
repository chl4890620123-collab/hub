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

    @staticmethod
    def _retry_delay(response: httpx.Response, attempt: int) -> float:
        """
        A quarter-second backoff is fine for a blip but never for a quota: importing a folder fires
        several analyses at once and Gemini answers 429, so a rate limit waits as long as the API asks
        (Retry-After, or Google's retryDelay in the error body) before falling back to exponential.
        """
        if response.status_code != 429:
            return min(2.0, 0.25 * (2**attempt))
        header = response.headers.get("Retry-After")
        if header:
            try:
                return max(1.0, min(float(header), config.GEMINI_MAX_RETRY_DELAY_SECONDS))
            except ValueError:
                pass
        try:
            for detail in response.json().get("error", {}).get("details", []):
                delay = str(detail.get("retryDelay", ""))
                if delay.endswith("s"):
                    return max(1.0, min(float(delay[:-1]), config.GEMINI_MAX_RETRY_DELAY_SECONDS))
        except Exception:  # noqa: BLE001 - a malformed quota body must not mask the original 429
            pass
        return min(config.GEMINI_MAX_RETRY_DELAY_SECONDS, 2.0 * (2**attempt))

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
            await asyncio.sleep(self._retry_delay(response, attempt))
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
