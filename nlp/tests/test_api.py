"""Tests de sécurité et de fonctionnement du service NLP."""
import os

import main


def test_health_is_public(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "UP"


def test_api_key_is_required(client):
    assert client.post("/analyze", json={"message": "bonjour"}).status_code == 401
    assert client.post("/analyze", json={"message": "bonjour"},
                       headers={"X-API-Key": "mauvaise"}).status_code == 401
    assert client.get("/rag/status").status_code == 401
    assert client.get("/rag/documents").status_code == 401


def test_no_cors_headers(client, auth):
    r = client.options("/analyze", headers={
        "Origin": "http://evil.example", "Access-Control-Request-Method": "POST"})
    assert "access-control-allow-origin" not in {k.lower() for k in r.headers}


def test_analyze_greeting(client, auth):
    r = client.post("/analyze", json={"message": "bonjour", "language": "fr"}, headers=auth)
    assert r.status_code == 200
    body = r.json()
    assert body["intention"] == "salutation"
    assert body["response"]


def test_status_does_not_leak_secrets(client, auth):
    r = client.get("/rag/status", headers=auth)
    assert r.status_code == 200
    assert "key" not in r.text.lower().replace("missing_api_key", "")


def test_rag_without_groq_degrades_gracefully(client, auth):
    r = client.post("/rag/ask", json={"question": "Quels sont les frais de scolarite ?"}, headers=auth)
    assert r.status_code == 200
    body = r.json()
    # Sans clé Groq : soit des extraits de documents, soit None (le backend passe au repli)
    assert "answer" in body
    for src in body.get("sources", []):
        assert "/" not in src and "\\" not in src  # jamais de chemin serveur


def test_path_traversal_is_rejected(client, auth):
    for name in ["..%2F..%2Fmain.py", "..\\..\\main.txt", "....txt", "a/b.txt", "%2e%2e%2fsecret.txt"]:
        r = client.get(f"/rag/documents/{name}/content", headers=auth)
        assert r.status_code in (400, 404), name
    r = client.get("/rag/documents/main.py/content", headers=auth)
    assert r.status_code == 400
    r = client.get("/rag/documents", params={"audience": "../../"}, headers=auth)
    assert r.status_code == 400


def test_upload_rejects_bad_names_and_types(client, auth):
    r = client.post("/rag/upload", headers=auth,
                    files={"file": ("../../evil.txt", b"x", "text/plain")})
    assert r.status_code == 400
    r = client.post("/rag/upload", headers=auth,
                    files={"file": ("script.py", b"print(1)", "text/plain")})
    assert r.status_code == 400


def test_upload_read_update_delete_cycle(client, auth):
    name = "test_regle_temporaire.txt"
    path = os.path.join(main.DOCUMENTS_DIR_PROF, name)
    try:
        r = client.post("/rag/upload", params={"audience": "professor"}, headers=auth,
                        files={"file": (name, "Regle de test : salle B12.".encode(), "text/plain")})
        assert r.status_code == 200, r.text
        assert os.path.isfile(path)

        r = client.get(f"/rag/documents/{name}/content", params={"audience": "professor"}, headers=auth)
        assert r.status_code == 200
        assert "B12" in r.json()["content"]

        r = client.put(f"/rag/documents/{name}/content", params={"audience": "professor"}, headers=auth,
                       json={"content": "Regle modifiee : salle C7."})
        assert r.status_code == 200
        assert r.json()["status"] in ("success", "partial")

        r = client.delete(f"/rag/documents/{name}", params={"audience": "professor"}, headers=auth)
        assert r.status_code == 200
        assert not os.path.exists(path)
    finally:
        if os.path.exists(path):
            os.remove(path)


def test_reindex(client, auth):
    r = client.post("/rag/index", params={"audience": "all"}, headers=auth)
    assert r.status_code == 200
    assert "ERREUR" not in r.json()["message"]
