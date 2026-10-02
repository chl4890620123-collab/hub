import httpx
import pytest

from app import config
from app.providers.gemini import GeminiProvider


def test_gemini_38_body_omits_deprecated_sampling_parameters():
    body = GeminiProvider.request_body("hello")
    config = body["generationConfig"]
    assert config == {"responseMimeType": "application/json"}
    assert "temperature" not in config
    assert "top_p" not in config
    assert "top_k" not in config
    assert "candidate_count" not in config


@pytest.mark.asyncio
async def test_quota_exhaustion_uses_alternate_model(monkeypatch):
    calls = []

    def respond(request):
        calls.append(request.url.path)
        if "gemini-2.5-flash" in request.url.path:
            return httpx.Response(429, json={"error": {"message": "quota exceeded"}})
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": '{"tasks": ["QA"]}'}]}}]})

    monkeypatch.setattr(config, "GEMINI_API_KEY", "test-key")
    monkeypatch.setattr(config, "AI_MODE", "gemini")
    monkeypatch.setattr(config, "GEMINI_MODEL", "gemini-2.5-flash")
    monkeypatch.setattr(config, "GEMINI_FALLBACK_MODEL", "gemini-3.6-flash")
    monkeypatch.setattr(config, "GEMINI_RETRIES", 1)

    async def no_wait(_):
        pass

    monkeypatch.setattr("app.providers.gemini.asyncio.sleep", no_wait)
    provider = GeminiProvider()
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        async def get_client():
            return client

        monkeypatch.setattr(provider._pool, "get", get_client)
        assert await provider.json_generate("QA task") == {"tasks": ["QA"]}
    assert calls == [
        "/v1beta/models/gemini-2.5-flash:generateContent",
        "/v1beta/models/gemini-2.5-flash:generateContent",
        "/v1beta/models/gemini-3.6-flash:generateContent",
    ]


@pytest.mark.asyncio
async def test_non_quota_errors_do_not_switch_models(monkeypatch):
    calls = []

    def respond(request):
        calls.append(request.url.path)
        return httpx.Response(403, json={"error": {"message": "invalid permission"}})

    monkeypatch.setattr(config, "GEMINI_API_KEY", "test-key")
    monkeypatch.setattr(config, "AI_MODE", "gemini")
    monkeypatch.setattr(config, "GEMINI_MODEL", "gemini-2.5-flash")
    monkeypatch.setattr(config, "GEMINI_FALLBACK_MODEL", "gemini-3.6-flash")
    provider = GeminiProvider()
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        async def get_client():
            return client

        monkeypatch.setattr(provider._pool, "get", get_client)
        with pytest.raises(httpx.HTTPStatusError) as exc:
            await provider.json_generate("QA task")
    assert exc.value.response.status_code == 403
    assert calls == ["/v1beta/models/gemini-2.5-flash:generateContent"]


@pytest.mark.asyncio
@pytest.mark.parametrize("failure", [500, 502, 503, 504, "timeout", "connect"])
async def test_transient_failure_retries_then_falls_back(monkeypatch, failure):
    calls = []
    def respond(request):
        calls.append(request.url.path)
        if "primary" in request.url.path:
            if failure == "timeout":
                raise httpx.ReadTimeout("", request=request)
            if failure == "connect":
                raise httpx.ConnectError("", request=request)
            return httpx.Response(failure)
        return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": '{"ok":true}'}]}}]})
    monkeypatch.setattr(config, "GEMINI_API_KEY", "test-key")
    monkeypatch.setattr(config, "AI_MODE", "gemini")
    monkeypatch.setattr(config, "GEMINI_MODEL", "primary")
    monkeypatch.setattr(config, "GEMINI_FALLBACK_MODEL", "alternate")
    monkeypatch.setattr(config, "GEMINI_RETRIES", 1)
    async def no_wait(_):
        pass
    monkeypatch.setattr("app.providers.gemini.asyncio.sleep", no_wait)
    provider = GeminiProvider()
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        async def get_client():
            return client
        monkeypatch.setattr(provider._pool, "get", get_client)
        assert await provider.json_generate("test") == {"ok": True}
    assert len(calls) == 3
    assert calls[-1].endswith("alternate:generateContent")


@pytest.mark.asyncio
async def test_exhausted_timeouts_have_nonempty_safe_detail(monkeypatch):
    def respond(request):
        raise httpx.ReadTimeout("", request=request)
    monkeypatch.setattr(config, "GEMINI_API_KEY", "secret-test-key")
    monkeypatch.setattr(config, "AI_MODE", "gemini")
    monkeypatch.setattr(config, "GEMINI_RETRIES", 0)
    monkeypatch.setattr(config, "GEMINI_FALLBACK_MODEL", "")
    provider = GeminiProvider()
    async with httpx.AsyncClient(transport=httpx.MockTransport(respond)) as client:
        async def get_client():
            return client
        monkeypatch.setattr(provider._pool, "get", get_client)
        with pytest.raises(RuntimeError, match="Gemini transport failure: ReadTimeout") as exc:
            await provider.json_generate("private document text")
        assert "secret-test-key" not in str(exc.value)
        assert "private document" not in str(exc.value)
