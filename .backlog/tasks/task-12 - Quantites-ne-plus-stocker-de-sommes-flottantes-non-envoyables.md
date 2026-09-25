---
id: TASK-12
title: 'Quantites : ne plus stocker de sommes flottantes non envoyables'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-25 09:36'
updated_date: '2026-09-25 10:37'
labels: []
dependencies: []
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Les quantites sont des Double. Les rescans (SessionRepository.ajouterLigne) et les boutons plus/moins (LignesCollecteAdapter) additionnent en virgule flottante : 2.3 puis moins donne 1.2999999999999998, 1.1+2.2 donne 3.3000000000000003. L'API refuse plus de 3 decimales (422) et ValidationEnvoi bloque donc l'envoi de la session, apres une cloture irreversible. Une saisie de 1.2345 n'est pas non plus refusee a la saisie. Effet lie : l'export CSV ecrit la quantite a 1 decimale (FormatUtils.formatQuantite), la meme session donne 1.3 dans le CSV et 1.25 chez Nirgescom.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Toute ecriture de quantite (ajouterLigne, mise a jour manuelle, plus/moins) est normalisee a 3 decimales au plus
- [x] #2 Une saisie a plus de 3 decimales est refusee ou arrondie visiblement a la saisie, jamais en douce a l'envoi
- [x] #3 L'export CSV et l'envoi Nirgescom portent la meme quantite pour une meme ligne, ou l'ecart est documente comme voulu
- [x] #4 Des tests couvrent les sommes 0.1+0.2, 1.1+2.2 et 2.3-1
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. FormatUtils.normaliserQuantite (3 decimales, arrondi sur la forme decimale) ; 2. l'appliquer a toute ecriture dans SessionRepository (ajouterLigne : creation et somme de rescan ; mettreAJourQuantite : saisie clavier et boutons +/-) ; 3. formatQuantite passe de 1 decimale fixe a 1 minimum / 3 maximum, pour l'ecran et l'export CSV (choix utilisateur) ; 4. quantiteAEcrire compare la valeur normalisee ; 5. tests JVM et instrumentes.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Choix utilisateur (2026-09-25) : jusqu'a 3 decimales partout, ecran et CSV. Les entiers s'ecrivent comme avant (12.0) ; une quantite fractionnaire sort 1.25 dans le CSV au lieu de 1.3 : le systeme tiers doit accepter un nombre variable de decimales (a confirmer de son cote). Saisie a plus de 3 decimales : arrondie au plus proche a l'ecriture, et visible apres re-affichage (1.2345 -> 1.235). Cas limite assume : si l'arrondi egale deja la valeur en base, rien n'est ecrit et le champ garde le texte tape jusqu'au prochain affichage. Les sessions anterieures peuvent encore porter une derive en base : elle s'affiche arrondie (1.3) et se corrige en retapant la valeur, ce que permet le correctif de la revue (3ebca60). Verification : testDebugUnitTest 78/78 (FormatQuantiteTest 7, LignesCollecteAdapterTest 8), connectedDebugAndroidTest 21/21 sur emulateur dont QuantitesNormaliseesTest 5 (1.1+2.2=3.3, 0.1+0.2=0.3, 2.3 puis moins = 1.3, 1.2345 -> 1.235, ligne ecrite acceptee par ValidationEnvoi), lintDebug et assembleRelease verts. CLAUDE.md mis a jour (FormatUtils, format d'export).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Toute quantite ecrite en base est normalisee a 3 decimales (SessionRepository via FormatUtils.normaliserQuantite) : les sommes de rescans et les boutons +/- ne produisent plus de valeur que Nirgescom refuserait apres une cloture irreversible. L'ecran et l'export CSV affichent jusqu'a 3 decimales (entiers inchanges), si bien que CSV et Nirgescom portent la meme quantite. Verifie par 78 tests JVM et 21 tests instrumentes sur emulateur, lint et build release verts.
<!-- SECTION:FINAL_SUMMARY:END -->
