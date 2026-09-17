---
id: TASK-7
title: 'Hygiène : permissions inutiles, sauvegarde, tests'
status: To Do
assignee: []
created_date: '2026-09-17 15:53'
labels: []
dependencies: []
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Le manifeste déclare READ_EXTERNAL_STORAGE et READ_MEDIA_IMAGES alors que les CSV sont lus via SAF, qui ne requiert aucune des deux. READ_MEDIA_IMAGES ne concerne même pas les CSV et est un mauvais signal à la publication.

allowBackup est à true sans dataExtractionRules : la base de collecte part en sauvegarde cloud et peut être restaurée dans un état périmé.

La couverture de test se limite à CsvParserTest.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 READ_EXTERNAL_STORAGE et READ_MEDIA_IMAGES sont retirés du manifeste et l'import CSV fonctionne toujours
- [ ] #2 allowBackup est à false ou des dataExtractionRules excluent la base
- [ ] #3 CsvImportService est couvert par des tests sur base Room in-memory
- [ ] #4 BarcodeScanService est couvert, résolution directe et via art_codebarre
<!-- AC:END -->
