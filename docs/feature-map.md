# Feature map

Working record for sections 1.2, 2 (feature-to-implementation mapping), 3 (patterns) and 7 (demonstration index) of `Stage2_Report.docx`. Update a row in the same commit as the code it describes.

Status: Not started, In progress, Done, Changed (see changes-from-stage1.md), Dropped (reason required).

| ID | Feature | Type | Status | Main classes | Key methods built | GUI | CLI | Video / timestamp |
|---|---|---|---|---|---|---|---|---|
| F01 | Scenario Setup | Hybrid | Done in the CLI (GUI form with the GUI slice) | StoryController, StoryAgent, Scenario, StoryCLI | StoryController.generateScenario(prompt) and StoryAgent.generateScenario (prompts/scenario.txt, JSON reply read with StoryAgent.jsonObjectIn; a draft without premise or setting is an error); StoryController.startStory(scenario, plotBasis) (keeps lore prepared in an empty session, else a new session); Scenario.readyToStart (premise and setting mandatory, title from the premise) | | Yes: `start` (idea to draft, or Enter to write it; each field can then be edited) | |
| F02 | Lore Entry Management | Deterministic | Done | StoryController, LoreBook, LoreEntry, StoryCLI, StoryGUI | StoryController.saveLoreEntry/removeLoreEntry/searchLore/getLoreEntry; LoreBook.add/update/remove/search/getPinned/find; LoreEntry.matches/copy, tag validation | Yes: Lore tab | Yes: `lore list/search/show/add/edit/pin/unpin/remove` | |
| F03 | Character Management | Deterministic | Done | StoryController, StorySession, Character, Relationship, StoryCLI, StoryGUI | StoryController.saveCharacter(originalName, c)/removeCharacter/getCharacter/listCharacters/addRelationship/removeRelationship; StorySession.addCharacter/updateCharacter/removeCharacter/addRelationship/findCharacter; Character.addRelationship/removeRelationship/describe | Yes: Characters tab | Yes: `char list/show/add/edit/remove`, `rel add/remove` | |
| F04 | Story Turn | Hybrid | In progress (agent loop done; controller and front ends next) | StoryAgent, InputModeStrategy, DoModeStrategy, SayModeStrategy, StoryModeStrategy, MemoryManager, PlotPlanner, ToolRegistry, AgentTool, ToolCall, ToolResult, SearchLoreTool, GetCharacterTool, SubmitTurnCommand | StoryAgent.takeTurn(session, input, mode) and runToolLoop (at most 8 model calls, refusal and empty narration are errors, lore ids from successful lookups recorded on the Turn); InputModeStrategy.getName/frame; MemoryManager.buildContext (summary + last 8 turns); PlotPlanner.planStep (next pending event as hidden guidance); ToolRegistry.register/schemas/execute; SearchLoreTool.execute; GetCharacterTool.execute | | | |
| F05 | Pivotal Events | Hybrid | Done in the CLI (GUI form with the GUI slice) | StoryController, StoryAgent, PlotPlanner, Plot, PivotalEvent, StoryCLI | StoryController.startStory; StoryAgent.planPlot(scenario, basis); PlotPlanner.createPlot (JSON reply parsed with Jackson, E1..En PENDING, at most 8, refusal / no events is an LLMException and no story starts); PlotPlanner.parseEvents; PlotPlanner.planStep feeds the next event into each turn as hidden guidance | | Yes: `start` (plot basis is the last question; the plan is never printed) | |
| F06 | Plot Steering | AI | Not started | PlotPlanner, UpdatePlotTool, Plot | | | | |
| F07 | Lore Proposals | Hybrid | Not started | ProposeLoreTool, LoreProposal, StoryController | | | | |
| F08 | Consistency Check | Hybrid | Not started | ConsistencyChecker, LoreBook | | | | |
| F09 | Story Memory Summarization | AI | Not started | MemoryManager, StoryAgent | | | | |
| F10 | Undo / Retry / Edit Turn | Hybrid | Not started | CommandHistory, SubmitTurnCommand, EditTurnCommand | | | | |
| F11 | Save and Load Session | Deterministic | Done (the recap after load is deterministic until F09) | StoryController, SessionRepository, JsonSessionRepository, CommandHistory, StoryCLI, StoryGUI | StoryController.saveSession/loadSession/listSessions/newSession (history cleared, observers moved to the loaded session); JsonSessionRepository.save (atomic temp-file write), load (missing / damaged / id mismatch errors), listSessions (newest first) | Yes: Story menu, New / Save / Load... | Yes: `new`, `save`, `load <id>`, `sessions` | |
| F12 | Ending Generation | AI | Not started | StoryAgent, PlotPlanner | | | | |

F06 and F09 were flagged in Stage 1 as possibly not distinct features. Make their behaviour visible in the app and the video.

## Patterns

| Pattern | Participants (Stage 1) | Implemented in | Status |
|---|---|---|---|
| Adapter | LLMClient, ClaudeAdapter, Anthropic SDK | llm/LLMClient.java (generate), llm/ClaudeAdapter.java (generate, toApiBody, fromApiBody), llm/FakeLLMClient.java (second Target implementation for tests) | Done (adaptee: anthropic-java `client.messages().create`) |
| Command | StoryCommand, SubmitTurnCommand, EditTurnCommand, CommandHistory | controller/StoryCommand.java (execute, undo), controller/CommandHistory.java (run, undoLast, canUndo, clear) | In progress (invoker done; concrete commands come with F04/F10) |
| Facade | StoryAgent over PlotPlanner, MemoryManager, ConsistencyChecker, ToolRegistry | agent/StoryAgent.java (create, takeTurn, runToolLoop: the only class that calls PlotPlanner.planStep, MemoryManager.buildContext, ToolRegistry.execute and LLMClient.generate in turn order) | In progress (ConsistencyChecker joins with F08) |
| MVC | StorySession (model), StoryGUI/StoryCLI (view), StoryController | controller/StoryController.java (every lore, character and save/load action), ui/StoryCLI.java (reads commands, prints results), ui/StoryGUI.java (Lore and Characters tabs, Story menu); neither holds story logic; model/* | Done for F02, F03, F11 |
| Observer | StorySession, StoryObserver, StoryGUI, StoryCLI | model/StorySession.java (attach, detach, notifyObservers; observers field is transient), ui/StoryObserver.java (onStoryChanged), ui/StoryCLI.java (onStoryChanged prints a status line), ui/StoryGUI.java (onStoryChanged refreshes lists and status bar), controller/StoryController.java (addObserver; moves observers to a new or loaded session) | Done |
| Strategy | InputModeStrategy, Do/Say/StoryModeStrategy | agent/InputModeStrategy.java (getName, frame), agent/DoModeStrategy.java, agent/SayModeStrategy.java, agent/StoryModeStrategy.java; context: agent/StoryAgent.java (takeTurn calls mode.frame and stores mode.getName on the Turn) | Done (the controller picks the strategy by name with F04's controller slice) |
