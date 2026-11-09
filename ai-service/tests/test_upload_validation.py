from io import BytesIO

import pytest
from fastapi import HTTPException, UploadFile

from app import main


@pytest.mark.asyncio
async def test_empty_ai_upload_is_bad_request():
    file = UploadFile(file=BytesIO(b""), filename="empty.wav")
    with pytest.raises(HTTPException) as exc:
        await main.read_limited_upload(file)
    assert exc.value.status_code == 400


@pytest.mark.asyncio
async def test_ai_upload_accepts_exact_limit_and_rejects_one_extra_byte(monkeypatch):
    monkeypatch.setattr(main, "MAX_AI_UPLOAD_BYTES", 4)
    accepted = UploadFile(file=BytesIO(b"1234"), filename="ok.wav")
    assert await main.read_limited_upload(accepted) == b"1234"
    too_large = UploadFile(file=BytesIO(b"12345"), filename="large.wav")
    with pytest.raises(HTTPException) as exc:
        await main.read_limited_upload(too_large)
    assert exc.value.status_code == 413


@pytest.mark.asyncio
async def test_ocr_upload_validation_does_not_turn_413_into_502(monkeypatch):
    monkeypatch.setattr(main, "MAX_AI_UPLOAD_BYTES", 4)
    file = UploadFile(file=BytesIO(b"12345"), filename="large.png")
    with pytest.raises(HTTPException) as exc:
        await main.ocr(file)
    assert exc.value.status_code == 413


@pytest.mark.asyncio
async def test_stt_upload_validation_does_not_turn_400_into_502():
    file = UploadFile(file=BytesIO(b""), filename="empty.wav")
    with pytest.raises(HTTPException) as exc:
        await main.stt(file)
    assert exc.value.status_code == 400
