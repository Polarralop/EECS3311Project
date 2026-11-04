package ca.yorku.storyteller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Editing the latest turn with EditTurnCommand, and how it combines with undo and retry (F10). */
class EditTurnTest {

    @TempDir
    Path dir;

    private final FakeLLMClient llm = new FakeLLMClient();
    private StoryController controller;
    private StorySession session;

    @BeforeEach
    void setUp() {
        controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        llm.enqueue(LLMResponse.ofText("{\"events\": [\"E\"]}"));
        controller.startStory(new Scenario("T", "A courier.", "", "A city", ""), "");
        session = controller.getSession();
    }

    @Test
    void editReplacesTheLatestNarrationAndUndoRestoresIt() {
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofText("Original."));
        controller.submitTurn("a", "do");
        controller.submitTurn("b", "do");
        controller.editTurn("  My version.  ");
        assertEquals("My version.", session.getTurns().get(1).getNarration());
        assertEquals("First.", session.getTurns().get(0).getNarration());

        controller.undoTurn();
        assertEquals(2, session.getTurns().size(), "undoing an edit keeps the turn");
        assertEquals("Original.", session.getTurns().get(1).getNarration());
        controller.undoTurn();
        assertEquals(1, session.getTurns().size());
    }

    @Test
    void editClearsTheConsistencyWarningsAndUndoBringsThemBack() {
        controller.saveLoreEntry(new ca.yorku.storyteller.model.LoreEntry(null, "Orrin", "Orrin is dead.", List.of(), true));
        llm.enqueue(LLMResponse.ofText("Orrin waves."))
                .enqueue(LLMResponse.ofText("{\"contradictions\": [{\"entry\": \"L1\", \"problem\": \"Orrin is dead.\"}]}"));
        Turn t = controller.submitTurn("a", "do");
        assertEquals(List.of("Contradicts L1 \"Orrin\" (canon): Orrin is dead."), t.getWarnings());

        controller.editTurn("Orrin's portrait hangs above the door.");
        assertTrue(t.getWarnings().isEmpty(), "the warning was about the agent's text");
        controller.undoTurn();
        assertEquals(1, t.getWarnings().size());
        assertEquals("Orrin waves.", t.getNarration());
    }

    @Test
    void emptyTextIsRejectedAndNothingChanges() {
        llm.enqueue(LLMResponse.ofText("Original."));
        controller.submitTurn("a", "do");
        assertThrows(IllegalArgumentException.class, () -> controller.editTurn(" \n "));
        assertEquals("Original.", session.lastTurn().orElseThrow().getNarration());
        controller.undoTurn();
        assertFalse(controller.canUndo(), "the rejected edit was not recorded");
    }

    @Test
    void thereMustBeATurnToEdit() {
        assertThrows(IllegalStateException.class, () -> controller.editTurn("text"));
        assertFalse(controller.canUndo());
    }

    @Test
    void retryAfterAnEditRegeneratesTheTurnAndDropsTheEdit() {
        llm.enqueue(LLMResponse.ofText("Original.")).enqueue(LLMResponse.ofText("Regenerated."));
        controller.submitTurn("a", "do");
        controller.editTurn("Mine.");
        Turn t = controller.retryTurn();
        assertEquals(List.of(t), session.getTurns());
        assertEquals("Regenerated.", t.getNarration());
        controller.undoTurn();
        assertTrue(session.getTurns().isEmpty());
        assertFalse(controller.canUndo());
    }

    @Test
    void aTurnLoadedFromASaveCanBeEditedAndTheEditUndone() {
        llm.enqueue(LLMResponse.ofText("Original."));
        controller.submitTurn("a", "do");
        controller.saveSession();
        llm.enqueue(LLMResponse.ofText("A recap."));
        controller.loadSession(session.getId());
        controller.editTurn("Mine.");
        controller.undoTurn();
        assertEquals("Original.", controller.getSession().lastTurn().orElseThrow().getNarration());
        assertThrows(IllegalStateException.class, () -> controller.retryTurn(), "the turn itself is not in the history");
    }
}
