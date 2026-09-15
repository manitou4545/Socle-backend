# Serveur de test rapide (optionnel)

Ce dossier N'EST PAS le vrai backend. C'est un petit serveur Python (Flask,
sans base de données réelle, tout en mémoire) qui reproduit exactement les
mêmes routes que le vrai backend Java, pour que tu puisses tester le
frontend (`socle/index.html` et `socle/admin.html`) en 30 secondes, sans
installer Java ni Maven.

## Lancer

```bash
pip install flask
python mock_server.py
```

Le serveur démarre sur http://localhost:8080 — exactement l'adresse que le
frontend utilise par défaut (`js/api.js`). Ouvre `socle/index.html` dans ton
navigateur : tu devrais pouvoir t'inscrire, publier, déverrouiller des
sections, etc. Mot de passe admin pour tester `admin.html` :
`NZJG_manitou4545!@#$%^&admi1`

⚠️ Les données disparaissent à l'arrêt du serveur (tout est en mémoire) —
c'est fait pour tester rapidement, pas pour un vrai usage. Pour ça, utilise
le vrai backend dans `backend-java/`.

## Ce test a déjà été fait pour toi

15 scénarios ont été validés avec ce serveur avant de te livrer le projet :
inscription, matricule dupliqué refusé, publication, masquage du contenu
verrouillé pour les visiteurs/étudiants non payeurs, déverrouillage payant
individuel, connexion admin (bon/mauvais mot de passe), routes admin
protégées, publication avec date libre, attribution de badge, suppression.
Voir `e2e_test.js` (nécessite Node.js) si tu veux les relancer toi-même
après avoir modifié le code.
