package ca.yorku.storyteller.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link ConsistencyChecker} and its place at the end of a turn (F08), with FakeLLMClient. */
class ConsistencyCheckerTest {

    private FakeLLMClient llm;
    private ConsistencyChecker checker;
    private StorySession session;

    @BeforeEach
    void setUp() {
        llm = new FakeLLMClient();
        checker = new ConsistencyChecker(llm);
        session = new StorySession("s");
        session.setScenario(new Scenario("Ashfall", "A courier.", "", "A city", ""));
        session.getLoreBook().add(new LoreEntry(null, "Orrin", "Guildmaster Orrin died in the fire.", List.of(), true));
        session.getLoreBook().add(new LoreEntry(null, "The Ember Guild", "Smiths who keep the forges lit.", List.of(), false));
        session.getLoreBook().add(new LoreEntry(null, "The Docks", "Flooded every spring.", List.of(), false));
    }

    @Test
    void pinnedAndUsedEntriesAreSentAndContradictionsBecomeWarnings() {
        llm.enqueue(LLMResponse.ofText("{\"contradictions\": [{\"entry\": \"L1\", "
                + "\"problem\": \"Orrin speaks, but he died in the fire.\"}]}"));
        List<String> warnings = checker.check("Orrin greets you warmly.", session, List.of("L2"));

        assertEquals(List.of("Contradicts L1 \"Orrin\" (canon): Orrin speaks, but he died in the fire."), warnings);
        String sent = llm.getRequests().get(0).getMessages().get(0).getText();
        assertTrue(sent.contains("L1 [canon] Orrin: Guildmaster Orrin died in the fire."));
        assertTrue(sent.contains("L2 The Ember Guild: Smiths"), "the lore the turn used");
        assertFalse(sent.contains("The Docks"), "unpinned lore the turn did not use is left out");
        assertTrue(sent.endsWith("## Passage to check\nOrrin greets you warmly."));
        assertTrue(llm.getRequests().get(0).getSystemPrompt().contains("contradictions"));
    }

    @Test
    void noContradictionsMeansNoWarnings() {
        llm.enqueue(LLMResponse.ofText("```json\n{\"contradictions\": []}\n```"));
        assertTrue(checker.check("The forges glow.", session, List.of()).isEmpty());
    }

    @Test
    void withoutPinnedOrUsedLoreTheCheckIsSkipped() {
        StorySession bare = new StorySession("b");
        bare.getLoreBook().add(new LoreEntry(null, "The Docks", "Flooded every spring.", List.of(), false));
        assertTrue(checker.check("Anything.", bare, List.of()).isEmpty());
        assertTrue(llm.getRequests().isEmpty(), "no model call");
    }

    @Test
    void aFailedRefusedOrUnreadableCheckSaysTheTurnWasNotChecked() {
        llm.enqueueFailure(new LLMException("Could not reach Claude."))
                .enqueue(LLMResponse.ofRefusal())
                .enqueue(LLMResponse.ofText("Looks fine to me."));
        for (int i = 0; i < 3; i++) {
            List<String> w = checker.check("Text.", session, List.of());
            assertEquals(1, w.size());
            assertTrue(w.get(0).startsWith(ConsistencyChecker.NOT_CHECKED), w.get(0));
        }
    }

    @Test
    void anUnknownEntryIsNamedAsGivenAndTheListIsCapped() {
        StringBuilder many = new StringBuilder("{\"contradictions\": [{\"entry\": \"L9\", \"problem\": \"Odd.\"}");
        for (int i = 0; i < 8; i++) {
            many.append(", {\"entry\": \"L1\", \"problem\": \"Again ").append(i).append(".\"}");
        }
        llm.enqueue(LLMResponse.ofText(many.append("]}").toString()));
        List<String> w = checker.check("Text.", session, List.of());
        assertEquals("Contradicts L9: Odd.", w.get(0));
        assertEquals(ConsistencyChecker.MAX_WARNINGS, w.size());
    }

    @Test
    void theAgentStoresTheWarningsOnTheTurnWithoutBlockingIt() {
        StoryAgent agent = StoryAgent.create(llm);
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("a", "search_lore", Map.of("query", "ember")))))
                .enqueue(LLMResponse.ofText("Orrin hammers at the Ember Guild's anvil."))
                .enqueue(LLMResponse.ofText("{\"contradictions\": [{\"entry\": \"L1\", \"problem\": \"Orrin is dead.\"}]}"));
        Turn t = agent.takeTurn(session, "visit the guild", new DoModeStrategy());

        assertEquals("Orrin hammers at the Ember Guild's anvil.", t.getNarration());
        assertEquals(List.of("Contradicts L1 \"Orrin\" (canon): Orrin is dead."), t.getWarnings());
        String checked = llm.getRequests().get(2).getMessages().get(0).getText();
        assertTrue(checked.contains("L2 The Ember Guild"), "the lore looked up in the turn is checked too");
        assertTrue(checked.endsWith("Orrin hammers at the Ember Guild's anvil."));
    }
}
