"""
Serveur de TEST reproduisant fidèlement le contrat API du backend Java
(mêmes routes, mêmes formes de réponse JSON, même logique métier) — utilisé
UNIQUEMENT pour valider ici que le code frontend (js/api.js et les vues)
appelle correctement le backend. Ce n'est PAS un remplacement du vrai
backend Java, juste un double de test.
"""
import json
import re
import uuid
import bcrypt_stub as bcrypt  # stub minimal, voir plus bas
from flask import Flask, request, jsonify

app = Flask(__name__)

DB = {"students": {}, "documents": {}, "transactions": [], "badges": [], "next_doc_id": 1, "next_page_lock_id": 1}
SESSIONS = {}  # token -> {"role": "STUDENT"/"ADMIN", "matricule": str|None}
ADMIN_PASSWORD_HASH = bcrypt.hashpw(b"NZJG_manitou4545!@#$%^&admi1")
FAIL_COUNT = {"n": 0}

MATRICULE_RE = re.compile(r'^\d{2}A\d{4}EM$', re.IGNORECASE)


def auth_role(required_role):
    token = request.headers.get("Authorization", "")
    token = token.replace("Bearer ", "").strip()
    session = SESSIONS.get(token)
    if not session or session["role"] != required_role:
        return None
    return session


def to_public_doc(d, matricule=None):
    is_author = matricule and matricule.upper() == (d.get("matricule") or "").upper()
    page_locks = []
    for p in d["pageLocks"]:
        unlocked = is_author or (matricule and (matricule, p["id"]) in UNLOCKS)
        page_locks.append({
            "id": p["id"], "pageDebut": p["pageDebut"], "pageFin": p["pageFin"],
            "prix": p["prix"], "unlocked": unlocked,
        })
    out = dict(d)
    out["pageLocks"] = page_locks
    return out


UNLOCKS = set()  # {(matricule, pageLockId)}


@app.route("/api/auth/register", methods=["POST"])
def register():
    body = request.get_json(force=True)
    matricule = body["matricule"].upper()
    if not MATRICULE_RE.match(matricule):
        return jsonify({"message": "Format de matricule invalide"}), 400
    if matricule in DB["students"]:
        return jsonify({"message": "Ce matricule est déjà associé à un profil SOCLE."}), 409
    student = {"id": len(DB["students"]) + 1, "matricule": matricule, "name": body["name"],
               "filiere": body["filiere"], "niveau": body.get("niveau", 1), "credits": 20}
    DB["students"][matricule] = student
    token = str(uuid.uuid4())
    SESSIONS[token] = {"role": "STUDENT", "matricule": matricule}
    return jsonify({"token": token, "student": student})


@app.route("/api/auth/login", methods=["POST"])
def login():
    body = request.get_json(force=True)
    matricule = body.get("matricule", "").upper()
    student = DB["students"].get(matricule)
    if not student:
        return jsonify({"message": "Matricule inconnu. Inscris-toi d'abord."}), 404
    token = str(uuid.uuid4())
    SESSIONS[token] = {"role": "STUDENT", "matricule": matricule}
    return jsonify({"token": token, "student": student})


@app.route("/api/auth/admin-login", methods=["POST"])
def admin_login():
    body = request.get_json(force=True)
    if FAIL_COUNT["n"] >= 5:
        return jsonify({"message": "Accès verrouillé (trop de tentatives)."}), 429
    if not bcrypt.checkpw(body.get("password", "").encode(), ADMIN_PASSWORD_HASH):
        FAIL_COUNT["n"] += 1
        return jsonify({"message": "Mot de passe incorrect"}), 401
    FAIL_COUNT["n"] = 0
    token = str(uuid.uuid4())
    SESSIONS[token] = {"role": "ADMIN", "matricule": None}
    return jsonify({"token": token})


@app.route("/api/documents", methods=["GET"])
def list_documents():
    token = request.headers.get("Authorization", "").replace("Bearer ", "").strip()
    session = SESSIONS.get(token)
    matricule = session["matricule"] if (session and session["role"] == "STUDENT") else None
    docs = sorted(DB["documents"].values(), key=lambda d: d["datePublication"], reverse=True)
    return jsonify([to_public_doc(d, matricule) for d in docs])


@app.route("/api/documents", methods=["POST"])
def publish_document():
    session = auth_role("STUDENT")
    if not session:
        return jsonify({"message": "Connexion étudiante requise"}), 401
    body = request.get_json(force=True)
    doc_id = DB["next_doc_id"]; DB["next_doc_id"] += 1
    page_locks = []
    for pr in body.get("pageLocks", []):
        pid = DB["next_page_lock_id"]; DB["next_page_lock_id"] += 1
        page_locks.append({"id": pid, "pageDebut": pr["pageDebut"], "pageFin": pr["pageFin"],
                            "prix": pr.get("prix", 10)})
    import datetime
    doc = {
        "id": doc_id, "titre": body["titre"], "resume": body.get("resume", ""),
        "type": body["type"], "filiere": body["filiere"], "auteur": body.get("auteur"),
        "matricule": session["matricule"], "annee": body.get("annee"),
        "datePublication": datetime.datetime.utcnow().isoformat() + "Z",
        "plagiatScore": 90, "consultations": 0, "downloads": 0, "pageCount": None, "fileUrl": None,
        "featured": False, "adminPublished": False, "pageLocks": page_locks,
    }
    DB["documents"][doc_id] = doc
    DB["students"][session["matricule"]]["credits"] += 10
    return jsonify(to_public_doc(doc, session["matricule"]))


@app.route("/api/documents/<int:doc_id>/consult", methods=["POST"])
def consult(doc_id):
    if doc_id in DB["documents"]:
        DB["documents"][doc_id]["consultations"] += 1
    return jsonify({"ok": True})


@app.route("/api/documents/<int:doc_id>/download", methods=["POST"])
def download(doc_id):
    if doc_id in DB["documents"]:
        DB["documents"][doc_id]["downloads"] += 1
        return jsonify({"fileUrl": DB["documents"][doc_id].get("fileUrl", "")})
    return jsonify({"fileUrl": ""})


@app.route("/api/documents/<int:doc_id>/upload-pdf", methods=["POST"])
def upload_pdf(doc_id):
    session = auth_role("STUDENT")
    if not session:
        return jsonify({"message": "Connexion étudiante requise"}), 401
    if doc_id not in DB["documents"]:
        return jsonify({"message": "Document introuvable"}), 404
    if "file" not in request.files:
        return jsonify({"message": "Fichier manquant"}), 400
    # Mock : pas de vrai envoi vers Cloudinary ici, on simule juste une URL.
    fake_url = f"https://res.cloudinary.com/demo/raw/upload/socle/documents/mock_{doc_id}.pdf"
    DB["documents"][doc_id]["fileUrl"] = fake_url
    return jsonify({"fileUrl": fake_url})


@app.route("/api/documents/page-locks/<int:page_lock_id>/unlock", methods=["POST"])
def unlock(page_lock_id):
    session = auth_role("STUDENT")
    if not session:
        return jsonify({"message": "Connexion étudiante requise"}), 401
    matricule = session["matricule"]
    for d in DB["documents"].values():
        for p in d["pageLocks"]:
            if p["id"] == page_lock_id:
                if d.get("matricule") and d["matricule"].upper() == matricule.upper():
                    return jsonify({"message": "Tu es l'auteur de ce document : accès déjà autorisé."})
                if (matricule, page_lock_id) in UNLOCKS:
                    return jsonify({"message": "Tu as déjà déverrouillé ces pages."})
                student = DB["students"][matricule]
                if student["credits"] < p["prix"]:
                    return jsonify({"message": "Crédits insuffisants."}), 402
                student["credits"] -= p["prix"]
                UNLOCKS.add((matricule, page_lock_id))
                DB["transactions"].insert(0, {"matricule": matricule, "documentTitre": d["titre"],
                                               "sectionTitre": f"Pages {p['pageDebut']}-{p['pageFin']}",
                                               "montant": p["prix"], "date": "now"})
                return jsonify({"message": "Pages déverrouillées", "creditsRestants": student["credits"]})
    return jsonify({"message": "Plage de pages introuvable"}), 404


@app.route("/api/documents/page-locks/<int:page_lock_id>/view", methods=["GET"])
def view_unlocked_pages(page_lock_id):
    session = auth_role("STUDENT")
    if not session:
        return jsonify({"message": "Connexion étudiante requise"}), 401
    matricule = session["matricule"]
    for d in DB["documents"].values():
        for p in d["pageLocks"]:
            if p["id"] == page_lock_id:
                is_author = d.get("matricule") and d["matricule"].upper() == matricule.upper()
                if not is_author and (matricule, page_lock_id) not in UNLOCKS:
                    return jsonify({"message": "Ces pages ne sont pas déverrouillées pour ton compte."}), 403
                # Mock : pas de vrai PDF stocké ici, on renvoie un message plutôt qu'un binaire.
                return jsonify({"message": f"[MOCK] Pages {p['pageDebut']}-{p['pageFin']} du document '{d['titre']}' — le vrai backend Java renverrait ici le PDF extrait."})
    return jsonify({"message": "Plage de pages introuvable"}), 404


@app.route("/api/ai/ask", methods=["POST"])
def ai_ask():
    session = auth_role("STUDENT")
    if not session:
        return jsonify({"message": "Connexion étudiante requise"}), 401
    body = request.get_json(force=True)
    doc = DB["documents"].get(body.get("documentId"))
    if not doc:
        return jsonify({"message": "Document introuvable"}), 404
    # Mock : pas de vrai appel à Groq ici, juste une confirmation que le contrat API fonctionne.
    return jsonify({"reply": f"[MOCK] Question reçue sur « {doc['titre']} » : \"{body.get('question','')}\". Le vrai backend Java interrogerait ici Groq avec le plan complet du document et le catalogue de la bibliothèque."})


@app.route("/api/admin/documents", methods=["GET"])
def admin_documents():
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    return jsonify(sorted(DB["documents"].values(), key=lambda d: d["datePublication"], reverse=True))


@app.route("/api/admin/documents/<int:doc_id>", methods=["DELETE"])
def admin_delete(doc_id):
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    DB["documents"].pop(doc_id, None)
    return jsonify({"ok": True})


@app.route("/api/admin/documents/<int:doc_id>/feature", methods=["PATCH"])
def admin_feature(doc_id):
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    d = DB["documents"][doc_id]
    d["featured"] = not d["featured"]
    return jsonify(d)


@app.route("/api/admin/documents", methods=["POST"])
def admin_publish():
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    body = request.get_json(force=True)
    doc_id = DB["next_doc_id"]; DB["next_doc_id"] += 1
    page_locks = []
    for pr in body.get("pageLocks", []):
        pid = DB["next_page_lock_id"]; DB["next_page_lock_id"] += 1
        page_locks.append({"id": pid, "pageDebut": pr["pageDebut"], "pageFin": pr["pageFin"],
                            "prix": pr.get("prix", 10)})
    date_pub = body.get("datePublication")
    date_iso = (date_pub + "T00:00:00Z") if date_pub else "now"
    doc = {
        "id": doc_id, "titre": body["titre"], "resume": body.get("resume", ""),
        "type": body["type"], "filiere": body["filiere"], "auteur": body.get("auteur"),
        "matricule": (body.get("matricule") or None), "annee": body.get("annee"),
        "datePublication": date_iso, "plagiatScore": 90, "consultations": 0, "downloads": 0,
        "pageCount": None, "fileUrl": None,
        "featured": False, "adminPublished": True, "pageLocks": page_locks,
    }
    DB["documents"][doc_id] = doc
    return jsonify(doc)


@app.route("/api/admin/transactions", methods=["GET"])
def admin_transactions():
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    return jsonify(DB["transactions"])


@app.route("/api/admin/badges", methods=["GET"])
def admin_badges_list():
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    return jsonify(DB["badges"])


@app.route("/api/admin/badges", methods=["POST"])
def admin_badges_award():
    if not auth_role("ADMIN"):
        return jsonify({"message": "Accès administrateur requis"}), 401
    body = request.get_json(force=True)
    mat = body["matricule"].upper()
    if mat not in DB["students"]:
        return jsonify({"message": "Matricule inconnu"}), 404
    if any(b["matricule"] == mat and b["badgeKey"] == body["badgeKey"] for b in DB["badges"]):
        return jsonify({"message": "Ce badge a déjà été attribué à cet étudiant."})
    badge = {"matricule": mat, "badgeKey": body["badgeKey"], "docTitre": body.get("docTitre"), "date": "now"}
    DB["badges"].append(badge)
    return jsonify(badge)


@app.route("/api/badges/<matricule>", methods=["GET"])
def badges_for(matricule):
    return jsonify([b for b in DB["badges"] if b["matricule"] == matricule.upper()])


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=8080, debug=False)
