"""Stage 2 class-diagram redraws (one per package that changed). See umlgen.py and build.py.

Each function returns the SVG for one figure; the names match the Stage 1 file names.
"""
from umlgen import Box, class_diagram, link


def hv(a, b, x=None):
    """Horizontal, then vertical, then horizontal: a and b are (x, y); x is the bend column."""
    x = (a[0] + b[0]) / 2 if x is None else x
    return [a, (x, a[1]), (x, b[1]), b]


def vh(a, b, y=None):
    """Vertical, then horizontal, then vertical: y is the bend row."""
    y = (a[1] + b[1]) / 2 if y is None else y
    return [a, (a[0], y), (b[0], y), b]


def ui():
    obs = Box("StoryObserver", 0, 90, methods=["+ onStoryChanged(session: StorySession): void"],
              stereo="interface", role="Observer: observer")
    gui = Box("StoryGUI", 30, 330, role="MVC: view · Observer: concrete observer", fields=[
        "- controller: StoryController",
        "- busy: boolean",
        "~ recap: JTextArea",
        "- recapSessionId: String",
        "~ pacer: TranscriptPacer",
        "- shownSessionId: String",
        "~ CHARS_PER_SECOND: int = 300 {static}",
    ], methods=[
        "+ StoryGUI(controller: StoryController)",
        "+ open(): void",
        "~ refreshHome(): void",
        "~ openSelectedStory(): void",
        "~ isShowingHome(): boolean",
        "- refreshStory(session: StorySession): void",
        "+ onStoryChanged(session: StorySession): void",
        "+ showError(message: String): void",
        "~ submitInput(): void",
        "~ undoTurn(): void",
        "~ retryTurn(): void",
        "~ editTurn(): void",
        "~ endStory(): void",
        "- offerEnding(): void",
        "- writeEnding(): void",
        "~ generateDraft(): void",
        "~ startStory(): void",
        "~ resolveSelected(approved: boolean): void",
        "~ saveLoreEntry(): void",
        "~ saveCharacter(): void",
        "~ save(): void",
        "~ load(): void",
        "~ showRecap(text: String): void",
        "# chooseSession(stories: List<StoryChoice>): String",
        "# confirm(question: String): boolean",
        "# askNarration(current: String): String",
        "# openStartForm(): void",
        "# runInBackground(busyText, work, onSuccess, onFailure): void",
    ])
    cli = Box("StoryCLI", gui.x + gui.w + 50, 330, role="MVC: view · Observer: concrete observer", fields=[
        "- controller: StoryController",
        "- charsPerSecond: int",
    ], methods=[
        "+ StoryCLI(controller, in: BufferedReader, out: PrintStream)",
        "+ StoryCLI(controller, in, out, charsPerSecond: int)",
        "+ run(): void",
        "+ handleCommand(line: String): void",
        "+ printError(message: String): void",
        "+ onStoryChanged(session: StorySession): void",
        "- start(): void",
        "- turn(input: String, mode: String): void",
        "- retry(): void",
        "- edit(): void",
        "- offerEnding(): void",
        "- end(): void",
        "- printStartScreen(): void",
        "- printEnding(ending: String, paced: boolean): void",
        "- printTurn(turn: Turn, paced: boolean): void",
        "- printText(text: String, paced: boolean): void",
        "- printProposals(): void",
        "- load(id: String): void",
        "~ createController(): StoryController {static}",
    ])
    pacer = Box("TranscriptPacer", cli.x, cli.y + cli.h + 70, role="paces new text on screen only", fields=[
        "- target: String",
        "- shown: int",
        "~ TICK_MS: int = 20 {static}",
        "~ MAX_REVEAL_MS: int = 10000 {static}",
    ], methods=[
        "~ TranscriptPacer(area: JTextArea, charsPerSecond: int)",
        "~ show(text: String, pace: boolean): void",
        "~ tick(): void",
        "~ skip(): void",
        "~ isRevealing(): boolean",
    ])
    w = max(cli.x + cli.w, pacer.x + pacer.w) + 30
    obs.x = (w - obs.w) / 2
    y = pacer.y + pacer.h / 2
    links = [
        link(vh(gui.anchor("t"), obs.anchor("b", 0.3), 270), "real"),
        link(vh(cli.anchor("t"), obs.anchor("b", 0.7), 270), "real"),
        link([(gui.x + gui.w, y), (pacer.x, y)], "comp"),
    ]
    return class_diagram("ui", "View", [obs, gui, cli, pacer], links, w,
                         max(gui.y + gui.h, pacer.y + pacer.h) + 40)


def controller():
    sc = Box("StoryController", 30, 90, role="MVC: controller · Command: client", fields=[
        "- modes: Map<String, InputModeStrategy>",
        "- session: StorySession",
        "- endingSuggested: boolean",
    ], methods=[
        "+ StoryController(repository: SessionRepository, agent: StoryAgent)",
        "+ addObserver(observer: StoryObserver): void",
        "+ newSession(): StorySession",
        "+ generateScenario(prompt: String): Scenario",
        "+ startStory(scenario: Scenario, plotBasis: String): void",
        "+ isStarted(): boolean",
        "+ modeNames(): List<String>",
        "+ submitTurn(input: String, modeName: String): Turn",
        "+ undoTurn(): void",
        "+ retryTurn(): Turn",
        "+ editTurn(newNarration: String): void",
        "+ canUndo(): boolean",
        "+ canEnd(): boolean",
        "+ isReadyToEnd(): boolean",
        "+ isEndingSuggested(): boolean",
        "+ isEnded(): boolean",
        "+ requestEnding(): String",
        "+ listPendingProposals(): List<LoreProposal>",
        "+ resolveProposal(id: String, approved: boolean): void",
        "+ saveLoreEntry(entry: LoreEntry): void",
        "+ removeLoreEntry(id: String): void",
        "+ searchLore(query: String): List<LoreEntry>",
        "+ getLoreEntry(id: String): LoreEntry",
        "+ saveCharacter(originalName: String, character: Character): void",
        "+ removeCharacter(name: String): void",
        "+ getCharacter(name: String): Character",
        "+ listCharacters(): List<Character>",
        "+ addRelationship(fromName, targetName, type, notes: String): void",
        "+ removeRelationship(fromName: String, targetName: String): void",
        "+ saveSession(): void",
        "+ loadSession(id: String): String",
        "+ listSessions(): List<String>",
        "+ listSavedStories(): List<SavedStory>",
    ])
    hist = Box("CommandHistory", sc.x + sc.w + 70, 90, role="Command: invoker", fields=[
        "- done: Deque<StoryCommand>",
    ], methods=[
        "+ run(command: StoryCommand): void",
        "+ undoLast(): StoryCommand",
        "+ canUndo(): boolean",
        "+ latest(type: Class<T>): Optional<T>",
        "+ peekLast(): Optional<StoryCommand>",
        "+ clear(): void",
    ])
    cmd = Box("StoryCommand", 0, 440, stereo="interface", role="Command: command",
              methods=["+ execute(): void", "+ undo(): void"])
    sub = Box("SubmitTurnCommand", sc.x + sc.w + 40, 690, role="Command: concrete command", fields=[
        "- session: StorySession",
        "- agent: StoryAgent",
        "- input: String",
        "- mode: InputModeStrategy",
        "- plotBefore: Plot",
        "- result: Turn",
    ], methods=[
        "+ execute(): void",
        "+ undo(): void",
        "+ getInput(): String",
        "+ getMode(): InputModeStrategy",
        "+ getResult(): Turn",
    ])
    edit = Box("EditTurnCommand", sub.x + sub.w + 40, 690, role="Command: concrete command", fields=[
        "- session: StorySession",
        "- newNarration: String",
        "- turn: Turn",
        "- oldNarration: String",
        "- oldWarnings: List<String>",
    ], methods=[
        "+ execute(): void",
        "+ undo(): void",
        "+ getOldNarration(): String",
    ])
    saved = Box("SavedStory", sub.x, sub.y + sub.h + 60, stereo="record", fields=[
        "+ id: String",
        "+ title: String",
        "+ savedAt: Instant",
    ])
    hist.x = max(hist.x, (sub.x + edit.x + edit.w) / 2 - hist.w / 2)
    cmd.x = hist.x + hist.w / 2 - cmd.w / 2
    w = edit.x + edit.w + 30
    sy = saved.y + saved.h / 2
    links = [
        link([(sc.x + sc.w, min(sy, sc.y + sc.h - 20)), (sc.x + sc.w + 20, min(sy, sc.y + sc.h - 20)),
              (sc.x + sc.w + 20, sy), (saved.x, sy)], "dep", label="lists"),
        link(hv((sc.x + sc.w, sc.y + 50), hist.anchor("l", 0.6)), "comp", mult="1"),
        link([hist.anchor("b"), (hist.x + hist.w / 2, cmd.y)], "agg", mult="0..*"),
        link(vh(sub.anchor("t"), cmd.anchor("b", 0.35), sub.y - 40), "real"),
        link(vh(edit.anchor("t"), cmd.anchor("b", 0.65), sub.y - 40), "real"),
    ]
    h = max(sc.y + sc.h, saved.y + saved.h) + 40
    return class_diagram("controller", "Controller", [sc, hist, cmd, sub, edit, saved], links, w, h)


def model_story_and_lore():
    ss = Box("StorySession", 0, 90, role="MVC: model · Observer: subject", fields=[
        "- id: String",
        "- summary: String",
        "- summarizedUpTo: int",
        "- nextProposalNumber: int",
        "- ending: String",
        "- observers: List<StoryObserver> {transient}",
        "+ UNTITLED: String = \"Untitled story\" {static}",
    ], methods=[
        "+ addTurn(turn: Turn): void",
        "+ removeLastTurn(): Turn",
        "+ lastTurn(): Optional<Turn>",
        "+ nextTurnNumber(): int",
        "+ addProposal(proposal: LoreProposal): void",
        "+ approveProposal(id: String): LoreEntry",
        "+ rejectProposal(id: String): void",
        "+ getPendingProposals(): List<LoreProposal>",
        "+ setSummary(summary: String, upTo: int): void",
        "+ getEnding(): String",
        "+ setEnding(ending: String): void",
        "+ isEnded(): boolean",
        "+ addCharacter(character: Character): void",
        "+ updateCharacter(originalName: String, c: Character): void",
        "+ removeCharacter(name: String): void",
        "+ addRelationship(fromName: String, r: Relationship): void",
        "+ findCharacter(name: String): Optional<Character>",
        "+ attach(observer: StoryObserver): void",
        "+ detach(observer: StoryObserver): void",
        "+ notifyObservers(): void",
        "+ getTitle(): String",
    ])
    row = ss.y + ss.h + 110
    turn = Box("Turn", 30, row, fields=[
        "- number: int",
        "- modeName: String",
        "- userInput: String",
        "- narration: String",
        "- usedLoreIds: List<String>",
        "- warnings: List<String>",
    ], methods=[
        "+ setNarration(narration: String): void",
        "+ setWarnings(warnings: List<String>): void",
    ])
    scen = Box("Scenario", turn.x + turn.w + 30, row, fields=[
        "- title: String",
        "- premise: String",
        "- tone: String",
        "- setting: String",
        "- openingSituation: String",
    ], methods=["+ readyToStart(): Scenario", "+ describe(): String"])
    book = Box("LoreBook", scen.x + scen.w + 30, row, fields=["- nextNumber: int"], methods=[
        "+ add(entry: LoreEntry): void",
        "+ update(entry: LoreEntry): void",
        "+ remove(id: String): void",
        "+ search(query: String): List<LoreEntry>",
        "+ getPinned(): List<LoreEntry>",
        "+ find(id: String): Optional<LoreEntry>",
        "+ getAll(): List<LoreEntry>",
        "+ size(): int",
    ])
    prop = Box("LoreProposal", book.x + book.w + 30, row, fields=[
        "- id: String",
        "- title: String",
        "- content: String",
        "- status: ProposalStatus",
    ], methods=["+ approve(): LoreEntry", "+ reject(): void", "~ reopen(): void"])
    w = prop.x + prop.w + 30
    ss.x = (w - ss.w) / 2
    entry = Box("LoreEntry", 0, book.y + book.h + 110, fields=[
        "- id: String",
        "- title: String",
        "- content: String",
        "- tags: List<String>",
        "- pinned: boolean",
    ], methods=["+ matches(query: String): boolean", "+ copy(): LoreEntry"])
    entry.x = book.x + book.w / 2 - entry.w / 2
    status = Box("ProposalStatus", 0, entry.y, stereo="enumeration", fields=["PENDING", "APPROVED", "REJECTED"])
    status.x = prop.x + prop.w / 2 - status.w / 2
    bus1, bus2 = ss.y + ss.h + 50, ss.y + ss.h + 70
    links = [
        link(vh(ss.anchor("b", 0.2), turn.anchor("t"), bus1), "comp", mult="0..*"),
        link(vh(ss.anchor("b", 0.4), scen.anchor("t"), bus2), "comp", mult="1"),
        link(vh(ss.anchor("b", 0.6), book.anchor("t"), bus2), "comp", mult="1"),
        link(vh(ss.anchor("b", 0.8), prop.anchor("t"), bus1), "comp", mult="0..*"),
        link([book.anchor("b"), (book.x + book.w / 2, entry.y)], "comp", mult="0..*"),
        link(vh(prop.anchor("b", 0.3), entry.anchor("t", 0.85), entry.y - 60), "dep", label="creates", label_at=0.5),
        link([(status.x + status.w / 2, prop.y + prop.h), status.anchor("t")], "dep"),
    ]
    h = entry.y + entry.h + 40
    return class_diagram("model (1 of 2)", "story, turns and lore",
                         [ss, turn, scen, book, prop, entry, status], links, w, h)


def agent():
    sa = Box("StoryAgent", 30, 90, role="Facade: facade · Strategy: context", fields=[
        "- MAX_TOOL_ROUNDS: int = 8",
        "- systemPrompt: String",
    ], methods=[
        "+ StoryAgent(llm, tools, planner, memory, checker)",
        "+ create(llm: LLMClient): StoryAgent {static}",
        "+ generateScenario(prompt: String): Scenario",
        "+ planPlot(scenario: Scenario, basis: String): Plot",
        "+ takeTurn(session, input, mode: InputModeStrategy): Turn",
        "+ readyToEnd(session: StorySession): boolean",
        "+ recap(session: StorySession): String",
        "+ generateEnding(session: StorySession): String",
        "- runToolLoop(request, session, usedLore: Set<String>): LLMResponse",
        "- buildTurnPrompt(session, input, mode): String",
        "~ jsonObjectIn(text: String): JsonNode {static}",
        "~ loadPrompt(name: String): String {static}",
    ])
    col = sa.x + sa.w + 90
    plan = Box("PlotPlanner", col, 90, role="Facade: subsystem", fields=["- MAX_EVENTS: int = 8"], methods=[
        "+ PlotPlanner(llm: LLMClient)",
        "+ createPlot(scenario: Scenario, basis: String): Plot",
        "+ planStep(session: StorySession, input: String): String",
        "+ readyToEnd(plot: Plot): boolean",
        "~ parseEvents(text: String): List<String> {static}",
    ])
    mem = Box("MemoryManager", col, plan.y + plan.h + 30, role="Facade: subsystem", fields=[
        "- recentTurnLimit: int = 8",
        "- summarizerPrompt: String",
        "- recapPrompt: String",
    ], methods=[
        "+ MemoryManager(llm: LLMClient, recentTurnLimit: int)",
        "+ buildContext(session: StorySession): String",
        "+ summarizeIfNeeded(session: StorySession): boolean",
        "+ recap(session: StorySession): String",
        "+ getRecentTurnLimit(): int",
    ])
    chk = Box("ConsistencyChecker", col, mem.y + mem.h + 30, role="Facade: subsystem", fields=[
        "~ MAX_WARNINGS: int = 5",
        "+ NOT_CHECKED: String {static}",
        "- prompt: String",
    ], methods=[
        "+ ConsistencyChecker(llm: LLMClient)",
        "+ check(narration, session, usedLoreIds): List<String>",
        "~ parseWarnings(text, entries): List<String> {static}",
    ])
    reg = Box("ToolRegistry", 30, sa.y + sa.h + 70, stereo="agent.tools", ref=True)
    llm = Box("LLMClient", reg.x + reg.w + 50, reg.y, stereo="llm", ref=True)
    ims = Box("InputModeStrategy", 0, reg.y + reg.h + 70, stereo="interface", role="Strategy: strategy",
              methods=["+ getName(): String", "+ frame(input: String): String"])
    srow = max(ims.y + ims.h, chk.y + chk.h) + 60
    strategies, x = [], 30
    for name in ("DoModeStrategy", "SayModeStrategy", "StoryModeStrategy"):
        b = Box(name, x, srow, role="Strategy: concrete strategy",
                methods=["+ getName(): String", "+ frame(input: String): String"])
        strategies.append(b)
        x += b.w + 40
    ims.x = strategies[1].x + strategies[1].w / 2 - ims.w / 2
    right = sa.x + sa.w
    links = [
        link([(right, plan.y + 60), (col, plan.y + 60)], "comp", mult="1"),
        link([(right, mem.y + 60), (col, mem.y + 60)], "comp", mult="1"),
        link(hv((right, sa.y + sa.h - 20), chk.anchor("l", 0.3), col - 40), "comp", mult="1"),
        link([(reg.x + reg.w / 2, sa.y + sa.h), reg.anchor("t")], "assoc", mult="1"),
        link([(llm.x + llm.w / 2, sa.y + sa.h), llm.anchor("t")], "assoc", mult="1"),
        link([(ims.x + ims.w / 2, sa.y + sa.h), ims.anchor("t")], "dep", label="uses", label_at=0.4),
    ]
    for b in strategies:
        links.append(link(vh(b.anchor("t"), ims.anchor("b"), srow - 30), "real"))
    w = max(col + max(plan.w, mem.w, chk.w), x - 40) + 30
    h = srow + strategies[0].h + 40
    return class_diagram("agent", "decides what happens next",
                         [sa, plan, mem, chk, reg, llm, ims] + strategies, links, w, h)


def agent_tools():
    reg = Box("ToolRegistry", 30, 330, role="Facade: subsystem", fields=[
        "- tools: Map<String, AgentTool>",
        "- required: Map<String, List<String>>",
    ], methods=[
        "+ register(tool: AgentTool): void",
        "+ schemas(): List<LLMToolSpec>",
        "+ execute(call: ToolCall, session: StorySession): ToolResult",
    ])
    mid = reg.x + reg.w + 90
    call = Box("ToolCall", mid, 90, stereo="record", fields=[
        "- id: String",
        "- toolName: String",
        "- arguments: Map<String,String>",
    ], methods=["+ argument(name: String): String"])
    tool = Box("AgentTool", mid, 330, stereo="interface", methods=[
        "+ getName(): String",
        "+ getDescription(): String",
        "+ getSchema(): String",
        "+ execute(args: Map<String,String>, session): ToolResult",
    ])
    res = Box("ToolResult", mid, tool.y + tool.h + 90, stereo="record", fields=[
        "- success: boolean",
        "- content: String",
        "- loreIds: List<String>",
    ], methods=[
        "+ ok(content: String): ToolResult {static}",
        "+ ok(content, loreIds): ToolResult {static}",
        "+ error(reason: String): ToolResult {static}",
    ])
    col = tool.x + tool.w + 90
    execute = "+ execute(args, session): ToolResult"
    specs = [
        ("SearchLoreTool", ["~ MAX_RESULTS: int = 5"], [execute]),
        ("GetCharacterTool", [], [execute]),
        ("ProposeLoreTool", [], [execute]),
        ("UpdatePlotTool", ["+ MAX_NEW_EVENTS: int = 6 {static}"],
         [execute, "~ parseEvents(raw: String): List<String> {static}"]),
    ]
    tools, y = [], 90
    for name, fields, methods in specs:
        b = Box(name, col, y, fields=fields, methods=methods)
        tools.append(b)
        y += b.h + 30
    tw = max(b.w for b in tools)
    for b in tools:
        b.w = tw
    ay = tool.y + tool.h / 2
    bus = tool.x + tool.w + 45
    links = [
        link([(reg.x + reg.w, ay + 30), (tool.x, ay + 30)], "agg", mult="1..*"),
        link(hv((reg.x + reg.w * 0.8, reg.y), call.anchor("l"), None)[0:1]
             + [(reg.x + reg.w * 0.8, call.y + call.h / 2), call.anchor("l")], "dep"),
        link([(tool.x + tool.w / 2, tool.y + tool.h), (tool.x + tool.w / 2, res.y)], "dep", label="returns"),
    ]
    for b in tools:
        links.append(link([b.anchor("l"), (bus, b.y + b.h / 2), (bus, ay), (tool.x + tool.w, ay)], "real"))
    w = col + tw + 30
    h = max(res.y + res.h, y - 30) + 40
    return class_diagram("agent.tools", "the only way the model touches state",
                         [reg, call, tool, res] + tools, links, w, h)


def persistence():
    repo = Box("SessionRepository", 30, 90, stereo="interface", methods=[
        "+ save(session: StorySession): void",
        "+ load(id: String): StorySession",
        "+ listSessions(): List<String>",
        "+ lastSaved(id: String): Instant",
    ])
    js = Box("JsonSessionRepository", repo.x + repo.w + 110, 90, fields=[
        "- directory: Path",
        "- mapper: ObjectMapper",
    ], methods=[
        "+ JsonSessionRepository(directory: Path)",
        "+ save(session: StorySession): void",
        "+ load(id: String): StorySession",
        "+ listSessions(): List<String>",
        "+ lastSaved(id: String): Instant",
    ])
    y = repo.y + repo.h / 2
    links = [link([(js.x, y), (repo.x + repo.w, y)], "real")]
    return class_diagram("persistence", "saved sessions", [repo, js], links, js.x + js.w + 30,
                         max(repo.y + repo.h, js.y + js.h) + 40)


ALL = {
    "02-ui": ui,
    "03-controller": controller,
    "04-model-story-and-lore": model_story_and_lore,
    "06-agent": agent,
    "07-agent-tools": agent_tools,
    "09-persistence": persistence,
}
