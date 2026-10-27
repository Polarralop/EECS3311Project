package ca.yorku.storyteller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Submit, undo and retry through SubmitTurnCommand and CommandHistory (F04, F10), with FakeLLMClient. */
class StoryTurnTest {

    @TempDir
    Path dir;

    private final FakeLLMClient llm = new FakeLLMClient();
    private StoryController controller;
    private StorySession session;
    private final List<StorySession> notifications = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        controller.newSession();
        controller.saveLoreEntry(new LoreEntry(null, "Ashfall", "A city under grey snow.", List.of(), true));
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"The letter is stolen.\", \"The guild burns.\"]}"));
        controller.startStory(new Scenario("Ashfall", "A courier.", "", "A city", ""), "");
        session = controller.getSession();
        controller.addObserver(notifications::add);
    }

    @Test
    void submitTurnAddsTheNarratedTurnAndNotifiesOnce() {
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("a", "search_lore", Map.of("query", "ashfall")))))
                .enqueue(LLMResponse.ofText("Snow falls."));
        Turn t = controller.submitTurn("look up", "do");
        assertEquals("Snow falls.", t.getNarration());
        assertEquals("Do", t.getModeName());
        assertEquals(List.of("L1"), t.getUsedLoreIds());
        assertEquals(List.of(t), session.getTurns());
        assertEquals(1, notifications.size());
        assertTrue(controller.canUndo());
    }

    @Test
    void theModeIsPickedByNameIgnoringCase() {
        llm.enqueue(LLMResponse.ofText("A."));
        assertEquals("Say", controller.submitTurn("hi", " SAY ").getModeName());
        assertEquals(List.of("Do", "Say", "Story"), controller.modeNames());
    }

    @Test
    void badInputIsRejectedWithoutAModelCall() {
        int before = llm.getRequests().size();
        assertThrows(IllegalArgumentException.class, () -> controller.submitTurn("  ", "do"));
        assertThrows(IllegalArgumentException.class, () -> controller.submitTurn("hi", "shout"));
        assertEquals(before, llm.getRequests().size());
        assertFalse(controller.canUndo());
    }

    @Test
    void turnsNeedAStartedStory() {
        controller.newSession();
        assertThrows(IllegalStateException.class, () -> controller.submitTurn("hi", "do"));
    }

    @Test
    void aFailedTurnAddsNothingAndCannotBeUndone() {
        llm.enqueueFailure(new LLMException("Could not reach Claude."));
        assertThrows(LLMException.class, () -> controller.submitTurn("hi", "do"));
        assertTrue(session.getTurns().isEmpty());
        assertFalse(controller.canUndo());
        assertTrue(notifications.isEmpty());
    }

    @Test
    void undoRemovesTheTurnAndRestoresThePlotAsItWas() {
        llm.enqueue(LLMResponse.ofText("A."));
        controller.submitTurn("hi", "do");
        session.getPlot().markReached("E1");
        controller.undoTurn();
        assertTrue(session.getTurns().isEmpty());
        assertEquals(EventStatus.PENDING, session.getPlot().getEvents().get(0).getStatus());
        assertFalse(controller.canUndo());
        assertEquals(2, notifications.size());
        assertThrows(IllegalStateException.class, () -> controller.undoTurn());
    }

    @Test
    void retryReplacesTheLastTurnFromTheSameInputAndMode() {
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofText("Second."));
        controller.submitTurn("open the gate", "say");
        Turn again = controller.retryTurn();
        assertEquals("Second.", again.getNarration());
        assertEquals("Say", again.getModeName());
        assertEquals("open the gate", again.getUserInput());
        assertEquals(List.of(again), session.getTurns());
        assertTrue(llm.getRequests().get(llm.getRequests().size() - 1).getMessages().get(0).getText()
                .contains("says, aloud: \"open the gate\""));
    }

    @Test
    void aFailedRetryLeavesTheTurnUndone() {
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofRefusal());
        controller.submitTurn("open the gate", "do");
        notifications.clear();
        assertThrows(LLMException.class, () -> controller.retryTurn());
        assertTrue(session.getTurns().isEmpty());
        assertFalse(controller.canUndo());
        assertEquals(1, notifications.size(), "the front ends still refresh to show the turn is gone");
    }

    @Test
    void retryNeedsATurnFromThisSession() {
        assertThrows(IllegalStateException.class, () -> controller.retryTurn());
    }

    @Test
    void undoHistoryIsClearedWhenAStoryIsLoaded() {
        llm.enqueue(LLMResponse.ofText("A."));
        controller.submitTurn("hi", "do");
        controller.saveSession();
        controller.loadSession(session.getId());
        assertFalse(controller.canUndo());
        assertEquals(1, controller.getSession().getTurns().size());
    }
}
