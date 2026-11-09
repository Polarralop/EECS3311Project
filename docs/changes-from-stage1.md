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

## F04/F10: Turn commands and retry details
- Original (Stage 1): `SubmitTurnCommand(input, mode, plotBefore, result)` with `execute`, `undo`, `getInput`; `CommandHistory.run/undoLast/canUndo`; `StoryController.retryTurn()` calls `undoLast()`, reads the input and runs a new command.
- Implemented: `SubmitTurnCommand(session, agent, input, mode)` also has `getMode()` and `getResult()`, and if the agent fails it puts the plot copy back before passing the error on (a tool call may have changed the plot mid-turn), so a failed turn adds no turn and changes no events; lore proposals it raised stay pending, as for an undone turn (F07). `CommandHistory` gains `latest(type)` and `peekLast()`. `retryTurn()` finds the latest `SubmitTurnCommand`, undoes any edits made to that turn after it, undoes the turn and runs a new command with the same input and mode; the front ends are notified even when the retry fails, because the turn is gone either way. `StoryController` gains `modeNames()` and `isStarted()`, and turns need a started story (a scenario).
- What changed and why: Stage 1 assumed the last command is always the turn, but after an edit (F10) the last command is the `EditTurnCommand`; regenerating the turn must discard the edit too. `getMode()` is how retry reuses the same strategy.
- How the user need is still met: undo removes the turn and restores the events; retry regenerates it from the same input; a failed retry leaves the turn undone and the front end shows the input again.
- Affected use cases / classes / methods / patterns: UC04, UC06; `SubmitTurnCommand`, `CommandHistory`, `StoryController`, `StoryCLI`; Command, Strategy.
- Diagram to update: `figures/class-diagram/03-controller.png`; SD08 (retry undoes edits first).
- Commit(s): feat(controller): submit turns through SubmitTurnCommand and CommandHistory

## F10: A loaded turn can be edited
- Original (Stage 1): the Undo, Retry and Edit buttons are available only while `CommandHistory.canUndo()` is true, so none of them work right after a load; `EditTurnCommand(newNarration, oldNarration)`.
- Implemented: `StoryController.editTurn(newNarration)` works whenever the story has a turn, including right after a load; the edit itself is then in the history and can be undone. Undo and retry still need a turn taken since the story was opened, because the history is not saved. `EditTurnCommand(session, newNarration)` keeps the edited `Turn` and its `oldNarration`.
- What changed and why: replacing text needs nothing that was lost at load time (no plot copy, no original input), so there is no reason to forbid it, and fixing the last paragraph of a story you just reopened is a common need.
- How the user need is still met: edit, undo of the edit, and the empty-text and cancel cases behave as in UC06.
- Affected use cases / classes / methods / patterns: UC06; `EditTurnCommand`, `StoryController`, `StoryCLI`; Command.
- Diagram to update: `figures/class-diagram/03-controller.png` (EditTurnCommand fields).
- Commit(s): feat(controller): edit a past turn with EditTurnCommand

## F07: Proposal checks and the pending list
- Original (Stage 1): `ProposeLoreTool.execute(args, session)` checks that the title and content are present and calls `StorySession.addProposal`; `StoryController.resolveProposal(id, approved)`; the front end calls `showProposal(proposal)`.
- Implemented: as designed, plus: the tool refuses a title that the lore book or another pending proposal already has (the model is told to read the existing entry instead), and `StoryController.listPendingProposals()` gives the front ends the list to show. When a proposal raised during a turn is followed by a failed model call, the front ends are still notified so the proposal appears. The front ends redraw the list from the session in `onStoryChanged` rather than through a separate `showProposal` call.
- What changed and why: without the title check the model tends to re-propose facts it has just looked up, and approval would then fail on the lore book's duplicate-title rule anyway. Redrawing from the session is how every other list in the views already works (Observer).
- How the user need is still met: the agent proposes, the user approves or rejects each proposal, and only approved proposals become lore.
- Affected use cases / classes / methods / patterns: UC05; `ProposeLoreTool`, `StoryController`, `StoryCLI`; Observer, MVC.
- Diagram to update: SD07 (no showProposal call; the list is redrawn on notify).
- Commit(s): feat(tools): raise lore proposals and resolve them in StoryController

## F06: UpdatePlotTool takes an action, and the guidance lists every pending event
- Original (Stage 1): `UpdatePlotTool.execute(args, session)` calls `Plot.markReached(eventId)` or `Plot.replacePending(events)`; `PlotPlanner.planStep(session, input)` returns guidance about the next pending event; a call naming an unknown event is a failed `ToolResult`.
- Implemented: one `update_plot` tool with an `action` argument: `"reached"` with `event_id`, or `"revise"` with `new_events` (a JSON array; plain text with one event per line is accepted too). New events get the next free ids (E4, E5, ... after E3, dropped events included), a revision holds 1 to 6 events (`UpdatePlotTool.MAX_NEW_EVENTS`), and revising is refused once every event is reached, so the story moves to an ending (F12) instead of growing forever. An event reached out of order is accepted and the result says which earlier one is still pending. `planStep` now also lists the later pending events and tells the model when to call `update_plot`, because a revision has to know what it is replacing.
- What changed and why: the Messages API gives the model one tool per name, and an `action` enum keeps both plot operations behind the single `UpdatePlotTool` class the design has. The limits keep a model from rewriting the plan into an endless story.
- How the user need is still met: when the user goes off-script the narration nudges back or the plan is revised to fit; the events stay hidden in both front ends, and undo or a failed turn puts the plan back (F10).
- Affected use cases / classes / methods / patterns: UC04 step 6; `UpdatePlotTool`, `PlotPlanner.planStep`, `StoryAgent.create` (registers the tool), `prompts/system.txt`; Facade (subsystem).
- Diagram to update: `figures/class-diagram/07-agent-tools.png` (UpdatePlotTool implements AgentTool, MAX_NEW_EVENTS, parseEvents); SD05 (update_plot inside the tool loop).
- Commit(s): feat(tools): steer the hidden plot with UpdatePlotTool

## F08: The check takes the lore the turn used, and an edit clears the warnings
- Original (Stage 1): `ConsistencyChecker.check(narration, session): List<String>` compares the narration with the pinned facts "together with the lore entries the turn used"; the agent stores the list in `Turn.warnings`; a failed check returns the turn "with a warning saying that it was not checked"; the front end shows warnings in `showNarration(turn)`.
- Implemented: `check(narration, session, usedLoreIds)`: the ids of the entries looked up during the turn are passed in, because the session does not know them until the turn is added. The model answers with a JSON list of `{entry, problem}`; each becomes a warning such as `Contradicts L1 "Orrin" (canon): ...`, at most 5 per turn. A failed call, a refusal or an unreadable answer all give one warning starting "This turn was not checked against the lore". `StoryAgent` takes the checker in its constructor. `Turn.setWarnings` is added, and `EditTurnCommand` clears a turn's warnings when the user replaces its narration and restores them on undo. The front ends redraw the warnings from the turns in `onStoryChanged` rather than through a `showNarration` call.
- What changed and why: the signature needs the used lore to do what the design describes. The warnings are about the agent's text; once the user has written their own, an old warning would point at words that are gone.
- How the user need is still met: contradictions with pinned canon or the lore in play are flagged under the turn and never block it; the user can retry or edit (F10).
- Affected use cases / classes / methods / patterns: UC04 steps 8, 8a, 8b; UC06 1b; `ConsistencyChecker`, `StoryAgent`, `Turn`, `EditTurnCommand`, `StoryCLI`, `StoryGUI`, `prompts/consistency-check.txt`; Facade, Command.
- Diagram to update: `figures/class-diagram/06-agent.png` (check signature, constructor), `04-model-story-and-lore.png` (Turn.setWarnings), `03-controller.png` (EditTurnCommand.oldWarnings); SD06.
- Commit(s): feat(agent): flag contradictions with pinned lore in ConsistencyChecker

## F09: Older turns are summarized at the start of a turn, not the end
- Original (Stage 1): at the end of `takeTurn` the agent calls `MemoryManager.summarizeIfNeeded(session)`; `buildContext(session)` returns the summary and the most recent turns up to `recentTurnLimit`; `MemoryManager` has no constructor arguments in the class diagram.
- Implemented: `takeTurn` calls `summarizeIfNeeded` first, then `buildContext`. `MemoryManager(llm)` / `MemoryManager(llm, recentTurnLimit)` take the model client. `summarizeIfNeeded` returns whether the summary changed. `buildContext` sends the summary followed by every turn the summary does not cover, normally exactly the last `recentTurnLimit` turns; when summarizing has failed it also sends the uncovered older turns, up to twice the limit.
- What changed and why: the turn being narrated is added to the session by `SubmitTurnCommand` only after `takeTurn` returns, so a summary written at the end of `takeTurn` would stop one turn short of the next turn's window and that turn would be in neither the summary nor the recent turns. Summarizing at the start sees the session as the narration will, so the summary always ends where the recent turns begin. Sending uncovered turns on failure keeps the "try again after the next turn" fallback from losing a turn meanwhile.
- How the user need is still met: long stories send a summary plus the recent turns; a failed summary is retried on the next turn and the story stays coherent in between.
- Affected use cases / classes / methods / patterns: UC04 steps 5 and 9a; `MemoryManager`, `StoryAgent`, `prompts/summarizer.txt`; Facade.
- Diagram to update: `figures/class-diagram/06-agent.png` (MemoryManager constructor and llm field); SD05 and SD06 (summarizeIfNeeded moves before buildContext).
- Commit(s): feat(agent): summarize older turns in MemoryManager

## F09/F11: The recap is shown with what was loaded, in a panel on the Story tab
- Original (Stage 1): `StoryController.loadSession(id)` calls `StoryAgent.recap(session)`, which delegates to `MemoryManager.recap(session)`, and returns the recap; the story screen shows it at the top; a failed recap means the story loads without one. Week 1 returned a deterministic line (counts of lore, characters and turns) until F09 was built.
- Implemented: as designed, and `loadSession` returns the deterministic "Loaded ..." line followed by "The story so far: <recap>". A story without turns gets no recap and no model call. When the recap fails or is refused, the story is loaded and the message ends with "(No recap this time: <reason>)". The recap is written from the scenario, the summary and the recent turns (prompts/recap.txt), never the hidden plan. `StoryGUI` loads in the background and shows the message in a "Welcome back" panel at the top of the Story tab, which hides when another story is opened; `StoryCLI` prints it.
- What changed and why: telling the user why there is no recap is more helpful than silently showing none, and the counts line confirms which story was opened. A panel instead of a dialog keeps the recap in view while the user reads the transcript.
- How the user need is still met: a reopened story shows a recap at the top of the story screen and play continues from the last turn; a failed recap never stops the load.
- Affected use cases / classes / methods / patterns: UC07 1a, 1d; `StoryController.loadSession`, `StoryAgent.recap`, `MemoryManager.recap`, `StoryGUI` (recap panel, showRecap), `StoryCLI` (load); Facade, MVC.
- Diagram to update: `figures/class-diagram/02-ui.png` (StoryGUI.showRecap), `06-agent.png` (recap methods); SD09 (recap call replaces describe).
- Commit(s): feat(controller): show a model-written recap when a story is loaded

## F12: The ending is stored on the session
- Original (Stage 1): `StoryAgent.generateEnding(session)` returns the closing passage to the controller and the front end shows it at the end of the story; `PlotPlanner.readyToEnd(plot)` uses `Plot.isComplete()`. The model has no field for the ending.
- Implemented: `StorySession` gains `ending` with `getEnding()`, `setEnding(text)` and `isEnded()`, so the ending is saved and loaded with the story (F11). `StoryAgent.readyToEnd(session)` is the agent's own entry point to `PlotPlanner.readyToEnd`, so the controller does not reach past the facade. `generateEnding` sends the plan with each event marked as happened or not (dropped events left out), says whether the plan was completed or the user ended early, and adds every lore entry and the story context.
- What changed and why: UC08's postcondition is "the story has a closing passage", which has to survive a save and load like every other part of the story. Marking the events lets an early ending avoid narrating events that never happened.
- How the user need is still met: the agent writes an ending from the pivotal events, the lore and the story so far, and the ending stays with the story.
- Affected use cases / classes / methods / patterns: UC08; `StorySession`, `StoryAgent`, `PlotPlanner`, `prompts/ending.txt`; Facade, MVC (model).
- Diagram to update: `figures/class-diagram/04-model-story-and-lore.png` (ending), `06-agent.png` (readyToEnd, generateEnding); SD10.
- Commit(s): feat(agent): write the story's ending in StoryAgent.generateEnding

## F12: How the ending is suggested, and what an ending closes
- Original (Stage 1): an End Story button calls `StoryController.requestEnding()`; "when the agent judges that the story is ready to end, a prompt suggests it" (once `PlotPlanner.readyToEnd(plot)` is true); an early request asks the user to confirm; on failure the controller calls `showError(message)` and the story is unchanged. Nothing is said about turns after an ending.
- Implemented: `requestEnding()` returns the ending and stores it with `StorySession.setEnding`. The controller adds four queries for the front ends: `canEnd()` (a started story with a turn), `isReadyToEnd()` (no pending events; when false the front end asks to confirm), `isEndingSuggested()` (true only right after the turn or retry that reached the last pivotal event, so a dismissed suggestion is not repeated every turn) and `isEnded()`. An ended story takes no more turns, undo, retry or edit; End story on an ended story asks to confirm and writes a new ending in its place. Errors reach the front end as exceptions, as for every other controller method. The GUI button reads "Rewrite ending" once the story has ended, and the CLI command is `end`.
- What changed and why: the design leaves "ready to end" as a state, which would prompt after every turn once the plan is complete; suggesting on the transition gives the user one prompt to accept or dismiss, as UC08 1a describes. Blocking turns keeps the closing passage the last thing in the story, and rewriting gives the user a way to try again without undo history (which is not saved).
- How the user need is still met: the user can end the story at any time (confirming when the plot is unfinished), the agent offers to end once the plan is complete, and the ending is shown at the end of the story in both front ends and kept when the story is saved.
- Affected use cases / classes / methods / patterns: UC08 (main flow, 1a, 1b, 2a); `StoryController`, `StoryCLI` (end, offerEnding), `StoryGUI` (endStory, confirm, offerEnding); MVC, Observer.
- Diagram to update: `figures/class-diagram/02-ui.png`, `03-controller.png`; SD10.
- Commit(s): feat(ui): end the story from StoryGUI and StoryCLI

## New: New story text is revealed gradually in both front ends
- Original (Stage 1): after a turn the controller notifies the observers and the front end shows the new narration (SD04); how fast it appears is not specified, so in week 3 the whole turn appeared at once.
- Implemented: a new package-private class `ui/TranscriptPacer` shows the GUI transcript. When the transcript only grows in the same story (a new turn, a retry, the ending), `show(text, pace)` reveals the new part a few characters every 20 ms (`tick()`) at `StoryGUI.CHARS_PER_SECOND` (300 characters a second), sped up so a reveal takes at most 10 seconds; clicking the transcript or pressing Send calls `skip()`. Loading, undoing and editing replace the text at once. `StoryCLI` takes the same speed in a new constructor `StoryCLI(controller, in, out, charsPerSecond)` and prints a new turn or ending gradually in `printText`; `turns` and the old constructor (used by tests) print at once.
- What changed and why: the user asked for the story to appear at a readable pace rather than all at once. Real streaming from the Messages API was considered and not used: a turn is a tool-use loop whose narration is checked by `ConsistencyChecker` and committed by `SubmitTurnCommand` before it is shown, so streaming would show text that a failed or retried turn later takes back, and it would change `LLMClient`, `StoryAgent` and SD04. Pacing the display keeps every turn validated first and leaves the model, agent and controller unchanged.
- How the user need is still met: every turn is still complete, checked and undoable before it is shown; only the display is paced.
- Affected use cases / classes / methods / patterns: UC04 (story turn) and UC08 (ending), the display step; `StoryGUI` (refreshStory, submitInput, CHARS_PER_SECOND), `TranscriptPacer` (new), `StoryCLI` (constructor, printTurn, printEnding, printText); MVC (view only), Observer (the view still refreshes from onStoryChanged).
- Diagram to update: `figures/class-diagram/02-ui.png` (TranscriptPacer, the StoryCLI constructor).
- Commit(s): feat(ui): reveal new story text gradually in both front ends

## F11: The app opens to a story picker, and stories are offered by title
- Original (Stage 1): UC07 has the user choose a saved session from a list (`StoryController.listSessions()` returns the ids) and `loadSession(id)` opens it. What the app shows on launch is not specified; in week 3 the GUI opened to empty tabs and the CLI to a help line.
- Implemented: `StoryController.listSavedStories()` returns the saved stories, most recently saved first, as `SavedStory(id, title, savedAt)` records (new, controller package), loading each save through `SessionRepository.load` (a damaged save is listed as "(cannot be read)" and loading it still reports why) and asking the new `SessionRepository.lastSaved(id)` when it was saved (`JsonSessionRepository` reads the save file's modification time). The GUI shows each story as its title followed by "Saved Nov 9, 2026, 4:27 AM" in grey (`StoryGUI.SavedStoryRenderer`, `savedLabel`), without the id; the Load dialog shows "title (saved ...)"; the CLI keeps the id, which `load <id>` needs, and adds the save time. `StorySession.getTitle()` gives the scenario's title, or `StorySession.UNTITLED` ("Untitled story") for a story prepared before its scenario. `StoryCLI` opens to "Select a story:" with each id and title and how to load one, or "No stories detected. Type \"start\" to create a story."; `sessions` also prints titles. `StoryGUI` now opens to a start screen instead of empty tabs (a `CardLayout` with a "home" card and the tabs): "Select story" with the saved stories by title, the newest selected, Open (or a double-click) and Create story..., or "No stories detected" with only Create story..., which opens the Start story form. The tabs appear once a story is open or started. The first tab, which was also labelled "Story" like the menu next to it, is now named after the open story (`StorySession.getTitle()`, cut to 40 characters with the whole title in its tooltip), and the line above the transcript only shows while the story has no scenario. Story > Load... lists titles too.
- What changed and why: the user asked for the app to open to a story picker, or to "No stories detected" with a way to create one, and for the duplicate "Story" tab to show the story's title instead; ids such as story-20261108-101500 do not say which story is which.
- How the user need is still met: loading still goes through `loadSession(id)` with its recap (F09) and fresh undo history; only how the user finds the story changed.
- Affected use cases / classes / methods / patterns: UC01 (start from the start screen), UC07 (choose a session); `StoryController.listSavedStories`, `StorySession.getTitle`, `StoryCLI` (printStartScreen, listSessions), `StoryGUI` (refreshHome, openSelectedStory, load, chooseSession, refreshStory, SavedStoryRenderer, savedLabel), `SavedStory` (new), `SessionRepository.lastSaved`, `JsonSessionRepository.lastSaved`; MVC.
- Diagram to update: `figures/class-diagram/03-controller.png` (listSavedStories, SavedStory), `04-model-story-and-lore.png` (getTitle), `09-persistence.png` (lastSaved), `02-ui.png`; all redrawn in `figures/stage2/`. SD09 still shows the user choosing from the list; only what the list shows changed.
- Commit(s): feat(controller): list saved stories by title and open the CLI to them; feat(ui): open StoryGUI to a story picker and name the tab after the story; feat(ui): show when each saved story was saved instead of its id

## New: The GUI has its own colour palette (StoryTheme)
- Original (Stage 1): the report does not specify how the GUI looks; `StoryGUI` used Swing's default Metal look (grey panels, blue highlights).
- Implemented: a new package-private class `ui/StoryTheme` holds the palette, one deep blue-purple and its shades: `BASE` (the window), `INSET` (text boxes and lists), `SURFACE` (the story column and dialogs), `SIDEBAR`, `RAISED` (cards and buttons), `LINE` (borders), `SELECTION`, `ACCENT` (the main button of a screen) and `ACCENT_SOFT`, with `TEXT`, `MUTED` and `DISABLED` for text. The shades get lighter from the back layer to the front one, so a panel that sits on another reads as a layer above it. `StoryTheme.install()` applies the palette to every Swing component through a Metal theme (`MetalLookAndFeel.setCurrentTheme`) plus a few `UIManager` defaults (flat borders, padding, plain fonts); `StoryGUI.main` calls it before building the window. Helpers style the components that stand out: `primary(button)` / `plain(button)` (the accent only while the button can be pressed), `quiet(button)` (a text link), `sectionLabel(text)`, `layer(component, colour)` and the rounded `StoryTheme.Card` panel.
- What changed and why: the user asked for a deep blue-purple main colour with shades for layering. Keeping the colours in one class means no colour is typed into `StoryGUI`'s layout code and the palette can be checked by tests (`StoryThemeTest`: the layers are one hue from dark to light, text contrast is at least 7:1 on every layer).
- How the user need is still met: display only. `StoryTheme` never reads or changes the story; every action still goes through `StoryController`. The CLI is unchanged.
- Affected use cases / classes / methods / patterns: none of the use cases; `StoryTheme` (new), `StoryGUI.main`; MVC (view only).
- Diagram to update: `figures/class-diagram/02-ui.png` (StoryTheme); redrawn in `figures/stage2/`.
- Commit(s): feat(ui): give StoryGUI a deep blue-purple palette with layered shades

## F02, F03, F04, F07, F09: Lore and characters move to a collapsible sidebar; the recap becomes a card
- Original (Stage 1): the report names what the GUI offers (lore and character management, the lore used in a turn, proposals, the recap) but not where it sits. Up to week 3 `StoryGUI` had three tabs (the story, Lore, Characters), a "Lore used in the last turn" list under the transcript, the proposals at the bottom of the Lore tab and the recap in a titled box with a Hide button at the top of the story tab.
- Implemented: the tabs are gone. An open story is one screen (`StoryGUI.buildStoryCard`): a heading with the story's title (`storyHeading`; the whole title is also its tooltip), the story column (recap, transcript, Undo / Retry / Edit / End story, input) and, on the right, a sidebar 400 px wide (`buildSidebar`). The sidebar holds a "Lore used this turn" card (a note when the last turn looked nothing up) above three tabs: Lore, Characters and Proposals (`sidebarTabs`, `LORE_TAB`, `CHARACTERS_TAB`, `PROPOSALS_TAB`). The button in the heading collapses the sidebar and brings it back (`setSidebarOpen`, `isSidebarOpen`); collapsed, it reads "« Lore & characters (2)" with the number of waiting proposals. Double-clicking a used entry opens it in the Lore tab and reopens a collapsed sidebar (`openLoreEntry`). Because the sidebar is narrow, each tab stacks its list above its form, the buttons are New / Save / Delete, and the proposals have their own tab (titled "Proposals (N)") with Approve and Reject enabled only while something is waiting. The recap is a `StoryTheme.Card` above the transcript (`buildRecapCard`): a raised shade with an accent stripe and a small "Welcome back · the story so far" heading, the text with no box around it, as tall as the text up to `RECAP_ROWS` (5) lines, and a quiet Dismiss link.
- What changed and why: the user asked for lore and characters in their own collapsible sidebar on the right, including the lore used this turn, and for the recap to stand out as a layer instead of looking like another menu. With the sidebar the lore and the cast stay visible while the story is written, instead of being a tab away.
- How the user need is still met: every control of F02, F03, F04, F07, F09 and F10 is still in the GUI and calls the same `StoryController` methods; only the layout changed. The hidden pivotal events are still never shown. The CLI is unchanged.
- Affected use cases / classes / methods / patterns: UC02, UC03, UC04, UC05 (review a proposal), UC07 (the recap), the display steps only; `StoryGUI` (buildStoryCard, buildStoryColumn, buildRecapCard, buildSidebar, setSidebarOpen, isSidebarOpen, openLoreEntry, refreshProposals, refreshStory, savedColour; `tabs` and `TAB_TITLE_LENGTH` removed), `StoryTheme.Card`; MVC (view only), Observer (the sidebar is refreshed from `onStoryChanged` like the old tabs).
- Diagram to update: `figures/class-diagram/02-ui.png` (the sidebar methods); redrawn in `figures/stage2/`.
- Commit(s): feat(ui): move lore, characters and used lore into a collapsible sidebar
