package ca.yorku.storyteller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.ProposalStatus;
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

/** From a propose_lore call during a turn to the user's decision in resolveProposal (F07). */
class LoreProposalFlowTest {

    @TempDir
    Path dir;

    private final FakeLLMClient llm = new FakeLLMClient();
    private StoryController controller;
    private StorySession session;
    private final List<StorySession> notifications = new ArrayList<>();

    @BeforeEach
    void setUp() {
        controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"E\"]}"));
        controller.startStory(new Scenario("T", "A courier.", "", "A city", ""), "");
        session = controller.getSession();
        controller.addObserver(notifications::add);
    }

    private void turnThatProposes(String title) {
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("p", "propose_lore",
                        Map.of("title", title, "content", "Smiths of the city.")))))
                .enqueue(LLMResponse.ofText("The forges glow."));
        controller.submitTurn("visit the forges", "do");
    }

    @Test
    void approvingAProposalAddsItToTheLoreBook() {
        turnThatProposes("The Ember Guild");
        assertEquals(1, controller.listPendingProposals().size());
        assertEquals(0, session.getLoreBook().size(), "nothing reaches the lore book before approval");
        notifications.clear();

        controller.resolveProposal("p1", true);

        assertTrue(controller.listPendingProposals().isEmpty());
        LoreEntry e = session.getLoreBook().getAll().get(0);
        assertEquals("The Ember Guild", e.getTitle());
        assertEquals("Smiths of the city.", e.getContent());
        assertEquals(ProposalStatus.APPROVED, session.getProposals().get(0).getStatus());
        assertEquals(1, notifications.size());
    }

    @Test
    void rejectingAProposalAddsNothing() {
        turnThatProposes("The Ember Guild");
        controller.resolveProposal("P1", false);
        assertEquals(0, session.getLoreBook().size());
        assertEquals(ProposalStatus.REJECTED, session.getProposals().get(0).getStatus());
        assertThrows(IllegalArgumentException.class, () -> controller.resolveProposal("P1", true));
    }

    @Test
    void aProposalWhoseTitleWasTakenMeanwhileStaysPending() {
        turnThatProposes("The Ember Guild");
        controller.saveLoreEntry(new LoreEntry(null, "The Ember Guild", "Written by the user.", List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> controller.resolveProposal("P1", true));
        assertEquals(1, controller.listPendingProposals().size());
    }

    @Test
    void proposalsFromAnUndoneTurnStayInTheList() {
        turnThatProposes("The Ember Guild");
        controller.undoTurn();
        assertEquals(1, controller.listPendingProposals().size());
    }

    @Test
    void proposalsRaisedBeforeAFailedTurnAreKeptAndShown() {
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("p", "propose_lore",
                        Map.of("title", "The Ember Guild", "content", "Smiths.")))))
                .enqueueFailure(new LLMException("down"));
        assertThrows(LLMException.class, () -> controller.submitTurn("go", "do"));
        assertEquals(1, controller.listPendingProposals().size());
        assertEquals(1, notifications.size());
    }

    @Test
    void unknownIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> controller.resolveProposal("P9", true));
    }
}
