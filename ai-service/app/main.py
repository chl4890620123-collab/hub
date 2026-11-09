# External providers are warmed/closed once; endpoints preserve the existing Spring API contract.
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, UploadFile, File, HTTPException
from app import config
from app.models.schemas import *
from app.providers.gemini import GeminiProvider
from app.providers.ocr import create_ocr_provider
from app.providers.speech import create_speech_provider
from app.services.analyzer import Analyzer
from app.services.rag import RagService
from app.services.change import ChangeService
from app.services.revise import ReviseService
from app.services.embedding import EmbeddingService

gemini = GeminiProvider()
ocr_provider = create_ocr_provider()
speech = create_speech_provider()
embedding = EmbeddingService()
analyzer = Analyzer(gemini)
rag = RagService(gemini)
changes = ChangeService(gemini)
reviser = ReviseService(gemini)


@asynccontextmanager
async def lifespan(_: FastAPI):
    await gemini.startup()
    await ocr_provider.startup()
    await speech.startup()
    if config.EMBED_WARMUP:
        await asyncio.to_thread(embedding.warmup)
    yield
    await gemini.close()
    await ocr_provider.close()
    await speech.close()


app = FastAPI(title="Hub AI Service", version="0.4.0", lifespan=lifespan)

# Keep uploads bounded even when the AI service is called directly. Match the
# Spring multipart limit so a large upload cannot reach the provider in memory.
MAX_AI_UPLOAD_BYTES = 100 * 1024 * 1024


async def read_limited_upload(file: UploadFile) -> bytes:
    data = await file.read(MAX_AI_UPLOAD_BYTES + 1)
    if not data:
        raise HTTPException(status_code=400, detail="빈 파일은 분석할 수 없습니다.")
    if len(data) > MAX_AI_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="파일 크기는 100MB 이하여야 합니다.")
    return data


@app.get("/health")
def health():
    return {
        "status": "ok",
        "mode": config.AI_MODE,
        "gemini": bool(config.GEMINI_API_KEY),
        "ocr": ocr_provider.info(),
        "stt": speech.info(),
        "embedding": embedding.info(),
    }


@app.post("/api/v1/analyze", response_model=AnalyzeResponse)
async def analyze(req: AnalyzeRequest):
    try:
        return await analyzer.analyze(req.text, req.source_date, req.project_members)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/api/v1/rag", response_model=RagResponse)
async def rag_answer(req: RagRequest):
    try:
        return await rag.answer(req)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/api/v1/changes", response_model=ChangeResponse)
async def change(req: ChangeRequest):
    try:
        return await changes.compare(req.before, req.after)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/api/v1/revise", response_model=ReviseResponse)
async def revise(req: ReviseRequest):
    try:
        return await reviser.revise(req)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/api/v1/ocr")
async def ocr(file: UploadFile = File(...)):
    try:
        data = await read_limited_upload(file)
        file_name = file.filename or "image.jpg"
        content_type = file.content_type or "image/jpeg"
        text = await ocr_provider.extract(file_name, content_type, data)
        return {
            "text": text,
            "metadata": [{
                "source": file_name,
                "input_type": "uploaded_document_ocr",
                "ocr_provider": ocr_provider.info().get("provider", "unknown"),
            }],
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/api/v1/stt")
async def stt(file: UploadFile = File(...)):
    try:
        return await speech.transcribe(
            file.filename or "meeting.wav",
            file.content_type or "audio/wav",
            await read_limited_upload(file),
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@app.post("/api/v1/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    return embedding.embed(req.texts, req.input_type)
