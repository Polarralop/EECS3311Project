# AI tool log

Running log for section 5 of the Stage 2 report: each AI tool, the model, what it was used for, and representative interactions. Token and money cost per session are in [COST-TRACKING.md](../COST-TRACKING.md).

Template:

```
## <date>: <tool> (<model>)
- Used for: <features, classes, tasks>
- Representative interaction: <the request, and what was accepted, changed or rejected>
```

## 2026-10-20: Claude Code (Claude Opus 5.5)
- Used for: project setup. Maven build, `.gitignore` and `.env.example`, one skeleton file per Stage 1 class in every package, test class shells, AGENTS.md, README and the docs logs.
- Representative interaction: asked to scaffold the repo from the Stage 2 checklist and Stage 1 notes without filling in the classes. Claude read the Stage 1 report and class diagram, mapped each class to its package, features and pattern role, and left method bodies for the feature work.

## 2026-10-20: Claude Code (Claude Opus 5.5)
- Used for: the Stage 2 report scaffold. `Stage2_Report.docx` reuses the Stage 1 report's styles, title page and table formatting, with the seven sections from the Stage 2 requirements, one mapping table per feature (F01 to F12), one table per pattern and placeholders for everything not built yet. AGENTS.md now says which log feeds which report section and to update the report in the same commit as the code.
- Representative interaction: asked to scaffold the Stage 2 report and keep it updated during development. Claude read the Stage 1 .docx structure and the Stage 2 checklist, prefilled only facts known from Stage 1 (use cases, sequence diagrams, feature types, planned classes and pattern participants) and left implementation details as marked placeholders.
