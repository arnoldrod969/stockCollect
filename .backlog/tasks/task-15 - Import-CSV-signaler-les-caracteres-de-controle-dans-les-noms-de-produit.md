---
id: TASK-15
title: 'Import CSV : signaler les caracteres de controle dans les noms de produit'
status: To Do
assignee: []
created_date: '2026-09-25 09:36'
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
