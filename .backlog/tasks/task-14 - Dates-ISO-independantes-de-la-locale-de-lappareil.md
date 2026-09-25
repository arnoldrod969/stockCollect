---
id: TASK-14
title: Dates ISO independantes de la locale de l'appareil
status: Done
assignee:
  - '@claude'
created_date: '2026-09-25 09:36'
updated_date: '2026-09-25 11:44'
labels: []
dependencies: []
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DateUtils.nowIso (et isoFormat) utilise un SimpleDateFormat partage, non thread-safe, construit avec Locale.getDefault(). Sur une tablette reglee dans une locale a chiffres non latins, la date sort du format ISO attendu par l'API (date_heure_cloture) et l'envoi finit en 422 permanent ; un acces concurrent peut aussi corrompre une date. ValidationEnvoi suppose pourtant la date garantie par construction.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Les dates ISO sont produites avec Locale.US (ou ROOT) et un formateur thread-safe
- [x] #2 Un test fixe le format produit sous une locale a chiffres non latins (ex. ar)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. DateUtils : remplacer les SimpleDateFormat partages par des DateTimeFormatter (immuables, thread-safe). ISO et nom de fichier en Locale.US ; affichage dans la locale de l'appareil.
2. Relecture (toDisplay/toDisplayDate) via parse(ParsePosition) pour garder la tolerance de l'ancien parse sur une suite eventuelle.
3. Verifier chaque appelant (nowIso, toFileName, toDisplay, toDisplayDate).
4. Test JVM DateUtilsTest sous ar-EG : format ISO, nom de fichier, relecture, concurrence.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
DateUtils : DateTimeFormatter immuables ; ISO et nom de fichier en Locale.US, affichage en locale appareil. Ajouts toIso(LocalDateTime), toFileName(LocalDateTime), parseIso interne (parse avec ParsePosition : suite apres les secondes toleree comme avant). Formats produits inchanges.
Appelants verifies : nowIso (SessionRepository x3, SyncService x2 dont date_heure_cloture, CsvExportService, CsvImportService x2, ParametresViewModel), toFileName (CsvExportService, ExportViewModel), toDisplay/toDisplayDate (DetailSession, Export, Historique, Home, ImportCatalogue, Recapitulatif). Aucune autre relecture de date stockee dans le code.
Test JVM app/src/test/.../util/DateUtilsTest.kt (7 tests, 0 ignore, 0 echec) sous ar-EG : temoin prouvant que l'ancien SimpleDateFormat sortait des chiffres arabo-indiens sur ce JVM, nowIso ASCII, format ISO et nom de fichier fixes, relecture, concurrence 8 threads.
Limite : une date deja ecrite en chiffres non latins sur une tablette (avant correctif) ne se relit plus et s'affiche brute ; elle reste invalide pour l'API.

DateUtilsTest (locale ar). Verification 25/09 : testDebugUnitTest, lintDebug et assembleRelease OK ; connectedDebugAndroidTest 67/67 sur emulateur (127.0.0.1:21503).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Dates ISO et nom de fichier en java.time + Locale.US, thread-safe ; DateUtilsTest fige le format sous une locale a chiffres arabes.
<!-- SECTION:FINAL_SUMMARY:END -->
