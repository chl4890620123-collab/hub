from __future__ import annotations

import asyncio
import re
import time
from pathlib import Path

import httpx

from app import config
from app.providers.http_client_pool import LazyAsyncClient


class GeminiSpeechProvider:
    """Gemini 3.5 Transcribe provider using the existing GEMINI_API_KEY.

    Browser MediaRecorder MIME values are normalized to the exact audio MIME types
    accepted by Gemini. Uploaded files are also polled until ACTIVE before the
    transcription request so slower media processing does not cause intermittent 400s.
    """

    _SUPPORTED_MIME_TYPES = {
        "audio/wav", "audio/mp3", "audio/aiff", "audio/aac", "audio/ogg",
        "audio/flac", "audio/mpeg", "audio/m4a", "audio/l16", "audio/opus",
        "audio/alaw", "audio/mulaw", "audio/webm",
    }
    _EXTENSION_MIME_TYPES = {
        ".wav": "audio/wav",
        ".mp3": "audio/mp3",
        ".aiff": "audio/aiff",
        ".aif": "audio/aiff",
        ".aac": "audio/aac",
        ".ogg": "audio/ogg",
        ".oga": "audio/ogg",
        ".flac": "audio/flac",
        ".mpeg": "audio/mpeg",
        ".m4a": "audio/m4a",
        ".mp4": "audio/m4a",
        ".opus": "audio/opus",
        ".webm": "audio/webm",
    }
    _MIME_ALIASES = {
        "audio/x-wav": "audio/wav",
        "audio/wave": "audio/wav",
        "audio/x-m4a": "audio/m4a",
        "audio/mp4": "audio/m4a",
        "audio/x-mp3": "audio/mp3",
    }

    def __init__(self) -> None:
        self.enabled = bool(config.GEMINI_API_KEY) and config.AI_MODE == "gemini"
        self._pool = LazyAsyncClient(
            timeout=httpx.Timeout(connect=10.0, read=600.0, write=600.0, pool=10.0),
            limits=httpx.Limits(max_connections=5, max_keepalive_connections=3),
        )

    async def startup(self) -> None:
        if self.enabled:
            await self._pool.get()

    async def close(self) -> None:
        await self._pool.close()

    def info(self) -> dict[str, object]:
        return {
            "provider": "gemini" if self.enabled else "unavailable",
            "model": config.GEMINI_TRANSCRIBE_MODEL,
            "language": config.STT_LANGUAGE,
            "diarization": config.STT_DIARIZATION,
            "word_timestamps": config.STT_WORD_TIMESTAMPS,
        }

    async def transcribe(self, file_name: str, content_type: str, data: bytes) -> dict:
        if not self.enabled:
            raise RuntimeError("Gemini STT requires HUB_AI_MODE=gemini and GEMINI_API_KEY")
        if not data:
            raise RuntimeError("STT audio file is empty")

        client = await self._pool.get()
        mime_type = self._normalize_mime_type(file_name, content_type)
        file_meta = await self._upload(client, file_name, mime_type, data)
        file_resource = str(file_meta.get("name") or "")
        if not file_resource:
            raise RuntimeError("Gemini Files API returned no file resource name")

        try:
            file_meta = await self._wait_until_active(client, file_meta)
            file_uri = str(file_meta.get("uri") or "")
            if not file_uri:
                raise RuntimeError("Gemini Files API returned no file URI")
            response = await client.post(
                "https://generativelanguage.googleapis.com/v1beta/interactions",
                headers={"x-goog-api-key": config.GEMINI_API_KEY, "Content-Type": "application/json"},
                json=self._request_body(file_uri, mime_type),
            )
            try:
                response.raise_for_status()
            except httpx.HTTPStatusError as exc:
                raise self._transcription_error(exc) from exc
            raw = response.json()
            text = self._output_text(raw)
            words = self._word_annotations(raw)
            return {
                "text": text,
                "segments": self._segments(words, text),
                "raw": raw,
            }
        finally:
            await self._delete_file(client, file_resource)

    async def _upload(self, client: httpx.AsyncClient, file_name: str, mime_type: str, data: bytes) -> dict:
        headers = {
            "x-goog-api-key": config.GEMINI_API_KEY,
            "X-Goog-Upload-Protocol": "resumable",
            "X-Goog-Upload-Command": "start",
            "X-Goog-Upload-Header-Content-Length": str(len(data)),
            "X-Goog-Upload-Header-Content-Type": mime_type,
            "Content-Type": "application/json",
        }
        start = await client.post(
            "https://generativelanguage.googleapis.com/upload/v1beta/files",
            headers=headers,
            json={"file": {"display_name": file_name}},
        )
        start.raise_for_status()
        upload_url = start.headers.get("x-goog-upload-url")
        if not upload_url:
            raise RuntimeError("Gemini Files API returned no upload URL")
        upload = await client.post(
            upload_url,
            headers={
                "Content-Length": str(len(data)),
                "X-Goog-Upload-Offset": "0",
                "X-Goog-Upload-Command": "upload, finalize",
                "Content-Type": mime_type,
            },
            content=data,
        )
        upload.raise_for_status()
        payload = upload.json()
        return payload.get("file") or {}

    async def _wait_until_active(self, client: httpx.AsyncClient, file_meta: dict) -> dict:
        state = str(file_meta.get("state") or "").upper()
        if state in {"ACTIVE", ""}:
            # Audio uploads are often immediately usable. If state is omitted, preserve
            # compatibility with the Files API response used by the official examples.
            return file_meta
        if state == "FAILED":
            raise RuntimeError("Gemini에서 음성 파일 처리에 실패했습니다. 다른 음성 파일로 다시 시도해 주세요.")

        deadline = time.monotonic() + config.STT_FILE_READY_TIMEOUT_SECONDS
        resource = str(file_meta.get("name") or "").lstrip("/")
        while state == "PROCESSING" and time.monotonic() < deadline:
            await asyncio.sleep(config.STT_FILE_POLL_INTERVAL_MS / 1000.0)
            response = await client.get(
                f"https://generativelanguage.googleapis.com/v1beta/{resource}",
                headers={"x-goog-api-key": config.GEMINI_API_KEY},
            )
            response.raise_for_status()
            payload = response.json()
            file_meta = payload.get("file") if isinstance(payload.get("file"), dict) else payload
            state = str(file_meta.get("state") or "").upper()
            if state == "FAILED":
                raise RuntimeError("Gemini에서 음성 파일 처리에 실패했습니다. 다른 음성 파일로 다시 시도해 주세요.")
        if state != "ACTIVE":
            raise RuntimeError("Gemini 음성 파일 준비 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.")
        return file_meta

    @classmethod
    def _normalize_mime_type(cls, file_name: str, content_type: str | None) -> str:
        raw = (content_type or "").split(";", 1)[0].strip().lower()
        if raw in {"", "application/octet-stream"}:
            raw = cls._EXTENSION_MIME_TYPES.get(Path(file_name or "").suffix.lower(), "")
        raw = cls._MIME_ALIASES.get(raw, raw)
        if raw not in cls._SUPPORTED_MIME_TYPES:
            supported = ", ".join(sorted(cls._SUPPORTED_MIME_TYPES))
            raise RuntimeError(f"지원하지 않는 음성 형식입니다: {content_type or file_name}. 지원 형식: {supported}")
        return raw

    @staticmethod
    def _transcription_error(exc: httpx.HTTPStatusError) -> RuntimeError:
        status = exc.response.status_code
        if status in {400, 413}:
            return RuntimeError(
                "음성 파일을 변환하지 못했습니다. 현재 화자 분리/단어 타임스탬프를 켠 회의 음성은 "
                "30분 이하의 지원 형식(WAV, MP3, M4A, WebM 등)으로 올려 주세요."
            )
        if status in {401, 403}:
            return RuntimeError("Gemini API 키 또는 사용 권한을 확인해 주세요.")
        if status == 429:
            return RuntimeError("Gemini 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.")
        return RuntimeError(f"Gemini STT 요청에 실패했습니다. HTTP {status}")

    @staticmethod
    def _request_body(file_uri: str, mime_type: str) -> dict:
        mode: str | dict[str, object]
        if config.STT_DIARIZATION or config.STT_WORD_TIMESTAMPS:
            mode = {"type": "verbatim"}
            if config.STT_DIARIZATION:
                mode["diarization_mode"] = "speaker"
            if config.STT_WORD_TIMESTAMPS:
                mode["timestamp_granularities"] = ["word"]
        else:
            mode = "smart"
        return {
            "model": config.GEMINI_TRANSCRIBE_MODEL,
            "input": [{"type": "audio", "uri": file_uri, "mime_type": mime_type}],
            "generation_config": {
                "transcription_config": {
                    "language_codes": [config.STT_LANGUAGE] if config.STT_LANGUAGE else [],
                    "mode": mode,
                }
            },
        }

    async def _delete_file(self, client: httpx.AsyncClient, file_resource: str) -> None:
        resource = file_resource.lstrip("/")
        try:
            await client.delete(
                f"https://generativelanguage.googleapis.com/v1beta/{resource}",
                headers={"x-goog-api-key": config.GEMINI_API_KEY},
            )
        except httpx.HTTPError:
            # File cleanup must not discard a successful transcript.
            pass

    @staticmethod
    def _output_text(raw: dict) -> str:
        direct = raw.get("output_text")
        if isinstance(direct, str) and direct.strip():
            return direct.strip()
        texts: list[str] = []
        for step in raw.get("steps") or []:
            for content in step.get("content") or []:
                text = content.get("text")
                if isinstance(text, str) and text.strip():
                    texts.append(text.strip())
        return "\n".join(texts).strip()

    @staticmethod
    def _word_annotations(raw: dict) -> list[dict]:
        words: list[dict] = []
        for step in raw.get("steps") or []:
            for content in step.get("content") or []:
                for annotation in content.get("annotations") or []:
                    if annotation.get("type") == "word_info" and annotation.get("text"):
                        words.append(annotation)
        return words

    @classmethod
    def _segments(cls, words: list[dict], full_text: str) -> list[dict]:
        if not words:
            return [{"start_ms": None, "end_ms": None, "speaker": "Speaker 1", "text": full_text}] if full_text else []
        segments: list[dict] = []
        current: dict | None = None
        for word in words:
            speaker = str(word.get("speaker") or "Speaker 1")
            text = str(word.get("text") or "").strip()
            if not text:
                continue
            start_ms = cls._offset_ms(word.get("start_offset"))
            end_ms = cls._offset_ms(word.get("end_offset"))
            if current is None or current["speaker"] != speaker:
                current = {"start_ms": start_ms, "end_ms": end_ms, "speaker": speaker, "text": text}
                segments.append(current)
            else:
                current["text"] = cls._join_word(str(current["text"]), text)
                current["end_ms"] = end_ms if end_ms is not None else current["end_ms"]
        return segments

    @staticmethod
    def _offset_ms(value: object) -> int | None:
        if not isinstance(value, str):
            return None
        match = re.fullmatch(r"\s*([0-9]+(?:\.[0-9]+)?)s\s*", value)
        return int(float(match.group(1)) * 1000) if match else None

    @staticmethod
    def _join_word(left: str, right: str) -> str:
        if not left:
            return right
        if right in {".", ",", "!", "?", ":", ";", ")", "]", "}"}:
            return left + right
        return left + " " + right
