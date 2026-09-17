---
id: TASK-5
title: 'Corriger les bugs de session et d''UI, retirer ENTREE et SORTIE'
status: In Progress
assignee: []
created_date: '2026-09-17 15:53'
updated_date: '2026-09-17 16:21'
labels: []
dependencies: []
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Quatre défauts, plus un alignement sur le contrat API.

SaisieViewModel.observerLignes relance un collecteur sur le Flow Room sans annuler le précédent, sur un ViewModel partagé par quatre écrans via activityViewModels().

SaisieViewModel.creerSession reprend un BROUILLON existant sans vérifier son typeOperation : choisir SORTIE peut alimenter un ancien inventaire.

LignesCollecteAdapter attache un OnFocusChangeListener après setText dans un ListAdapter : une vue recyclée alors qu'elle a le focus peut committer sur la mauvaise ligne.

ScanResultatFragment remet la quantité à 0 sur une saisie intermédiaire comme 1. ou une chaîne vide.

Enfin, le contrat API n'accepte que INVENTAIRE et COMMANDE : une session ENTREE ou SORTIE serait rejetée en 422. Décision prise de les retirer de l'app.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 observerLignes annule le Job précédent avant d'en relancer un
- [ ] #2 creerSession ne reprend un brouillon que si son typeOperation correspond, sinon l'utilisateur choisit
- [ ] #3 L'édition de quantité dans la liste ne peut plus committer sur la mauvaise ligne après recyclage
- [ ] #4 Une saisie intermédiaire dans le champ quantité n'écrase plus la valeur par 0
- [ ] #5 ENTREE et SORTIE ne sont plus proposés à la création de session
- [ ] #6 Les constantes TypeOperation.ENTREE et SORTIE sont conservées et les sessions existantes de ces types restent lisibles dans l'Historique
- [ ] #7 Aucune migration ni suppression des sessions ENTREE/SORTIE déjà en base
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Fait : lignesJob annulé avant relance dans SaisieViewModel.observerLignes ; nouvel état SaisieUiState.BrouillonAutreType avec dialogue de choix dans NouvelleSessionFragment ; LignesCollecteAdapter relit la ligne par adapterPosition et détache le listener avant setText ; ScanResultatFragment ne suit plus le champ frappe par frappe, la quantité est lue au clic avec message d'erreur si illisible.
<!-- SECTION:NOTES:END -->

## Comments

<!-- COMMENTS:BEGIN -->
author: @claude
created: 2026-09-17 16:21
---
Correction d'analyse : le critère « ENTREE et SORTIE ne sont plus proposés à la création de session » repose sur une prémisse fausse de ma part.

Vérification faite : les cartes card_entree et card_sortie existent dans fragment_nouvelle_session.xml mais sont déjà des placeholders volontaires — alpha 0.45, clickable=false, focusable=false, sous-titre « Bientôt disponible » — et aucun listener n'y est branché. typeSelectionne vaut toujours INVENTAIRE.

Conséquences : aucune session ENTREE/SORTIE ne peut être créée, ni aujourd'hui ni dans les bases existantes ; le risque de rejet 422 par l'API n'existe pas ; les critères sur la conservation des constantes et l'absence de migration sont sans objet.

Je n'ai donc rien retiré : supprimer ces deux cartes effacerait un signal de roadmap posé délibérément, pour un gain nul. Le garde-fou sur le type du brouillon repris a été conservé, il servira à l'arrivée de COMMANDE.

Question : faut-il quand même retirer les deux cartes, ou ajuster les critères d'acceptation pour acter qu'il n'y a rien à faire ?
---
<!-- COMMENTS:END -->
