---
id: TASK-1
title: Réaligner la documentation du contrat API
status: Done
assignee: []
created_date: '2026-09-17 15:52'
labels: []
dependencies: []
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
La copie du contrat embarquée dans stockCollect (docs/SPEC-API.md) avait divergé de la référence nirgescom-api/docs/SPEC.md : table de staging, URL de base, et la moitié des routes manquantes. Côté API, la spec annonçait FastAPI/Pydantic alors que le code est en Flask/pymysql.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 docs/SPEC-API.md supprimé et CLAUDE.md pointe vers ../nirgescom-api/docs/SPEC.md
- [ ] #2 Les mentions FastAPI, Pydantic, Uvicorn et SQLAlchemy ne figurent plus dans nirgescom-api/docs/SPEC.md
- [ ] #3 INSERT IGNORE documenté avec sa raison (le compte MySQL n'a pas le privilège UPDATE)
- [ ] #4 La normalisation de la quantité à 3 décimales dans hash_ligne est documentée
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Copie périmée supprimée, CLAUDE.md pointe désormais vers la source unique. Les 4 dérives de la spec API corrigées.
<!-- SECTION:FINAL_SUMMARY:END -->
