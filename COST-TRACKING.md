# Cost tracking

Approximate token use and cost of AI tools used to build this project, for the AI tool log in the Stage 2 report. Precision is not required; each row says how it was estimated. What each session did in detail is in [docs/ai-tool-log.md](docs/ai-tool-log.md).

Costs are API-equivalent: what the tokens would cost at Anthropic's published per-token rates. Sessions run through a Claude subscription are not billed per token, so the actual charge may be $0.

## Rates used

| Model | Input $/1M | Output $/1M | Cache read $/1M |
|---|---|---|---|
| Claude Opus 5.5 (development sessions) | 4.00 | 20.00 | 0.20 |
| Claude Sonnet 5.5 (the app's LLM) | 2.00 | 10.00 | 0.20 |

## Development sessions

| Date | Tool | Model | Task | Tokens (approx.) | Cost (approx.) | How estimated |
|---|---|---|---|---|---|---|
| 2026-10-20 | Claude Code (cloud) | Claude Opus 5.5 | Project scaffolding: Maven build, package and class skeletons, AGENTS.md, docs | ~150K context, ~1.5M input incl. cache reads, ~30K output | ~$1.50 | Session token counter; about 25 model calls re-reading a cached context averaging ~60K tokens |
| 2026-10-20 | Claude Code (cloud) | Claude Opus 5.5 | Stage 2 report scaffold (Stage2_Report.docx) and AGENTS.md report rules | ~90K context, ~1.2M input incl. cache reads, ~25K output | ~$1.00 | Session token counter; about 25 model calls re-reading a cached context averaging ~50K tokens |
| 2026-10-20 | Claude Code (cloud) | Claude Opus 5.5 | Week 1 implementation: model classes, F02 lore, F03 characters, F11 JSON save/load, ClaudeAdapter with tool use, StoryController, CLI and GUI tabs, 91 tests, docs and Stage 2 report | ~170K context, ~8.9M input incl. cache reads, ~90K output | ~$4.50 | Session token counter; about 85 model calls re-reading a cached context that grew from ~40K to ~170K (cache reads $0.20/1M, cache writes, output $20/1M). No subagents were used. |
| 2026-10-27 | Claude Code (cloud) | Claude Opus 5.5 | Week 2 implementation: F04 tool registry and tool-use loop, F05 pivotal-event planning, F01 scenario draft, F10 SubmitTurnCommand/EditTurnCommand with undo and retry, F07 lore proposals, GUI story tab and start form, 184 tests, redrawn class diagrams 02 to 08 and SD03/SD09, docs and Stage 2 report | ~200K context, ~24M input incl. cache reads, ~250K output | ~$10.50 | Session token counter; about 200 model calls re-reading a cached context that grew from ~30K to ~200K (cache reads $0.20/1M, cache writes, output $20/1M). No subagents were used. |

## App usage (Claude Sonnet 5.5 during development)

Real API calls the app makes while being developed or recorded. Tests use FakeLLMClient and cost nothing.

| Date | Purpose | Input tokens | Output tokens | Cost | Source |
|---|---|---|---|---|---|
