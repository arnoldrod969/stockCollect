---
id: TASK-12
title: 'Quantites : ne plus stocker de sommes flottantes non envoyables'
status: To Do
assignee: []
created_date: '2026-09-25 09:36'
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
- [ ] #1 Toute ecriture de quantite (ajouterLigne, mise a jour manuelle, plus/moins) est normalisee a 3 decimales au plus
- [ ] #2 Une saisie a plus de 3 decimales est refusee ou arrondie visiblement a la saisie, jamais en douce a l'envoi
- [ ] #3 L'export CSV et l'envoi Nirgescom portent la meme quantite pour une meme ligne, ou l'ecart est documente comme voulu
- [ ] #4 Des tests couvrent les sommes 0.1+0.2, 1.1+2.2 et 2.3-1
<!-- AC:END -->
