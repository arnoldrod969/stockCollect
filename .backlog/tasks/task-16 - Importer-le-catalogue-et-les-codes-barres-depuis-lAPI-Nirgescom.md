---
id: TASK-16
title: Importer le catalogue et les codes-barres depuis l'API Nirgescom
status: To Do
assignee: []
created_date: '2026-09-25 10:49'
labels: []
dependencies:
  - TASK-11
  - TASK-15
references:
  - ../nirgescom-api/docs/SPEC.md
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Aujourd'hui le catalogue et la table de correspondance art_codebarre ne viennent que de fichiers CSV. L'API expose GET /catalog (articles, code-barres principal, nom, niveaux de prix, avec ETag / If-None-Match, filtre sur le depot de la cle) et GET /codes-barres (correspondance code-barres secondaire vers code produit), cf. ../nirgescom-api/docs/SPEC.md §8. Quand le catalogue viendra de l'API, les codes-barres secondaires doivent pouvoir etre remis a jour par la meme voie, sinon ils disparaissent ou restent figes. L'import CSV reste disponible : la collecte doit continuer a fonctionner sans reseau. Point d'attention : la vue codebarre_article est aujourd'hui VIDE sur jdbout2023 alors que l'app gere 266 codes-barres secondaires issus du CSV ; une reponse vide ne doit pas effacer une correspondance existante (meme regle que GET /magasins).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Depuis l'ecran d'import, l'utilisateur peut mettre a jour le catalogue depuis l'API (GET /catalog), avec ETag : un 304 est un succes sans reecriture
- [ ] #2 Le catalogue recu de l'API passe par la meme analyse et le meme arbitrage des conflits de codes-barres que l'import CSV, avant toute ecriture
- [ ] #3 L'utilisateur peut mettre a jour les codes-barres secondaires depuis l'API (GET /codes-barres), avec les memes controles que l'import CSV de correspondance (regle un code-barre = un article)
- [ ] #4 Une reponse vide de GET /codes-barres n'efface pas la correspondance existante ; l'utilisateur est informe
- [ ] #5 Les imports CSV restent disponibles et inchanges pour un usage hors ligne
- [ ] #6 Les erreurs reseau et HTTP (401, 403, 500, 503) ont un message propre, sur le modele de ReponsesNirgescom, couvert par des tests JVM sans appel reseau reel
<!-- AC:END -->
