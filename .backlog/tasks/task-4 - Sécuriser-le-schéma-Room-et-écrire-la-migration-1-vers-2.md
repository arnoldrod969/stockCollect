---
id: TASK-4
title: Sécuriser le schéma Room et écrire la migration 1 vers 2
status: In Progress
assignee: []
created_date: '2026-09-17 15:53'
updated_date: '2026-09-17 16:44'
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
- [ ] #1 room.schemaLocation est configuré et app/schemas/1.json est committé
- [ ] #2 fallbackToDestructiveMigration() est retiré de DatabaseModule.kt
- [ ] #3 La migration 1 vers 2 ajoute uuid_session, statut_sync, date_derniere_tentative, nb_tentatives et message_erreur_sync
- [ ] #4 uuid_session est un UUID v4 stable, renseigné à la création de la session
- [ ] #5 Une base en version 1 contenant des sessions survit à la migration sans perte
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
<!-- SECTION:NOTES:END -->
