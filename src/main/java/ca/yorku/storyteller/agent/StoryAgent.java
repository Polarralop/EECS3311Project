package ca.yorku.storyteller.agent;

import ca.yorku.storyteller.agent.tools.GetCharacterTool;
import ca.yorku.storyteller.agent.tools.ProposeLoreTool;
import ca.yorku.storyteller.agent.tools.SearchLoreTool;
import ca.yorku.storyteller.agent.tools.ToolCall;
import ca.yorku.storyteller.agent.tools.ToolRegistry;
import ca.yorku.storyteller.agent.tools.ToolResult;
import ca.yorku.storyteller.llm.LLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMMessage;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import ca.yorku.storyteller.llm.LLMToolResult;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single front door to the agent subsystem: scenario, plot planning, turns, recaps and endings.
 * Callers never touch the planner, memory, tools or model directly; the order of the steps of a
 * turn lives here.
 *
 * <p>Features: F01 F04-F09 F11 F12.
 * <p>Patterns: Facade (Facade), Strategy (Context).
 * <p>TODO (Stage 1 design): recap(session) (F09), generateEnding() (F12).
 */
public class StoryAgent {

    /** Model calls allowed in one turn before the agent gives up, so a confused model cannot loop forever. */
    static final int MAX_TOOL_ROUNDS = 8;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LLMClient llm;
    private final ToolRegistry tools;
    private final PlotPlanner planner;
    private final MemoryManager memory;
    private final String systemPrompt;

    public StoryAgent(LLMClient llm, ToolRegistry tools, PlotPlanner planner, MemoryManager memory) {
        this.llm = llm;
        this.tools = tools;
        this.planner = planner;
        this.memory = memory;
        this.systemPrompt = loadPrompt("system");
    }

    /** An agent with the standard tools, planner and memory over the given model. */
    public static StoryAgent create(LLMClient llm) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new SearchLoreTool());
        registry.register(new GetCharacterTool());
        registry.register(new ProposeLoreTool());
        return new StoryAgent(llm, registry, new PlotPlanner(llm), new MemoryManager());
    }

    /**
     * Drafts a scenario from the user's short idea (F01). The draft is returned for the user to edit;
     * nothing is stored.
     *
     * @throws IllegalArgumentException if the idea is empty
     * @throws LLMException if the model declines, fails, or the draft has no premise or setting
     */
    public Scenario generateScenario(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Describe the story you want in a few words first.");
        }
        LLMResponse reply = llm.generate(new LLMRequest(loadPrompt("scenario"),
                List.of(LLMMessage.user(prompt.strip())), List.of(), 2_000));
        if (reply.isRefused()) {
            throw new LLMException("Claude declined to draft that story. Rephrase the idea or write the scenario yourself.");
        }
        JsonNode draft = jsonObjectIn(reply.getText());
        Scenario scenario = draft == null ? null : new Scenario(draft.path("title").asText(""),
                draft.path("premise").asText(""), draft.path("tone").asText(""),
                draft.path("setting").asText(""), draft.path("openingSituation").asText(""));
        if (scenario == null || scenario.getPremise().isEmpty() || scenario.getSetting().isEmpty()) {
            throw new LLMException("Claude's draft was incomplete. Try again or write the scenario yourself.");
        }
        return scenario;
    }

    /**
     * Plans the hidden pivotal events for a new story (F05).
     *
     * @throws LLMException if the model declines, fails or returns no events
     */
    public Plot planPlot(Scenario scenario, String basis) {
        return planner.createPlot(scenario, basis);
    }

    /**
     * Narrates one turn: frames the input with the mode, adds the story so far and the hidden plot
     * guidance, then runs the tool-use loop until the model answers with narration. The turn is
     * returned, not added; the caller (SubmitTurnCommand) adds it so it can be undone.
     *
     * @throws IllegalArgumentException if the input is empty
     * @throws LLMException if the model declines, fails or never finishes the narration
     */
    public Turn takeTurn(StorySession session, String input, InputModeStrategy mode) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Type something for the turn first.");
        }
        String prompt = buildTurnPrompt(session, input, mode);
        LLMRequest request = new LLMRequest(systemPrompt, List.of(LLMMessage.user(prompt)), tools.schemas());
        Set<String> usedLore = new LinkedHashSet<>();
        LLMResponse reply = runToolLoop(request, session, usedLore);
        return new Turn(session.nextTurnNumber(), mode.getName(), input.strip(), reply.getText().strip(),
                List.copyOf(usedLore), List.of());
    }

    /**
     * Calls the model and, while it asks for tools, runs each call through the registry and sends the
     * results back. Lore ids returned by successful lookups are collected into {@code usedLore}.
     */
    private LLMResponse runToolLoop(LLMRequest request, StorySession session, Set<String> usedLore) {
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            LLMResponse reply = llm.generate(request);
            if (reply.isRefused()) {
                throw new LLMException("Claude declined to continue the story with that input. Try rephrasing it.");
            }
            if (!reply.hasToolCalls()) {
                if (reply.getText().isBlank()) {
                    throw new LLMException("Claude returned no narration. Try again.");
                }
                return reply;
            }
            List<LLMToolResult> results = new ArrayList<>();
            for (LLMToolCall c : reply.getToolCalls()) {
                ToolResult result = tools.execute(new ToolCall(c.id(), c.name(), c.arguments()), session);
                if (result.success()) {
                    usedLore.addAll(result.loreIds());
                }
                results.add(new LLMToolResult(c.id(), result.content(), !result.success()));
            }
            request = request.withMessages(reply.getAssistantMessage(), LLMMessage.toolResults(results));
        }
        throw new LLMException("Claude kept looking things up without finishing the turn. Try again.");
    }

    private String buildTurnPrompt(StorySession session, String input, InputModeStrategy mode) {
        StringBuilder sb = new StringBuilder();
        if (session.getScenario() != null) {
            sb.append("## Scenario\n").append(session.getScenario().describe()).append("\n\n");
        }
        if (!session.getCharacters().isEmpty()) {
            sb.append("## Known characters (look them up with get_character)\n")
                    .append(String.join(", ", session.getCharacters().stream().map(Character::getName).toList()))
                    .append("\n\n");
        }
        String context = memory.buildContext(session);
        sb.append("## Story so far\n").append(context.isEmpty() ? "This is the first turn." : context).append("\n\n");
        String guidance = planner.planStep(session, input);
        if (!guidance.isEmpty()) {
            sb.append("## Hidden plot guidance (never reveal this)\n").append(guidance).append("\n\n");
        }
        sb.append("## This turn (").append(mode.getName()).append(" mode)\n").append(mode.frame(input));
        return sb.toString();
    }

    /**
     * The JSON object in a reply, ignoring any text or code fence around it; null if there is none or it
     * cannot be read.
     */
    static JsonNode jsonObjectIn(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(text.substring(start, end + 1));
            return node.isObject() ? node : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Reads src/main/resources/prompts/{name}.txt. */
    static String loadPrompt(String name) {
        try (InputStream in = StoryAgent.class.getResourceAsStream("/prompts/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("The prompt file prompts/" + name + ".txt is missing.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
