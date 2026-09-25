---
id: TASK-14
title: Dates ISO independantes de la locale de l'appareil
status: To Do
assignee: []
created_date: '2026-09-25 09:36'
labels: []
dependencies: []
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
DateUtils.nowIso (et isoFormat) utilise un SimpleDateFormat partage, non thread-safe, construit avec Locale.getDefault(). Sur une tablette reglee dans une locale a chiffres non latins, la date sort du format ISO attendu par l'API (date_heure_cloture) et l'envoi finit en 422 permanent ; un acces concurrent peut aussi corrompre une date. ValidationEnvoi suppose pourtant la date garantie par construction.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Les dates ISO sont produites avec Locale.US (ou ROOT) et un formateur thread-safe
- [ ] #2 Un test fixe le format produit sous une locale a chiffres non latins (ex. ar)
<!-- AC:END -->
