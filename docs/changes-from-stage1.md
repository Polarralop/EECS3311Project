# Changes from Stage 1

Running log for section 4 of the Stage 2 report. Add an entry when a feature, class or pattern is added, replaced, removed or substantially changed, at the time of the change.

Template:

```
## <Original ID or "New">: <short title>
- Original (Stage 1):
- Implemented:
- What changed and why:
- How the user need is still met:
- Affected use cases / classes / methods / patterns:
- Diagram to update:
- Commit(s):
```

## F03: Relationships point at a character by name
- Original (Stage 1): `Relationship` has an association to its target `Character`; `StorySession` has `addCharacter` and `findCharacter`, and the controller checks each relationship's target before calling `Character.addRelationship(r)`.
- Implemented: `Relationship` stores `targetName` (a `String`) instead of a `Character` reference. `StorySession` gains `updateCharacter(originalName, character)`, `removeCharacter(name)` and `addRelationship(fromName, r)`; the target checks (target exists, not itself) live in `StorySession` so every caller gets them.
- What changed and why: a name reference has no cycles, so a session saves to JSON (F11) without object-identity bookkeeping. Edit and remove were implied by "manage characters" (UC03) but had no methods in the class diagram. When a character is renamed, `updateCharacter` rewrites the relationships that point at the old name; when one is removed, relationships pointing at it are removed too.
- How the user need is still met: the user still adds, edits and removes characters and one-way relationships, with the same validation (empty name, duplicate name, missing target, self-relationship).
- Affected use cases / classes / methods / patterns: UC03; `Relationship`, `Character`, `StorySession`; MVC model only.
- Diagram to update: `figures/class-diagram/05-model-characters-and-events.png` (Relationship field, StorySession methods).
- Commit(s): feat(model): manage characters and relationships in StorySession
