# Feature map

Working record for sections 1.2, 2 (feature-to-implementation mapping), 3 (patterns) and 7 (demonstration index) of `Stage2_Report.docx`. Update a row in the same commit as the code it describes.

Status: Not started, In progress, Done, Changed (see changes-from-stage1.md), Dropped (reason required).

| ID | Feature | Type | Status | Main classes | Key methods built | GUI | CLI | Video / timestamp |
|---|---|---|---|---|---|---|---|---|
| F01 | Scenario Setup | Hybrid | Not started | StoryController, StoryAgent, Scenario | | | | |
| F02 | Lore Entry Management | Deterministic | Not started | StoryController, LoreBook, LoreEntry | | | | |
| F03 | Character Management | Deterministic | Not started | StoryController, StorySession, Character, Relationship | | | | |
| F04 | Story Turn | Hybrid | Not started | SubmitTurnCommand, StoryAgent, ToolRegistry, InputModeStrategy | | | | |
| F05 | Pivotal Events | Hybrid | Not started | StoryAgent, PlotPlanner, Plot, PivotalEvent | | | | |
| F06 | Plot Steering | AI | Not started | PlotPlanner, UpdatePlotTool, Plot | | | | |
| F07 | Lore Proposals | Hybrid | Not started | ProposeLoreTool, LoreProposal, StoryController | | | | |
| F08 | Consistency Check | Hybrid | Not started | ConsistencyChecker, LoreBook | | | | |
| F09 | Story Memory Summarization | AI | Not started | MemoryManager, StoryAgent | | | | |
| F10 | Undo / Retry / Edit Turn | Hybrid | Not started | CommandHistory, SubmitTurnCommand, EditTurnCommand | | | | |
| F11 | Save and Load Session | Deterministic | Not started | SessionRepository, JsonSessionRepository | | | | |
| F12 | Ending Generation | AI | Not started | StoryAgent, PlotPlanner | | | | |

F06 and F09 were flagged in Stage 1 as possibly not distinct features. Make their behaviour visible in the app and the video.

## Patterns

| Pattern | Participants (Stage 1) | Implemented in | Status |
|---|---|---|---|
| Adapter | LLMClient, ClaudeAdapter, Anthropic SDK | | Not started |
| Command | StoryCommand, SubmitTurnCommand, EditTurnCommand, CommandHistory | | Not started |
| Facade | StoryAgent over PlotPlanner, MemoryManager, ConsistencyChecker, ToolRegistry | | Not started |
| MVC | StorySession (model), StoryGUI/StoryCLI (view), StoryController | | Not started |
| Observer | StorySession, StoryObserver, StoryGUI, StoryCLI | | Not started |
| Strategy | InputModeStrategy, Do/Say/StoryModeStrategy | | Not started |
