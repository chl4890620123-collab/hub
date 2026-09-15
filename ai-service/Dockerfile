# Hub AI service. Base API deps stay light; local E5/PaddleOCR are one optional bundle.
FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PYTHONUTF8=1 PYTHONIOENCODING=UTF-8 LANG=C.UTF-8 LC_ALL=C.UTF-8 TOKENIZERS_PARALLELISM=false HF_HOME=/opt/huggingface
WORKDIR /app
RUN useradd --system --create-home --uid 10001 hubai

ARG HUB_INSTALL_LOCAL_MODELS=true
ARG HUB_PRELOAD_E5=false
ARG HUB_EMBED_MODEL=intfloat/multilingual-e5-base
COPY requirements.txt requirements-local.txt ./
# torch has no GPU here, and its default PyPI wheel is the CUDA build (500+MB plus several more
# CUDA sub-packages) - installing the CPU-only build first keeps sentence-transformers (pulled in
# by requirements-local.txt) from redownloading the huge GPU one afterward.
# --timeout/--retries bound pip's own per-read wait so a stalled download fails in minutes instead
# of hanging silently for the build's entire time budget (observed: a 196MB torch wheel stopped
# mid-download with zero further progress for 49 minutes on one deploy attempt, undetected by
# every timeout upstream of pip itself). The outer `timeout` is a hard backstop in case pip's own
# retry logic doesn't trigger for some reason.
RUN python -m pip install --no-cache-dir --timeout 20 --retries 3 -r requirements.txt && \
    if [ "$HUB_INSTALL_LOCAL_MODELS" = "true" ]; then \
      timeout 600 python -m pip install --no-cache-dir --timeout 20 --retries 3 torch --index-url https://download.pytorch.org/whl/cpu && \
      python -m pip install --no-cache-dir --timeout 20 --retries 3 -r requirements-local.txt; \
    fi
RUN mkdir -p /opt/huggingface && \
    if [ "$HUB_INSTALL_LOCAL_MODELS" = "true" ] && [ "$HUB_PRELOAD_E5" = "true" ]; then \
      python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${HUB_EMBED_MODEL}')"; \
    fi && chmod -R a+rX /opt/huggingface
COPY app ./app
RUN chown -R hubai:hubai /app
USER hubai
EXPOSE 8000
CMD ["python","-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8000"]
