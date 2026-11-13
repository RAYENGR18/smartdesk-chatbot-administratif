"""
Configuration des tests : aucun accès réseau.
- clé API de test ;
- base vectorielle dans un dossier temporaire ;
- embeddings factices (pas de téléchargement du modèle HuggingFace) ;
- pas de clé Groq (le service répond en mode dégradé).
"""
import os
import shutil
import sys
import tempfile

import pytest

TEST_KEY = "cle-de-test-nlp"
_TMP = tempfile.mkdtemp(prefix="smartdesk-nlp-tests-")

os.environ["NLP_API_KEY"] = TEST_KEY
os.environ["GROQ_API_KEY"] = ""
os.environ["CHROMA_DB_PATH"] = os.path.join(_TMP, "chroma")
os.environ["CHROMA_DB_PROF_PATH"] = os.path.join(_TMP, "chroma_prof")

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import rag_service  # noqa: E402
from langchain_core.embeddings import DeterministicFakeEmbedding  # noqa: E402

rag_service._embeddings = DeterministicFakeEmbedding(size=64)


@pytest.fixture(scope="session")
def client():
    from fastapi.testclient import TestClient
    import main

    with TestClient(main.app) as c:
        yield c
    shutil.rmtree(_TMP, ignore_errors=True)


@pytest.fixture
def auth():
    return {"X-API-Key": TEST_KEY}
