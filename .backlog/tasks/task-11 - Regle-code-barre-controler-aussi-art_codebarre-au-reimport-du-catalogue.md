---
id: TASK-11
title: 'Regle code-barre : controler aussi art_codebarre au reimport du catalogue'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-25 09:36'
updated_date: '2026-09-25 10:57'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Regle decidee par l'utilisateur : le catalogue l'emporte sur la correspondance.
2. ArtCodebarreDao : getAll() et supprimerCodesBarres(codes).
3. analyserCatalogue lit art_codebarre (lecture seule) et liste les codes principaux entrants deja rattaches a un AUTRE article dans art_codebarre (porteur final = premier article du fichier, le meme quelle que soit la resolution).
4. appliquerCatalogue recalcule les evictions dans la transaction et supprime ces correspondances avant d'ecrire les articles ; ImportResult porte le decompte et le detail (code-barre, ancien article, nouvel article).
5. Information utilisateur : paragraphe dans le dialogue d'arbitrage si arbitrage, sinon dans le compte-rendu d'import. Pas de choix supplementaire.
6. CLAUDE.md (Domain model) + test Room en memoire.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Regle de priorite decidee par l'utilisateur (2026-09-25) : LE CATALOGUE L'EMPORTE sur la correspondance. Un code principal entrant rattache dans art_codebarre a un AUTRE article en est retire, sans choix a faire ; l'utilisateur en est seulement informe. Le sens inverse (correspondance qui vise un code principal d'un autre article) reste une ligne rejetee par importCorrespondance.
Implementation : ArtCodebarreDao.getAll / supprimerCodesBarres ; AnalyseCatalogue.correspondancesRetirees (CorrespondanceRetiree : code-barre, article qui le perd, article du catalogue) calculee par analyserCatalogue en lecture seule ; appliquerCatalogue la recalcule dans sa transaction (etat de la base au moment de l'ecriture) et supprime les correspondances avant d'ecrire les articles. Le porteur retenu est le premier article du fichier (gagnant de l'arbitrage), donc le retrait ne depend pas de la resolution choisie. Un code rattache au meme article dans les deux tables reste (redondant, inoffensif).
Information : paragraphe dans le dialogue d'arbitrage (resumeCorrespondancesRetirees) ; ImportResult.nbCorrespondancesRetirees + ligne du resume + detail en tete de la liste 'Details' du compte-rendu.
Regle consignee dans CLAUDE.md (Domain model). Tests : ImportCatalogueTest (androidTest), 2 cas TASK-11 — non executes ici (pas d'emulateur).
<!-- SECTION:NOTES:END -->
