---
id: TASK-8
title: Synchro WiFi des sessions vers Nirgescom
status: Done
assignee: []
created_date: '2026-09-17 15:54'
updated_date: '2026-09-18 15:54'
labels: []
dependencies:
  - TASK-4
  - TASK-5
references:
  - ../nirgescom-api/docs/SPEC.md
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Brancher l'app sur l'API nirgescom-api, déjà implémentée (8 routes, tests, Swagger). L'app n'a aujourd'hui aucun code réseau.

Contrat : ../nirgescom-api/docs/SPEC.md, sections 4 et 5.

Points de vigilance connus :
- Le manifeste n'a ni INTERNET ni ACCESS_NETWORK_STATE, et s'en revendique explicitement. La spec assume l'absence de HTTPS sur le LAN, donc Android 9+ bloquera le trafic en clair sans network_security_config.
- magasin doit valoir exactement le libellé porté par la clé d'API, sinon 403. Ce n'est pas sessions.lieu.
- Le renvoi d'une session déjà reçue répond 201 avec lignes_ignorees superieur a 0 : c'est un succès, pas une erreur.
- hash_ligne ne doit pas être envoyé, l'API le calcule et fait foi.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Le manifeste déclare INTERNET et ACCESS_NETWORK_STATE avec un network_security_config autorisant l'hôte du LAN en clair
- [x] #2 Un écran Paramètres permet de saisir URL, clé d'API, magasin et identifiant tablette, et de tester la connexion via GET /health
- [x] #3 Une session CLOTUREE peut être envoyée par POST /documents et passe à SYNCHRONISEE sur 201
- [x] #4 Une erreur réseau ou 5xx passe la session à ECHEC_SYNC et le réessai est possible
- [x] #5 Le renvoi d'une session déjà synchronisée est traité comme un succès
- [x] #6 hash_ligne n'est pas envoyé dans le corps de la requête
- [x] #7 L'Historique peut interroger GET /documents/{session_id} pour afficher l'état Nirgescom
- [x] #8 L'export CSV reste disponible sur une session CLOTUREE, indépendamment de la synchro
- [x] #9 uuid_session est un UUID v4 stable, renseigne a la creation de la session (deplace depuis TASK-4)
- [x] #10 Le magasin n'est pas saisi mais choisi dans la liste fournie par GET /magasins, mise en cache localement, et le champ Depot disparait de Nouvelle Session
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Decoupe en deux tranches pour livrer l ecran visible d abord.

TRANCHE 1 (celle-ci) — AC1 et AC2 :
1. Manifeste : INTERNET + ACCESS_NETWORK_STATE, et network_security_config autorisant le clair. La spec assume l absence de HTTPS sur le LAN (SPEC section 3) et Android 9+ bloque le trafic non chiffre par defaut.
2. ParametresSync : wrapper SharedPreferences injecte par Hilt (pas de DataStore, aucune dependance de ce type dans le projet). Champs : urlApi, cleApi, magasin, identifiantTablette.
3. NirgescomClient minimal : GET /health via HttpURLConnection, pas de librairie ajoutee. Base URL = <hote>/api, /health sans authentification (SPEC 4.1).
4. ParametresFragment + ParametresViewModel + layout, suivant les conventions du projet : ViewBinding, LiveData republiee depuis le ViewModel, sealed class d etat en tete du fichier du ViewModel, identifiants francais.
5. Entree dans nav_graph avec l argument dummy, et une ligne row_parametres sur l accueil a cote de Import catalogue.

TRANCHE 2 (suivante) — AC3 a AC8 : POST /documents, statut_sync, uuid_session pose a la creation de session, bouton Synchroniser, consultation GET /documents/{id} dans l Historique.

Point de vigilance repris du contrat : magasin doit valoir exactement le libelle porte par la cle d API, sinon 403. L ecran doit le dire a l utilisateur.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Tranche 1 livree (AC1, AC2). Verifie sur emulateur API 28 contre un vrai endpoint /api/health :
- adresse vide -> « Renseignez d abord l adresse du serveur. »
- port ferme -> « Serveur injoignable. Verifiez le WiFi et l adresse. » suivi du detail reseau
- serveur debout -> « Connexion etablie. API version 0.1. », et le serveur a journalise la requete GET /api/health
- les 4 reglages survivent a un force-stop puis relance ; l accueil passe de « Non configure » a « Magasin : ... »

Decision assumee sur network_security_config : cleartext autorise globalement. L adresse est saisie a l execution et vaut une IP privee quelconque ; cleartextTrafficPermitted ne s applique qu a des domaines litteraux, Android ne sait pas exprimer une plage. Une liste d hotes imposerait de recompiler par magasin. Le fichier disparait quand l API passe en TLS.

Reste la tranche 2 : AC3 a AC9 (POST /documents, statut_sync, uuid_session pose a la creation, bouton Synchroniser, GET /documents/{id} dans l Historique).

Tranche 1bis — depot = magasin (AC10).

Decision : depot et magasin sont une seule notion. Le champ Depot de Nouvelle Session est supprime ; la session herite du magasin regle et en garde un instantane dans sessions.lieu (meme principe que nom_produit_snap). Relire le reglage a l envoi etiquetterait silencieusement la collecte sous le mauvais depot apres une reconfiguration ; l instantane fait echouer l envoi en 403, ce qui est corrigeable.

Base v3 : table magasins (code_magasin PK, nom_magasin nullable, date_import), MIGRATION_2_3, schema 3.json committe, MigrationTest etendu (2 vers 3, et 1 vers 3 enchainee). 4 tests instrumentes verts.

nom_magasin est nullable pour de vrai cote API : un depot sans libelle est liste mais refuse, il n y aurait rien a envoyer.

Defaut trouve a l execution : le MaterialAutoCompleteTextView en ExposedDropdownMenu ne s ouvrait pas du tout sur la tablette de test (aucune fenetre popup, aucune erreur en logcat, ni au tap sur le champ ni sur l icone). Remplace par un champ non saisissable qui ouvre un MaterialAlertDialog liste — le motif de selection deja utilise partout ailleurs dans l app, et qui sait refuser une entree non selectionnable.

Verifie sur emulateur contre une instance de l API branchee sur la base de dev (3 depots dont un sans libelle) :
1. cache vide -> Enregistrer refuse : « Recuperez d abord la liste des depots, puis choisissez-en un. »
2. mauvaise cle -> « Cle d API refusee par le serveur » (401 distingue d une panne reseau)
3. bonne cle -> « 3 depots recuperes. 1 sans libelle, non selectionnables. », table magasins peuplee avec le NULL conserve
4. MOKOLO (sans libelle) -> refuse avec sa raison
5. second appel -> 304 -> « Liste deja a jour (3 depots). », rien n est reecrit
6. NGOYA I enregistre -> accueil « Magasin : NGOYA I », prefs magasin_code=NGOYA1 / magasin_libelle=NGOYA I / etag
7. Nouvelle Session -> plus aucun champ Depot, sessions.lieu vaut « NGOYA I » en base
8. magasin change pour LEBOUDI -> la session deja creee garde « NGOYA I »
9. serveur coupe -> « Serveur injoignable » et la liste en cache reste ouvrable

gradle testDebugUnitTest, connectedDebugAndroidTest, lintDebug et assembleRelease passent.

Tranche 2 livree (AC3 a AC9).

SyncService compose SessionDao + LigneCollecteDao + ParametresSync + NirgescomClient, comme CsvExportService compose DAO et SAF. SessionRepository ne connait pas le reseau.

uuid_session est pose a la CREATION de session, pas au premier envoi : l API calcule hash_ligne a partir de lui, un renvoi apres coupure doit produire exactement les memes hash. Les sessions anterieures a la migration 1 vers 2 en recoivent un au premier envoi, via un UPDATE garde par WHERE uuid_session IS NULL.

Le champ magasin envoye est l INSTANTANE de la session (sessions.lieu), pas le reglage courant. Une tablette reconfiguree entre la collecte et l envoi rangerait sinon la collecte sous un depot ou elle n a pas eu lieu, en silence.

hash_ligne n est pas envoye : construireCorps enumere exhaustivement les champs, il n y figure pas.

Verifie sur emulateur contre l API branchee sur la base de dev, apres import du vrai catalogue (2723 articles, les 4 lignes a virgule recollees) :
AC3 : session cloturee -> « Session envoyee. 2 lignes enregistrees. », statut Synchronisee ; les 2 lignes sont en base avec codemagasin NGOYA1, type_operation 1, statut_import EN_ATTENTE et un hash_ligne calcule par l API
AC4 : serveur coupe -> « Echec de synchronisation » + « Serveur injoignable » ; serveur rendu -> reessai reussi
AC5 : renvoi -> 201 avec lignes_ignorees=2 -> « Session deja recue par Nirgescom : 2 lignes etaient deja en base. », traite en succes
AC7 : « Etat cote Nirgescom » -> « 0 traitees, 2 en attente, 0 en erreur (sur 2). »
AC8 : la session synchronisee garde son bouton Exporter dans l Historique, son statut reste Cloturee
403 : session collectee sous LEBOUDI envoyee avec une cle NGOYA I -> « Depot refuse... Cette session a ete collectee sous LEBOUDI, qui ne correspond pas au depot de la cle d API en place. » C est le comportement bruyant voulu par l instantane.

testDebugUnitTest, connectedDebugAndroidTest, lintDebug et assembleRelease passent.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Synchro WiFi complete. L app declare INTERNET avec un network_security_config en clair (l API n a pas de TLS sur le LAN), un ecran Parametres porte adresse, cle, tablette et le choix du depot dans la liste renvoyee par GET /magasins, et une session cloturee part vers Nirgescom par POST /documents depuis le Detail session, avec reprise sur echec et consultation de l etat par GET /documents/{id}. Verifie de bout en bout sur emulateur contre l API branchee sur la base de dev : envoi 201, renvoi idempotent traite en succes, coupure reseau -> ECHEC_SYNC puis reessai reussi, 403 explicite quand le depot de la session ne correspond pas a la cle, et lignes retrouvees en base de staging. testDebugUnitTest, connectedDebugAndroidTest, lintDebug et assembleRelease passent.
<!-- SECTION:FINAL_SUMMARY:END -->
