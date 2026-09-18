---
id: TASK-9
title: Exporter en CSV une session autre que la derniere cloturee
status: To Do
assignee: []
created_date: '2026-09-18 17:01'
labels: []
dependencies: []
type: feature
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
L'ecran Export (ExportViewModel / CsvExportService) ne connait qu'une seule session : la plus recente au statut CLOTUREE (SessionDao.getMostRecentCloturee). Une session plus ancienne, cloturee mais jamais exportee, n'a aujourd'hui aucun chemin vers l'export.

Le probleme est devenu visible en retirant un bouton mort : item_session_historique.xml portait un bouton Exporter sur chaque ligne, qu'aucun code n'ecoutait. HistoriqueAdapter n'y a jamais touche, et le btn_exporter branche par HistoriqueFragment est celui de fragment_historique.xml, un autre bouton du meme nom. Il s'affichait aussi sur les brouillons, ou l'export est refuse. Il a ete supprime : un bouton qui ne fait rien coute plus qu'il n'apporte.

Le besoin, lui, reste. Cas reel : le WiFi tombe, deux sessions sont cloturees dans la journee, seule la derniere est exportable. La premiere n'a plus aucun moyen de sortir de la tablette.

Piste : passer un idSession en argument Safe Args a la destination Export, et ouvrir l'export depuis le Detail session, a cote du bouton Envoyer. Le service CsvExportService prend deja un idSession, c'est la couche ecran qui ne sait pas le transmettre.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Une session CLOTUREE ou EXPORTEE quelconque peut etre exportee depuis l'Historique ou le Detail session
- [ ] #2 L'ecran Export affiche la session demandee et non systematiquement la plus recente
- [ ] #3 L'export d'une session deja EXPORTEE reste possible (reecriture d'un fichier perdu)
<!-- AC:END -->
