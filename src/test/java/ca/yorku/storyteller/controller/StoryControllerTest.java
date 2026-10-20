package ca.yorku.storyteller.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.persistence.JsonSessionRepository;
import ca.yorku.storyteller.ui.StoryObserver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the lore, character and save/load paths of {@link StoryController}. */
class StoryControllerTest {

    @TempDir
    Path dir;

    private StoryController controller;
    private final List<StorySession> notifications = new ArrayList<>();
    private final StoryObserver recorder = notifications::add;

    @BeforeEach
    void setUp() {
        controller = new StoryController(new JsonSessionRepository(dir));
        controller.addObserver(recorder);
    }

    @Test
    void everythingNeedsASessionFirst() {
        assertThrows(IllegalStateException.class, () -> controller.searchLore(""));
        assertThrows(IllegalStateException.class, () -> controller.saveSession());
        assertThrows(IllegalStateException.class,
                () -> controller.saveCharacter(null, new Character("Mira", "", List.of())));
    }

    @Test
    void newSessionNotifiesObservers() {
        StorySession s = controller.newSession();
        assertEquals(1, notifications.size());
        assertSame(s, notifications.get(0));
        assertTrue(s.getId().startsWith("story-"));
    }

    @Test
    void saveLoreEntryAddsThenUpdatesAndNotifiesEachTime() {
        controller.newSession();
        controller.saveLoreEntry(new LoreEntry(null, "Ashfall", "A city.", List.of("city"), false));
        LoreEntry edit = controller.getLoreEntry("L1");
        edit.setPinned(true);
        controller.saveLoreEntry(edit);

        assertEquals(3, notifications.size());
        List<LoreEntry> found = controller.searchLore("city");
        assertEquals(1, found.size());
        assertTrue(found.get(0).isPinned());
    }

    @Test
    void getLoreEntryReturnsACopy() {
        controller.newSession();
        controller.saveLoreEntry(new LoreEntry(null, "Ashfall", "A city.", List.of(), false));
        LoreEntry copy = controller.getLoreEntry("L1");
        copy.setTitle("Changed");
        assertEquals("Ashfall", controller.searchLore("").get(0).getTitle());
    }

    @Test
    void invalidLoreChangesNothingAndDoesNotNotify() {
        controller.newSession();
        controller.saveLoreEntry(new LoreEntry(null, "Ashfall", "A city.", List.of(), false));
        int before = notifications.size();
        assertThrows(IllegalArgumentException.class,
                () -> controller.saveLoreEntry(new LoreEntry(null, "ashfall", "Duplicate.", List.of(), false)));
        assertThrows(IllegalArgumentException.class, () -> controller.removeLoreEntry("L9"));
        assertEquals(before, notifications.size());
        assertEquals(1, controller.searchLore("").size());
    }

    @Test
    void charactersAndRelationshipsGoThroughTheController() {
        controller.newSession();
        controller.saveCharacter(null, new Character("Mira", "Apprentice.", List.of()));
        controller.saveCharacter(null, new Character("Tomas", "Brother.", List.of()));
        controller.addRelationship("Mira", "Tomas", "sister", "");
        Character edit = controller.getCharacter("Tomas");
        edit.setName("Tomas Vell");
        controller.saveCharacter("Tomas", edit);

        assertEquals("Tomas Vell", controller.getCharacter("Mira").getRelationships().get(0).getTargetName());
        controller.removeRelationship("Mira", "Tomas Vell");
        assertTrue(controller.getCharacter("Mira").getRelationships().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> controller.removeRelationship("Mira", "Tomas Vell"));
        assertThrows(IllegalArgumentException.class, () -> controller.addRelationship("Mira", "Ghost", "friend", ""));
        controller.removeCharacter("Mira");
        assertEquals(1, controller.listCharacters().size());
    }

    @Test
    void saveAndLoadSwapsTheSessionAndMovesObservers() {
        StorySession first = controller.newSession();
        controller.saveLoreEntry(new LoreEntry(null, "Ashfall", "A city.", List.of(), true));
        controller.saveSession();
        StorySession second = controller.newSession();
        notifications.clear();

        String recap = controller.loadSession(first.getId());

        StorySession loaded = controller.getSession();
        assertNotSame(first, loaded);
        assertEquals(first.getId(), loaded.getId());
        assertTrue(recap.contains("1 lore entries"));
        assertEquals(List.of(loaded), notifications);
        second.notifyObservers();
        assertEquals(1, notifications.size(), "the old session no longer notifies the front ends");
        assertEquals(List.of(first.getId()), controller.listSessions());
    }

    @Test
    void undoHistoryIsEmptyAfterALoad() {
        StorySession s = controller.newSession();
        controller.saveSession();
        // Stand-in command: the real turn commands arrive with F04/F10.
        int[] undone = {0};
        StoryCommand marker = new StoryCommand() {
            @Override
            public void execute() {
            }

            @Override
            public void undo() {
                undone[0]++;
            }
        };
        CommandHistory history = historyOf(controller);
        history.run(marker);
        assertTrue(controller.canUndo());

        controller.loadSession(s.getId());

        assertFalse(controller.canUndo());
        assertEquals(0, undone[0]);
    }

    @Test
    void failedLoadKeepsTheCurrentStory() {
        StorySession current = controller.newSession();
        assertThrows(IllegalArgumentException.class, () -> controller.loadSession("missing"));
        assertSame(current, controller.getSession());
    }

    private static CommandHistory historyOf(StoryController c) {
        try {
            var f = StoryController.class.getDeclaredField("history");
            f.setAccessible(true);
            return (CommandHistory) f.get(c);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
