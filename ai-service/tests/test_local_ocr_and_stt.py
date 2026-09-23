import pytest
from app.providers.gemini_speech import GeminiSpeechProvider
from app.providers.paddle_ocr import PaddleOcrProvider


def test_paddle_result_payload_accepts_wrapped_and_direct_shapes():
    direct = {"rec_texts": ["안녕하세요"], "rec_scores": [0.99]}
    wrapped = {"res": direct}
    assert PaddleOcrProvider._payload(direct)["rec_texts"] == ["안녕하세요"]
    assert PaddleOcrProvider._payload(wrapped)["rec_texts"] == ["안녕하세요"]


def test_gemini_stt_request_reuses_transcribe_model_and_korean():
    body = GeminiSpeechProvider._request_body("files/demo", "audio/wav")
    assert body["model"] == config.GEMINI_TRANSCRIBE_MODEL
    cfg = body["generation_config"]["transcription_config"]
    assert cfg["language_codes"] == ["ko-KR"]
    assert cfg["mode"]["diarization_mode"] == "speaker"
    assert cfg["mode"]["timestamp_granularities"] == ["word"]


def test_gemini_word_annotations_become_speaker_segments():
    words = [
        {"text": "로그인", "speaker": "spk_1", "start_offset": "0.100s", "end_offset": "0.500s"},
        {"text": "수정", "speaker": "spk_1", "start_offset": "0.600s", "end_offset": "1.000s"},
        {"text": "완료", "speaker": "spk_2", "start_offset": "1.100s", "end_offset": "1.400s"},
    ]
    segments = GeminiSpeechProvider._segments(words, "")
    assert len(segments) == 2
    assert segments[0] == {"start_ms": 100, "end_ms": 1000, "speaker": "spk_1", "text": "로그인 수정"}
    assert segments[1]["speaker"] == "spk_2"


def test_paddle_extract_filters_low_confidence_and_removes_temp_file():
    import os

    class FakeEngine:
        seen_path = None

        def predict(self, path):
            self.seen_path = path
            assert os.path.exists(path)
            return [{"rec_texts": ["첫 줄", "낮은 점수", "둘째 줄"], "rec_scores": [0.99, 0.10, 0.95]}]

    provider = PaddleOcrProvider()
    fake = FakeEngine()
    provider._engine = fake
    text = provider._extract_sync("scan.jpg", b"not-a-real-image-needed-by-fake-engine")
    assert text == "첫 줄\n둘째 줄"
    assert fake.seen_path is not None
    assert not os.path.exists(fake.seen_path)


def test_gemini_stt_normalizes_browser_media_recorder_mime_types():
    assert GeminiSpeechProvider._normalize_mime_type("meeting.webm", "audio/webm;codecs=opus") == "audio/webm"
    assert GeminiSpeechProvider._normalize_mime_type("meeting.m4a", "audio/mp4") == "audio/m4a"
    assert GeminiSpeechProvider._normalize_mime_type("meeting.m4a", "application/octet-stream") == "audio/m4a"


def test_gemini_stt_rejects_unsupported_mime_type():
    import pytest

    with pytest.raises(RuntimeError, match="지원하지 않는 음성 형식"):
        GeminiSpeechProvider._normalize_mime_type("meeting.txt", "text/plain")


def test_gemini_stt_error_explains_30_minute_limit():
    import httpx

    request = httpx.Request("POST", "https://example.invalid")
    response = httpx.Response(400, request=request)
    error = GeminiSpeechProvider._transcription_error(httpx.HTTPStatusError("bad request", request=request, response=response))
    assert "30분 이하" in str(error)


@pytest.mark.asyncio
async def test_gemini_stt_full_flow_waits_for_active_and_deletes_remote_file(monkeypatch):
    import httpx
    from app import config

    class FakeClient:
        def __init__(self):
            self.posts = []
            self.gets = []
            self.deletes = []

        async def post(self, url, headers=None, json=None, content=None):
            self.posts.append((url, headers or {}, json, content))
            request = httpx.Request("POST", url)
            if url.endswith("/upload/v1beta/files"):
                return httpx.Response(200, request=request, headers={"x-goog-upload-url": "https://upload.test/file"})
            if url == "https://upload.test/file":
                return httpx.Response(200, request=request, json={"file": {"name": "files/demo", "uri": "files://demo", "state": "PROCESSING"}})
            if url.endswith("/v1beta/interactions"):
                return httpx.Response(200, request=request, json={
                    "output_text": "로그인 수정 완료",
                    "steps": [{"content": [{"annotations": [
                        {"type": "word_info", "text": "로그인", "speaker": "spk_1", "start_offset": "0.1s", "end_offset": "0.4s"},
                        {"type": "word_info", "text": "수정", "speaker": "spk_1", "start_offset": "0.5s", "end_offset": "0.8s"},
                        {"type": "word_info", "text": "완료", "speaker": "spk_2", "start_offset": "0.9s", "end_offset": "1.2s"},
                    ]}]}],
                })
            raise AssertionError(url)

        async def get(self, url, headers=None):
            self.gets.append(url)
            request = httpx.Request("GET", url)
            return httpx.Response(200, request=request, json={"name": "files/demo", "uri": "files://demo", "state": "ACTIVE"})

        async def delete(self, url, headers=None):
            self.deletes.append(url)
            return httpx.Response(200, request=httpx.Request("DELETE", url))

    class FakePool:
        def __init__(self, client):
            self.client = client

        async def get(self):
            return self.client

        async def close(self):
            return None

    fake = FakeClient()
    provider = GeminiSpeechProvider()
    provider.enabled = True
    provider._pool = FakePool(fake)
    monkeypatch.setattr(config, "GEMINI_API_KEY", "test-key")
    monkeypatch.setattr(config, "STT_FILE_POLL_INTERVAL_MS", 1)

    result = await provider.transcribe("meeting.webm", "audio/webm;codecs=opus", b"audio-bytes")

    assert result["text"] == "로그인 수정 완료"
    assert len(result["segments"]) == 2
    assert fake.gets == ["https://generativelanguage.googleapis.com/v1beta/files/demo"]
    assert fake.deletes == ["https://generativelanguage.googleapis.com/v1beta/files/demo"]
    upload_headers = fake.posts[1][1]
    assert upload_headers["Content-Type"] == "audio/webm"
    interaction_body = fake.posts[2][2]
    assert interaction_body["input"][0]["mime_type"] == "audio/webm"
