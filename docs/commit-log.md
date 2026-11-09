# Commit log

At least 5 meaningful code commits per period (Toronto time). Report-only, formatting-only, empty or artificially split commits do not count. Nov 10 is in no period.

## Period 1: Oct 20 to Oct 26

| # | Commit | Summary | Features |
|---|---|---|---|
| 1 | [64aa840](https://github.com/Polarralop/EECS3311Project/commit/64aa840) | feat(model): add lore entries with tag, search and pin in LoreBook | F02 |
| 2 | [a80fa3a](https://github.com/Polarralop/EECS3311Project/commit/a80fa3a) | feat(model): manage characters and relationships in StorySession | F03 |
| 3 | [957b423](https://github.com/Polarralop/EECS3311Project/commit/957b423) | feat(model): notify StoryObserver on session changes | F04, F05, F07, F09, F10 (model, Observer) |
| 4 | [0913872](https://github.com/Polarralop/EECS3311Project/commit/0913872) | feat(persistence): save and load sessions as JSON | F11 |
| 5 | [cee9994](https://github.com/Polarralop/EECS3311Project/commit/cee9994) | feat(llm): send requests through ClaudeAdapter with tool-use support | F04 (Adapter) |
| 6 | [c749429](https://github.com/Polarralop/EECS3311Project/commit/c749429) | feat(controller): expose lore, characters and save/load in StoryCLI | F02, F03, F11 (MVC) |
| 7 | [2990b8f](https://github.com/Polarralop/EECS3311Project/commit/2990b8f) | feat(ui): manage lore, characters and saves in StoryGUI | F02, F03, F11 (MVC, Observer) |

## Period 2: Oct 27 to Nov 2

| # | Commit | Summary | Features |
|---|---|---|---|
| 1 | [2f31992](https://github.com/Polarralop/EECS3311Project/commit/2f31992) | feat(tools): register SearchLoreTool and GetCharacterTool in ToolRegistry | F04 (tools) |
| 2 | [139c4c0](https://github.com/Polarralop/EECS3311Project/commit/139c4c0) | feat(agent): run the tool-use loop in StoryAgent.takeTurn | F04, F06 (Facade, Strategy) |
| 3 | [569e476](https://github.com/Polarralop/EECS3311Project/commit/569e476) | feat(agent): plan hidden pivotal events before the story starts | F01, F05 |
| 4 | [c5a57de](https://github.com/Polarralop/EECS3311Project/commit/c5a57de) | feat(agent): draft a scenario from a short prompt | F01 |
| 5 | [912b3d0](https://github.com/Polarralop/EECS3311Project/commit/912b3d0) | feat(controller): submit turns through SubmitTurnCommand and CommandHistory | F04, F10 (Command) |
| 6 | [f260c7f](https://github.com/Polarralop/EECS3311Project/commit/f260c7f) | feat(controller): edit a past turn with EditTurnCommand | F10 (Command) |
| 7 | [d86552d](https://github.com/Polarralop/EECS3311Project/commit/d86552d) | feat(tools): raise lore proposals and resolve them in StoryController | F07 |
| 8 | [70e760d](https://github.com/Polarralop/EECS3311Project/commit/70e760d) | feat(ui): play turns and review proposals in StoryGUI | F04, F07, F10, F01 (GUI) |

## Period 3: Nov 3 to Nov 9

| # | Commit | Summary | Features |
|---|---|---|---|
| 1 | [53dcf44](https://github.com/Polarralop/EECS3311Project/commit/53dcf44) | feat(tools): steer the hidden plot with UpdatePlotTool | F06 |
| 2 | [bf3e57d](https://github.com/Polarralop/EECS3311Project/commit/bf3e57d) | feat(agent): flag contradictions with pinned lore in ConsistencyChecker | F08 |
| 3 | [d80ffd1](https://github.com/Polarralop/EECS3311Project/commit/d80ffd1) | feat(agent): summarize older turns in MemoryManager | F09 |
| 4 | [62b5881](https://github.com/Polarralop/EECS3311Project/commit/62b5881) | feat(controller): show a model-written recap when a story is loaded | F09, F11 |
| 5 | [0de62b7](https://github.com/Polarralop/EECS3311Project/commit/0de62b7) | feat(agent): write the story's ending in StoryAgent.generateEnding | F12 |
| 6 | [c242f87](https://github.com/Polarralop/EECS3311Project/commit/c242f87) | feat(ui): end the story from StoryGUI and StoryCLI | F12 (GUI, CLI) |
| 7 | [04dae82](https://github.com/Polarralop/EECS3311Project/commit/04dae82) | feat(ui): reveal new story text gradually in both front ends | F04, F10, F12 (GUI, CLI) |
| 8 | [ff829a3](https://github.com/Polarralop/EECS3311Project/commit/ff829a3) | feat(controller): list saved stories by title and open the CLI to them | F11 (CLI) |
| 9 | [63a3cc5](https://github.com/Polarralop/EECS3311Project/commit/63a3cc5) | feat(ui): open StoryGUI to a story picker and name the tab after the story | F01, F11 (GUI) |
