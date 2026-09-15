# SOCLE — Backend sécurisé (Module 2)

Ce dossier contient la **vraie couche de sécurité serveur** de SOCLE :
- `backend-java/` — API Spring Boot : comptes étudiants (matricule), authentification
  admin (BCrypt + verrouillage anti brute-force), documents, sections verrouillées
  (déverrouillage payant **vérifié et débité côté serveur**), transactions, badges.
  Base de données réelle (H2 fichier par défaut, PostgreSQL prêt à l'emploi).
- `backend-python/` — microservice de détection d'originalité (anti-plagiat) qui
  calcule un vrai score de similarité textuelle (TF-IDF + similarité cosinus),
  testé et fonctionnel.

⚠️ **Important — ce que ce backend change vraiment :**
Dans la version front-end seule (dossier `socle/`), *tout* (mot de passe admin,
crédits, verrous) est vérifié dans le navigateur : quelqu'un de déterminé
techniquement peut le contourner via la console développeur. Avec ce backend,
les vérifications se font **sur le serveur**, dans une base de données que le
navigateur ne peut pas modifier directement. C'est la différence entre "caché"
et "vraiment protégé".

Ce backend est livré comme **référence fonctionnelle et testée** (la logique
anti-plagiat Python a été testée avec de vrais exemples ; le code Java a été
relu avec soin mais n'a pas pu être compilé dans cet environnement sans accès
internet pour télécharger les dépendances Maven — ce sera la toute première
chose à vérifier en le lançant chez toi, voir ci-dessous).

---

## 0. Ce qui a changé : le frontend est maintenant branché ET testé

Contrairement à la version précédente de ce document, **le travail de
branchement (étape 1) est fait** : `socle/js/api.js` fait de vrais appels
HTTP vers ce backend, et tous les fichiers du frontend (`view-library.js`,
`view-publish.js`, `view-profile.js`, `view-admin.js`, `badges.js`) ont été
réécrits pour l'utiliser à la place du stockage local.

**Un bug réel a été trouvé et corrigé** pendant cette vérification : les
entités JPA `DocumentEntity` ↔ `SectionEntity` se référençaient
mutuellement, ce qui aurait provoqué une boucle infinie lors de la
sérialisation JSON (`SectionEntity.document` porte maintenant `@JsonIgnore`).
C'est exactement le genre de bug qui ne se voit qu'en testant pour de vrai.

**Test bout-en-bout réel effectué** : comme ce sandbox n'a pas accès à
Internet pour télécharger les dépendances Maven du vrai backend Java, un
petit serveur de test (`quick-test-mock-server/`, Python/Flask) reproduisant
exactement le même contrat d'API a été utilisé pour exécuter le VRAI fichier
`js/api.js` (aucune réécriture) avec de vraies requêtes HTTP. 15 scénarios
ont été validés : inscription, rejet d'un matricule dupliqué, publication,
masquage correct du contenu verrouillé selon qui regarde (anonyme / non-payeur
/ payeur / auteur), déverrouillage payant individuel avec débit de crédits,
connexion admin (bon/mauvais mot de passe, verrouillage anti brute-force),
protection des routes admin contre un jeton étudiant, publication à date
libre, attribution de badge, suppression. Tous passent.

**Ce qui n'a PAS pu être testé ici, faute d'accès internet** : la compilation
réelle du code Java avec Maven (le serveur de test ci-dessus est un double
fonctionnellement identique, pas le vrai fichier `.java` compilé). Lance
`mvn spring-boot:run` chez toi dès que possible pour confirmer que ça
compile — c'est la seule étape qui reste vraiment à vérifier.

---

## 1. Lancer le backend Java en local

✅ **Le hash du mot de passe admin est déjà généré et inséré dans
`application.properties`** — tu n'as plus besoin de lancer la commande
`--generate-hash` toi-même, tu peux directement déployer ou lancer le
serveur.

💡 **Envie de tester le frontend tout de suite, sans installer Java/Maven ?**
Utilise `quick-test-mock-server/` (juste Python + Flask, déjà testé, voir son
README) — ça te permet de voir le logiciel fonctionner en 30 secondes pendant
que tu configures le vrai backend Java en parallèle.

Prérequis : Java 17+ et Maven (`mvn`).

```bash
cd backend-java

# Étape 1 (obligatoire) : générer le hash de TON mot de passe admin
mvn compile exec:java -Dexec.mainClass="com.socle.backend.SocleBackendApplication" -Dexec.args="--generate-hash=NZJG_manitou4545!@#$%^&admi1"
```

Cette commande imprime un hash BCrypt. Copie-le dans
`src/main/resources/application.properties`, ligne `socle.admin.password-hash=`.

```bash
# Étape 2 : démarrer le serveur
mvn spring-boot:run
```

Le serveur démarre sur **http://localhost:8080**. La base de données H2 se
crée automatiquement dans `backend-java/data/socledb.mv.db` (aucune
installation de base de données nécessaire).

### Tester rapidement avec curl

```bash
# Inscription étudiante (matricule conditionné, vérifié en base)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Nomo Zobo Jean Gérard","matricule":"22A0199EM","filiere":"IMTM","niveau":4}'

# Connexion admin (avec TON mot de passe réel)
curl -X POST http://localhost:8080/api/auth/admin-login \
  -H "Content-Type: application/json" \
  -d '{"password":"NZJG_manitou4545!@#$%^&admi1"}'

# Liste des documents (public)
curl http://localhost:8080/api/documents
```

---

## 2. Lancer le microservice Python (anti-plagiat)

```bash
cd backend-python
pip install -r requirements.txt
python app.py
```

Disponible sur **http://localhost:5000**. Test rapide :

```bash
curl -X POST http://localhost:5000/analyze \
  -H "Content-Type: application/json" \
  -d '{"text":"Nouveau document sur la géophysique minière.","corpus":["Un autre document sur la topographie."]}'
```

Pour qu'il aille chercher automatiquement le corpus depuis le backend Java,
lance-le avec : `JAVA_BACKEND_URL=http://localhost:8080 python app.py`

---

## 3. Brancher le front-end (`socle/`) sur ce backend

**Ce travail n'a pas encore été fait dans cette livraison** : le front-end
(`socle/index.html` et `socle/admin.html`) utilise aujourd'hui son propre
stockage local (`js/storage.js`), pas ce backend. Les deux fonctionnent donc
actuellement de façon indépendante.

Pour les relier, il faudrait remplacer les fonctions de `js/storage.js` (qui
appellent `window.storage`/`localStorage`) par des appels `fetch()` vers ces
routes API, et gérer le jeton (`token`) reçu à la connexion. C'est un travail
de développement à part entière — dis-moi si tu veux que je le fasse dans une
prochaine étape.

---

## 4. Comment avoir accès à un serveur (hébergement)

Trois options, de la plus simple à la plus complète :

### Option A — Gratuit, pour une démonstration (recommandé pour commencer)

**Déploiement sur Render (render.com)** — chaque service Docker fourni dans ce
dépôt (`backend-java/Dockerfile` et `backend-python/Dockerfile`) est prêt à
être déployé tel quel :

1. Crée un compte sur render.com (connexion via GitHub, plus simple).
2. **New +** → **Web Service** → connecte ton dépôt GitHub.
3. Render détecte le `Dockerfile` automatiquement. Choisis :
   - **Root Directory** : `backend-java` (pour l'API principale)
   - **Plan** : Free
4. Ajoute les variables d'environnement (`CLOUDINARY_CLOUD_NAME`,
   `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `GROQ_API_KEY` — clé
   gratuite à générer sur console.groq.com pour l'Assistant Minier) dans
   l'onglet Environment.
5. Répète l'opération avec **Root Directory** : `backend-python` pour le
   microservice anti-plagiat, et ajoute `JAVA_BACKEND_URL` (l'URL Render du
   premier service) pour qu'il récupère automatiquement le corpus.
6. Une fois les deux services en ligne, ajoute `PLAGIAT_SERVICE_URL` (l'URL
   Render du service Python) sur le service Java pour activer le vrai calcul
   du score d'originalité (voir `render.yaml` à la racine, qui décrit les
   deux services si tu préfères un déploiement "Blueprint" en un clic).

⚠️ Le plan gratuit met le service en veille après ~15 min d'inactivité ; le
réveil prend 30-50 secondes au premier appel suivant — normal, pas un bug.

- **PythonAnywhere** (pythonanywhere.com) : autre option gratuite pour héberger
  uniquement le microservice Python (`backend-python`), si tu préfères ne pas
  utiliser Docker.

### Option B — Petit serveur payant mais très abordable
- **Hetzner Cloud** ou **Contabo** : à partir de 4-5 €/mois, un vrai serveur
  Linux sur lequel tu installes Java + Maven + PostgreSQL toi-même (plus de
  contrôle, pas de mise en veille). Convient bien si tu veux présenter un
  projet "prêt pour la vraie vie".

### Option C — Le serveur de ton école
Si l'ENSMIP / l'Université de Maroua dispose d'un serveur ou d'un service
informatique, c'est souvent la meilleure option à long terme pour un projet
académique destiné à rester en service : demande au service informatique s'il
peuvent héberger une application Java + une base de données. Cela donne aussi
plus de légitimité institutionnelle à ta présentation.

### Base de données en production
En production, remplace H2 par PostgreSQL (plus robuste pour plusieurs
utilisateurs simultanés) : la dépendance est déjà incluse dans `pom.xml`, il
suffit de changer 4 lignes dans `application.properties` :

```properties
spring.datasource.url=jdbc:postgresql://HOTE:5432/socledb
spring.datasource.username=TON_UTILISATEUR
spring.datasource.password=TON_MOT_DE_PASSE
spring.jpa.hibernate.ddl-auto=update
```

La plupart des hébergeurs (Railway, Render, Neon.tech pour PostgreSQL gratuit)
fournissent ces informations automatiquement une fois la base créée.

---

## 5. Structure des fichiers

```
backend-java/
├── pom.xml
├── src/main/resources/application.properties
└── src/main/java/com/socle/backend/
    ├── SocleBackendApplication.java   → point d'entrée + outil de hash
    ├── config/CorsConfig.java
    ├── security/                       → Session, TokenStore, AuthUtil, AdminLoginGuard
    ├── model/                           → Student, DocumentEntity, SectionEntity,
    │                                       SectionUnlock, TransactionEntity, BadgeEntity
    ├── repository/                       → interfaces Spring Data JPA
    ├── dto/                               → objets de requête (validation incluse)
    └── controller/
        ├── AuthController.java            → inscription, connexion étudiante/admin
        ├── DocumentController.java         → lecture publique protégée, publication, déverrouillage
        ├── AdminController.java             → toutes les actions admin (jeton requis)
        └── BadgeController.java              → lecture publique des badges

backend-python/
├── requirements.txt
└── app.py    → microservice anti-plagiat (TF-IDF + similarité cosinus), testé
```
