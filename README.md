# StockCollect — version originale (branche `v1-original`)

Cette branche **fige la version d'origine** de StockCollect, telle qu'elle était sur `master`
au commit `e384227` (« Bugfixes & regles de gestion session/inventaire »), avant tout le travail V2.

- Application **entièrement hors ligne** : import du catalogue CSV, scan, comptage, export CSV.
  Aucune permission `INTERNET`, aucune synchro vers Nirgescom.
- **Aucune des corrections des lots 0 à 7** (import du catalogue, migrations Room, bugs de
  session, build release…). Ces corrections et la synchro WiFi vivent sur `feat/v2-synchro-wifi`,
  destinée à être fusionnée dans `master`.
- Base Room en **version 1**, avec `fallbackToDestructiveMigration()`.

Ce README est le seul ajout par rapport à `e384227` : le code est strictement celui d'origine.

## Règles

- **Ne jamais fusionner `master` ni `feat/v2-synchro-wifi` dans cette branche**, sinon elle
  cesse d'être la version d'origine.
- Un correctif urgent sur la V1 se fait dans une branche partie d'ici
  (`git switch -c v1-correctif v1-original`), pas directement sur `v1-original`.

## Revenir sur cette version

```bash
git fetch origin
git switch v1-original              # ou : git worktree add ../stockCollect-v1 v1-original
./gradlew :app:assembleDebug        # appId debug : com.jdcosmetics.stockcollect.debug
./gradlew installDebug
```

`./gradlew :app:assembleRelease` **échoue** sur cette version : `app/build.gradle.kts` référence
`proguard-rules.pro`, qui n'existe pas (corrigé en V2, lot 1). Seul le build debug fonctionne.

## Attention : installer la V1 sur une tablette qui a déjà la V2

La V1 et la V2 ont le **même `applicationId` et le même `versionCode` (1)**. Android accepte donc
d'installer la V1 par-dessus la V2 **sans aucun avertissement**. Au premier lancement, Room trouve
une base en version 3 alors que le code attend la version 1 : `fallbackToDestructiveMigration()`
autorise aussi les rétrogradations, donc **la base est effacée et recréée vide** — catalogue,
sessions en cours et historique compris.

Avant de réinstaller la V1 sur une tablette qui a tourné en V2 :

1. Clôturer et **exporter en CSV** (ou synchroniser) toutes les sessions à conserver ;
2. Garder à portée les fichiers catalogue et `art_codebarre` pour les réimporter ;
3. Installer la V1, puis réimporter le catalogue et les correspondances.

Dans l'autre sens (V1 → V2), aucune perte : la V2 embarque la migration 1 → 2 → 3.
