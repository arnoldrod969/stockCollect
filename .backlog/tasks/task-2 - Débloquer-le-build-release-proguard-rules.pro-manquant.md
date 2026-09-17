---
id: TASK-2
title: Débloquer le build release (proguard-rules.pro manquant)
status: Done
assignee: []
created_date: '2026-09-17 15:52'
updated_date: '2026-09-17 16:38'
labels: []
dependencies: []
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
app/build.gradle.kts référence proguard-rules.pro, qui n'existe pas : assembleRelease échoue. R8 et shrinkResources sont actifs sans aucune keep rule pour Room, Hilt, ML Kit ou Navigation. Aucun APK de production n'est produisible.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 app/proguard-rules.pro existe avec les keep rules Room, Hilt, ML Kit et Navigation
- [x] #2 assembleRelease produit un APK sans erreur
- [x] #3 Les fragments restent instanciables depuis nav_graph.xml en build minifié
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Fichier écrit avec les keep rules. Reste à vérifier assembleRelease : le wrapper réclame Gradle 8.4 qui n'est pas installé, vérification faite avec le Gradle 8.7 déjà présent.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
app/proguard-rules.pro écrit : keep rules Room (Class.forName sur _Impl), Hilt, ML Kit, et Fragments/Activity/Application instanciés par réflexion depuis nav_graph.xml.

Les dontwarn OpenCSV ont été retirés en même temps que la dépendance (cf. TASK-6).

Vérification d'assembleRelease bloquée : le wrapper réclame Gradle 8.4, jamais téléchargé entièrement sur cette machine (réseau instable, échecs Connection reset). Les vérifications passent par le Gradle 8.7 déjà installé, sur décision de l'utilisateur.

Vérification objective :
- assembleRelease OK avec Gradle 8.7, tâches minifyReleaseWithR8 et shrinkReleaseRes exécutées, APK de 24 Mo produit.
- mapping.txt : les 10 destinations de nav_graph.xml apparaissent en mapping identité (non renommées, non supprimées), ainsi que leurs superclasses Hilt_*, MainActivity et StockCollectApp.
- APK release signé avec le keystore debug puis installé sur l'émulateur (API 28) et lancé : HomeFragment, ImportCatalogueFragment, HistoriqueFragment et NouvelleSessionFragment s'affichent correctement après navigation réelle, aucun FATAL EXCEPTION, ClassNotFoundException, NoSuchMethodError ni InflateException dans logcat.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Ajout de app/proguard-rules.pro : keep rules pour Room (Class.forName sur _Impl), Hilt, ML Kit, et les Fragments/Activity/Application que Navigation instancie par réflexion depuis nav_graph.xml. Vérifié par assembleRelease réussi, par l'inspection du mapping R8 (classes conservées sans renommage) et par l'exécution réelle de l'APK minifié sur émulateur, navigation comprise, sans aucune exception.
<!-- SECTION:FINAL_SUMMARY:END -->
