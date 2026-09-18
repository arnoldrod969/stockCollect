# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Sync WiFi (V2)

Contrat avec l'API Nirgescom : **`../nirgescom-api/docs/SPEC.md`** — c'est la seule source qui fait
foi, dans le dépôt de l'API. Ne pas en recopier une version ici : la copie précédente
(`docs/SPEC-API.md`) avait divergé sur la table de staging, l'URL de base et la moitié des routes.

Sections qui concernent l'app : §4 (endpoints), §5 (cycle de vie d'une session), §8 (pré-commande),
§9 (consultation dépôts et stock).

**L'app n'est plus hors ligne** : `INTERNET` est déclarée, avec un `network_security_config` qui
autorise le trafic en clair — l'API n'a pas de TLS sur le LAN et son adresse est une IP privée
saisie à l'exécution, qu'Android ne sait pas exprimer autrement qu'en autorisant tout.

Quatre routes sont branchées, toutes depuis `data/remote/NirgescomClient.kt` en `HttpURLConnection`
(pas d'OkHttp ni de Retrofit : le volume ne le justifie pas) : `GET /health` (test de connexion,
non authentifié), `GET /magasins` (liste des dépôts, avec `ETag` / `If-None-Match`),
`POST /documents` (envoi d'une session, via `domain/service/SyncService.kt`) et
`GET /documents/{session_id}` (état côté Nirgescom). Le reste du fonctionnement — collecte, import,
export CSV — n'a besoin d'aucun réseau et doit le rester.

Points de contact avec le modèle actuel, à connaître avant de toucher aux sessions :

- `session_id` est un **UUID v4** côté API ; `SessionEntity.idSession` est un `Long` local. Les deux
  coexistent. L'UUID est posé **à la création de la session**, pas au premier envoi : l'API calcule
  `hash_ligne` à partir de lui et un renvoi après coupure doit produire exactement les mêmes hash.
  Les sessions d'avant la migration 1→2 en reçoivent un au premier envoi, via un `UPDATE` gardé par
  `WHERE uuid_session IS NULL` — une fois écrit, il ne bouge plus.
- Un `201` est **toujours** un succès, y compris quand `lignes_ignorees` vaut le total : c'est un
  renvoi après coupure, les doublons sont ignorés et non rejetés. En faire une erreur ferait
  réessayer indéfiniment une session pourtant arrivée.
- `magasin` doit valoir **exactement** le libellé porté par la clé d'API (comparaison stricte côté
  serveur, sinon `403`). **Dépôt et magasin sont une seule et même notion** : le magasin n'est plus
  saisi mais choisi dans la liste que `GET /magasins` fournit, mise en cache dans la table
  `magasins` (base v3). L'écran Nouvelle Session n'a plus de champ Dépôt ; la session hérite du
  magasin réglé et en garde un **instantané** dans `sessions.lieu`, comme `nom_produit_snap`.
  Relire le réglage à l'envoi étiquetterait silencieusement la collecte sous le mauvais dépôt si la
  tablette a été reconfigurée entre-temps ; l'instantané fait au contraire échouer l'envoi en `403`.
  Les sessions antérieures gardent leur ancien texte libre et restent lisibles.
- Le contrat n'accepte que `INVENTAIRE` et `COMMANDE`.
- Le statut de synchro vit dans une colonne **séparée** de `statut` : une session peut être
  exportée en CSV *et* synchronisée, l'export restant un repli.
- Ne pas envoyer `hash_ligne` : l'API le calcule et fait foi.

## Project

StockCollect — Android app for JD Cosmetics warehouse staff. Import a product catalogue from CSV, scan barcodes to collect stock counts, export the result as CSV. Single Gradle module `:app`. Everything lives in a local Room database and the whole collection flow — import, scan, count, CSV export — works **with no network at all**; the WiFi sync to Nirgescom (see above) is an addition on top, never a prerequisite.

## Commands

```bash
./gradlew :app:assembleDebug        # build (debug appId is com.jdcosmetics.stockcollect.debug)
./gradlew installDebug              # install on a connected device
./gradlew :app:testDebugUnitTest    # unit tests (only CsvParserTest.kt exists today)
./gradlew :app:lintDebug            # stock AGP lint — no ktlint/detekt/spotless configured
```

Run a single test:

```bash
./gradlew :app:testDebugUnitTest --tests "*CsvParserTest*"
```

`./gradlew :app:assembleRelease` currently **fails**: `app/build.gradle.kts` points at `proguard-rules.pro`, which does not exist. R8 + `shrinkResources` are enabled with no keep rules for Room / Hilt / OpenCSV / ML Kit, so that file needs real content, not just to be created empty.

## Stack

AGP 8.3.2, Kotlin 1.9.23, Gradle 8.4, compileSdk/targetSdk 34, minSdk 26, JVM target 17. Versions are centralised in `gradle/libs.versions.toml`.

- **kapt, not KSP** — both Room and Hilt use kapt. Don't introduce KSP for one and leave the other.
- **ViewBinding, no Compose.** There is not a single `findViewById` in the codebase.

## Architecture

Single-Activity (`ui/MainActivity.kt`) + Navigation Component with **Safe Args**. Graph: `app/src/main/res/navigation/nav_graph.xml`.

- Destinations that take no real argument still declare a throwaway `dummy: string = ""` argument. This is deliberate — it forces Safe Args to generate a `Directions` class for them. Keep the pattern when adding a no-arg destination.
- Hilt throughout: `@HiltAndroidApp StockCollectApp`, `@AndroidEntryPoint` fragments, `@HiltViewModel` ViewModels. Modules are `di/DatabaseModule.kt` (database + the five DAOs) and `di/ServiceModule.kt`.
- **There is no uniform repository layer.** `SessionRepository` exists and is used only by `SaisieViewModel`; every other ViewModel injects DAOs directly. Follow whatever the file you're editing already does rather than introducing repositories wholesale.
- **Room `Flow` never reaches the UI.** ViewModels collect Flows inside `viewModelScope` and republish as `LiveData`: `private val _x = MutableLiveData<T>()` + `val x: LiveData<T> = _x`. Sealed UI-state classes (`ScanUiState`, `SaisieUiState`, `ExportUiState`, `ImportUiState`) are declared at file top level, above the ViewModel, in the ViewModel's own file.
- One-shot events use the `util/Event.kt` wrapper (`consume()` once vs `peek()` to render), but only in `ScanViewModel`. Other screens expose plain state plus an explicit `resetState()` that the fragment calls after handling.
- `SaisieViewModel` is shared via `by activityViewModels()` across NouvelleSession → Saisie → Recapitulatif → ScanResultat. Everything else uses `by viewModels()`.
- Every adapter is `ListAdapter` + `DiffUtil.ItemCallback` + a ViewBinding ViewHolder, with click handling passed as constructor lambdas.

## Domain model

Spread across five entities, a repository and a service — worth reading this before touching data code.

- **`articles`** — the catalogue. PK `code_produit`, optional unique `code_barre_principal`, plus `nom_produit`, `quantite_ref`, `prix`. Loaded from the catalogue CSV.
- **`art_codebarre`** — secondary barcodes. PK `code_barre` → `code_produit`, for products with several packagings or lots.
- **Two-step barcode resolution** (`domain/service/BarcodeScanService.resoudre()`): look up `articles.code_barre_principal`; if that misses, look up `art_codebarre` and re-resolve by `code_produit` (`viaTableCB = true` so the UI can say so); otherwise `NonTrouve`. There is **no create-article-on-the-fly path** — an unknown barcode is a dead end by design.
- **Business rule — an article may carry several barcodes, but a barcode belongs to exactly one article.** The schema already enforces it *within* each table (unique index on `articles.code_barre_principal`, `code_barre` as PK of `art_codebarre`), but **nothing enforces it across the two** — so `importCorrespondance` checks it explicitly via `ArticleDao.getCodesBarresPrincipaux()`. Without that check a stray mapping would be dead code: `resoudre()` queries `articles` first and would never reach it.
  - The real catalogue violates the rule on 9 barcodes, which is why the catalogue import runs in **two phases**: `analyserCatalogue` reads and validates without writing, then the user arbitrates (import the losers without a barcode / skip them / cancel), and `appliquerCatalogue` writes inside a single transaction. Nothing touches the database before the choice is made.
  - Before writing, `ArticleDao.libererCodesBarres` detaches every incoming barcode from its current holder. Reassigning a barcode from one article to another would otherwise violate the unique index depending on `UPDATE` ordering.
  - The app **never merges two article records** on its own: it reports, correction belongs in Nirgescom.
- **`sessions`** — one collection run of a `TypeOperation`. **Only `INVENTAIRE` can be created** — the Nouvelle Session screen offers a single card, because the Nirgescom API contract accepts only `INVENTAIRE` (and `COMMANDE`, from étape 2); anything else is rejected with a 422. `TypeOperation.ENTREE` / `SORTIE` are deliberately **kept as constants with their labels**: sessions of those types may already exist on deployed tablets and must stay readable and exportable in the Historique — the filter chips there still cover them. Don't reintroduce them as creatable types. Moves one-way `BROUILLON → CLOTUREE → EXPORTEE`. The transitions in `SessionDao` are guarded UPDATEs returning rows-affected: `cloturer` only matches `BROUILLON`, `marquerExportee` only matches `CLOTUREE`. **Irreversible on purpose** — there is no path back to BROUILLON.
- **`lignes_collecte`** — one product + quantity inside a session. `code_barre_scanne` is null when the line was added by text search. `nom_produit_snap` is a deliberate snapshot of the product name so past sessions survive a catalogue re-import — don't "normalise" it away. `SessionRepository.ajouterLigne` enforces one line per `(session, code_produit)` by **summing** quantities on rescan rather than inserting a duplicate.
- `sessions.nb_lignes` is a denormalised counter, refreshed only on insert and delete (correct, since merging doesn't change the count).
- **`exports`** — append-only audit trail: filename, timestamp, line count, SAF URI.
- **`magasins`** — local cache of `GET /magasins`, the only source of choosable dépôts. `nom_magasin` is **nullable** (the API really returns nulls); such a dépôt is listed but refused, since the libellé is what `POST /documents` sends. Replaced wholesale on each fetch (`MagasinDao.remplacerTout`) so a dépôt withdrawn server-side stops being offered; an empty response is rejected rather than written, to avoid emptying a working cache. The `ETag` lives in `ParametresSync`, and a `304` is a success, not an error.

Flow: import catalogue → import barcode correspondence → create BROUILLON session → scan or search adds lines → `cloturer` → `CsvExportService.exporter` writes the CSV and flips the session to EXPORTEE.

## CSV contracts

Easy to break, and not visible from any single file.

- **All imports are headerless.** `CsvParser.lireFichier` treats every line as data; a file with a header row simply produces one error row.
- Catalogue columns: `0 = code_produit`, `1 = code_barre_principal`, **`2` ignored**, `3 = nom_produit`, then **quantity and price are read from the END of the row** (second-to-last and last). Requires ≥ 4 columns. Always go through `CsvParser.mapperCatalogue` — never index `Constants.COL_CATALOGUE_QUANTITE` / `_PRIX` directly, they only hold for a 6-column row.
  - Why: the source file does **not** escape commas inside product names. `AL-NUAIM WHITE ORCHID 9,9ML` yields 7 columns. Reading fixed indices silently truncated the name, set the quantity to 0 and took the price from the wrong column — on 4 real rows of `catalogue17022025.csv`, with no error reported. `mapperCatalogue` rejoins the surplus into the name; `CsvParserTest` pins the four real cases.
- Correspondence columns: `0 = code_barre`, `1 = code_produit`. Requires ≥ 2, refuses to run while `articles` is empty, and does a full `deleteAll()` + reinsert.
- Separator is auto-detected per file — `;` wins only on a strict majority over `,`. Encoding is UTF-8 with an ISO-8859-1 fallback.
- An import aborts entirely if more than 10% of rows error (`Constants.IMPORT_SEUIL_ERREUR_POURCENTAGE`). A quantity or price that is present but not numeric is an **error**, not a silent `0.0`.
- Export (`CsvExportService`): comma-separated, CRLF, UTF-8, header `Code Barre,Code Produit,Nom Produit,Quantité`. Commas inside product names are replaced with spaces rather than quoted.
  - **No UTF-8 BOM, deliberately.** The file is consumed by a third-party system that does not tolerate it. Excel on Windows will therefore show accented names and the `Quantité` header as mojibake — that is expected, not a bug to fix. Do not reintroduce a BOM.
  - The flat, unquoted format is likewise assumed: no product name in the catalogue contains a `"`.
- Filename comes from `DateUtils.toFileName()` → `STOCK_ddMMyyyy_HHmm.csv`, written to a user-chosen SAF URI.
- Sample files sit at the repo root (`catalogue17022025.csv`, `art_codebarre.csv`, `produits.csv`, `STOCK_10062026_1210.csv`) and work as import fixtures. They are **untracked on purpose** — real JD Cosmetics product data, prices included — so `git status` always lists them and a fresh clone won't have them. Don't commit them, and don't "fix" the dirty working tree by adding them. The unit tests don't need them: `CsvParserTest` pins the tricky real rows as inline strings.

## Language conventions

Domain and UI identifiers are **French** (`resoudre`, `ajouterLigne`, `cloturer`, `LignesCollecteAdapter`, `nbLignes`, view ids like `btnScanner` / `tvNbLignes`). Framework and lifecycle identifiers stay English (`onViewCreated`, `binding`, `uiState`, `Idle`/`Loading`). All user-facing text is French; `res/values/strings.xml` is the default locale and there are no translations.

A lot of user-facing text is **hardcoded French literals in Kotlin** rather than in `strings.xml` — dialog titles and messages, statut labels, Snackbar text, and the nav_graph labels. Match whichever the file you're editing already does.

- Statuses and types are `object StatutSession` / `object TypeOperation` string constants in `data/db/entity/SessionEntity.kt`, **not enums**. `TypeOperation.label()` gives the display string.
- Formatting lives in `util/DateUtils.kt`, which holds two objects: `DateUtils` (`nowIso`, `toDisplay`, `toFileName`) and `FormatUtils` (`formatQuantite` → one decimal in `Locale.US`, `formatPrix` → FCFA).

## Changing the database schema

The database is at `version = 3`. `.fallbackToDestructiveMigration()` has been **removed** — it silently wiped collected sessions on the magasinier's tablet at every schema change. A missing migration now makes the database fail to open, which is loud but recoverable.

Every entity change therefore needs three things, not one: a `Migration` in `data/db/Migrations.kt` added to the `MIGRATIONS` array, the exported JSON in `app/schemas/` **committed**, and a case in `app/src/androidTest/.../MigrationTest.kt`. The migration's raw SQL must match Room's generated `createSql` **character for character** (backticks, column order, `DEFAULT` values) — `runMigrationsAndValidate` is what catches a mismatch before the tablet does.

<!-- BACKLOG.MD GUIDELINES START -->
<!-- backlog.md-instructions-version: 1.48.0 -->
<CRITICAL_INSTRUCTION>

## Backlog.md Workflow

This project uses Backlog.md for task and project management.

**For every user request in this project, run `backlog instructions overview` before answering or taking action.**

Use the overview to decide whether to search, read, create, or update Backlog tasks.

Before task lifecycle actions, read the matching detailed guide:
- `backlog instructions task-creation` before creating or splitting tasks
- `backlog instructions task-execution` before planning, changing status or assignee, adding a plan or implementation notes, or implementing task work
- `backlog instructions task-finalization` before checking acceptance criteria, writing final summaries, or moving tasks to terminal statuses

Use `backlog <command> --help` before running unfamiliar commands. Help shows options, fields, and examples.

Do not edit Backlog task, draft, document, decision, or milestone markdown files directly. Use the `backlog` CLI so metadata, relationships, and history stay consistent.

</CRITICAL_INSTRUCTION>
<!-- BACKLOG.MD GUIDELINES END -->
