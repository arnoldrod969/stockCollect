---
id: TASK-16
title: Importer le catalogue et les codes-barres depuis l'API Nirgescom
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-25 10:49'
updated_date: '2026-09-25 11:24'
labels: []
dependencies:
  - TASK-11
  - TASK-15
references:
  - ../nirgescom-api/docs/SPEC.md
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Aujourd'hui le catalogue et la table de correspondance art_codebarre ne viennent que de fichiers CSV. L'API expose GET /catalog (articles, code-barres principal, nom, niveaux de prix, avec ETag / If-None-Match, filtre sur le depot de la cle) et GET /codes-barres (correspondance code-barres secondaire vers code produit), cf. ../nirgescom-api/docs/SPEC.md §8. Quand le catalogue viendra de l'API, les codes-barres secondaires doivent pouvoir etre remis a jour par la meme voie, sinon ils disparaissent ou restent figes. L'import CSV reste disponible : la collecte doit continuer a fonctionner sans reseau. Point d'attention : la vue codebarre_article est aujourd'hui VIDE sur jdbout2023 alors que l'app gere 266 codes-barres secondaires issus du CSV ; une reponse vide ne doit pas effacer une correspondance existante (meme regle que GET /magasins).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Depuis l'ecran d'import, l'utilisateur peut mettre a jour le catalogue depuis l'API (GET /catalog), avec ETag : un 304 est un succes sans reecriture
- [ ] #2 Le catalogue recu de l'API passe par la meme analyse et le meme arbitrage des conflits de codes-barres que l'import CSV, avant toute ecriture
- [ ] #3 L'utilisateur peut mettre a jour les codes-barres secondaires depuis l'API (GET /codes-barres), avec les memes controles que l'import CSV de correspondance (regle un code-barre = un article)
- [ ] #4 Une reponse vide de GET /codes-barres n'efface pas la correspondance existante ; l'utilisateur est informe
- [ ] #5 Les imports CSV restent disponibles et inchanges pour un usage hors ligne
- [ ] #6 Les erreurs reseau et HTTP (401, 403, 500, 503) ont un message propre, sur le modele de ReponsesNirgescom, couvert par des tests JVM sans appel reseau reel
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. data/remote : modeles ArticleDistant / CodeBarreDistant, resultat generique ResultatReferentiel<T> (Ok+etag, Inchange=304, CleRefusee 401, NonAutorise 403, ParametreRefuse 422, ConfigurationServeur 500, Indisponible 503, Injoignable, UrlInvalide, ReponseInattendue) ; classement dans ReponsesNirgescom.referentiel() (pur, teste JVM).
2. NirgescomClient : recupererCatalogue(etag) et recupererCodesBarres(etag), If-None-Match, decodage en flux (android.util.JsonReader, sans charger le corps en String ni l'arbre JSON), hors thread principal, delai de lecture allonge.
3. ParametresSync : etagCatalogue, etagCodesBarres.
4. domain/service/ImportNirgescom.kt (pur, teste JVM) : ArticleDistant -> LigneCatalogue (prix = prix_detail, quantite absente), CodeBarreDistant -> couple, suite a donner a chaque reponse (304 = deja a jour, catalogue vide = refuse, codes-barres vides = correspondance conservee, messages d'erreur).
5. ImportNirgescomService : orchestre client + CsvImportService (meme analyse, meme arbitrage, meme importCorrespondance) ; ETag enregistre seulement apres ecriture reussie ; tout import (fichier ou API) du catalogue oublie les ETag catalogue + codes-barres, un import fichier des codes-barres oublie l'ETag codes-barres.
6. CsvImportService : messages globaux selon la source (fichier / Nirgescom), appliquerCatalogue(conserverQuantitesRef) pour garder quantite_ref existante (absente de l'API).
7. UI import : deux boutons Nirgescom (visibles si estConfigure, sinon message vers Parametres), meme dialogue d'arbitrage.
8. Tests JVM + test instrumente Room en memoire du service (sans reseau), CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Constats et decisions (implementation) :
- Correspondance API -> ArticleEntity : code_produit -> code_produit ; code_barre -> code_barre_principal ; nom_produit -> nom_produit ; prix_detail (prix rayon) -> prix (null -> 0.0 comme une colonne vide du CSV). Les 5 autres niveaux de prix et reference_origine sont ignores.
- quantite_ref absente de l'API : un article existant GARDE sa valeur (appliquerCatalogue(conserverQuantitesRef = true), lecture code_produit->quantite_ref dans la transaction), un article nouveau entre a 0. Le CSV ne change pas (false par defaut). quantite_ref n'est lue nulle part ailleurs dans l'app.
- Comme l'import CSV, l'import API est un upsert : un article absent de la reponse n'est pas supprime.
- GET /codes-barres n'est pas filtre par depot alors que GET /catalog l'est (SPEC 8) : pour la source Nirgescom seulement, un couple dont l'article n'est pas au catalogue de la tablette est ecarte (nbIgnores + ligne d'information), pas compte en erreur ni dans le seuil des 10 % ; sinon l'import tomberait des que la vue serveur sera remplie. Si tout est hors catalogue, rien n'est remplace. Le CSV garde l'erreur 'article absent du catalogue'.
- ETag : enregistre seulement apres ecriture reussie (arbitrage annule = pas d'ETag), rejoue seulement si la table locale n'est pas vide, efface par un import CSV (catalogue -> les deux ETag, codes-barres -> le sien) et toute ecriture du catalogue efface celui des codes-barres. Liste de codes-barres vide : aucun ETag retenu, l'information est redonnee a chaque fois.
- Decodage des 200 en flux (android.util.JsonReader), coupure reseau en cours de lecture = injoignable, JSON malforme = reponse illisible. Delai de lecture 60 s pour ces deux routes.
- 403 n'est pas renvoye par /catalog ni /codes-barres aujourd'hui (401, 422, 500, 503 seulement) ; gere quand meme (NonAutorise).
<!-- SECTION:NOTES:END -->
