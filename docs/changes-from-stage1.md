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

## Adapter: structured messages and tool calls in the llm package
- Original (Stage 1): `LLMRequest(systemPrompt, messages: List<String>, toolSchemas: List<String>)`, `LLMResponse(text, refused)` with `hasToolCalls()`; `ClaudeAdapter.toApiBody(request): String`, `fromApiBody(json: String)` and an external `AnthropicApi.createMessage(jsonBody)`.
- Implemented: `LLMRequest` holds `List<LLMMessage>` and `List<LLMToolSpec>` plus `maxTokens`; `LLMResponse` adds `toolCalls`, `stopReason`, token usage and the assistant message to append. New value types `LLMMessage`, `LLMToolCall`, `LLMToolResult`, `LLMToolSpec`, and `LLMException` for failures. `toApiBody` builds the SDK's `MessageCreateParams` and `fromApiBody` reads the SDK's `Message`; the adaptee is the anthropic-java SDK (`client.messages().create`). `FakeLLMClient` can also script failures and records requests.
- What changed and why: a tool-use loop (F04) needs messages with roles, tool calls and tool results, which a list of strings cannot carry. The typed SDK replaces hand-written JSON, so the adapter converts between two object models rather than to and from strings. Each assistant reply keeps the SDK's own copy (opaque to everything but the adapter) so Claude's thinking blocks are sent back unchanged during a tool loop, as the API requires. These types live in `llm` so that package does not depend on `agent.tools`; the agent maps `LLMToolCall` to `ToolCall`.
- How the user need is still met: the agent still talks to one `LLMClient` interface, and only `ClaudeAdapter` knows about Anthropic.
- Affected use cases / classes / methods / patterns: UC01, UC04 to UC08; `LLMRequest`, `LLMResponse`, `ClaudeAdapter`, `FakeLLMClient`, new `LLMMessage`, `LLMToolCall`, `LLMToolResult`, `LLMToolSpec`, `LLMException`; Adapter.
- Diagram to update: `figures/class-diagram/08-llm.png`.
- Commit(s): feat(llm): send requests through ClaudeAdapter with tool-use support

## Controller: errors travel as exceptions, and extra management methods
- Original (Stage 1): on invalid input the controller "calls showError(message)" on the front end; `saveCharacter(character)` covers add and edit; the controller lists only the methods named in the sequence diagrams.
- Implemented: controller methods throw `IllegalArgumentException` / `IllegalStateException` (or `UncheckedIOException` for save files) with a readable message and change nothing; the front end catches it and shows it (`StoryCLI.printError`, `StoryGUI.showError`). Validation rules live in the model classes, so every caller gets them. `saveCharacter(originalName, character)` takes the name being edited (null for a new character) so a rename can be told apart from a new character. Added: `addObserver`, `newSession`, `getLoreEntry`, `removeLoreEntry`, `getCharacter`, `listCharacters`, `removeCharacter`, `addRelationship`, `removeRelationship`, `listSessions`, `canUndo`. Until F09 lands, `loadSession` returns a short deterministic recap (counts of lore, characters and turns) instead of `StoryAgent.recap(session)`.
- What changed and why: the controller does not hold a reference to a particular front end (two can be open), so returning the error to the caller is simpler than calling back into one view. Edit and remove were part of UC02 and UC03 but had no controller methods.
- How the user need is still met: the user sees the same error messages and nothing changes on an error.
- Affected use cases / classes / methods / patterns: UC02, UC03, UC07; `StoryController`, `StoryCLI`; MVC.
- Diagram to update: `figures/class-diagram/03-controller.png`; SD03 and SD09 show `showError` called by the controller.
- Commit(s): feat(controller): expose lore, characters and save/load in StoryCLI

## F04: Tool types carry what the agent loop needs
- Original (Stage 1): `AgentTool` has `getName()`, `getSchema()` and `execute(args, session)`; `ToolCall(toolName, arguments)`; `ToolResult(success, content)`; `ToolRegistry.schemas(): List<String>`.
- Implemented: `AgentTool` also has `getDescription()` (the text that tells the model when to use the tool). `ToolCall` is a record with the model's call `id` as well, so each result can be matched to its call. `ToolResult` is a record with `loreIds`, the lore entries the result showed the model, which is how a turn records the lore it used. `ToolRegistry.schemas()` returns `List<LLMToolSpec>` (name, description, JSON Schema) instead of bare schema strings. The registry reads each tool's `required` arguments from its schema and rejects a call that is missing one before the tool runs; an unknown tool or a tool that throws becomes a failed `ToolResult`, never an exception.
- What changed and why: the Messages API needs a description and an id per tool call, and the F04 output "a record of which lore entries were used" needs the tool result to say which entries it returned. `LLMToolSpec` already existed in `llm` (see the Adapter entry), so the registry hands the agent ready-made specs.
- How the user need is still met: the agent still reaches the story only through validated tool calls, and an invalid request is reported back to the agent while the turn continues (UC04 4b).
- Affected use cases / classes / methods / patterns: UC04; `AgentTool`, `ToolCall`, `ToolResult`, `ToolRegistry`, `SearchLoreTool`, `GetCharacterTool`; Facade (subsystem).
- Diagram to update: `figures/class-diagram/07-agent-tools.png`.
- Commit(s): feat(tools): register SearchLoreTool and GetCharacterTool in ToolRegistry

## F04/F06: Steering guidance is built without a separate model call
- Original (Stage 1): during a turn, `PlotPlanner.planStep(session, input)` sends the input, the recent story and the next pending event to the model and returns guidance (continue, nudge or revise), which then goes into the narration request.
- Implemented: `planStep` builds the guidance from the plot itself: how many events are reached, the next pending event, and an instruction to nudge back gradually; it returns an empty string when the story has no plan and a "bring the threads together" note when every event is reached. The narration call receives it in a section marked hidden, and that same call decides whether to continue, nudge or (with `UpdatePlotTool`, F06) revise.
- What changed and why: a second model call per turn would double the cost and latency of every turn for a decision the narrating model can make itself while it already has the guidance, the input and the story in front of it. The decision is still the model's, so F06 stays an AI feature.
- How the user need is still met: the story is still steered towards the hidden events, and the events are still never shown to the user.
- Affected use cases / classes / methods / patterns: UC04 step 6; `PlotPlanner.planStep`, `StoryAgent.takeTurn`; Facade.
- Diagram to update: SD05 (no model call inside planStep).
- Commit(s): feat(agent): run the tool-use loop in StoryAgent.takeTurn

## F04: StoryAgent wiring and the turn loop limit
- Original (Stage 1): `StoryAgent` composes `PlotPlanner`, `ConsistencyChecker` and `MemoryManager` and uses `ToolRegistry` and `LLMClient`; `runToolLoop(request, session): LLMResponse` repeats while the response has tool calls.
- Implemented: `StoryAgent(llm, tools, planner, memory)` plus `StoryAgent.create(llm)`, which registers the standard tools. `runToolLoop(request, session, usedLore)` also collects the lore ids of successful lookups, and stops after 8 model calls with an error so a model that keeps calling tools cannot loop forever. The system prompt is read from `src/main/resources/prompts/system.txt`. `ConsistencyChecker` is added to the constructor when F08 is built.
- What changed and why: the loop needs a bound; recording the lore used is the F04 output "a record on the turn of which lore entries were used".
- How the user need is still met: as designed; a turn that cannot finish shows a message and adds nothing (UC04 7a).
- Affected use cases / classes / methods / patterns: UC04; `StoryAgent`; Facade, Strategy.
- Diagram to update: `figures/class-diagram/06-agent.png`.
- Commit(s): feat(agent): run the tool-use loop in StoryAgent.takeTurn

## F01/F05: Starting a story keeps lore prepared beforehand
- Original (Stage 1): `StoryController.startStory(scenario, plotBasis)` checks the mandatory fields, has the events planned and creates the `StorySession` that holds the scenario; the GUI reaches it from a plus button in a story list.
- Implemented: `startStory` calls the new `Scenario.readyToStart()` (premise and setting mandatory; an empty title becomes the first words of the premise), then `StoryAgent.planPlot`, and only then changes anything. If the open session is empty and not started (created with `new`, possibly with lore and characters added), the scenario and plot are put on it; otherwise a new session is created. `StoryController` now takes the `StoryAgent` in its constructor, and `isStarted()` tells the front ends whether turns are possible. The front ends build the controller through `StoryCLI.createController()`, which reads `.env`; without an API key the app still opens and model features report the missing key.
- What changed and why: week 1 already let the user add lore and characters to a session before any scenario exists (F02 and F03 do not need one), so starting the story must not throw that preparation away. Planning before any change means a failed plan leaves everything as it was (F05: "the story is not started").
- How the user need is still met: the user still writes the scenario, optionally a plot basis, and starts; the plan stays hidden.
- Affected use cases / classes / methods / patterns: UC01; `StoryController`, `Scenario`, `StoryAgent`, `PlotPlanner`, `StoryCLI`, `StoryGUI`; Facade, MVC.
- Diagram to update: `figures/class-diagram/03-controller.png` (constructor, isStarted), `04-model-story-and-lore.png` (readyToStart); SD02 (session reused when empty).
- Commit(s): feat(agent): plan hidden pivotal events before the story starts
