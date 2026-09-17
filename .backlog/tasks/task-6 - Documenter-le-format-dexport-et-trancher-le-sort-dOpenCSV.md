---
id: TASK-6
title: Documenter le format d'export et trancher le sort d'OpenCSV
status: In Progress
assignee: []
created_date: '2026-09-17 15:53'
updated_date: '2026-09-17 16:21'
labels: []
dependencies: []
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
L'export CSV a été passé sans BOM UTF-8, volontairement : le fichier est consommé par un système tiers qui ne le tolère pas. Ce choix n'est écrit nulle part et sera défait par quelqu'un qui verra des accents cassés dans Excel.

Constants.UTF8_BOM est devenu du code mort.

Par ailleurs OpenCSV est déclaré dans app/build.gradle.kts mais importé nulle part : CsvParser est écrit à la main.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Le choix d'un export sans BOM est documenté dans CLAUDE.md avec sa raison
- [ ] #2 Constants.UTF8_BOM est supprimé
- [ ] #3 Le format plat sans guillemets est documenté comme assumé
- [ ] #4 OpenCSV est soit réellement utilisé par CsvParser, soit retiré des dépendances avec ses dontwarn dans proguard-rules.pro
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Fait : choix d'un export sans BOM documenté dans CLAUDE.md et dans Constants.kt à l'endroit même où la constante se trouvait ; Constants.UTF8_BOM supprimée (aucune référence restante) ; format plat sans guillemets documenté comme assumé, vérifié sur les données (aucun nom de produit ne contient de guillemet).

OpenCSV retiré de app/build.gradle.kts : la dépendance n'était importée nulle part. Le parsing reste fait à la main, et doit le rester — le fichier source n'échappe pas ses virgules, aucun parseur conforme ne saurait le lire correctement. Les dontwarn correspondants ont été retirés de proguard-rules.pro.
<!-- SECTION:NOTES:END -->
