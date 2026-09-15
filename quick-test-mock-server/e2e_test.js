// Test end-to-end réel : charge le VRAI fichier js/api.js du frontend
// (aucune réécriture, aucune simulation) et l'exécute avec le fetch natif
// de Node contre le serveur de test qui tourne sur http://127.0.0.1:8080.
//
// Usage : node e2e_test.js [chemin/vers/socle/js/api.js]
// Par défaut, suppose que tu as extrait les deux zips (frontend "socle" et
// backend "module2-backend") côte à côte dans le même dossier parent.

const fs = require('fs');
const vm = require('vm');
const path = require('path');

const apiPath = process.argv[2] || path.join(__dirname, '..', '..', 'socle', 'js', 'api.js');
if (!fs.existsSync(apiPath)) {
  console.error(`Impossible de trouver js/api.js à l'emplacement : ${apiPath}`);
  console.error('Indique le bon chemin : node e2e_test.js /chemin/vers/socle/js/api.js');
  process.exit(1);
}
const apiCode = fs.readFileSync(apiPath, 'utf8');

const sandbox = {
  window: { SOCLE_API_BASE_URL: 'http://127.0.0.1:8080' },
  localStorage: (() => {
    const store = {};
    return {
      getItem: k => (k in store ? store[k] : null),
      setItem: (k, v) => { store[k] = v; },
      removeItem: k => { delete store[k]; },
    };
  })(),
  fetch,
  console,
};
vm.createContext(sandbox);
vm.runInContext(apiCode, sandbox);

let failures = 0;
function check(label, cond, extra) {
  if (cond) { console.log('  ✅', label); }
  else { console.log('  ❌', label, extra !== undefined ? JSON.stringify(extra) : ''); failures++; }
}

(async () => {
  console.log('\n=== 1) Inscription étudiante (matricule conditionné) ===');
  const reg = await sandbox.apiRequest('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name: 'Nomo Zobo Jean Gérard', matricule: '22a0199em', filiere: 'IMTM', niveau: 4 }),
  });
  check('token reçu', !!reg.token);
  check('matricule normalisé en majuscules', reg.student.matricule === '22A0199EM', reg.student.matricule);
  check('20 crédits offerts', reg.student.credits === 20, reg.student.credits);
  sandbox.setStudentToken(reg.token); // c'est exactement ce que fait view-profile.js après inscription
  const studentToken = reg.token;

  console.log('\n=== 2) Rejet d\'un matricule déjà pris ===');
  try {
    await sandbox.apiRequest('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name: 'Un autre', matricule: '22A0199EM', filiere: 'SQE', niveau: 1 }),
    });
    check('doublon de matricule refusé', false);
  } catch (e) {
    check('doublon de matricule refusé', /déjà/.test(e.message), e.message);
  }

  console.log('\n=== 3) Publication d\'un document avec une section verrouillée ===');
  const created = await sandbox.apiFetchAsStudent('/api/documents', {
    method: 'POST',
    body: JSON.stringify({
      titre: 'Étude de stabilité des talus', resume: 'Résumé test', type: 'TPE', filiere: 'IMTM',
      auteur: 'Nomo Zobo Jean Gérard', annee: 2026,
      sections: [
        { titre: 'Introduction', contenu: 'Texte libre', locked: false, prix: 0 },
        { titre: 'Données sensibles', contenu: 'Coordonnées GPS précises', locked: true, prix: 15 },
      ],
    }),
  });
  check('document créé avec id', !!created.id, created.id);
  check('2 sections créées', created.sections.length === 2, created.sections.length);
  const lockedSection = created.sections.find(s => s.locked);
  check('section marquée verrouillée avec le bon prix', lockedSection.locked === true && lockedSection.prix === 15);
  check('l\'auteur voit le contenu de SA PROPRE section verrouillée (comportement attendu)', lockedSection.contenu === 'Coordonnées GPS précises');
  const docId = created.id, sectionId = lockedSection.id;

  console.log('\n=== 4) Liste publique : contenu verrouillé masqué pour un visiteur ANONYME ===');
  sandbox.setStudentToken(null); // simule un visiteur qui n'est pas connecté
  const publicList = await sandbox.apiFetchPublic('/api/documents');
  const seenDoc = publicList.find(d => d.id === docId);
  const seenLocked = seenDoc.sections.find(s => s.id === sectionId);
  check('contenu verrouillé masqué pour un visiteur anonyme', seenLocked.contenu === null, seenLocked.contenu);

  console.log('\n=== 5) Un DEUXIÈME étudiant s\'inscrit et tente de lire la section verrouillée ===');
  const reg2 = await sandbox.apiRequest('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name: 'Autre Étudiant', matricule: '23A0044EM', filiere: 'SQE', niveau: 2 }),
  });
  sandbox.setStudentToken(reg2.token); // bascule de session (comme un autre étudiant sur son propre appareil)
  const listAsStudent2 = await sandbox.apiFetchAsStudent('/api/documents');
  const seenByS2 = listAsStudent2.find(d => d.id === docId).sections.find(s => s.id === sectionId);
  check('contenu verrouillé masqué pour un étudiant qui n\'a PAS payé', seenByS2.contenu === null, seenByS2.contenu);

  console.log('\n=== 6) Déverrouillage payant par l\'étudiant 2 ===');
  const unlockResult = await sandbox.apiFetchAsStudent(`/api/documents/sections/${sectionId}/unlock`, { method: 'POST' });
  check('crédits débités (20 - 15 = 5)', unlockResult.creditsRestants === 5, unlockResult.creditsRestants);

  console.log('\n=== 7) Après paiement, le contenu devient visible POUR LUI SEULEMENT ===');
  const listAfter = await sandbox.apiFetchAsStudent('/api/documents');
  const nowVisible = listAfter.find(d => d.id === docId).sections.find(s => s.id === sectionId);
  check('contenu maintenant visible pour l\'étudiant 2', nowVisible.contenu === 'Coordonnées GPS précises', nowVisible.contenu);

  sandbox.setStudentToken(studentToken); // retour à la session du premier étudiant (l'auteur)
  const listAsOriginalAuthor = await sandbox.apiFetchAsStudent('/api/documents');
  const authorSees = listAsOriginalAuthor.find(d => d.id === docId).sections.find(s => s.id === sectionId);
  check('l\'auteur voit toujours sa propre section (sans payer)', authorSees.contenu === 'Coordonnées GPS précises');

  console.log('\n=== 8) Un troisième étudiant (n\'a pas payé) ne voit toujours rien ===');
  const reg3 = await sandbox.apiRequest('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ name: 'Curieux', matricule: '24A0077EM', filiere: 'GMPG', niveau: 3 }),
  });
  sandbox.setStudentToken(reg3.token);
  const listAsS3 = await sandbox.apiFetchAsStudent('/api/documents');
  const s3sees = listAsS3.find(d => d.id === docId).sections.find(s => s.id === sectionId);
  check('un étudiant tiers non payeur ne voit toujours pas le contenu', s3sees.contenu === null, s3sees.contenu);

  console.log('\n=== 9) Connexion admin — mauvais mot de passe rejeté ===');
  try {
    await sandbox.apiRequest('/api/auth/admin-login', { method: 'POST', body: JSON.stringify({ password: 'mauvais' }) });
    check('mauvais mot de passe admin rejeté', false);
  } catch (e) {
    check('mauvais mot de passe admin rejeté', /incorrect/i.test(e.message), e.message);
  }

  console.log('\n=== 10) Connexion admin — bon mot de passe accepté ===');
  const adminLogin = await sandbox.apiRequest('/api/auth/admin-login', {
    method: 'POST', body: JSON.stringify({ password: 'NZJG_manitou4545!@#$%^&admi1' }),
  });
  check('jeton admin reçu', !!adminLogin.token);
  const adminToken = adminLogin.token;

  console.log('\n=== 11) Un étudiant ne peut PAS accéder aux routes admin avec son propre jeton ===');
  try {
    await sandbox.apiRequest('/api/admin/documents', {}, studentToken);
    check('un jeton étudiant est rejeté sur une route admin', false);
  } catch (e) {
    check('un jeton étudiant est rejeté sur une route admin', /401|administrateur/i.test(e.message), e.message);
  }

  console.log('\n=== 12) L\'admin voit le contenu intégral (y compris verrouillé) ===');
  const adminDocs = await sandbox.apiRequest('/api/admin/documents', {}, adminToken);
  check('admin liste les documents', adminDocs.length >= 1, adminDocs.length);

  console.log('\n=== 13) Publication admin avec date libre ===');
  const adminDoc = await sandbox.apiRequest('/api/admin/documents', {
    method: 'POST',
    body: JSON.stringify({
      titre: 'Archive régularisée', resume: 'Ancien document', type: 'Mémoire', filiere: 'RPC',
      auteur: 'Ancien Diplômé', annee: 2019, datePublication: '2019-06-15',
      sections: [{ titre: 'Contenu', contenu: 'Texte', locked: false, prix: 0 }],
    }),
  }, adminToken);
  check('date de publication libre appliquée', adminDoc.datePublication.startsWith('2019-06-15'), adminDoc.datePublication);
  check('marqué adminPublished', adminDoc.adminPublished === true);

  console.log('\n=== 14) Attribution de badge par l\'admin ===');
  const badgeResult = await sandbox.apiRequest('/api/admin/badges', {
    method: 'POST',
    body: JSON.stringify({ matricule: '22A0199EM', badgeKey: 'hse', docTitre: created.titre }),
  }, adminToken);
  check('badge attribué', badgeResult.badgeKey === 'hse', badgeResult);

  const myBadges = await sandbox.apiFetchPublic('/api/badges/22A0199EM');
  check('badge visible publiquement pour ce matricule', myBadges.some(b => b.badgeKey === 'hse'));

  console.log('\n=== 15) Suppression admin d\'un document ===');
  await sandbox.apiRequest(`/api/admin/documents/${adminDoc.id}`, { method: 'DELETE' }, adminToken);
  const afterDelete = await sandbox.apiFetchPublic('/api/documents');
  check('document supprimé', !afterDelete.some(d => d.id === adminDoc.id));

  console.log('\n============================================');
  if (failures === 0) console.log('✅ TOUS LES TESTS SONT PASSÉS (' + '15 scénarios' + ')');
  else console.log('❌ ' + failures + ' échec(s) détecté(s)');
  console.log('============================================\n');
  process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error('ERREUR FATALE:', e); process.exit(1); });
