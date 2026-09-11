import asyncio

import httpx


class LazyAsyncClient:
    """One lazily-created httpx client per provider; centralizes safe connection reuse and shutdown."""

    def __init__(self, *, timeout: httpx.Timeout, limits: httpx.Limits, http2: bool = False) -> None:
        self._timeout = timeout
        self._limits = limits
        self._http2 = http2
        self._client: httpx.AsyncClient | None = None
        self._lock = asyncio.Lock()

    async def get(self) -> httpx.AsyncClient:
        if self._client is not None:
            return self._client
        async with self._lock:
            if self._client is None:
                self._client = httpx.AsyncClient(timeout=self._timeout, limits=self._limits, http2=self._http2)
        return self._client

    async def close(self) -> None:
        client, self._client = self._client, None
        if client is not None:
            await client.aclose()
