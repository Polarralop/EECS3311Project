# AI Interactive Storyteller

EECS 3311 agent project. An interactive storyteller for solo role-players: the agent tracks lore, characters and a hidden plan of pivotal events, so a long story stays consistent and can still surprise you. It runs as a GUI or a CLI over the same controller and uses Claude Sonnet 5.5.

> Status: Stage 2 in progress. The project structure is in place; features are being implemented. See [docs/feature-map.md](docs/feature-map.md).

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

Lore, characters and save/load work today; story turns are next. To try them with the sample story:

```
mkdir -p saves && cp samples/sample-ashfall.json saves/
mvn compile exec:java@cli
> load sample-ashfall
> lore list
> lore add            # answer the prompts: title, content, tags, pinned
> char show Mira
> save
```

In the GUI, use Story > Load... and pick `sample-ashfall`, then the Lore and Characters tabs. Type `help` in the CLI for every command.

## Project documents

- `Stage1_Report.docx` and `figures/`: the Stage 1 design
- `Stage2_Report.docx`: the Stage 2 implementation report, filled in as features land
- [AGENTS.md](AGENTS.md): how to work in this repo, including commit rules
- [docs/feature-map.md](docs/feature-map.md): features F01 to F12 and their status
- [docs/changes-from-stage1.md](docs/changes-from-stage1.md): changes from the Stage 1 design
- [docs/commit-log.md](docs/commit-log.md): weekly commit record
- [docs/ai-tool-log.md](docs/ai-tool-log.md) and [COST-TRACKING.md](COST-TRACKING.md): AI tool usage and cost
