---
id: TASK-18
title: 'Import CSV des codes-barres refuse : afficher le detail des lignes fautives'
status: To Do
assignee: []
created_date: '2026-09-25 14:13'
labels:
  - bug
  - import
dependencies: []
ordinal: 19000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Quand l'import par fichier de la correspondance code-barres depasse le seuil de 10 % d'erreurs, ImportCatalogueViewModel.importerCorrespondance ne transmet pas result.erreurs et ImportCatalogueFragment n'affiche qu'une Snackbar. Le magasinier ne voit pas quelles lignes corriger, contrairement a l'import du catalogue et a l'import des codes-barres depuis Nirgescom, qui affichent un dialogue avec le detail. Releve par la revue du 25/09.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Un import CSV de codes-barres refuse affiche un dialogue avec le message et la liste des lignes fautives, comme l'import du catalogue
- [ ] #2 ImportUiState.Error porte les erreurs de ligne pour la correspondance
<!-- AC:END -->
