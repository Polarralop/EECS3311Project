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

## Model: small additions to the Stage 1 model classes
- Original (Stage 1): `StorySession` has `addTurn`, `removeLastTurn(): Turn`, `lastTurn(): Turn`, `addProposal`, `approveProposal`, `attach`, `notifyObservers`; `Plot.nextPending(): PivotalEvent`.
- Implemented: `lastTurn()` and `Plot.nextPending()` return `Optional` (there may be no turn or no pending event). `StorySession` also has `detach(observer)`, `rejectProposal(id)`, `getPendingProposals()`, `setSummary(text, upTo)` and `nextTurnNumber()`. Lore and proposal ids are assigned by the owning collection (`L1`, `P1`, ...). `Plot.replacePending` marks the old pending events `DROPPED` rather than deleting them.
- What changed and why: `Optional` makes the empty case explicit instead of returning null. The extra methods are the obvious counterparts the features need (a rejected proposal in F07, the summary in F09). Keeping dropped events lets the plan's history be saved and inspected in tests.
- How the user need is still met: no behaviour visible to the user changes.
- Affected use cases / classes / methods / patterns: UC04, UC05, UC06; `StorySession`, `Plot`, `LoreProposal`; Observer (subject gains `detach`).
- Diagram to update: `figures/class-diagram/04-model-story-and-lore.png`, `05-model-characters-and-events.png`.
- Commit(s): feat(model): notify StoryObserver on session changes

## F11: Save errors are exceptions from the repository
- Original (Stage 1): `SessionRepository.save/load/listSessions` with no error type; "a missing or damaged file produces an error message".
- Implemented: `load` throws `IllegalArgumentException` for a missing save and `UncheckedIOException` for an unreadable or damaged one (including a file whose stored id does not match its name); `save` throws `UncheckedIOException` and writes through a temporary file so a failed save never leaves a half-written story. Ids are limited to letters, digits, `-` and `_` because they become file names.
- What changed and why: the design did not say how errors travel; standard unchecked exceptions keep the interface signatures as designed while letting the controller turn them into a message.
- How the user need is still met: the user still gets an error message and stays where they were.
- Affected use cases / classes / methods / patterns: UC07; `SessionRepository`, `JsonSessionRepository`.
- Diagram to update: none (signatures unchanged).
- Commit(s): feat(persistence): save and load sessions as JSON
