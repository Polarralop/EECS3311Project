# AGENTS.md

Instructions for AI coding agents (Claude Code and others) working in this repository. Read this whole file before making changes. Humans are welcome to follow it too.

## Project

AI Interactive Storyteller, the EECS 3311 agent project. A solo role-player writes a story with an agent that tracks lore, characters and a hidden list of pivotal events, so a long story stays consistent and can still surprise the user.

- Language: Java 21 (a course requirement). Build: Maven.
- LLM used by the app: Claude Sonnet 5.5 (`claude-sonnet-5-5`), reached only through `LLMClient`.
- Two front ends, `StoryGUI` (Swing) and `StoryCLI`, over one `StoryController`. Every major feature must work from both.
- Stage 2 (implementation) is due **Nov 10, 2026**.

### Source of truth

The design is the Stage 1 report, `Stage1_Report.docx`, with its diagrams in `figures/`:

- `figures/class-diagram/` has one PNG per package. Match class, method and package names to these.
- `figures/sequence-diagrams/` shows how classes collaborate for each feature (SD01 to SD10).
- `docs/feature-map.md` lists the 12 features (F01 to F12) with their classes, key methods and status.

Do not edit `Stage1_Report.docx`; it is the submitted Stage 1 deliverable. If the code has to differ from the design, that is allowed, but log it (see "Changing the design").

## Layout

```
src/main/java/ca/yorku/storyteller/
  ui/            StoryGUI, StoryCLI, StoryObserver          (MVC view, Observer)
  controller/    StoryController, CommandHistory, commands  (MVC controller, Command)
  model/         StorySession and everything it owns        (MVC model, Observer subject)
  agent/         StoryAgent facade, planner, memory, checker, input-mode strategies
  agent/tools/   AgentTool and the tools the model may call
  llm/           LLMClient, ClaudeAdapter, FakeLLMClient    (Adapter)
  persistence/   SessionRepository, JsonSessionRepository
src/main/resources/prompts/   prompt text loaded by the agent
src/test/java/...             JUnit tests, mirroring the main packages
samples/                      safe sample sessions (no keys, no personal data)
docs/                         running logs used to write the Stage 2 report
```

Put a new class in the package its Stage 1 diagram puts it in. Do not create new top-level packages without logging the change.

## Build and run

```
mvn compile                 # build
mvn test                    # run tests
mvn compile exec:java@gui   # launch the GUI
mvn compile exec:java@cli   # launch the CLI
```

The app reads `ANTHROPIC_API_KEY` from `.env` (copy `.env.example`). Run `mvn test` before every commit; a commit must build.

## Architecture rules

These come from the Stage 1 design and the course rubric. Do not break them.

1. **Front ends only display and forward.** `StoryGUI` and `StoryCLI` call `StoryController` and refresh through `StoryObserver`. No story logic in the UI.
2. **The agent never changes state directly.** It reads or changes lore, characters and the plot only through `AgentTool` calls that the app validates and executes via `ToolRegistry`.
3. **New lore needs the user's approval.** The agent raises a `LoreProposal`; only `StoryController.resolveProposal()` adds it to the `LoreBook`.
4. **Pinned lore wins.** `ConsistencyChecker` flags contradictions with pinned entries; it warns, it does not block the turn.
5. **The pivotal events stay hidden** from the user in both front ends.
6. **Only `ClaudeAdapter` knows about Anthropic.** Everything else uses `LLMClient`, `LLMRequest` and `LLMResponse`. Tests use `FakeLLMClient` and never make live, paid calls.
7. **Undo history is not saved.** `CommandHistory` starts empty after a load.
8. **No fakes in the delivered app.** The rubric rejects mockups, empty methods, hard-coded demo responses and classes that nothing uses. A `TODO` stub is fine while a feature is in progress, but a feature is not "done" until its path runs end to end.
9. **Six patterns are designed** (Adapter, Command, Facade, MVC, Observer, Strategy) and five are required. Keep every pattern visible in the code and solving the problem the report says it solves.

## Commits

Every commit follows [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/#summary):

```
<type>(<optional scope>): <description>

<body: what changed and why, wrapped at 72>

<footer: Refs: F04, BREAKING CHANGE: ..., Co-Authored-By: ...>
```

- **Types:** `feat` (new behaviour), `fix` (bug fix), `test` (tests only), `refactor` (no behaviour change), `perf`, `build` (Maven, dependencies), `docs`, `chore`, `ci`, `style`.
- **Scope:** the package (`agent`, `ui`, `llm`, `model`, `controller`, `persistence`, `tools`) or a feature ID. Example: `feat(agent): run the tool-use loop in StoryAgent.takeTurn`.
- **Description:** imperative, lower case, no full stop, under about 70 characters.
- **Footer:** add `Refs: F04` (or several IDs) whenever a commit works on a feature, so the report can map commits to features.

### Every commit must mean something

The course requires at least **5 meaningful code commits per week** (Oct 20 to 26, Oct 27 to Nov 2, Nov 3 to 9, Toronto time). Commits that are report-only, formatting-only, empty, or split artificially do not count. So:

- Each commit is one coherent, working step: it builds, its tests pass, and it touches implementation or test code under `src/`.
- Do not split one change into several tiny commits to raise the count, and do not pile a week of work into one commit either. A good size is one method group, one tool, one command, or one feature slice with its tests.
- No `wip`, `fix typo`, `update` or `misc` commits. Squash those locally before pushing.
- Update the docs that describe a change (`docs/feature-map.md`, `docs/changes-from-stage1.md`) **in the same commit** as the code, rather than as a separate docs commit.
- Push during the week the work is done; the commit date is what counts. After pushing, add the commit link to `docs/commit-log.md` under the right week (this may ride along in the next code commit).

## Adding or changing a feature

Follow these steps for every feature (F01 to F12) or substantial change:

1. **Read the design.** Find the feature in `docs/feature-map.md`, then its section in `Stage1_Report.docx` (section 2.2 for the spec, section 9 for the walk-through) and its sequence diagram in `figures/sequence-diagrams/`.
2. **Branch.** Work on a feature branch, for example `feat/f04-story-turn`, unless the session tells you which branch to use.
3. **Implement in thin slices.** Model and deterministic logic first, then the agent and tools, then the controller, then both front ends. Each slice is its own conventional commit with tests.
4. **Test.** Add JUnit tests next to the code. Agent and tool tests use `FakeLLMClient`. Deterministic features (F02, F03, F11) need tests that cover the validation and error paths too.
5. **Show it in both front ends.** A feature counts only when it is reachable from the GUI and the CLI.
6. **Document as you go**, in the same commits:
   - `docs/feature-map.md`: update the status, the classes and files, and the key methods actually built.
   - `docs/changes-from-stage1.md`: add an entry if anything differs from Stage 1 (see below).
   - `COST-TRACKING.md` and `docs/ai-tool-log.md`: add a row for the session (see below).
7. **Open a pull request** with a summary, the features it touches (`Refs: F04`), how it was tested, and any change from Stage 1.

### Changing the design

Any added, replaced, removed or substantially modified feature, class or pattern needs an entry in `docs/changes-from-stage1.md` with: the original ID, what was built, what changed and why, how the user need is still met, and the affected use cases, classes, methods and patterns. Write the entry when you make the change, not at the end. Also note when a UML diagram in `figures/` no longer matches the code, so it can be redrawn for the Stage 2 report.

## AI usage and cost tracking

The course requires a log of every AI tool used, with token and money cost. At the end of each AI-assisted session:

- Add a row to `COST-TRACKING.md`: date, tool, model, what the session did, approximate tokens and approximate cost. Estimates are fine; say how you estimated.
- Add an entry to `docs/ai-tool-log.md` describing what the tool was used for (features, classes, tasks) and one representative interaction.
- The app's own Claude Sonnet 5.5 usage during development (testing with a real key) also goes in `COST-TRACKING.md`.

## Secrets and private files

- Never commit API keys, `.env`, `.env.*` (other than `.env.example`) or `config.local.json`. Do not print keys in logs, test output, screenshots or sample files.
- If a key is ever committed, stop and tell the user: it must be rotated and removed from history.
- `.notes` in the repository root is the owner's private, gitignored notes file. Do not commit it, and do not edit it unless asked.
