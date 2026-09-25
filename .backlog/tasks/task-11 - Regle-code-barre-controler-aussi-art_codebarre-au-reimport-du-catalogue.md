---
id: TASK-11
title: 'Regle code-barre : controler aussi art_codebarre au reimport du catalogue'
status: To Do
assignee: []
created_date: '2026-09-25 09:36'
labels: []
dependencies:
  - TASK-3
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Le controle 'un code-barre = un article' (TASK-3 #7) ne fonctionne que dans un sens. importCorrespondance verifie que le code-barre n'est pas deja le code principal d'un autre article, mais appliquerCatalogue ne consulte jamais art_codebarre : un catalogue qui donne a P01 comme code principal un code deja rattache a P02 dans art_codebarre passe sans alerte. Le code designe alors deux articles et le rattachement a P02 devient mort, puisque resoudre() trouve P01 en premier. Il faut d'abord decider qui l'emporte, le catalogue ou la correspondance.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 La regle de priorite entre catalogue et correspondance est decidee et consignee
- [ ] #2 analyserCatalogue detecte les codes principaux entrants deja presents dans art_codebarre pour un autre article, avant toute ecriture
- [ ] #3 Le conflit est presente a l'utilisateur, dans le dialogue d'arbitrage existant ou equivalent
- [ ] #4 Un test Room en memoire couvre ce sens du controle
<!-- AC:END -->
