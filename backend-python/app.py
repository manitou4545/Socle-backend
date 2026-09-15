"""
Microservice Python — Détection de similarité / anti-plagiat pour SOCLE.

Calcule un score d'originalité réel (pas simulé) en comparant le texte
d'un document soumis au corpus des documents déjà publiés, en utilisant
une vectorisation TF-IDF + similarité cosinus (technique standard de
détection de plagiat textuel, langue française prise en charge).

Lancement local :
    pip install -r requirements.txt
    python app.py
    -> service disponible sur http://localhost:5000

Le backend Java peut appeler ce service via HTTP (voir README-backend.md).
"""

import os
from flask import Flask, request, jsonify
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import requests

app = Flask(__name__)

# Mots vides français basiques (évite une dépendance NLTK juste pour ça)
FRENCH_STOPWORDS = [
    "le","la","les","un","une","des","de","du","et","à","au","aux","ce","ces","cette",
    "en","sur","dans","par","pour","avec","sans","est","sont","été","être","avoir","que",
    "qui","dont","où","comme","plus","moins","son","sa","ses","leur","leurs","ne","pas",
    "se","sa","on","il","elle","nous","vous","ils","elles","ou","mais","donc","or","ni","car",
]

JAVA_BACKEND_URL = os.environ.get("JAVA_BACKEND_URL", "").rstrip("/")


def compute_originality(text, corpus_texts):
    """Retourne (score_originalite_0_100, index_plus_similaire, similarite_max_0_1)."""
    corpus_texts = [c for c in corpus_texts if c and c.strip()]
    if not text or not text.strip() or len(corpus_texts) == 0:
        return 100, None, 0.0

    documents = [text] + corpus_texts
    vectorizer = TfidfVectorizer(stop_words=FRENCH_STOPWORDS, ngram_range=(1, 2))
    try:
        tfidf = vectorizer.fit_transform(documents)
    except ValueError:
        # Corpus trop petit / vocabulaire vide après nettoyage
        return 100, None, 0.0

    similarities = cosine_similarity(tfidf[0:1], tfidf[1:]).flatten()
    max_sim = float(similarities.max()) if len(similarities) else 0.0
    best_idx = int(similarities.argmax()) if len(similarities) else None
    score = round((1 - max_sim) * 100)
    score = max(0, min(100, score))
    return score, best_idx, round(max_sim, 4)


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/analyze", methods=["POST"])
def analyze():
    """
    Corps attendu :
    {
      "text": "texte complet du document soumis (résumé + sections)",
      "corpus": ["texte du document 1", "texte du document 2", ...]   // optionnel si JAVA_BACKEND_URL défini
    }
    """
    data = request.get_json(force=True, silent=True) or {}
    text = data.get("text", "")
    corpus = data.get("corpus")

    if corpus is None:
        corpus = fetch_corpus_from_backend()

    score, best_idx, max_sim = compute_originality(text, corpus)
    return jsonify({
        "originality_score": score,
        "max_similarity": max_sim,
        "most_similar_index": best_idx,
        "corpus_size": len(corpus),
    })


def fetch_corpus_from_backend():
    """Récupère automatiquement le texte de tous les documents déjà publiés
    depuis le backend Java, si JAVA_BACKEND_URL est configuré."""
    if not JAVA_BACKEND_URL:
        return []
    try:
        resp = requests.get(f"{JAVA_BACKEND_URL}/api/documents", timeout=5)
        resp.raise_for_status()
        docs = resp.json()
        texts = []
        for d in docs:
            parts = [d.get("titre", ""), d.get("resume", "")]
            for s in d.get("sections", []):
                if s.get("contenu"):
                    parts.append(s["contenu"])
            texts.append(" ".join(parts))
        return texts
    except Exception:
        return []


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
