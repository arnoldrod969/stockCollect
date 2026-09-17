---
id: TASK-6
title: Documenter le format d'export et trancher le sort d'OpenCSV
status: Done
assignee: []
created_date: '2026-09-17 15:53'
updated_date: '2026-09-17 17:54'
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
- [x] #1 Le choix d'un export sans BOM est documenté dans CLAUDE.md avec sa raison
- [x] #2 Constants.UTF8_BOM est supprimé
- [x] #3 Le format plat sans guillemets est documenté comme assumé
- [x] #4 OpenCSV est soit réellement utilisé par CsvParser, soit retiré des dépendances avec ses dontwarn dans proguard-rules.pro
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Fait : choix d'un export sans BOM documenté dans CLAUDE.md et dans Constants.kt à l'endroit même où la constante se trouvait ; Constants.UTF8_BOM supprimée (aucune référence restante) ; format plat sans guillemets documenté comme assumé, vérifié sur les données (aucun nom de produit ne contient de guillemet).

OpenCSV retiré de app/build.gradle.kts : la dépendance n'était importée nulle part. Le parsing reste fait à la main, et doit le rester — le fichier source n'échappe pas ses virgules, aucun parseur conforme ne saurait le lire correctement. Les dontwarn correspondants ont été retirés de proguard-rules.pro.

Export verifie octet par octet sur un fichier reellement produit par l app (STOCK_17092026_1332.csv, 221 octets, session de 3 lignes) : premiers octets 43 6F 64 soit « Cod », donc aucun BOM ; fins de ligne CRLF ; en-tete Code Barre,Code Produit,Nom Produit,Quantite ; accent de « Quantite » correct en UTF-8. Constants.UTF8_BOM supprime, OpenCSV retire de build.gradle.kts et ses dontwarn retires de proguard-rules.pro. La session est passee a EXPORTEE (l ecran Exporter affiche ensuite « Aucune session cloturee a exporter »).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Le format d export est fige et documente : pas de BOM UTF-8 (le CSV est consomme par un systeme tiers qui ne le tolere pas), format plat sans guillemets assume, CRLF. Constants.UTF8_BOM supprime, OpenCSV retire des dependances puisque le fichier source contient des virgules non echappees qu aucun parseur conforme ne devinerait. Verifie octet par octet sur un export reellement produit par l app.
<!-- SECTION:FINAL_SUMMARY:END -->
