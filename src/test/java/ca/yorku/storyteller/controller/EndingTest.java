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
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Suggesting, writing and keeping the story's ending through StoryController (F12), with FakeLLMClient. */
class EndingTest {

    @TempDir
    Path dir;

    private final FakeLLMClient llm = new FakeLLMClient();
    private StoryController controller;
    private StorySession session;
    private final List<StorySession> notifications = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"The letter is stolen.\"]}"));
        controller.startStory(new Scenario("Ashfall", "A courier.", "", "A city", ""), "");
        session = controller.getSession();
        controller.addObserver(notifications::add);
    }

    /** A turn in which the model marks the only pivotal event as reached. */
    private void reachTheLastEvent() {
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("u", "update_plot",
                        Map.of("action", "reached", "event_id", "E1")))))
                .enqueue(LLMResponse.ofText("A thief snatches the letter."));
        controller.submitTurn("guard the letter", "do");
    }

    @Test
    void theTurnThatReachesTheLastEventSuggestsAnEndingOnce() {
        llm.enqueue(LLMResponse.ofText("Nothing yet."));
        controller.submitTurn("wait", "do");
        assertFalse(controller.isEndingSuggested());
        assertFalse(controller.isReadyToEnd());

        reachTheLastEvent();
        assertTrue(controller.isEndingSuggested());
        assertTrue(controller.isReadyToEnd());

        llm.enqueue(LLMResponse.ofText("You chase the thief."));
        controller.submitTurn("chase", "do");
        assertFalse(controller.isEndingSuggested(), "dismissed: not suggested again on later turns");
        assertTrue(controller.isReadyToEnd(), "End story still needs no confirmation");
    }

    @Test
    void retryingTheTurnThatReachedTheLastEventSuggestsAgain() {
        reachTheLastEvent();
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("u", "update_plot",
                        Map.of("action", "reached", "event_id", "E1")))))
                .enqueue(LLMResponse.ofText("The letter is gone."));
        controller.retryTurn();
        assertTrue(controller.isEndingSuggested());
    }

    @Test
    void requestEndingStoresTheEndingAndClosesTheStory() {
        reachTheLastEvent();
        notifications.clear();
        llm.enqueue(LLMResponse.ofText("Dawn finds you at the gate, the letter ash."));
        String ending = controller.requestEnding();

        assertEquals("Dawn finds you at the gate, the letter ash.", ending);
        assertEquals(ending, session.getEnding());
        assertTrue(controller.isEnded());
        assertFalse(controller.isEndingSuggested());
        assertEquals(1, notifications.size());
        assertTrue(llm.getRequests().get(llm.getRequests().size() - 1).getMessages().get(0).getText()
                .contains("[happened] The letter is stolen."));

        assertThrows(IllegalStateException.class, () -> controller.submitTurn("more", "do"));
        assertThrows(IllegalStateException.class, () -> controller.undoTurn());
        assertThrows(IllegalStateException.class, () -> controller.retryTurn());
        assertThrows(IllegalStateException.class, () -> controller.editTurn("Mine."));
        assertEquals(1, session.getTurns().size());
    }

    @Test
    void anEndedStoryCanHaveItsEndingRewritten() {
        reachTheLastEvent();
        llm.enqueue(LLMResponse.ofText("First ending.")).enqueue(LLMResponse.ofText("Second ending."));
        controller.requestEnding();
        assertEquals("Second ending.", controller.requestEnding());
        assertEquals("Second ending.", session.getEnding());
    }

    @Test
    void anEarlyEndingIsWrittenFromTheCurrentPoint() {
        llm.enqueue(LLMResponse.ofText("You stand at the gate."));
        controller.submitTurn("look", "do");
        assertFalse(controller.isReadyToEnd(), "the front end asks the user to confirm");
        llm.enqueue(LLMResponse.ofText("You turn back home."));
        controller.requestEnding();
        assertTrue(controller.isEnded());
        assertTrue(llm.getRequests().get(llm.getRequests().size() - 1).getMessages().get(0).getText()
                .contains("[did not happen] The letter is stolen."));
    }

    @Test
    void anEndingNeedsAStartedStoryWithATurn() {
        assertFalse(controller.canEnd());
        int calls = llm.getRequests().size();
        assertEquals("Take at least one turn before ending the story.",
                assertThrows(IllegalStateException.class, () -> controller.requestEnding()).getMessage());
        controller.newSession();
        assertThrows(IllegalStateException.class, () -> controller.requestEnding());
        assertEquals(calls, llm.getRequests().size(), "no model call");
    }

    @Test
    void aFailedEndingChangesNothing() {
        reachTheLastEvent();
        notifications.clear();
        llm.enqueueFailure(new LLMException("Could not reach Claude."));
        assertThrows(LLMException.class, () -> controller.requestEnding());
        assertFalse(controller.isEnded());
        assertTrue(notifications.isEmpty());
        assertTrue(controller.canUndo(), "play can go on");
    }

    @Test
    void theEndingIsKeptAcrossSaveAndLoad() {
        reachTheLastEvent();
        llm.enqueue(LLMResponse.ofText("The end of Ashfall."));
        controller.requestEnding();
        controller.saveSession();
        controller.newSession();
        llm.enqueue(LLMResponse.ofText("You lost the letter and the city."));
        controller.loadSession(session.getId());

        assertTrue(controller.isEnded());
        assertEquals("The end of Ashfall.", controller.getSession().getEnding());
        assertThrows(IllegalStateException.class, () -> controller.submitTurn("more", "do"));
    }
}
