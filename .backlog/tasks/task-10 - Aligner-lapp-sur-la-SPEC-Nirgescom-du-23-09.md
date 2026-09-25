---
id: TASK-10
title: Aligner l'app sur la SPEC Nirgescom du 23/09
status: To Do
assignee: []
created_date: '2026-09-25 09:11'
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
- [ ] #1 Un 500 est traite comme une erreur non reessayable, avec un message qui oriente vers la configuration du serveur et non vers le WiFi
- [ ] #2 Le 403 de POST /documents pour un session_id deja rattache a un autre magasin est distingue du 403 magasin
- [ ] #3 GET /documents/{session_id} gere 403 (session d'un autre magasin) et 422 (UUID invalide) avec un message propre
- [ ] #4 Avant envoi, l'app refuse avec un message nommant la ligne : espaces en tete ou fin de code_produit/code_barre, emoji ou caractere de controle dans un texte, quantite > 999999.999 ou a plus de 3 decimales
- [ ] #5 L'app n'envoie aucun parametre de requete inconnu sur les routes GET
- [ ] #6 La correspondance statut HTTP vers resultat est couverte par des tests JVM, sans appel reseau reel
- [ ] #7 La section Sync de CLAUDE.md reflete la SPEC du 23/09 (codes magasin numeriques, 500 non reessayable)
<!-- AC:END -->
