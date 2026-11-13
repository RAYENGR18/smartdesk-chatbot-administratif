from fastapi import FastAPI, UploadFile, File, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Literal
from dotenv import load_dotenv
from contextlib import asynccontextmanager
from nlp_service import analyze_message
from rag_service import (answer_with_rag, index_document, create_vector_store,
                         chat_with_groq, load_vector_store, rewrite_question,
                         DOCUMENTS_DIR, DOCUMENTS_DIR_STUDENT, DOCUMENTS_DIR_PROF,
                         CHROMA_DIR, CHROMA_DIR_PROF)
import shutil
import os
from datetime import datetime

def _get_kb_dir(audience: str) -> str:
    """Retourne le dossier knowledge_base selon l'audience."""
    if audience == "professor":
        return DOCUMENTS_DIR_PROF
    elif audience == "student_only":
        return DOCUMENTS_DIR_STUDENT
    else:  # student (défaut) = dossier racine
        return DOCUMENTS_DIR

# Charger les variables d'environnement
load_dotenv()

# ============================================
# AUTO-INDEX AU DÉMARRAGE
# ============================================
def _safe_print(msg):
    """Print robuste pour Windows (encodage cp1252 compatible)."""
    try:
        print(msg)
    except UnicodeEncodeError:
        try:
            print(msg.encode('ascii', errors='replace').decode('ascii'))
        except Exception:
            pass

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Indexe automatiquement la base de connaissances au démarrage"""
    _safe_print("[START] FPST SmartDesk NLP Service demarrage...")
    _safe_print("[INFO] Verification de la base de connaissances...")

    vector_store = load_vector_store()
    if vector_store is None:
        _safe_print("[INFO] Premiere execution - Indexation de la base de connaissances...")
        try:
            result = create_vector_store()
            if result:
                _safe_print("[OK] Base de connaissances indexee avec succes !")
            else:
                _safe_print("[WARN] Aucun document trouve dans knowledge_base/")
        except Exception as e:
            _safe_print(f"[ERR] Erreur indexation au demarrage: {e}")
    else:
        _safe_print("[OK] Base vectorielle deja existante, chargee.")

    # Compter les fichiers knowledge_base
    kb_path = "knowledge_base/"
    if os.path.exists(kb_path):
        files = [f for f in os.listdir(kb_path) if f.endswith(('.txt', '.pdf', '.docx'))]
        _safe_print(f"[INFO] {len(files)} fichiers dans la base de connaissances")

    groq_key = os.getenv("GROQ_API_KEY")
    if groq_key:
        _safe_print(f"[OK] Cle Groq API configuree ({groq_key[:10]}...)")
    else:
        _safe_print("[WARN] GROQ_API_KEY non configuree !")

    _safe_print("[READY] FPST SmartDesk est pret !")
    yield
    _safe_print("[STOP] FPST SmartDesk arrete.")

app = FastAPI(
    title="FPST SmartDesk — Faculty Assistant & Smart Technology Oracle",
    description="Service NLP + RAG + Groq pour la gestion de faculté privée",
    version="2.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:4200", "http://localhost:8080", "http://localhost:61748", "*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

class MessageRequest(BaseModel):
    message: str
    language: str = "fr"
    conversation_history: Optional[list] = None

class RagRequest(BaseModel):
    question: str
    language: str = "fr"
    user_role: str = "ETUDIANT"
    conversation_history: Optional[list] = None

class GroqChatRequest(BaseModel):
    message: str
    language: str = "fr"
    student_context: Optional[dict] = None
    user_role: str = "ETUDIANT"
    conversation_history: Optional[list] = None

class UpdateContentRequest(BaseModel):
    content: str

class RewriteRequest(BaseModel):
    message: str
    language: str = "fr"
    conversation_history: Optional[list] = None

# ============================================
# ROUTE PRINCIPALE NLP
# ============================================
@app.post("/analyze")
async def analyze(request: MessageRequest):
    result = analyze_message(
        request.message,
        request.language,
        request.conversation_history
    )
    return result

# ============================================
# ROUTE REWRITE — REFORMULATION DE REQUÊTE
# ============================================
@app.post("/rewrite")
async def rewrite(request: RewriteRequest):
    """Rewrites an ambiguous follow-up into a standalone question using conversation history."""
    rewritten = rewrite_question(
        request.message,
        request.conversation_history or [],
        request.language
    )
    return {"original": request.message, "rewritten": rewritten}

# ============================================
# ROUTE RAG — QUESTION SUR LES DOCUMENTS
# ============================================
@app.post("/rag/ask")
async def ask_rag(request: RagRequest):
    result = answer_with_rag(
        request.question,
        request.language,
        request.user_role,
        request.conversation_history
    )
    return result

# ============================================
# ROUTE GROQ — CHAT DIRECT AVEC IA
# ============================================
@app.post("/groq/chat")
async def groq_chat(request: GroqChatRequest):
    """Chat direct avec FPST SmartDesk via Groq pour les questions générales"""
    # Pass student_context to the system prompt (NOT injected into the user message)
    # Injecting into user message caused Groq to respond about the profile instead of the question
    result = chat_with_groq(
        request.message,
        request.language,
        request.user_role,
        request.student_context,
        request.conversation_history
    )
    return result

# ============================================
# ROUTE UPLOAD DOCUMENT (avec audience)
# ============================================
@app.post("/rag/upload")
async def upload_document(
    file: UploadFile = File(...),
    audience: str = Query(default="student", description="student | professor")
):
    upload_dir = _get_kb_dir(audience)
    os.makedirs(upload_dir, exist_ok=True)
    filepath = os.path.join(upload_dir, file.filename)

    with open(filepath, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # Indexer uniquement dans le vector store de l'audience
    try:
        aud_vs = "professor" if audience == "professor" else "student"
        import shutil as sh
        chroma_dir = CHROMA_DIR_PROF if aud_vs == "professor" else CHROMA_DIR
        if os.path.exists(chroma_dir):
            sh.rmtree(chroma_dir)
        create_vector_store(aud_vs)
        return {"message": f"Document '{file.filename}' indexe avec succes ({audience})", "status": "success"}
    except Exception as e:
        return {"message": f"Fichier sauvegarde mais indexation echouee: {e}", "status": "partial"}

# ============================================
# ROUTE RÉINDEXER TOUS LES DOCUMENTS
# ============================================
@app.post("/rag/index")
async def index_all(audience: str = Query(default="all", description="student | professor | all")):
    """Force la réindexation complète de la base de connaissances"""
    import shutil as sh
    results = []

    audiences_to_rebuild = []
    if audience in ("student", "all"):
        audiences_to_rebuild.append("student")
    if audience in ("professor", "all"):
        audiences_to_rebuild.append("professor")

    for aud in audiences_to_rebuild:
        chroma_dir = CHROMA_DIR_PROF if aud == "professor" else CHROMA_DIR
        if os.path.exists(chroma_dir):
            sh.rmtree(chroma_dir)
            _safe_print(f"[INFO] Chroma {aud} supprime")
        try:
            vs = create_vector_store(aud)
            results.append(f"{aud}: {'OK' if vs else 'vide'}")
        except Exception as e:
            results.append(f"{aud}: ERREUR {e}")

    return {"message": "Reindexation terminee — " + " | ".join(results), "status": "success"}

# ============================================
# ROUTE STATUS RAG
# ============================================
@app.get("/rag/status")
async def rag_status():
    """Vérifie l'état complet du service FPST SmartDesk"""
    from rag_service import GROQ_API_KEY

    has_vs_student = load_vector_store("student") is not None
    has_vs_prof    = load_vector_store("professor") is not None
    has_groq_key   = GROQ_API_KEY is not None and len(GROQ_API_KEY) > 0

    def count_files(path):
        if os.path.exists(path):
            return len([f for f in os.listdir(path)
                        if os.path.isfile(os.path.join(path, f))
                        and f.endswith(('.txt', '.pdf', '.docx'))])
        return 0

    student_files = count_files(DOCUMENTS_DIR) + count_files(DOCUMENTS_DIR_STUDENT)
    prof_files    = count_files(DOCUMENTS_DIR_PROF)

    return {
        "assistant_name": "FPST SmartDesk",
        "version": "2.1",
        "vector_store_ready": has_vs_student,
        "vector_store_prof_ready": has_vs_prof,
        "groq_configured": has_groq_key,
        "knowledge_base_files": student_files,
        "professor_files": prof_files,
        "status": "ready" if (has_groq_key and has_vs_student) else
                  "partial" if has_groq_key else "missing_api_key"
    }

# ============================================
# ROUTE LISTE DES DOCUMENTS (avec audience)
# ============================================
@app.get("/rag/documents")
async def list_documents(audience: str = Query(default="student")):
    kb_path = _get_kb_dir(audience)
    os.makedirs(kb_path, exist_ok=True)
    docs = []
    for f in sorted(os.listdir(kb_path)):
        filepath = os.path.join(kb_path, f)
        if not os.path.isfile(filepath):
            continue
        if f.endswith(('.txt', '.pdf', '.docx')):
            stat = os.stat(filepath)
            docs.append({
                "name": f,
                "size": stat.st_size,
                "modified": datetime.fromtimestamp(stat.st_mtime).strftime("%d/%m/%Y %H:%M"),
                "type": f.rsplit('.', 1)[-1].upper(),
                "editable": f.endswith('.txt'),
                "audience": audience
            })
    return {"documents": docs, "count": len(docs)}

# ============================================
# ROUTE SUPPRIMER UN DOCUMENT (avec audience)
# ============================================
@app.delete("/rag/documents/{filename}")
async def delete_document(filename: str, audience: str = Query(default="student")):
    kb_path = _get_kb_dir(audience)
    filepath = os.path.join(kb_path, filename)
    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail="Fichier non trouve")
    os.remove(filepath)
    try:
        import shutil as sh
        aud_vs = "professor" if audience == "professor" else "student"
        chroma_dir = CHROMA_DIR_PROF if aud_vs == "professor" else CHROMA_DIR
        if os.path.exists(chroma_dir):
            sh.rmtree(chroma_dir)
        create_vector_store(aud_vs)
    except Exception:
        pass
    return {"message": f"Document '{filename}' supprime et base reindexee", "status": "success"}

# ============================================
# ROUTE LIRE CONTENU D'UN DOCUMENT TXT (avec audience)
# ============================================
@app.get("/rag/documents/{filename}/content")
async def get_document_content(filename: str, audience: str = Query(default="student")):
    if not filename.endswith('.txt'):
        raise HTTPException(status_code=400, detail="Seuls les fichiers .txt peuvent etre lus")
    kb_path = _get_kb_dir(audience)
    filepath = os.path.join(kb_path, filename)
    if not os.path.exists(filepath):
        raise HTTPException(status_code=404, detail="Fichier non trouve")
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    return {"filename": filename, "content": content, "audience": audience}

# ============================================
# ROUTE MODIFIER CONTENU D'UN DOCUMENT TXT
# ============================================
@app.put("/rag/documents/{filename}/content")
async def update_document_content(filename: str, body: UpdateContentRequest,
                                  audience: str = Query(default="student")):
    if not filename.endswith('.txt'):
        raise HTTPException(status_code=400, detail="Seuls les fichiers .txt peuvent etre modifies")
    kb_path = _get_kb_dir(audience)
    filepath = os.path.join(kb_path, filename)

    # 1. Sauvegarder le fichier (priorité absolue)
    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(body.content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erreur ecriture fichier: {str(e)}")

    # 2. Reindexer (non bloquant si echec)
    try:
        import shutil as sh
        aud_vs = "professor" if audience == "professor" else "student"
        chroma_dir = CHROMA_DIR_PROF if aud_vs == "professor" else CHROMA_DIR
        if os.path.exists(chroma_dir):
            sh.rmtree(chroma_dir)
        create_vector_store(aud_vs)
        return {"message": f"Contenu de '{filename}' mis a jour et base reindexee", "status": "success"}
    except Exception as e:
        # Le fichier est sauvegardé même si l'indexation échoue
        return {"message": f"Fichier '{filename}' sauvegarde. Reindexation en attente (relancez le service).", "status": "partial", "error": str(e)}

# ============================================
# ROUTE DE SANTÉ
# ============================================
@app.get("/health")
async def health():
    return {
        "status": "✅ FPST SmartDesk (Faculty Assistant & Smart Technology Oracle) is running!",
        "version": "2.0",
        "services": ["NLP", "RAG", "Groq AI"],
        "languages": ["Français", "العربية", "الدارجة التونسية"]
    }
