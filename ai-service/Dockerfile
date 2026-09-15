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
# The CPU wheel used to come straight from download.pytorch.org, but that download stalled at
# zero progress on 6/6 consecutive attempts across two production deploys (each attempt bounded to
# 5 minutes and retried 3x - all 18 stalled identically), pointing to a persistent network/routing
# problem reaching that specific host from the production machine, not a transient blip. Mirrored
# as a GitHub Release asset instead (same torch-2.14.0+cpu wheel, sha256 pinned below) so the
# download goes over github.com's release CDN, which that machine already reaches reliably for
# every git operation in this same deploy.
ARG HUB_TORCH_CPU_WHEEL_URL=https://github.com/chl4890620123-collab/hub/releases/download/torch-cpu-2.14.0/torch-2.14.0%2Bcpu-cp313-cp313-manylinux_2_28_x86_64.whl
ARG HUB_TORCH_CPU_WHEEL_SHA256=160e1bc46aeded3111d2801f8ae10dc9a1b946843a7e126b4dbf5e19c5706e95
RUN python -m pip install --no-cache-dir --timeout 20 --retries 3 -r requirements.txt && \
    if [ "$HUB_INSTALL_LOCAL_MODELS" = "true" ]; then \
      python -m pip install --no-cache-dir --timeout 20 --retries 3 "${HUB_TORCH_CPU_WHEEL_URL}#sha256=${HUB_TORCH_CPU_WHEEL_SHA256}" && \
      python -m pip install --no-cache-dir --timeout 20 --retries 3 -r requirements-local.txt; \
    fi
RUN mkdir -p /opt/huggingface && \
    if [ "$HUB_INSTALL_LOCAL_MODELS" = "true" ] && [ "$HUB_PRELOAD_E5" = "true" ]; then \
      python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${HUB_EMBED_MODEL}')"; \
    fi && chown -R hubai:hubai /opt/huggingface
COPY app ./app
RUN chown -R hubai:hubai /app
USER hubai
EXPOSE 8000
CMD ["python","-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8000"]
