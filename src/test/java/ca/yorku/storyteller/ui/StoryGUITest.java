package ca.yorku.storyteller.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import ca.yorku.storyteller.llm.LLMException;
import ca.yorku.storyteller.llm.LLMResponse;
import ca.yorku.storyteller.llm.LLMToolCall;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link StoryGUI}'s panels without a window (works headless): fills the forms, clicks the
 * buttons and checks what reaches the controller and what the observer refresh shows.
 */
class StoryGUITest {

    /** Scripted model; tests never make live calls. */
    private final FakeLLMClient llm = new FakeLLMClient();

    @TempDir
    Path dir;

    private StoryController controller;
    private TestGUI gui;

    /** Records dialogs instead of opening them. */
    private static class TestGUI extends StoryGUI {
        final List<String> errors = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        String sessionToPick;

        TestGUI(StoryController c) {
            super(c);
        }

        @Override
        public void showError(String message) {
            errors.add(message);
        }

        @Override
        protected void showMessage(String message) {
            messages.add(message);
        }

        @Override
        protected String chooseSession(List<String> ids) {
            return sessionToPick;
        }

        String narrationToType;

        @Override
        protected String askNarration(String current) {
            return narrationToType;
        }

        @Override
        protected void openStartForm() {
        }

        /** Model calls run straight away on the test's thread instead of a SwingWorker. */
        @Override
        protected <T> void runInBackground(String busyText, Supplier<T> work, Consumer<T> onSuccess, Runnable onFailure) {
            completeNow(work, onSuccess, onFailure);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        controller = new StoryController(new JsonSessionRepository(dir), StoryAgent.create(llm));
        onEdt(() -> {
            gui = new TestGUI(controller);
            controller.addObserver(gui);
        });
    }

    private static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void savingLoreWithoutAStoryShowsAnError() throws Exception {
        onEdt(() -> {
            gui.loreTitle.setText("Ashfall");
            gui.loreContent.setText("A city.");
            gui.loreSave.doClick();
        });
        assertEquals(List.of("Start a new story or load one first."), gui.errors);
    }

    @Test
    void loreFormAddsEditsSearchesAndDeletes() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.loreTitle.setText("Ashfall");
            gui.loreContent.setText("A city under grey snow.");
            gui.loreTags.setText("place, city");
            gui.loreSave.doClick();
            gui.loreNew.doClick();
            gui.loreTitle.setText("Ember Guild");
            gui.loreContent.setText("Smiths.");
            gui.lorePinned.setSelected(true);
            gui.loreSave.doClick();
        });
        assertEquals(2, gui.loreItems.size());
        assertEquals("Ember Guild", gui.loreItems.get(0).getTitle(), "pinned entries are listed first");
        assertTrue(gui.status.getText().contains("2 lore entries"));

        onEdt(() -> {
            gui.loreList.setSelectedIndex(1);
            gui.loreContent.setText("A city buried in ash.");
            gui.loreSave.doClick();
            gui.loreSearch.setText("ash");
        });
        assertEquals("A city buried in ash.", controller.getLoreEntry("L1").getContent());
        assertEquals(1, gui.loreItems.size());

        onEdt(() -> {
            gui.loreSearch.setText("");
            gui.loreList.setSelectedIndex(0);
            gui.loreDelete.doClick();
        });
        assertEquals(1, controller.searchLore("").size());
        assertTrue(gui.errors.isEmpty(), gui.errors.toString());
    }

    @Test
    void invalidLoreShowsTheModelsMessage() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.loreContent.setText("No title.");
            gui.loreSave.doClick();
            gui.loreTitle.setText("Bad tags");
            gui.loreTags.setText("two words");
            gui.loreSave.doClick();
        });
        assertEquals("A lore entry needs a title.", gui.errors.get(0));
        assertTrue(gui.errors.get(1).contains("not a valid tag"));
    }

    @Test
    void characterFormAddsRenamesAndRelates() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.characterName.setText("Mira");
            gui.characterTraits.setText("stubborn, curious");
            gui.characterSave.doClick();
            gui.characterNew.doClick();
            gui.characterName.setText("Tomas");
            gui.characterSave.doClick();
            gui.characterList.setSelectedIndex(0);
            gui.relationshipTarget.setSelectedItem("Tomas");
            gui.relationshipType.setText("sister");
            gui.relationshipAdd.doClick();
        });
        assertEquals(1, gui.relationshipItems.size());
        assertEquals("sister", controller.getCharacter("Mira").relationshipTo("Tomas").orElseThrow().getType());

        onEdt(() -> {
            gui.characterList.setSelectedIndex(1);
            gui.characterName.setText("Tomas Vell");
            gui.characterSave.doClick();
        });
        assertEquals("Tomas Vell", controller.getCharacter("Mira").getRelationships().get(0).getTargetName());
        assertEquals(List.of("stubborn", "curious"), controller.getCharacter("Mira").getTraits());

        onEdt(() -> {
            gui.characterList.setSelectedIndex(0);
            gui.relationshipList.setSelectedIndex(0);
            gui.relationshipRemove.doClick();
            gui.characterNew.doClick();
            gui.characterName.setText("mira");
            gui.characterSave.doClick();
        });
        assertTrue(controller.getCharacter("Mira").getRelationships().isEmpty());
        assertEquals(List.of("A character named mira already exists."), gui.errors);
    }

    @Test
    void saveAndLoadThroughTheMenuActions() throws Exception {
        onEdt(() -> {
            controller.newSession();
            gui.characterName.setText("Mira");
            gui.characterSave.doClick();
            gui.act(gui::save);
        });
        String id = controller.getSession().getId();
        onEdt(() -> {
            controller.newSession();
            gui.sessionToPick = id;
            gui.act(gui::load);
        });
        assertEquals(1, gui.characterItems.size());
        assertTrue(gui.messages.get(1).startsWith("Loaded"));
        assertTrue(gui.status.getText().contains(id));
    }

    // ---- story tab, start form and proposals (F01, F04, F05, F07, F10) ----

    private static final String PLAN = "{\"events\": [\"The letter is stolen by the guild.\"]}";

    /** Fills the start form and clicks Start story. */
    private void startFromTheForm() throws Exception {
        llm.enqueue(LLMResponse.ofText(PLAN));
        onEdt(() -> {
            gui.scenarioPremise.setText("A courier carries a forbidden letter.");
            gui.scenarioSetting.setText("A city under grey snow");
            gui.scenarioOpening.setText("Mira waits at the gate.");
            gui.plotBasis.setText("a betrayal");
            gui.startButton.doClick();
        });
    }

    @Test
    void turnControlsAreUnavailableUntilTheStoryStarts() throws Exception {
        onEdt(() -> controller.newSession());
        assertFalse(gui.send.isEnabled());
        assertFalse(gui.input.isEnabled());
        assertFalse(gui.undo.isEnabled());
        assertTrue(gui.storyTitle.getText().startsWith("Story not started yet"));
    }

    @Test
    void theFormDraftsAScenarioAndStartsTheStoryWithoutShowingThePlan() throws Exception {
        llm.enqueue(LLMResponse.ofText("{\"title\": \"Ashfall\", \"premise\": \"A courier.\", \"tone\": \"grim\", "
                + "\"setting\": \"A snowy city.\", \"openingSituation\": \"At the gate.\"}"));
        onEdt(() -> {
            gui.scenarioIdea.setText("a courier in the snow");
            gui.generate.doClick();
        });
        assertEquals("Ashfall", gui.scenarioTitle.getText());
        assertEquals("A snowy city.", gui.scenarioSetting.getText());

        llm.enqueue(LLMResponse.ofText(PLAN));
        onEdt(() -> {
            gui.scenarioTitle.setText("Ember Road");
            gui.startButton.doClick();
        });
        assertTrue(controller.isStarted());
        assertEquals("Ember Road", gui.storyTitle.getText());
        assertEquals("At the gate.", gui.transcript.getText());
        assertTrue(gui.send.isEnabled());
        assertEquals("", gui.scenarioTitle.getText(), "the form is cleared after a start");
        assertFalse(gui.transcript.getText().contains("stolen"));
        assertFalse(gui.status.getText().contains("stolen"));
        assertTrue(gui.errors.isEmpty(), gui.errors.toString());
    }

    @Test
    void aStartWithoutASettingShowsAnErrorAndStartsNothing() throws Exception {
        onEdt(() -> {
            gui.scenarioPremise.setText("A premise.");
            gui.startButton.doClick();
        });
        assertEquals(List.of("A story needs a setting."), gui.errors);
        assertFalse(controller.isStarted());
        assertEquals("A premise.", gui.scenarioPremise.getText(), "the form keeps what was typed");
    }

    @Test
    void sendShowsTheNarrationAndTheLoreUsed() throws Exception {
        onEdt(() -> {
            controller.newSession();
            controller.saveLoreEntry(new ca.yorku.storyteller.model.LoreEntry(null, "Ashfall", "A city.", List.of(), true));
        });
        startFromTheForm();
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(new LLMToolCall("a", "search_lore", Map.of("query", "ashfall")))))
                .enqueue(LLMResponse.ofText("Snow settles on the gate."));
        onEdt(() -> {
            gui.modeChoice.setSelectedItem("Say");
            gui.input.setText("Open up!");
            gui.send.doClick();
        });
        assertTrue(gui.transcript.getText().endsWith("> Say: Open up!\n\nSnow settles on the gate."));
        assertEquals("Ashfall", gui.usedLoreItems.get(0).getTitle());
        assertEquals("", gui.input.getText());
        assertTrue(gui.undo.isEnabled() && gui.retry.isEnabled() && gui.edit.isEnabled());
    }

    @Test
    void aFailedTurnKeepsTheInputInTheBox() throws Exception {
        startFromTheForm();
        llm.enqueueFailure(new LLMException("Could not reach Claude. Check your internet connection."));
        onEdt(() -> {
            gui.input.setText("climb the wall");
            gui.send.doClick();
        });
        assertEquals(List.of("Could not reach Claude. Check your internet connection."), gui.errors);
        assertEquals("climb the wall", gui.input.getText());
        assertTrue(controller.getSession().getTurns().isEmpty());
    }

    @Test
    void undoRetryAndEditButtons() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofText("Second."));
        onEdt(() -> {
            gui.input.setText("knock");
            gui.send.doClick();
            gui.retry.doClick();
        });
        assertTrue(gui.transcript.getText().endsWith("Second."));

        gui.narrationToType = "My own ending to the turn.";
        onEdt(() -> gui.edit.doClick());
        assertTrue(gui.transcript.getText().endsWith("My own ending to the turn."));

        gui.narrationToType = null;
        onEdt(() -> gui.edit.doClick());
        assertTrue(gui.transcript.getText().endsWith("My own ending to the turn."), "cancel changes nothing");

        onEdt(() -> gui.undo.doClick());
        assertTrue(gui.transcript.getText().endsWith("Second."));
        onEdt(() -> gui.undo.doClick());
        assertEquals("Mira waits at the gate.", gui.transcript.getText());
        assertFalse(gui.undo.isEnabled());
        assertTrue(gui.errors.isEmpty(), gui.errors.toString());
    }

    @Test
    void aFailedRetryPutsTheInputBack() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofText("First.")).enqueue(LLMResponse.ofRefusal());
        onEdt(() -> {
            gui.modeChoice.setSelectedItem("Story");
            gui.input.setText("The bells rang.");
            gui.send.doClick();
            gui.modeChoice.setSelectedItem("Do");
            gui.retry.doClick();
        });
        assertTrue(gui.errors.get(0).contains("declined"));
        assertEquals("The bells rang.", gui.input.getText());
        assertEquals("Story", gui.modeChoice.getSelectedItem());
        assertTrue(controller.getSession().getTurns().isEmpty());
    }

    @Test
    void proposalsAreListedWithACountAndCanBeApprovedOrRejected() throws Exception {
        startFromTheForm();
        llm.enqueue(LLMResponse.ofToolCalls("", List.of(
                        new LLMToolCall("a", "propose_lore", Map.of("title", "The Ember Guild", "content", "Smiths.")),
                        new LLMToolCall("b", "propose_lore", Map.of("title", "Grey Snow", "content", "It never melts.")))))
                .enqueue(LLMResponse.ofText("The forges glow."));
        onEdt(() -> {
            gui.input.setText("visit the forges");
            gui.send.doClick();
        });
        assertEquals(2, gui.proposalItems.size());
        assertTrue(gui.status.getText().contains("2 lore proposals waiting"));

        onEdt(() -> {
            gui.proposalList.setSelectedIndex(0);
            gui.approve.doClick();
        });
        assertEquals("Smiths.", controller.getLoreEntry("L1").getContent());
        assertEquals(1, gui.proposalItems.size());

        onEdt(() -> {
            gui.reject.doClick();
            gui.proposalList.setSelectedIndex(0);
            gui.reject.doClick();
        });
        assertEquals(List.of("Select a proposal first."), gui.errors);
        assertEquals(0, gui.proposalItems.size());
        assertEquals(1, controller.searchLore("").size());
    }
}
