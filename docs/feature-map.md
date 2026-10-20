# Feature map

Working record for sections 1.2, 2 (feature-to-implementation mapping), 3 (patterns) and 7 (demonstration index) of `Stage2_Report.docx`. Update a row in the same commit as the code it describes.

Status: Not started, In progress, Done, Changed (see changes-from-stage1.md), Dropped (reason required).

| ID | Feature | Type | Status | Main classes | Key methods built | GUI | CLI | Video / timestamp |
|---|---|---|---|---|---|---|---|---|
| F01 | Scenario Setup | Hybrid | Not started | StoryController, StoryAgent, Scenario | | | | |
| F02 | Lore Entry Management | Deterministic | In progress (CLI done, GUI next) | StoryController, LoreBook, LoreEntry, StoryCLI | StoryController.saveLoreEntry/removeLoreEntry/searchLore/getLoreEntry; LoreBook.add/update/remove/search/getPinned/find; LoreEntry.matches/copy, tag validation | | Yes: `lore list/search/show/add/edit/pin/unpin/remove` | |
| F03 | Character Management | Deterministic | In progress (CLI done, GUI next) | StoryController, StorySession, Character, Relationship, StoryCLI | StoryController.saveCharacter(originalName, c)/removeCharacter/getCharacter/listCharacters/addRelationship/removeRelationship; StorySession.addCharacter/updateCharacter/removeCharacter/addRelationship/findCharacter; Character.addRelationship/removeRelationship/describe | | Yes: `char list/show/add/edit/remove`, `rel add/remove` | |
| F04 | Story Turn | Hybrid | Not started | SubmitTurnCommand, StoryAgent, ToolRegistry, InputModeStrategy | | | | |
| F05 | Pivotal Events | Hybrid | Not started | StoryAgent, PlotPlanner, Plot, PivotalEvent | | | | |
| F06 | Plot Steering | AI | Not started | PlotPlanner, UpdatePlotTool, Plot | | | | |
| F07 | Lore Proposals | Hybrid | Not started | ProposeLoreTool, LoreProposal, StoryController | | | | |
| F08 | Consistency Check | Hybrid | Not started | ConsistencyChecker, LoreBook | | | | |
| F09 | Story Memory Summarization | AI | Not started | MemoryManager, StoryAgent | | | | |
| F10 | Undo / Retry / Edit Turn | Hybrid | Not started | CommandHistory, SubmitTurnCommand, EditTurnCommand | | | | |
| F11 | Save and Load Session | Deterministic | In progress (CLI done, GUI next; recap waits for F09) | StoryController, SessionRepository, JsonSessionRepository, CommandHistory, StoryCLI | StoryController.saveSession/loadSession/listSessions/newSession (history cleared, observers moved to the loaded session); JsonSessionRepository.save (atomic temp-file write), load (missing / damaged / id mismatch errors), listSessions (newest first) | | Yes: `new`, `save`, `load <id>`, `sessions` | |
| F12 | Ending Generation | AI | Not started | StoryAgent, PlotPlanner | | | | |

F06 and F09 were flagged in Stage 1 as possibly not distinct features. Make their behaviour visible in the app and the video.

## Patterns

| Pattern | Participants (Stage 1) | Implemented in | Status |
|---|---|---|---|
| Adapter | LLMClient, ClaudeAdapter, Anthropic SDK | llm/LLMClient.java (generate), llm/ClaudeAdapter.java (generate, toApiBody, fromApiBody), llm/FakeLLMClient.java (second Target implementation for tests) | Done (adaptee: anthropic-java `client.messages().create`) |
| Command | StoryCommand, SubmitTurnCommand, EditTurnCommand, CommandHistory | controller/StoryCommand.java (execute, undo), controller/CommandHistory.java (run, undoLast, canUndo, clear) | In progress (invoker done; concrete commands come with F04/F10) |
| Facade | StoryAgent over PlotPlanner, MemoryManager, ConsistencyChecker, ToolRegistry | | Not started |
| MVC | StorySession (model), StoryGUI/StoryCLI (view), StoryController | controller/StoryController.java (every lore, character and save/load action), ui/StoryCLI.java (reads commands, prints results; no story logic), model/* | In progress (CLI done; GUI next) |
| Observer | StorySession, StoryObserver, StoryGUI, StoryCLI | model/StorySession.java (attach, detach, notifyObservers; observers field is transient), ui/StoryObserver.java (onStoryChanged), ui/StoryCLI.java (onStoryChanged prints a status line), controller/StoryController.java (addObserver; moves observers to a new or loaded session) | In progress (CLI observer done; GUI next) |
| Strategy | InputModeStrategy, Do/Say/StoryModeStrategy | | Not started |
