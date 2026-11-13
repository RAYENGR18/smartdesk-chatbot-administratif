# Installation du Serveur RAG

## Prérequis
- Python 3.10+
- pip

## Installation des dépendances
```bash
pip install fastapi==0.104.1
pip install chromadb==0.4.18
pip install sentence-transformers==2.2.2
pip install groq==0.4.1
pip install uvicorn==0.24.0
```

## Lancement du serveur
```bash
python rag_service.py
```

## Port
Le serveur tourne sur le port **8000**
