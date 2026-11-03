package ca.yorku.storyteller.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Context building and summarizing in {@link MemoryManager} (F04, F09), with FakeLLMClient. */
class MemoryManagerTest {

    private static StorySession withTurns(int n) {
        StorySession s = new StorySession("s");
        for (int i = 1; i <= n; i++) {
            s.addTurn(new Turn(i, "Do", "input " + i, "narration " + i, List.of(), List.of()));
        }
        return s;
    }

    @Test
    void emptyBeforeTheFirstTurn() {
        assertEquals("", new MemoryManager(null).buildContext(new StorySession("s")));
    }

    @Test
    void onlyTheRecentTurnsAreSentWordForWordOnceTheSummaryCoversTheRest() {
        StorySession s = withTurns(4);
        s.setSummary("Earlier.", 2);
        String c = new MemoryManager(null, 2).buildContext(s);
        assertFalse(c.contains("narration 2"));
        assertTrue(c.contains("Earlier.\n\nTurn 3 (Do): input 3\nnarration 3"));
        assertTrue(c.endsWith("narration 4"));
    }

    @Test
    void turnsTheSummaryMissesAreSentTooUpToTwiceTheLimit() {
        String c = new MemoryManager(null, 2).buildContext(withTurns(6));
        assertTrue(c.startsWith("Turn 3 (Do)"), "no summary yet, so turns 3 and 4 are kept as well");
        assertFalse(c.contains("narration 2"), "but never more than twice the limit");
    }

    // ---- summarizeIfNeeded (F09) ----

    @Test
    void aShortStoryIsNotSummarized() {
        FakeLLMClient llm = new FakeLLMClient();
        assertFalse(new MemoryManager(llm, 3).summarizeIfNeeded(withTurns(3)));
        assertTrue(llm.getRequests().isEmpty());
    }

    @Test
    void turnsOlderThanTheLimitAreFoldedIntoTheSummary() {
        FakeLLMClient llm = new FakeLLMClient().enqueue(LLMResponse.ofText("  Mira crossed the city.  "));
        StorySession s = withTurns(5);
        assertTrue(new MemoryManager(llm, 3).summarizeIfNeeded(s));

        assertEquals("Mira crossed the city.", s.getSummary());
        assertEquals(2, s.getSummarizedUpTo());
        String sent = llm.getRequests().get(0).getMessages().get(0).getText();
        assertTrue(sent.startsWith("## Summary so far\nNone yet"));
        assertTrue(sent.contains("Turn 1 (Do): input 1\nnarration 1\n\nTurn 2 (Do): input 2\nnarration 2"));
        assertFalse(sent.contains("narration 3"), "recent turns are not summarized");
        assertTrue(llm.getRequests().get(0).getSystemPrompt().contains("running summary"));
    }

    @Test
    void theOldSummaryIsExtendedAndOnlyNewTurnsAreSent() {
        FakeLLMClient llm = new FakeLLMClient().enqueue(LLMResponse.ofText("Longer."));
        StorySession s = withTurns(6);
        s.setSummary("Short.", 2);
        assertTrue(new MemoryManager(llm, 3).summarizeIfNeeded(s));
        String sent = llm.getRequests().get(0).getMessages().get(0).getText();
        assertTrue(sent.startsWith("## Summary so far\nShort.\n\n## Turns to add\nTurn 3 (Do)"));
        assertFalse(sent.contains("narration 2"));
        assertEquals(3, s.getSummarizedUpTo());
        assertFalse(new MemoryManager(llm, 3).summarizeIfNeeded(s), "already up to date");
    }

    @Test
    void aFailedOrRefusedSummaryKeepsTheOldOneForNextTime() {
        FakeLLMClient llm = new FakeLLMClient().enqueueFailure(new LLMException("overloaded"))
                .enqueue(LLMResponse.ofRefusal()).enqueue(LLMResponse.ofText(" "));
        StorySession s = withTurns(5);
        s.setSummary("Old.", 1);
        MemoryManager memory = new MemoryManager(llm, 3);
        for (int i = 0; i < 3; i++) {
            assertFalse(memory.summarizeIfNeeded(s));
        }
        assertEquals("Old.", s.getSummary());
        assertEquals(1, s.getSummarizedUpTo());
    }

    @Test
    void theAgentSummarizesBeforeBuildingTheTurnsContext() {
        FakeLLMClient llm = new FakeLLMClient();
        StoryAgent agent = StoryAgent.create(llm);
        StorySession s = withTurns(MemoryManager.DEFAULT_RECENT_TURN_LIMIT + 2);
        llm.enqueue(LLMResponse.ofText("The first two turns, told short.")).enqueue(LLMResponse.ofText("Next."));
        Turn t = agent.takeTurn(s, "go on", new StoryModeStrategy());

        assertEquals("Next.", t.getNarration());
        assertEquals(2, s.getSummarizedUpTo());
        String prompt = llm.getRequests().get(1).getMessages().get(0).getText();
        assertTrue(prompt.contains("Summary of earlier events:\nThe first two turns, told short.\n\nTurn 3 (Do)"));
        assertFalse(prompt.contains("narration 2\n"));
    }

    @Test
    void theSummaryComesFirst() {
        StorySession s = withTurns(3);
        s.setSummary("Mira reached the city.", 2);
        String c = new MemoryManager(null, 1).buildContext(s);
        assertTrue(c.startsWith("Summary of earlier events:\nMira reached the city.\n\nTurn 3"));
    }

    @Test
    void theLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryManager(null, 0));
    }
}
