---
id: TASK-17
title: 'Catalogue : une ligne en double ne doit pas faire perdre l''article'
status: To Do
assignee: []
created_date: '2026-09-25 14:13'
labels:
  - bug
  - import
dependencies: []
ordinal: 18000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Si une meme ligne (meme code_produit, meme code-barres) apparait deux fois dans le fichier catalogue, CsvImportService.detecterConflits cree un conflit dont le gagnant et le perdant ont le meme code_produit. Le filtrage se faisant par code_produit, le choix 'Ignorer ces articles' fait disparaitre l'article entierement et 'Importer sans code-barres' lui retire son code-barres. Releve par la revue du 25/09 (CsvImportService.kt ~l.251-259 et ~347-358). L'API n'est a priori pas concernee (liste_article filtree par depot, sans doublon), un fichier CSV l'est.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Un fichier contenant deux fois la meme ligne importe l'article une seule fois, avec son code-barres, quel que soit le choix d'arbitrage
- [ ] #2 Un vrai conflit (deux code_produit differents pour un meme code-barres) reste presente a l'arbitrage comme aujourd'hui
- [ ] #3 Un test Room en memoire couvre le doublon exact
<!-- AC:END -->
