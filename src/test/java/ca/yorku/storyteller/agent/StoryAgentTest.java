package ca.yorku.storyteller.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMMessage;
import ca.yorku.storyteller.llm.LLMRequest;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import ca.yorku.storyteller.llm.LLMToolResult;
import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link StoryAgent}. Agent tests use FakeLLMClient, never live API calls. */
class StoryAgentTest {

    private FakeLLMClient llm;
    private StoryAgent agent;
    private StorySession session;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        agent = StoryAgent.create(llm);
        session = new StorySession("s1");
        session.setScenario(new Scenario("Ashfall", "A courier carries a forbidden letter.", "grim",
                "A city under grey snow", "Mira waits at the gate."));
        session.getLoreBook().add(new LoreEntry(null, "Ashfall", "A city under grey snow.", List.of("city"), true));
        session.addCharacter(new Character("Mira", "A courier.", List.of()));
    }

    private static LLMToolCall call(String id, String tool, Map<String, String> args) {
        return new LLMToolCall(id, tool, args);
    }

    @Test
    void plainNarrationBecomesATurnWithNoLore() {
        llm.enqueue(LLMResponse.ofText("  The gate creaks open.  "));
        Turn t = agent.takeTurn(session, "open the gate", new DoModeStrategy());
        assertEquals(1, t.getNumber());
        assertEquals("Do", t.getModeName());
        assertEquals("open the gate", t.getUserInput());
        assertEquals("The gate creaks open.", t.getNarration());
        assertTrue(t.getUsedLoreIds().isEmpty());
        assertTrue(session.getTurns().isEmpty(), "takeTurn returns the turn; it does not add it");
    }

    @Test
    void toolCallsRunThroughTheRegistryAndTheLoreUsedIsRecorded() {
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(
                        call("a", "search_lore", Map.of("query", "ashfall")),
                        call("b", "get_character", Map.of("name", "Mira")))))
                .enqueue(LLMResponse.ofText("Snow settles on Mira's hood."));
        Turn t = agent.takeTurn(session, "look around", new DoModeStrategy());

        assertEquals("Snow settles on Mira's hood.", t.getNarration());
        assertEquals(List.of("L1"), t.getUsedLoreIds());
        LLMRequest second = llm.getRequests().get(1);
        List<LLMMessage> msgs = second.getMessages();
        assertEquals(3, msgs.size());
        assertEquals(LLMMessage.Role.ASSISTANT, msgs.get(1).getRole());
        List<LLMToolResult> results = msgs.get(2).getToolResults();
        assertEquals("a", results.get(0).toolCallId());
        assertTrue(results.get(0).content().contains("[canon] Ashfall"));
        assertFalse(results.get(1).error());
        assertTrue(results.get(1).content().startsWith("Mira: A courier."));
    }

    @Test
    void anInvalidToolCallIsReportedToTheModelAndTheTurnContinues() {
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(call("x", "burn_city", Map.of()))))
                .enqueue(LLMResponse.ofText("Nothing burns."));
        Turn t = agent.takeTurn(session, "burn it", new DoModeStrategy());
        assertEquals("Nothing burns.", t.getNarration());
        LLMToolResult r = llm.getRequests().get(1).getMessages().get(2).getToolResults().get(0);
        assertTrue(r.error());
        assertTrue(r.content().startsWith("There is no tool named burn_city"));
    }

    @Test
    void everyRequestCarriesTheToolsAndTheSystemPrompt() {
        llm.enqueue(LLMResponse.ofText("Fine."));
        agent.takeTurn(session, "wait", new DoModeStrategy());
        LLMRequest r = llm.getRequests().get(0);
        assertEquals(List.of("search_lore", "get_character"), r.getTools().stream().map(s -> s.name()).toList());
        assertTrue(r.getSystemPrompt().contains("Never reveal"));
    }

    @Test
    void thePromptHoldsTheScenarioCharactersHistoryAndFramedInput() {
        session.addTurn(new Turn(1, "Say", "Hello", "The guard nods.", List.of(), List.of()));
        llm.enqueue(LLMResponse.ofText("Fine."));
        agent.takeTurn(session, "Where is the guild?", new SayModeStrategy());
        String prompt = llm.getRequests().get(0).getMessages().get(0).getText();
        assertTrue(prompt.contains("Premise: A courier carries a forbidden letter."));
        assertTrue(prompt.contains("## Known characters (look them up with get_character)\nMira"));
        assertTrue(prompt.contains("Turn 1 (Say): Hello\nThe guard nods."));
        assertTrue(prompt.contains("## This turn (Say mode)\nThe user's character says, aloud: \"Where is the guild?\""));
        assertFalse(prompt.contains("Hidden plot guidance"), "no plan yet, so no guidance");
    }

    @Test
    void theNextPendingEventIsSentAsHiddenGuidance() {
        session.setPlot(new Plot("", List.of(new PivotalEvent("E1", "The letter is stolen."),
                new PivotalEvent("E2", "The guild burns."))));
        llm.enqueue(LLMResponse.ofText("Fine."));
        agent.takeTurn(session, "walk", new DoModeStrategy());
        String prompt = llm.getRequests().get(0).getMessages().get(0).getText();
        assertTrue(prompt.contains("## Hidden plot guidance (never reveal this)"));
        assertTrue(prompt.contains("Next pivotal event (E1): The letter is stolen."));
        assertFalse(prompt.contains("The guild burns."), "only the next event is sent");
    }

    @Test
    void aRefusalIsAnErrorAndNoTurnIsMade() {
        llm.enqueue(LLMResponse.ofRefusal());
        LLMException e = assertThrows(LLMException.class,
                () -> agent.takeTurn(session, "something", new StoryModeStrategy()));
        assertTrue(e.getMessage().contains("declined"));
    }

    @Test
    void aFailedModelCallPropagates() {
        llm.enqueueFailure(new LLMException("Could not reach Claude."));
        LLMException e = assertThrows(LLMException.class,
                () -> agent.takeTurn(session, "something", new DoModeStrategy()));
        assertEquals("Could not reach Claude.", e.getMessage());
    }

    @Test
    void emptyNarrationIsAnError() {
        llm.enqueue(LLMResponse.ofText("   "));
        assertThrows(LLMException.class, () -> agent.takeTurn(session, "x", new DoModeStrategy()));
    }

    @Test
    void aModelThatNeverStopsCallingToolsIsCutOff() {
        for (int i = 0; i < StoryAgent.MAX_TOOL_ROUNDS; i++) {
            llm.enqueue(LLMResponse.ofToolCalls("", List.of(call("c" + i, "search_lore", Map.of("query", "x")))));
        }
        LLMException e = assertThrows(LLMException.class, () -> agent.takeTurn(session, "x", new DoModeStrategy()));
        assertTrue(e.getMessage().contains("without finishing"));
        assertEquals(0, llm.remaining());
    }

    @Test
    void emptyInputIsRejectedWithoutCallingTheModel() {
        assertThrows(IllegalArgumentException.class, () -> agent.takeTurn(session, "  ", new DoModeStrategy()));
        assertTrue(llm.getRequests().isEmpty());
    }
}
