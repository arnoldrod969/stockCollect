---
id: TASK-7
title: 'Hygiène : permissions inutiles, sauvegarde, tests'
status: To Do
assignee: []
created_date: '2026-09-17 15:53'
updated_date: '2026-09-18 17:02'
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
- [x] #1 READ_EXTERNAL_STORAGE et READ_MEDIA_IMAGES sont retirés du manifeste et l'import CSV fonctionne toujours
- [x] #2 allowBackup est à false ou des dataExtractionRules excluent la base
- [ ] #3 CsvImportService est couvert par des tests sur base Room in-memory
- [ ] #4 BarcodeScanService est couvert, résolution directe et via art_codebarre
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC1 : READ_EXTERNAL_STORAGE et READ_MEDIA_IMAGES retires, et l import CSV a ete rejoue ensuite de bout en bout (catalogue 2723 articles + 267 correspondances) ainsi que l export via SAF — aucune permission n est necessaire. AC2 : allowBackup=false.
AC3 et AC4 restent a faire : aucun test sur CsvImportService ni BarcodeScanService.

Passe de qualite livree dans le commit Lot 7 (ae09a4a), issue de trois passes du skill impeccable menees en parallele puis verifiees a l'execution.

Trouvailles qui n'auraient pas ete vues par relecture seule :
- « Permission camera refusee » en blanc sur blanc casse (1,1:1), invisible au moment ou il sert
- bouton Exporter mort sur chaque ligne de l'Historique (deux btn_exporter homonymes dans deux layouts)
- accueil fige au retour : le ViewModel survit a l'aller-retour, seul le reglage etait relu, donc le raccourci Reprendre ne montrait pas le brouillon qu'on venait de creer
- etape 2 de l'import destructrice et non annoncee, et cliquable a zero article
- roles Material 3 non declares : composants retombes sur la palette M3 teintee violet
- theme DayNight avec la moitie des roles seulement : mode nuit incoherent, passe en Light

Reste a trancher avec l'utilisateur, hors de ce lot :
- la pastille de statut de l'Historique est bleue quel que soit le statut (bg_chip est un shape statique) : Brouillon, Cloturee et Exportee sont visuellement identiques
- le bandeau bleu de l'accueil double l'ActionBar, deux bandes bleues empilees ; le retirer change l'identite de l'ecran
- item_session_export.xml est mort, aucun binding ne le reference
- values-night reel, pour rendre un mode sombre complet
<!-- SECTION:NOTES:END -->
