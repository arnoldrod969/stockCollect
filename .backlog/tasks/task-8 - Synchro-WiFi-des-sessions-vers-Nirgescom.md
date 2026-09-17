---
id: TASK-8
title: Synchro WiFi des sessions vers Nirgescom
status: To Do
assignee: []
created_date: '2026-09-17 15:54'
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
- [ ] #1 Le manifeste déclare INTERNET et ACCESS_NETWORK_STATE avec un network_security_config autorisant l'hôte du LAN en clair
- [ ] #2 Un écran Paramètres permet de saisir URL, clé d'API, magasin et identifiant tablette, et de tester la connexion via GET /health
- [ ] #3 Une session CLOTUREE peut être envoyée par POST /documents et passe à SYNCHRONISEE sur 201
- [ ] #4 Une erreur réseau ou 5xx passe la session à ECHEC_SYNC et le réessai est possible
- [ ] #5 Le renvoi d'une session déjà synchronisée est traité comme un succès
- [ ] #6 hash_ligne n'est pas envoyé dans le corps de la requête
- [ ] #7 L'Historique peut interroger GET /documents/{session_id} pour afficher l'état Nirgescom
- [ ] #8 L'export CSV reste disponible sur une session CLOTUREE, indépendamment de la synchro
<!-- AC:END -->
