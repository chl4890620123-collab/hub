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
from app.services.embedding import EmbeddingService

gemini = GeminiProvider()
ocr_provider = create_ocr_provider()
speech = create_speech_provider()
embedding = EmbeddingService()
analyzer = Analyzer(gemini)
rag = RagService(gemini)
changes = ChangeService(gemini)


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
        return await analyzer.analyze(req.text, req.source_date)
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.post("/api/v1/rag", response_model=RagResponse)
async def rag_answer(req: RagRequest):
    try:
        return await rag.answer(req)
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.post("/api/v1/changes", response_model=ChangeResponse)
async def change(req: ChangeRequest):
    try:
        return await changes.compare(req.before, req.after)
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.post("/api/v1/ocr")
async def ocr(file: UploadFile = File(...)):
    try:
        data = await file.read()
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
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.post("/api/v1/stt")
async def stt(file: UploadFile = File(...)):
    try:
        return await speech.transcribe(
            file.filename or "meeting.wav",
            file.content_type or "audio/wav",
            await file.read(),
        )
    except Exception as e:
        raise HTTPException(status_code=502, detail=str(e))


@app.post("/api/v1/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    return embedding.embed(req.texts, req.input_type)
