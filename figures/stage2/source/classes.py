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
        "~ sidebar: JPanel",
        "~ sidebarTabs: JTabbedPane",
        "~ sidebarToggle: JButton",
        "~ RECAP_ROWS: int = 5 {static}",
        "~ CHARS_PER_SECOND: int = 300 {static}",
    ], methods=[
        "+ StoryGUI(controller: StoryController)",
        "+ open(): void",
        "~ refreshHome(): void",
        "~ openSelectedStory(): void",
        "~ isShowingHome(): boolean",
        "- buildStoryCard(): void",
        "- buildRecapCard(): Component",
        "- buildSidebar(): Component",
        "~ setSidebarOpen(open: boolean): void",
        "~ isSidebarOpen(): boolean",
        "~ openLoreEntry(id: String): void",
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
    theme = Box("StoryTheme", cli.x, pacer.y + pacer.h + 60, role="the GUI's palette: one blue-purple, layered shades", fields=[
        "~ BASE, INSET, SURFACE, SIDEBAR, RAISED: Color {static}",
        "~ LINE, SELECTION, ACCENT, ACCENT_SOFT: Color {static}",
        "~ TEXT, MUTED, DISABLED: Color {static}",
    ], methods=[
        "~ install(): void {static}",
        "~ primary(button: JButton): void {static}",
        "~ plain(button: JButton): void {static}",
        "~ quiet(button: JButton): void {static}",
        "~ sectionLabel(text: String): JLabel {static}",
        "~ layer(component: T, background: Color): T {static}",
    ])
    w = max(cli.x + cli.w, pacer.x + pacer.w, theme.x + theme.w) + 30
    obs.x = (w - obs.w) / 2
    y = pacer.y + pacer.h / 2
    ty = theme.y + theme.h / 2
    links = [
        link(vh(gui.anchor("t"), obs.anchor("b", 0.3), 270), "real"),
        link(vh(cli.anchor("t"), obs.anchor("b", 0.7), 270), "real"),
        link([(gui.x + gui.w, y), (pacer.x, y)], "comp"),
        link([(gui.x + gui.w, ty), (theme.x, ty)], "dep"),
    ]
    return class_diagram("ui", "View", [obs, gui, cli, pacer, theme], links, w,
                         max(gui.y + gui.h, pacer.y + pacer.h, theme.y + theme.h) + 40)


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



def overview():
    """Figure 1: every class in the code by name, grouped by package, with the main relationships.

    Members are left out; Figures 2 to 9 show them package by package.
    """
    from umlgen import PKG, PKG_LINE, MUTED, SANS, svg_doc, t

    def c(name, x, y, stereo=None, ref=False):
        return Box(name, x, y, stereo=stereo, compact=True, ref=ref)

    def poly(*pts, kind="assoc", **kw):
        return link(list(pts), kind, **kw)

    # ---- row 1: ui, controller, agent
    obs = c("StoryObserver", 50, 100, "interface")
    gui = c("StoryGUI", 300, 100)
    cli = c("StoryCLI", 300, 190)
    pacer = c("TranscriptPacer", 50, 320)
    theme = c("StoryTheme", 300, 320)

    ctl = c("StoryController", 640, 100)
    hist = c("CommandHistory", 640, 200)
    saved = c("SavedStory", 870, 170, "record")
    cmd = c("StoryCommand", 640, 290, "interface")
    sub = c("SubmitTurnCommand", 640, 400)
    edit = c("EditTurnCommand", 880, 400)

    agent = c("StoryAgent", 1170, 100)
    planner = c("PlotPlanner", 1440, 90)
    checker = c("ConsistencyChecker", 1440, 160)
    memory = c("MemoryManager", 1440, 230)
    ims = c("InputModeStrategy", 1210, 330, "interface")
    do = c("DoModeStrategy", 1500, 310)
    say = c("SayModeStrategy", 1500, 370)
    story = c("StoryModeStrategy", 1500, 430)

    # ---- row 2: persistence, model, agent.tools, llm
    repo = c("SessionRepository", 50, 640, "interface")
    jrepo = c("JsonSessionRepository", 50, 760)

    sess = c("StorySession", 400, 810)
    parts = [c(n, 640, 640 + i * 76) for i, n in enumerate(
        ["Scenario", "LoreBook", "Turn", "LoreProposal", "Plot", "Character"])]
    scen, book, turn, prop, plot, char = parts
    entry = c("LoreEntry", 880, book.y)
    pstat = c("ProposalStatus", 880, prop.y - 9, "enumeration")
    event = c("PivotalEvent", 880, plot.y)
    estat = c("EventStatus", 1110, plot.y - 9, "enumeration")
    rel = c("Relationship", 880, char.y)

    reg = c("ToolRegistry", 1330, 640)
    tcall = c("ToolCall", 1330, 740, "record")
    tool = c("AgentTool", 1330, 860, "interface")
    tres = c("ToolResult", 1330, 990, "record")
    tools = [c(n, 1580, 760 + i * 66) for i, n in
             enumerate(["SearchLoreTool", "GetCharacterTool", "ProposeLoreTool", "UpdatePlotTool"])]

    llm = c("LLMClient", 1920, 640, "interface")
    fake = c("FakeLLMClient", 1920, 770)
    claude = c("ClaudeAdapter", 1920, 850)
    api = c("AnthropicApi", 1920, 960, "external", ref=True)
    exc = c("LLMException", 1920, 1090)
    req = c("LLMRequest", 2190, 640)
    msg = c("LLMMessage", 2190, 730)
    spec = c("LLMToolSpec", 2190, 810, "record")
    resp = c("LLMResponse", 2190, 930)
    lcall = c("LLMToolCall", 2190, 1020, "record")
    lres = c("LLMToolResult", 2400, 721, "record")

    for group in ([do, say, story], tools, [planner, checker, memory], parts, [fake, claude, exc],
                  [entry, rel, event], [req, resp, msg, spec, lcall], [pacer, obs], [gui, cli]):
        w = max(b.w for b in group)
        for b in group:
            b.w = w

    boxes = [obs, gui, cli, pacer, theme, ctl, hist, saved, cmd, sub, edit, agent, planner, checker, memory,
             ims, do, say, story, repo, jrepo, sess, entry, pstat, event, estat, rel, reg, tcall, tool, tres,
             llm, fake, claude, api, exc, req, msg, spec, resp, lcall, lres] + parts + tools

    def frame(name, bs, pad=26):
        x0 = min(b.x for b in bs) - pad
        y0 = min(b.y for b in bs) - pad - 34
        x1 = max(b.x + b.w for b in bs) + pad
        y1 = max(b.y + b.h for b in bs) + pad
        return (f'<rect x="{x0}" y="{y0}" width="{x1 - x0}" height="{y1 - y0}" rx="10" fill="{PKG}" '
                f'stroke="{PKG_LINE}" stroke-width="1.6"/>' + t(x0 + 18, y0 + 28, name, 20, "bold"))

    frames = [
        frame("ui", [obs, gui, cli, pacer, theme]),
        frame("controller", [ctl, hist, saved, cmd, sub, edit]),
        frame("agent", [agent, planner, checker, memory, ims, do, say, story]),
        frame("persistence", [repo, jrepo]),
        frame("model", [sess, entry, pstat, event, estat, rel] + parts),
        frame("agent.tools", [reg, tcall, tool, tres] + tools),
        frame("llm", [llm, fake, claude, api, exc, req, msg, spec, resp, lcall, lres]),
    ]

    def A(b, side, f=0.5):
        return b.anchor(side, f)

    def X(b, f):
        return b.x + b.w * f

    def Y(b, f):
        return b.y + b.h * f

    links = []
    # ui: both views are observers; the GUI owns a pacer and uses the palette; both call the controller
    lane = obs.x + obs.w + 25
    links += [
        poly(A(gui, "l"), (lane, Y(gui, 0.5)), (lane, Y(obs, 0.35)), A(obs, "r", 0.35), kind="real"),
        poly(A(cli, "l"), (lane, Y(cli, 0.5)), (lane, Y(obs, 0.65)), A(obs, "r", 0.65), kind="real"),
        poly(A(gui, "r", 0.7), (470, Y(gui, 0.7)), (470, 280), (X(pacer, 0.7), 280), A(pacer, "t", 0.7),
             kind="comp"),
        poly(A(gui, "r", 0.9), (490, Y(gui, 0.9)), (490, 295), (X(theme, 0.6), 295), A(theme, "t", 0.6),
             kind="dep"),
        poly(A(gui, "r", 0.3), A(ctl, "l", 0.3), mult="1"),
        poly(A(cli, "r"), (580, Y(cli, 0.5)), (580, Y(ctl, 0.7)), A(ctl, "l", 0.7), mult="1"),
    ]
    # controller
    links += [
        poly(A(ctl, "b", 0.4), (X(ctl, 0.4), hist.y), kind="comp"),
        poly(A(hist, "b", 0.4), (X(hist, 0.4), cmd.y), kind="agg", mult="0..*"),
        poly(A(ctl, "r", 0.8), (840, Y(ctl, 0.8)), (840, Y(saved, 0.5)), A(saved, "l"), kind="dep"),
        poly(A(sub, "t", 0.5), (X(sub, 0.5), cmd.y + cmd.h), kind="real"),
        poly(A(edit, "t"), (X(edit, 0.5), 375), (X(cmd, 0.75), 375), A(cmd, "b", 0.75), kind="real"),
        poly(A(ctl, "r", 0.4), (agent.x, Y(ctl, 0.4)), mult="1"),
        poly(A(sub, "b", 0.85), (X(sub, 0.85), 480), (1140, 480), (1140, Y(agent, 0.85)), A(agent, "l", 0.85)),
    ]
    # agent
    for b, f in ((planner, 0.25), (checker, 0.5), (memory, 0.75)):
        links.append(poly(A(agent, "r", f), (1400, Y(agent, f)), (1400, Y(b, 0.5)), A(b, "l"), kind="comp"))
    links.append(poly(A(agent, "b", 0.6), (X(agent, 0.6), ims.y), mult="3"))
    bus = do.x - 30
    for s in (do, say, story):
        links.append(poly(A(s, "l"), (bus, Y(s, 0.5)), (bus, Y(ims, 0.5)), A(ims, "r"), kind="real"))
    # every agent class that calls the model holds an LLMClient
    llm_lane = 1830
    links.append(poly(A(agent, "t", 0.5), (X(agent, 0.5), 66), (llm_lane + 30, 66), (llm_lane + 30, 560),
                      (X(llm, 0.3), 560), A(llm, "t", 0.3), mult="1"))
    for b in (planner, checker, memory):
        links.append(poly(A(b, "r"), (llm_lane, Y(b, 0.5)), (llm_lane, 575), (X(llm, 0.6), 575), A(llm, "t", 0.6)))
    links.append(t(X(llm, 0.6) + 14, 612, "PlotPlanner, ConsistencyChecker and MemoryManager also hold one",
                   12, fill=MUTED, italic=True))
    # controller to persistence, model and tools
    links += [
        poly(A(ctl, "l", 0.9), (610, Y(ctl, 0.9)), (610, 520), (X(repo, 0.6), 520), A(repo, "t", 0.6), mult="1"),
        poly(A(ctl, "l", 0.95), (600, Y(ctl, 0.95)), (600, 535), (X(sess, 0.45), 535), A(sess, "t", 0.45),
             label="current", label_at=0.55),
        poly(A(sub, "b", 0.3), (X(sub, 0.3), 550), (X(sess, 0.65), 550), A(sess, "t", 0.65)),
        poly(A(edit, "b", 0.4), (X(edit, 0.4), 565), (X(sess, 0.85), 565), A(sess, "t", 0.85)),
        poly(A(agent, "b", 0.15), (X(agent, 0.15), 585), (X(reg, 0.5), 585), A(reg, "t"), mult="1"),
    ]
    # persistence
    links += [
        poly(A(jrepo, "t", 0.4), (X(jrepo, 0.4), repo.y + repo.h), kind="real"),
        poly(A(repo, "r", 0.7), (340, Y(repo, 0.7)), (340, Y(sess, 0.4)), A(sess, "l", 0.4), kind="dep",
             label="saves / loads", label_at=0.5),
        poly(A(sess, "l", 0.75), (320, Y(sess, 0.75)), (320, 880), (20, 880), (20, Y(obs, 0.5)), A(obs, "l"),
             kind="agg", label="notifies 0..* observers", label_at=0.45),
    ]
    # model
    spine = 600
    for b in parts:
        links.append(poly(A(sess, "r"), (spine, Y(sess, 0.5)), (spine, Y(b, 0.5)), A(b, "l"), kind="comp"))
    links += [
        poly(A(book, "r"), A(entry, "l"), kind="comp", mult="0..*"),
        poly(A(prop, "r"), (pstat.x, Y(prop, 0.5)), kind="dep"),
        poly(A(plot, "r"), A(event, "l"), kind="comp", mult="1..*"),
        poly(A(event, "r"), (estat.x, Y(event, 0.5)), kind="dep"),
        poly(A(char, "r"), A(rel, "l"), kind="comp", mult="0..*"),
        poly(A(prop, "r", 0.2), (850, Y(prop, 0.2)), (850, entry.y + entry.h + 14), (X(entry, 0.3), entry.y + entry.h + 14),
             A(entry, "b", 0.3), kind="dep", label="becomes", label_at=0.2),
    ]
    # agent.tools
    tbus = tools[0].x - 40
    links += [
        poly(A(reg, "b", 0.3), (X(reg, 0.3), tcall.y), kind="dep"),
        poly(A(reg, "r"), (X(reg, 1) + 60, Y(reg, 0.5)), (X(reg, 1) + 60, Y(tool, 0.25)), A(tool, "r", 0.25),
             kind="agg", mult="1..*"),
        poly(A(tool, "b", 0.4), (X(tool, 0.4), tres.y), kind="dep", label="returns"),
        poly(A(tool, "l", 0.6), (1290, Y(tool, 0.6)), (1290, 1150), (X(sess, 0.5), 1150), A(sess, "b"),
             kind="dep", label="reads / updates", label_at=0.5),
    ]
    for b in tools:
        links.append(poly(A(b, "l"), (tbus, Y(b, 0.5)), (tbus, Y(tool, 0.65)), A(tool, "r", 0.65), kind="real"))
    # llm
    rl = llm.x - 25
    links += [
        poly(A(fake, "l"), (rl, Y(fake, 0.5)), (rl, Y(llm, 0.6)), A(llm, "l", 0.6), kind="real"),
        poly(A(claude, "l"), (rl, Y(claude, 0.5)), (rl, Y(llm, 0.6)), A(llm, "l", 0.6), kind="real"),
        poly(A(claude, "b", 0.5), (X(claude, 0.5), api.y),
             label="anthropic-java SDK", label_at=0.5),
        poly(A(llm, "r", 0.3), A(req, "l", 0.3), kind="dep", label="takes"),
        poly(A(llm, "r", 0.75), (2150, Y(llm, 0.75)), (2150, Y(resp, 0.5)), A(resp, "l"), kind="dep",
             label="returns", label_at=0.6),
        poly(A(req, "l", 0.8), (2170, Y(req, 0.8)), (2170, Y(msg, 0.5)), A(msg, "l"), kind="comp"),
        poly(A(req, "l", 0.8), (2170, Y(req, 0.8)), (2170, Y(spec, 0.5)), A(spec, "l"), kind="comp"),
        poly(A(resp, "b", 0.5), (X(resp, 0.5), lcall.y), kind="comp"),
        poly(A(msg, "r"), (lres.x, Y(msg, 0.5)), kind="comp"),
    ]
    w = max(b.x + b.w for b in boxes) + 40
    h = 1200
    body = frames + links + [b.svg() for b in boxes]
    body.append(t(30, h - 14, "Every class in the code, by package; members are shown in Figures 2 to 9. "
                  "Only the main relationships are drawn.", 13, fill=MUTED, family=SANS))
    return svg_doc(w, h, body, "#ffffff")


ALL["01-overview"] = overview
