package ca.yorku.storyteller.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.controller.StoryController;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import ca.yorku.storyteller.agent.StoryAgent;
import ca.yorku.storyteller.llm.FakeLLMClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
}
