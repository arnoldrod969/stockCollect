---
id: TASK-10
title: Aligner l'app sur la SPEC Nirgescom du 23/09
status: Done
assignee:
  - '@claude'
created_date: '2026-09-25 09:11'
updated_date: '2026-09-25 09:37'
labels: []
dependencies:
  - TASK-8
references:
  - ../nirgescom-api/docs/SPEC.md
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La SPEC de nirgescom-api (docs/SPEC.md) a evolue du 18 au 23/09 (commits 4d88a16 a acfa8e0), apres la livraison de la synchro (TASK-8). Plusieurs points changent le comportement attendu de l'app : un 500 n'est plus reessayable, de nouveaux 403 et 422 existent, et POST /documents valide plus strictement les champs. Sans alignement, l'app reessaierait indefiniment une erreur de configuration serveur et afficherait des refus 422 incomprehensibles au magasinier.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Un 500 est traite comme une erreur non reessayable, avec un message qui oriente vers la configuration du serveur et non vers le WiFi
- [x] #2 Le 403 de POST /documents pour un session_id deja rattache a un autre magasin est distingue du 403 magasin
- [x] #3 GET /documents/{session_id} gere 403 (session d'un autre magasin) et 422 (UUID invalide) avec un message propre
- [x] #4 Avant envoi, l'app refuse avec un message nommant la ligne : espaces en tete ou fin de code_produit/code_barre, emoji ou caractere de controle dans un texte, quantite > 999999.999 ou a plus de 3 decimales
- [x] #5 L'app n'envoie aucun parametre de requete inconnu sur les routes GET
- [x] #6 La correspondance statut HTTP vers resultat est couverte par des tests JVM, sans appel reseau reel
- [x] #7 La section Sync de CLAUDE.md reflete la SPEC du 23/09 (codes magasin numeriques, 500 non reessayable)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Extraire de NirgescomClient la correspondance (code HTTP, detail decode, corps decode) -> Resultat* en fonctions pures (data/remote/ReponsesNirgescom.kt), le decodage org.json restant dans le client (org.json n'est pas utilisable en test JVM).
2. POST /documents : 500 -> ResultatEnvoi.ConfigurationServeur (non reessayable, message 'configuration du serveur'), 403 'session_id ... appartient a un autre magasin' -> ResultatEnvoi.SessionAutreMagasin distinct de MagasinRefuse, 400 -> Invalide.
3. GET /documents/{id} : 403 -> NonAutorisee, 422 -> IdentifiantInvalide, 500 -> ConfigurationServeur ; messages dans DetailSessionViewModel.
4. GET /magasins : 500 -> ConfigurationServeur ; message dans ParametresViewModel.
5. SyncService : validation avant envoi (fonction pure) : espaces autour de code_produit/code_barre, emoji/controle dans tout texte, longueurs, quantite <0, >999999.999 ou >3 decimales telle que serialisee ; message nommant l'article. Aucune valeur modifiee.
6. URL des routes construite par une fonction pure sans parametre de requete, testee.
7. Tests JVM JUnit4 : correspondance statut -> resultat et validation avant envoi.
8. CLAUDE.md section Sync : exemples de codes/libelles magasin, 500 non reessayable.
9. Compiler : testDebugUnitTest et assembleDebug.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implementation (worktree agent, non commitee) :
- data/remote/ReponsesNirgescom.kt (nouveau) : correspondance pure code HTTP + detail decode -> ResultatEnvoi/Etat/Magasins, et url() sans parametre de requete. NirgescomClient ne fait plus que E/S + decodage org.json.
- POST /documents : 500 -> ConfigurationServeur (message IT, pas WiFi, ne promet pas qu'un reessai suffit ; statut ECHEC_SYNC, envoi toujours propose) ; 403 'session_id ... appartient a un autre magasin' -> SessionAutreMagasin (reconnu au texte de detail) ; 403 num_document reste MagasinRefuse ; 400 -> Invalide (avant : reessayable).
- GET /documents/{id} : 403 -> NonAutorisee (avant : confondu avec cle refusee), 422 -> IdentifiantInvalide, 500 -> ConfigurationServeur.
- GET /magasins : 500 -> ConfigurationServeur (avant : reponse inattendue) ; 503 decrit comme base injoignable, reessayable.
- SyncService : ValidationEnvoi avant envoi (espaces autour des codes, emoji/surrogate et controle < 0x20 ou 0x7F dans tout texte, longueurs 50/255/100/100, quantite <0, >999999.999 ou >3 decimales telle que serialisee par org.json). Rien n'est modifie ; ResultatSync.Impossible, aucun statut ecrit.
- Aucune route GET n'envoie de parametre de requete (verifie a la lecture, url() teste).
- Codes magasin : aucun format suppose dans l'app ; SPEC cite aussi 220301A1 (alphanumerique).
- CLAUDE.md section Sync mise a jour.
Tests : ReponsesNirgescomTest (30), ValidationEnvoiTest (14), CsvParserTest (15) verts ; testDebugUnitTest + assembleDebug BUILD SUCCESSFUL.
Constats hors zone : somme flottante des rescans (SessionRepository.ajouterLigne) peut produire 0.30000000000000004 -> desormais bloque avant envoi, mais la cause reste ; un nom catalogue avec tabulation (CSV separe par ;) serait bloque ; CsvParser.parseLigne trimme deja tous les champs, donc l'import ne laisse pas passer d'espaces autour des codes.

Integration (commits 7bc9d2e, 3ebca60) : testDebugUnitTest 70/70 (ReponsesNirgescomTest 30, ValidationEnvoiTest 15), lintDebug et assembleRelease verts. Preuves : #1 #2 #3 par ReponsesNirgescomTest (classement 500 ConfigurationServeur, 403 session_id vs magasin vs num_document, GET 403/422/500) ; #4 par ValidationEnvoiTest ; #5 par le test de url() et la lecture des trois routes GET ; #6 idem, aucune dependance ni appel reseau ; #7 section Sync de CLAUDE.md relue. Revue independante : ajout du refus ENTREE/SORTIE et du plafond 5000 lignes avant envoi, U+0085 traite comme espace (strip() Python), marquerEchecSync ne degrade plus une session SYNCHRONISEE. Limite : aucun appel a l'API reelle (consigne utilisateur, elle ecrit dans jdbout2023), les messages n'ont pas ete vus a l'ecran ; le decodage org.json (lireDetail/lireRecapitulatif) n'est pas couvert en JVM.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
App alignee sur la SPEC Nirgescom du 23/09 : 500 non reessayable avec message 'configuration serveur', 403 session_id distingue, 403/422 geres sur GET /documents/{id}, validation avant envoi rejouant les regles 422 de l'API (espaces, emoji, controles, quantite, longueurs, type, 5000 lignes). Correspondance HTTP -> resultat extraite dans ReponsesNirgescom et couverte par 30 tests JVM ; 70/70 tests, lint et build release verts. Non verifie contre l'API reelle, par choix.
<!-- SECTION:FINAL_SUMMARY:END -->
