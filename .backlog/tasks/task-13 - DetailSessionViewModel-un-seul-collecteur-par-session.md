---
id: TASK-13
title: 'DetailSessionViewModel : un seul collecteur par session'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-25 09:36'
updated_date: '2026-09-25 11:04'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. DetailSessionViewModel.charger : garder le Job du collecteur (lignesJob), l'annuler avant d'en relancer un, comme SaisieViewModel.observerLignes.
2. Test instrumente Room en memoire : DAO des lignes enveloppe pour compter les collecteurs actifs et noter lequel publie ; apres deux charger, un seul actif et seul le second publie.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
charger annule lignesJob avant de relancer le collecteur (DetailSessionViewModel). Test instrumente app/src/androidTest/.../ui/detail/DetailSessionViewModelTest.kt : DAO enveloppe comptant collecteurs actifs et emetteurs ; apres deux charger, 1 actif et seul le 2e publie apres une ecriture. Compile (assembleDebugAndroidTest OK) ; pas encore execute sur appareil : ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.jdcosmetics.stockcollect.ui.detail.DetailSessionViewModelTest
<!-- SECTION:NOTES:END -->
