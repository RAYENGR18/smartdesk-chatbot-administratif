# SmartDesk - service NLP (FastAPI + RAG)
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app
# PyTorch version CPU (quelques centaines de Mo au lieu de plusieurs Go de bibliothèques CUDA)
RUN pip install torch --index-url https://download.pytorch.org/whl/cpu
COPY requirements.txt .
RUN pip install -r requirements.txt \
    && python -m spacy download fr_core_news_sm
COPY . .
RUN useradd --system --uid 10001 --create-home app \
    && mkdir -p /data && chown -R app /data /app/knowledge_base
USER app
ENV NLP_HOST=0.0.0.0 \
    NLP_PORT=8000 \
    CHROMA_DB_PATH=/data/chroma_db \
    CHROMA_DB_PROF_PATH=/data/chroma_db_prof \
    HF_HOME=/data/huggingface
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=300s --retries=20 \
    CMD python -c "import urllib.request,sys; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4)" || exit 1
CMD ["python", "main.py"]
