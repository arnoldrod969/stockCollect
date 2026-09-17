---
id: TASK-8
title: Synchro WiFi des sessions vers Nirgescom
status: In Progress
assignee: []
created_date: '2026-09-17 15:54'
updated_date: '2026-09-17 18:40'
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
- [ ] #3 Une session CLOTUREE peut être envoyée par POST /documents et passe à SYNCHRONISEE sur 201
- [ ] #4 Une erreur réseau ou 5xx passe la session à ECHEC_SYNC et le réessai est possible
- [ ] #5 Le renvoi d'une session déjà synchronisée est traité comme un succès
- [ ] #6 hash_ligne n'est pas envoyé dans le corps de la requête
- [ ] #7 L'Historique peut interroger GET /documents/{session_id} pour afficher l'état Nirgescom
- [ ] #8 L'export CSV reste disponible sur une session CLOTUREE, indépendamment de la synchro
- [ ] #9 uuid_session est un UUID v4 stable, renseigne a la creation de la session (deplace depuis TASK-4)
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
<!-- SECTION:NOTES:END -->
