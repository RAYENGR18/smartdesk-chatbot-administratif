"""
FPST SmartDesk - service NLP + RAG + Groq (FastAPI).

Sécurité :
  - le service n'est appelé QUE par le backend Spring Boot (jamais par le navigateur) :
    pas de CORS, et chaque requête doit porter l'en-tête X-API-Key = NLP_API_KEY ;
  - il écoute par défaut sur 127.0.0.1 (pas accessible depuis le réseau) ;
  - les noms de fichiers de la base de connaissances sont validés
    (aucun accès possible en dehors du dossier knowledge_base).
"""

import hmac
import os
import re
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Optional

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, HTTPException, Query, Request, UploadFile
from pydantic import BaseModel, Field

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
load_dotenv(os.path.join(BASE_DIR, "..", ".env"))
load_dotenv(os.path.join(BASE_DIR, ".env"))

from nlp_service import analyze_message  # noqa: E402
from rag_service import (  # noqa: E402
    CHROMA_DIR, CHROMA_DIR_PROF, DOCUMENTS_DIR, DOCUMENTS_DIR_PROF, DOCUMENTS_DIR_STUDENT,
    GROQ_API_KEY, answer_with_rag, chat_with_groq, create_vector_store, load_vector_store,
    rebuild_vector_store, rewrite_question,
)

NLP_API_KEY = os.getenv("NLP_API_KEY", "")
MAX_UPLOAD_BYTES = 10 * 1024 * 1024
ALLOWED_EXTENSIONS = (".txt", ".pdf", ".docx")
AUDIENCES = {"student", "student_only", "professor", "all"}
SAFE_FILENAME = re.compile(r"^[\w ()\-.]{1,150}$", re.UNICODE)


def _safe_print(msg):
    """Print robuste pour Windows (console cp1252)."""
    try:
        print(msg)
    except UnicodeEncodeError:
        try:
            print(msg.encode("ascii", errors="replace").decode("ascii"))
        except Exception:
            pass


# ============================================
# CONTRÔLE D'ACCÈS : clé partagée avec le backend
# ============================================
async def require_api_key(request: Request):
    if not NLP_API_KEY:
        raise HTTPException(status_code=503, detail="NLP_API_KEY non configuree sur le service NLP")
    provided = request.headers.get("X-API-Key", "")
    if not hmac.compare_digest(provided.encode(), NLP_API_KEY.encode()):
        raise HTTPException(status_code=401, detail="Cle API invalide")


# ============================================
# VALIDATION DES CHEMINS
# ============================================
def _audience(audience: str) -> str:
    a = (audience or "student").strip().lower()
    if a not in AUDIENCES:
        raise HTTPException(status_code=400, detail="Audience invalide")
    return a


def _get_kb_dir(audience: str) -> str:
    """Dossier knowledge_base selon l'audience."""
    if audience == "professor":
        return DOCUMENTS_DIR_PROF
    if audience == "student_only":
        return DOCUMENTS_DIR_STUDENT
    return DOCUMENTS_DIR  # student (défaut) = dossier racine


def _vector_audience(audience: str) -> str:
    return "professor" if audience == "professor" else "student"


def _safe_path(audience: str, filename: str, extensions=ALLOWED_EXTENSIONS) -> str:
    """Chemin absolu du fichier, garanti à l'intérieur du dossier de l'audience."""
    name = os.path.basename((filename or "").replace("\\", "/"))
    if (not name or name != filename or ".." in name
            or not SAFE_FILENAME.match(name) or not name.lower().endswith(extensions)):
        raise HTTPException(status_code=400, detail="Nom de fichier invalide")
    base = os.path.realpath(_get_kb_dir(audience))
    path = os.path.realpath(os.path.join(base, name))
    if os.path.dirname(path) != base:
        raise HTTPException(status_code=400, detail="Nom de fichier invalide")
    return path


# ============================================
# AUTO-INDEX AU DÉMARRAGE
# ============================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    _safe_print("[START] FPST SmartDesk NLP Service demarrage...")
    if not NLP_API_KEY:
        _safe_print("[WARN] NLP_API_KEY absente : toutes les requetes seront refusees (voir .env)")

    try:
        if load_vector_store() is None:
            _safe_print("[INFO] Premiere execution - indexation de la base de connaissances...")
            if create_vector_store():
                _safe_print("[OK] Base de connaissances indexee")
            else:
                _safe_print("[WARN] Aucun document trouve dans knowledge_base/")
        else:
            _safe_print("[OK] Base vectorielle existante chargee")
    except Exception as e:
        _safe_print(f"[ERR] Erreur indexation au demarrage: {e}")

    if GROQ_API_KEY:
        _safe_print("[OK] Cle Groq API configuree")
    else:
        _safe_print("[WARN] GROQ_API_KEY non configuree : le chatbot repondra en mode degrade")

    _safe_print("[READY] FPST SmartDesk est pret !")
    yield
    _safe_print("[STOP] FPST SmartDesk arrete.")


app = FastAPI(
    title="FPST SmartDesk - service NLP",
    description="Service NLP + RAG + Groq, appele uniquement par le backend",
    version="2.2",
    lifespan=lifespan,
    # Pas de documentation interactive exposée par défaut
    docs_url="/docs" if os.getenv("NLP_ENABLE_DOCS") == "true" else None,
    redoc_url=None,
    openapi_url="/openapi.json" if os.getenv("NLP_ENABLE_DOCS") == "true" else None,
)

secured = [Depends(require_api_key)]


# ============================================
# MODÈLES DE REQUÊTE (tailles bornées)
# ============================================
class MessageRequest(BaseModel):
    message: str = Field(..., max_length=2000)
    language: str = Field("fr", max_length=5)
    conversation_history: Optional[list] = Field(None, max_length=40)


class RagRequest(BaseModel):
    question: str = Field(..., max_length=2000)
    language: str = Field("fr", max_length=5)
    user_role: str = Field("ETUDIANT", max_length=20)
    conversation_history: Optional[list] = Field(None, max_length=40)


class GroqChatRequest(BaseModel):
    message: str = Field(..., max_length=2000)
    language: str = Field("fr", max_length=5)
    student_context: Optional[dict] = None
    user_role: str = Field("ETUDIANT", max_length=20)
    conversation_history: Optional[list] = Field(None, max_length=40)


class UpdateContentRequest(BaseModel):
    content: str = Field(..., max_length=2_000_000)


class RewriteRequest(BaseModel):
    message: str = Field(..., max_length=2000)
    language: str = Field("fr", max_length=5)
    conversation_history: Optional[list] = Field(None, max_length=40)


# ============================================
# NLP / RAG / GROQ
# ============================================
@app.post("/analyze", dependencies=secured)
async def analyze(request: MessageRequest):
    return analyze_message(request.message, request.language, request.conversation_history)


@app.post("/rewrite", dependencies=secured)
async def rewrite(request: RewriteRequest):
    """Reformule une question de suivi ambiguë en question autonome."""
    rewritten = rewrite_question(request.message, request.conversation_history or [], request.language)
    return {"original": request.message, "rewritten": rewritten}


@app.post("/rag/ask", dependencies=secured)
async def ask_rag(request: RagRequest):
    return answer_with_rag(request.question, request.language, request.user_role,
                           request.conversation_history)


@app.post("/groq/chat", dependencies=secured)
async def groq_chat(request: GroqChatRequest):
    return chat_with_groq(request.message, request.language, request.user_role,
                          request.student_context, request.conversation_history)


# ============================================
# BASE DE CONNAISSANCES
# ============================================
@app.post("/rag/upload", dependencies=secured)
async def upload_document(file: UploadFile = File(...),
                          audience: str = Query(default="student")):
    aud = _audience(audience)
    filepath = _safe_path(aud, file.filename)
    os.makedirs(os.path.dirname(filepath), exist_ok=True)

    data = await file.read(MAX_UPLOAD_BYTES + 1)
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Fichier trop volumineux (10 Mo maximum)")
    with open(filepath, "wb") as buffer:
        buffer.write(data)

    try:
        rebuild_vector_store(_vector_audience(aud))
        return {"message": f"Document '{os.path.basename(filepath)}' indexe avec succes ({aud})",
                "status": "success"}
    except Exception as e:
        _safe_print(f"[ERR] Indexation apres upload: {e}")
        return {"message": "Fichier sauvegarde mais indexation echouee", "status": "partial"}


@app.post("/rag/index", dependencies=secured)
async def index_all(audience: str = Query(default="all")):
    """Force la réindexation complète."""
    aud = _audience(audience)
    targets = []
    if aud in ("student", "student_only", "all"):
        targets.append("student")
    if aud in ("professor", "all"):
        targets.append("professor")

    results = []
    for target in targets:
        try:
            vs = rebuild_vector_store(target)
            results.append(f"{target}: {'OK' if vs else 'vide'}")
        except Exception as e:
            _safe_print(f"[ERR] Reindexation {target}: {e}")
            results.append(f"{target}: ERREUR")
    return {"message": "Reindexation terminee - " + " | ".join(results), "status": "success"}


@app.get("/rag/status", dependencies=secured)
async def rag_status():
    """État du service (sans aucune information secrète)."""
    def count_files(path):
        if os.path.exists(path):
            return len([f for f in os.listdir(path)
                        if os.path.isfile(os.path.join(path, f)) and f.lower().endswith(ALLOWED_EXTENSIONS)])
        return 0

    has_vs_student = os.path.exists(CHROMA_DIR) and bool(os.listdir(CHROMA_DIR))
    has_vs_prof = os.path.exists(CHROMA_DIR_PROF) and bool(os.listdir(CHROMA_DIR_PROF))
    has_groq_key = bool(GROQ_API_KEY)

    return {
        "assistant_name": "FPST SmartDesk",
        "version": "2.2",
        "vector_store_ready": has_vs_student,
        "vector_store_prof_ready": has_vs_prof,
        "groq_configured": has_groq_key,
        "knowledge_base_files": count_files(DOCUMENTS_DIR) + count_files(DOCUMENTS_DIR_STUDENT),
        "professor_files": count_files(DOCUMENTS_DIR_PROF),
        "status": "ready" if (has_groq_key and has_vs_student) else
                  "partial" if has_groq_key else "missing_api_key",
    }


@app.get("/rag/documents", dependencies=secured)
async def list_documents(audience: str = Query(default="student")):
    aud = _audience(audience)
    kb_path = _get_kb_dir(aud)
    os.makedirs(kb_path, exist_ok=True)
    docs = []
    for f in sorted(os.listdir(kb_path)):
        filepath = os.path.join(kb_path, f)
        if not os.path.isfile(filepath) or not f.lower().endswith(ALLOWED_EXTENSIONS):
            continue
        stat = os.stat(filepath)
        docs.append({
            "name": f,
            "size": stat.st_size,
            "modified": datetime.fromtimestamp(stat.st_mtime).strftime("%d/%m/%Y %H:%M"),
            "type": f.rsplit(".", 1)[-1].upper(),
            "editable": f.lower().endswith(".txt"),
            "audience": aud,
        })
    return {"documents": docs, "count": len(docs)}


@app.delete("/rag/documents/{filename}", dependencies=secured)
async def delete_document(filename: str, audience: str = Query(default="student")):
    aud = _audience(audience)
    filepath = _safe_path(aud, filename)
    if not os.path.isfile(filepath):
        raise HTTPException(status_code=404, detail="Fichier non trouve")
    os.remove(filepath)
    try:
        rebuild_vector_store(_vector_audience(aud))
    except Exception as e:
        _safe_print(f"[ERR] Reindexation apres suppression: {e}")
    return {"message": f"Document '{filename}' supprime et base reindexee", "status": "success"}


@app.get("/rag/documents/{filename}/content", dependencies=secured)
async def get_document_content(filename: str, audience: str = Query(default="student")):
    aud = _audience(audience)
    filepath = _safe_path(aud, filename, (".txt",))
    if not os.path.isfile(filepath):
        raise HTTPException(status_code=404, detail="Fichier non trouve")
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()
    return {"filename": filename, "content": content, "audience": aud}


@app.put("/rag/documents/{filename}/content", dependencies=secured)
async def update_document_content(filename: str, body: UpdateContentRequest,
                                  audience: str = Query(default="student")):
    aud = _audience(audience)
    filepath = _safe_path(aud, filename, (".txt",))
    os.makedirs(os.path.dirname(filepath), exist_ok=True)

    try:
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(body.content)
    except OSError as e:
        _safe_print(f"[ERR] Ecriture {filepath}: {e}")
        raise HTTPException(status_code=500, detail="Erreur d'ecriture du fichier")

    try:
        rebuild_vector_store(_vector_audience(aud))
        return {"message": f"Contenu de '{filename}' mis a jour et base reindexee", "status": "success"}
    except Exception as e:
        _safe_print(f"[ERR] Reindexation: {e}")
        return {"message": f"Fichier '{filename}' sauvegarde. Reindexation en attente.",
                "status": "partial"}


# ============================================
# SANTÉ (public, sans information sensible)
# ============================================
@app.get("/health")
async def health():
    return {"status": "UP", "service": "FPST SmartDesk NLP", "version": "2.2"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=os.getenv("NLP_HOST", "127.0.0.1"),
        port=int(os.getenv("NLP_PORT", "8000")),
    )
