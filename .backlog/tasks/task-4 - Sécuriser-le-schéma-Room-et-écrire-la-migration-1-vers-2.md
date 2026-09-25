---
id: TASK-4
title: Sécuriser le schéma Room et écrire la migration 1 vers 2
status: Done
assignee: []
created_date: '2026-09-17 15:53'
updated_date: '2026-09-17 18:12'
labels: []
dependencies: []
references:
  - ../nirgescom-api/docs/SPEC.md
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La base est en version 1 avec fallbackToDestructiveMigration() et aucun schéma exporté : app/schemas/ n'existe pas car room.schemaLocation n'est pas passé en argument kapt, malgré exportSchema = true.

La synchro WiFi (SPEC §5) exige d'ajouter des colonnes sur la table sessions. En l'état, ce changement effacera les sessions collectées sur les tablettes.

Autant écrire une seule migration portant tout ce dont la V2 a besoin plutôt que d'en enchaîner deux.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 room.schemaLocation est configuré et app/schemas/1.json est committé
- [x] #2 fallbackToDestructiveMigration() est retiré de DatabaseModule.kt
- [x] #3 La migration 1 vers 2 ajoute uuid_session, statut_sync, date_derniere_tentative, nb_tentatives et message_erreur_sync
- [x] #4 Une base en version 1 contenant des sessions survit à la migration sans perte
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Première tranche faite : room.schemaLocation ajouté en argument kapt dans app/build.gradle.kts. Le build confirmait le diagnostic par un warning explicite (Schema export directory was not provided).

Reste : committer app/schemas/1.json une fois généré, retirer fallbackToDestructiveMigration, puis écrire la migration 1 vers 2. L'ordre compte — il faut le schéma 1 exporté comme référence avant de pouvoir écrire et valider la migration.

app/schemas/com.jdcosmetics.stockcollect.data.db.StockCollectDatabase/1.json généré et le warning Room a disparu du build. Le schéma confirme la règle de gestion : index unique sur articles.code_barre_principal, code_barre en clé primaire de art_codebarre.

Migration écrite. Base passée en version 2, fallbackToDestructiveMigration retiré au profit de addMigrations(*MIGRATIONS).

Migrations.kt contient MIGRATION_1_2 : ajout sur sessions de uuid_session (TEXT nullable), statut_sync (NOT NULL DEFAULT 'NON_SYNCHRONISEE'), date_derniere_tentative, nb_tentatives (NOT NULL DEFAULT 0), message_erreur_sync.

uuid_session est nullable à dessein : SQLite ne sait pas générer d'UUID, donc aucun backfill n'est possible en SQL. Il sera posé à la création pour les nouvelles sessions et à la première synchronisation pour les anciennes. Une fois écrit il ne bouge plus, l'idempotence de hash_ligne en dépend.

statut_sync est une colonne distincte de statut : l'export CSV restant un repli disponible à tout moment, une session peut être EXPORTEE et synchronisée.

Objet StatutSync ajouté (constantes chaîne, comme StatutSession).

Piège traité : SQLite exige un DEFAULT pour ajouter une colonne NOT NULL à une table peuplée, et Room compare ces DEFAULT au @ColumnInfo(defaultValue) de l'entité. Un désaccord ne se verrait qu'au premier lancement sur une tablette déjà en v1. D'où l'ajout de androidx.room:room-testing et d'un test instrumenté MigrationTest qui rejoue la migration sur une vraie base et la valide contre le schéma exporté.

MigrationTest instrumente vert sur emulateur API 28 (2 tests) : une base v1 contenant une session BROUILLON survit, ses champs metier sont intacts, statut_sync prend NON_SYNCHRONISEE et nb_tentatives 0. runMigrationsAndValidate confronte le schema obtenu a app/schemas/2.json, ce qui garantit que le DEFAULT de la migration correspond au @ColumnInfo de l entite. Verifie aussi sur l appareil apres usage reel : PRAGMA user_version = 2.

AC4 NON coche : uuid_session existe en colonne mais n est pas encore renseigne a la creation d une session (verifie en base, la session 1 a uuid_session NULL). C est du ressort de TASK-8, qui porte la synchro. A arbitrer : deplacer cet AC vers TASK-8 ou le traiter ici.

AC « uuid_session est un UUID v4 stable, renseigne a la creation de la session » deplace vers TASK-8 sur decision utilisateur : la colonne et la migration relevent bien de cette tache, mais le fait de la renseigner appartient a la synchro.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Le schema Room est exporte (app/schemas/1.json et 2.json committes), fallbackToDestructiveMigration retire au profit de addMigrations, et la migration 1 vers 2 ajoute en une fois les cinq colonnes dont la synchro a besoin. Verifie par MigrationTest instrumente sur emulateur API 28 : une base v1 contenant une session survit sans perte et le schema obtenu est confronte a 2.json, ce qui garantit que les DEFAULT de la migration correspondent aux @ColumnInfo des entites. Confirme aussi sur l appareil apres usage reel (PRAGMA user_version = 2).
<!-- SECTION:FINAL_SUMMARY:END -->
