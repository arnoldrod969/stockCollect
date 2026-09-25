---
id: TASK-15
title: 'Import CSV : signaler les caracteres de controle dans les noms de produit'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-25 09:36'
updated_date: '2026-09-25 10:57'
labels: []
dependencies: []
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Un nom de produit contenant une tabulation ou un autre caractere de controle (fichier separe par ';', ou octet parasite) passe l'import du catalogue sans erreur, puis bloque l'envoi de toute session qui contient l'article : l'API refuse les caracteres de controle en 422, et ValidationEnvoi le signale avant envoi. Le probleme doit etre vu a l'import, ou il est corrigeable, plutot qu'a l'envoi.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 L'import du catalogue signale une ligne dont le nom ou un code contient un caractere de controle, ou un emoji hors BMP
- [ ] #2 Le comportement choisi (erreur de ligne ou nettoyage explicite annonce a l'utilisateur) est teste dans CsvParserTest ou equivalent
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Reutiliser ValidationEnvoi.problemeTexte (SyncService.kt) sans le deplacer.
2. analyserCatalogue : code produit, code-barre principal (code=true, LONGUEUR_CODE) et nom (LONGUEUR_NOM_PRODUIT) -> erreur de ligne 'Ligne N : le nom de l'article contient ...', comptee dans le seuil de 10 %.
3. importCorrespondance : meme controle sur code-barre et code produit.
4. Pas de nettoyage : le trim de CsvParser reste tel quel.
5. Tests Room en memoire (androidTest) : tabulation interne, emoji, seuil.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Comportement retenu : ERREUR DE LIGNE (comptee dans le seuil de 10 %, 'Ligne N : le nom de l'article contient ...'), pas de nettoyage. Regles reutilisees telles quelles : ValidationEnvoi.problemeTexte (SyncService.kt), appelee depuis CsvImportService, sans deplacement de code. Catalogue : code produit et code-barre (code=true, 50 car.), nom (255 car.). Correspondance : code-barre et code produit (code=true). Le trim de CsvParser est inchange : un espace en bordure reste accepte (teste). Effet de bord assume : un code-barre catalogue > 50 caracteres devient aussi une erreur de ligne (il bloquerait l'envoi de meme). Les caracteres C1 (0x80-0x9F) hors NEL ne sont pas signales : problemeTexte ne les signale pas non plus a l'envoi.
Tests : ImportCatalogueTest (tabulation dans le nom, emoji, controle dans code-barre et code produit, bordures rognees) et CorrespondanceCodeBarreTest (tabulation, emoji) — androidTest, non executes ici.
<!-- SECTION:NOTES:END -->
