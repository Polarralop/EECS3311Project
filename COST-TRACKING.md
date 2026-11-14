# Cost tracking

Approximate token use and cost of AI tools used to build this project, for the AI tool log in the Stage 2 report. Precision is not required; each row says how it was estimated. What each session did in detail is in [docs/ai-tool-log.md](docs/ai-tool-log.md).

Costs are API-equivalent: what the tokens would cost at Anthropic's published per-token rates. Sessions run through a Claude subscription are not billed per token, so the actual charge may be $0.

## Rates used

| Model | Input $/1M | Output $/1M | Cache read $/1M |
|---|---|---|---|
| Claude Opus 5.5 (development sessions) | 4.00 | 20.00 | 0.20 |
| Claude Fable 5.2 (one development session, 2026-11-09; taken as twice the Opus 5.5 rates, as Shane recalled) | 8.00 | 40.00 | 0.40 |
| Claude Sonnet 5.5 (the app's LLM) | 2.00 | 10.00 | 0.20 |

## Development sessions

| Date | Tool | Model | Task | Tokens (approx.) | Cost (approx.) | How estimated |
|---|---|---|---|---|---|---|
| 2026-10-20 | Claude Code (cloud) | Claude Opus 5.5 | Project scaffolding: Maven build, package and class skeletons, AGENTS.md, docs | ~150K context, ~1.5M input incl. cache reads, ~30K output | ~$1.50 | Session token counter; about 25 model calls re-reading a cached context averaging ~60K tokens |
| 2026-10-20 | Claude Code (cloud) | Claude Opus 5.5 | Stage 2 report scaffold (Stage2_Report.docx) and AGENTS.md report rules | ~90K context, ~1.2M input incl. cache reads, ~25K output | ~$1.00 | Session token counter; about 25 model calls re-reading a cached context averaging ~50K tokens |
| 2026-10-20 | Claude Code (cloud) | Claude Opus 5.5 | Week 1 implementation: model classes, F02 lore, F03 characters, F11 JSON save/load, ClaudeAdapter with tool use, StoryController, CLI and GUI tabs, 91 tests, docs and Stage 2 report | ~170K context, ~8.9M input incl. cache reads, ~90K output | ~$4.50 | Session token counter; about 85 model calls re-reading a cached context that grew from ~40K to ~170K (cache reads $0.20/1M, cache writes, output $20/1M). No subagents were used. |
| 2026-10-27 | Claude Code (cloud) | Claude Opus 5.5 | Week 2 implementation: F04 tool registry and tool-use loop, F05 pivotal-event planning, F01 scenario draft, F10 SubmitTurnCommand/EditTurnCommand with undo and retry, F07 lore proposals, GUI story tab and start form, 184 tests, redrawn class diagrams 02 to 08 and SD03/SD09, docs and Stage 2 report | ~200K context, ~24M input incl. cache reads, ~250K output | ~$10.50 | Session token counter; about 200 model calls re-reading a cached context that grew from ~30K to ~200K (cache reads $0.20/1M, cache writes, output $20/1M). No subagents were used. |
| 2026-11-03 | Claude Code (cloud) | Claude Opus 5.5 | Week 3 implementation: F06 UpdatePlotTool, F08 ConsistencyChecker, F09 summaries and load recap, F12 ending generation with the End story controls in both front ends, 236 tests, a small SVG generator for the UML redraws (figures/stage2/source), class diagrams 02, 03, 04, 06, 07 and sequence diagrams SD01, SD02, SD04 to SD10 redrawn, docs and Stage 2 report | ~200K context, ~32M input incl. cache reads, ~400K output | ~$16.00 | Session token counter; about 250 model calls re-reading a cached context of up to ~200K, one context compaction (cache reads $0.20/1M, cache writes, output $20/1M). No subagents were used. |
| 2026-11-09 | Claude Code (cloud) | Claude Opus 5.5 | GUI polish asked for by Shane: new turns and endings revealed gradually (TranscriptPacer, paced CLI printing), the app opens to a story picker or "No stories detected" with Create story..., stories listed by title, the Story tab named after the open story; 14 new tests (250); class diagrams 02, 03, 04 redrawn; docs and Stage 2 report | ~120K context, ~8M input incl. cache reads, ~120K output | ~$4.50 | Estimated from the session length: about 80 model calls re-reading a cached context growing to ~120K (cache reads $0.20/1M), plus output at $20/1M. No subagents. |
| 2026-11-09 | Claude Code (cloud) | Claude Fable 5.2 | GUI polish asked for by Shane: lore, characters, proposals and the lore used this turn moved into a collapsible sidebar on the right; a deep blue-purple palette with layered shades (StoryTheme); the recap as a "Story recap" card above the transcript; 8 new tests (260); class diagram 02 redrawn; docs and Stage 2 report | ~230K context, ~7M input incl. cache reads, ~85K output | ~$8.50 | Estimated from the session length: about 60 model calls re-reading a cached context growing to ~230K (cache reads $0.40/1M, cache writes), plus output at $40/1M. Fable 5.2 rates are taken as twice Opus 5.5's, as Shane recalled; not checked against a published price list. Includes the follow-up (labels, New button). No subagents (Opus subagents were allowed but not needed). |
| 2026-11-14 | Claude Code (cloud) | Claude Opus 5.5 | Stage 2 report completion: sections 1.1, 1.3 to 1.5, 5.2 to 5.4, 6.2 to 6.4 filled from the code and logs; Figure 1 (class overview) redrawn with every class; GUI screenshots added; period 3 commit table completed; fresh-clone and secrets checks; README updated; report renamed stage2-report and exported to PDF | ~140K context, ~10M input incl. cache reads, ~85K output | ~$4.50 | Estimated from the session length: about 80 model calls re-reading a cached context growing to ~140K (cache reads $0.20/1M, cache writes), plus output at $20/1M. No subagents. |

## App usage (Claude Sonnet 5.5 during development)

Real API calls the app makes while being developed or recorded. Tests use FakeLLMClient and cost nothing.

| Date | Purpose | Input tokens | Output tokens | Cost | Source |
|---|---|---|---|---|---|
