# AI Interactive Storyteller

EECS 3311 agent project. An interactive storyteller for solo role-players: the agent tracks lore, characters and a hidden plan of pivotal events, so a long story stays consistent and can still surprise you. It runs as a GUI or a CLI over the same controller and uses Claude Sonnet 5.5.

> Status: Stage 2 complete. All 12 features (F01 to F12) work from both the GUI and the CLI; see [docs/feature-map.md](docs/feature-map.md) and the Stage 2 report, [stage2-report.pdf](stage2-report.pdf).

## Prerequisites

- Java 21 (JDK)
- Maven 3.9 or newer
- An Anthropic API key

## Setup

```
git clone https://github.com/Polarralop/EECS3311Project.git
cd EECS3311Project
cp .env.example .env        # then put your key in ANTHROPIC_API_KEY
mvn compile                 # downloads dependencies and builds
```

`.env` is gitignored. Never commit a real key.

## Run

```
mvn compile exec:java@gui   # GUI
mvn compile exec:java@cli   # CLI
```

## Test

```
mvn test
```

Tests use a fake LLM client and make no paid API calls.

## Example usage

Lore, characters and save/load need no API key. To try them with the sample story:

```
mkdir -p saves && cp samples/sample-ashfall.json saves/
mvn compile exec:java@cli
> load sample-ashfall
> lore list
> lore add            # answer the prompts: title, content, tags, pinned
> char show Mira
> save
```

With a key in `.env`, play a story in the CLI:

```
> start               # describe an idea and let the storyteller draft the scenario, or write it yourself
> do knock on the gate
> say Who goes there?
> story The bells stop ringing all at once.
> proposals           # lore the storyteller proposed; approve <id> or reject <id>
> undo                # or retry, or edit
> end                 # write the ending
> save
```

In the GUI, the start screen lists saved stories (pick `sample-ashfall` and Open), or use Create story... to start one. The story is on the left (mode, input, Send, Undo, Retry, Edit, End story); lore, characters and proposals are in the sidebar on the right. Type `help` in the CLI for every command.

## Project documents

- `Stage1_Report.docx` and `figures/`: the Stage 1 design
- `stage2-report.docx` and `stage2-report.pdf`: the Stage 2 implementation report
- [AGENTS.md](AGENTS.md): how to work in this repo, including commit rules
- [docs/feature-map.md](docs/feature-map.md): features F01 to F12 and their status
- [docs/changes-from-stage1.md](docs/changes-from-stage1.md): changes from the Stage 1 design
- [docs/commit-log.md](docs/commit-log.md): weekly commit record
- [docs/ai-tool-log.md](docs/ai-tool-log.md) and [COST-TRACKING.md](COST-TRACKING.md): AI tool usage and cost
