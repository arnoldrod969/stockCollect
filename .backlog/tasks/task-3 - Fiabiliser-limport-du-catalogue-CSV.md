---
id: TASK-3
title: Fiabiliser l'import du catalogue CSV
status: Done
assignee: []
created_date: '2026-09-17 15:52'
updated_date: '2026-09-25 10:10'
labels: []
dependencies: []
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Trois défauts sur le fichier réel catalogue17022025.csv (2723 lignes).

1. Neuf codes-barres sont revendiqués par plusieurs articles. L'index unique sur code_barre_principal fait que le premier import supprime silencieusement 9 articles (REPLACE) et que tout réimport échoue (SQLiteConstraintException).

2. Quatre lignes contiennent une virgule non échappée dans le nom du produit : les colonnes se décalent, le nom est tronqué, la quantité tombe à 0 et le prix est lu dans la mauvaise colonne. Aucune erreur n'est signalée.

3. Les imports ne sont pas transactionnels : un échec en cours de route laisse des données partielles, et importCorrespondance peut laisser la table vide après son deleteAll.

Règle de gestion retenue : un article peut porter plusieurs codes-barres, un code-barre n'appartient qu'à un seul article.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Les conflits de codes-barres sont détectés avant toute écriture et présentés à l'utilisateur
- [x] #2 L'utilisateur peut choisir entre importer sans code-barres, ignorer les articles concernés, ou annuler
- [x] #3 Le dialogue distingue les fiches dupliquées (même nom) des vrais conflits (noms différents)
- [x] #4 Les 4 lignes à virgule non échappée sont importées avec le nom complet, la bonne quantité et le bon prix
- [x] #5 Une quantité ou un prix non numérique produit une erreur au lieu d'un 0 silencieux
- [x] #6 Les deux imports sont transactionnels
- [x] #7 La règle un code-barre = un article est vérifiée entre articles et art_codebarre
- [x] #8 Un double import de catalogue17022025.csv réussit deux fois de suite avec 2723 articles
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Import scindé en analyserCatalogue (sans écriture) puis appliquerCatalogue (transactionnel). CsvParser.mapperCatalogue lit quantité et prix depuis la fin de la ligne. Dialogue à 3 options dans ImportCatalogueFragment. Reste à compiler et tester.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Import scindé : analyserCatalogue (lecture + contrôles, aucune écriture) puis appliquerCatalogue (une seule transaction Room). Nouvel état ImportUiState.ConflitsDetectes et dialogue à 3 options dans ImportCatalogueFragment (importer sans code-barres / ignorer / annuler), avec décompte par famille.

CsvParser.mapperCatalogue lit quantité et prix depuis la fin de la ligne ; 6 tests ajoutés dans CsvParserTest à partir des 4 lignes réelles du catalogue.

ArticleDao.libererCodesBarres détache les codes-barres entrants avant écriture : sans cela, réattribuer un code-barre d'un article à un autre violait l'index unique selon l'ordre des UPDATE.

Contrôle croisé articles/art_codebarre ajouté via ArticleDao.getCodesBarresPrincipaux.

Compile non encore validée : deux erreurs corrigées (ServiceModule non mis à jour, bindingAdapterPosition absent en recyclerview 1.1.0), build en cours.

Compilation validée et tests verts : 15 tests, 0 échec, dont les 6 nouveaux sur mapperCatalogue couvrant les 4 lignes réelles à virgule non échappée.

Verification runtime sur emulateur API 28, catalogue17022025.csv reel (2723 lignes) :
- AC1/AC2/AC3 : dialogue de conflits affiche 11 articles perdants, 7 fiches en double et 2 vrais conflits ; les trois boutons sont atteignables (ils ne l etaient pas avant, cf. commit f95dc29).
- AC4 : EDP D,LOVE 20 ML importe avec son nom entier et prix 1500.00 (etait « EDP D », prix 0.00) ; idem AL-NUAIM WHITE ORCHID 9,9ML.
- AC5 : fichier de test a quantite « ABC » et prix « DIX » -> refus explicite « Trop d erreurs (2/3 lignes) » avec les deux lignes nommees, rien ecrit en base.
- AC6 : ecritures dans db.withTransaction ; le refus ci-dessus laisse le catalogue intact a 2723 articles.
- AC8 : import joue deux fois de suite, second passage 11 nouveaux + 2712 mis a jour = 2723 articles, aucune disparition.
- AC7 NON coche : le controle croise existe et tourne (import art_codebarre.csv, 267 entrees, 0 erreur) mais aucune donnee ne le viole, donc le rejet lui-meme n est pas exerce.

AC7 - test instrumente ajoute (non encore execute) : app/src/androidTest/java/com/jdcosmetics/stockcollect/domain/service/CorrespondanceCodeBarreTest.kt. Base Room en memoire, vrai CsvImportService.importCorrespondance, CSV temporaire dans cacheDir via Uri.fromFile. 4 cas : (1) code-barre principal d'un AUTRE article -> ligne rejetee avec erreur nommee (ligne, code-barre, porteur), rien d'ecrit pour elle, le reste importe (1/10 = seuil non depasse) ; (2) code-barre principal du MEME article -> accepte et ecrit dans art_codebarre (condition proprietaire != codeProduit, CsvImportService.kt:336), redondant mais inoffensif ; (3) correspondance saine -> inseree ; (4) 2/10 conflits = 20 % -> import refuse, art_codebarre inchangee (retour anticipe avant la transaction, CsvImportService.kt:354). Aucun bug trouve dans le controle. assembleDebugAndroidTest et testDebugUnitTest (15/15) OK.

Limite : le controle ne joue que dans un sens. appliquerCatalogue ne consulte pas art_codebarre : un reimport de catalogue qui donne comme code principal a P01 un code deja secondaire de P02 n'est pas detecte ; la correspondance devient morte (resoudre() trouve P01 d'abord). Non corrige, hors perimetre du test : a trancher.

Verification 2026-09-25 (emulateur API 28, connectedDebugAndroidTest 16/16) : CorrespondanceCodeBarreTest vert sur ses 4 cas - code-barre deja principal d'un autre article rejete avec erreur nommee et rien ecrit ; code principal du meme article accepte ; correspondance saine inseree ; au-dela de 10 % d'erreurs import refuse et art_codebarre inchangee. Le critere 7 est coche pour le sens verifie par le code (import de correspondance contre articles). Le sens inverse (reimport du catalogue contre art_codebarre) n'est PAS controle : suivi ouvert en TASK-11, sur decision de l'utilisateur.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Import du catalogue fiabilise : conflits de codes-barres arbitres avant ecriture, lignes a virgule non echappee recollees, erreurs numeriques signalees, imports transactionnels. Regle un code-barre = un article prouvee par test instrumente Room en memoire (CorrespondanceCodeBarreTest, 4 cas verts sur emulateur) pour l'import de correspondance ; le controle au reimport du catalogue reste a faire (TASK-11).
<!-- SECTION:FINAL_SUMMARY:END -->
