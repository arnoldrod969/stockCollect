---
id: TASK-13
title: 'DetailSessionViewModel : un seul collecteur par session'
status: To Do
assignee: []
created_date: '2026-09-25 09:36'
labels: []
dependencies: []
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DetailSessionViewModel.charger lance un nouveau collecteur sur le Flow Room a chaque recreation de la vue, sans annuler le precedent : meme defaut que celui corrige dans SaisieViewModel.observerLignes (TASK-5 #1). Les collecteurs s'empilent et republient la meme donnee.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 charger annule le Job precedent avant d'en relancer un, ou ne relance rien pour la meme session
- [ ] #2 Un test montre qu'apres deux appels un seul collecteur publie
<!-- AC:END -->
