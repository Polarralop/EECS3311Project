package ca.yorku.storyteller.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.storyteller.model.Character;
import ca.yorku.storyteller.model.EventStatus;
import ca.yorku.storyteller.model.LoreEntry;
import ca.yorku.storyteller.model.LoreProposal;
import ca.yorku.storyteller.model.PivotalEvent;
import ca.yorku.storyteller.model.Plot;
import ca.yorku.storyteller.model.Relationship;
import ca.yorku.storyteller.model.Scenario;
import ca.yorku.storyteller.model.StorySession;
import ca.yorku.storyteller.model.Turn;
import ca.yorku.storyteller.ui.StoryObserver;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link JsonSessionRepository}. */
class JsonSessionRepositoryTest {

    @TempDir
    Path dir;

    private JsonSessionRepository repo;

    @BeforeEach
    void setUp() {
        repo = new JsonSessionRepository(dir.resolve("saves"));
    }

    private static StorySession fullSession(String id) {
        StorySession s = new StorySession(id);
        s.setScenario(new Scenario("Ashfall", "A city under grey snow.", "Bleak", "Volcanic north", "The bell rings."));
        Plot plot = new Plot("A rescue", List.of(new PivotalEvent("E1", "Mira finds the map."),
                new PivotalEvent("E2", "The mountain wakes.")));
        plot.markReached("E1");
        s.setPlot(plot);
        s.getLoreBook().add(new LoreEntry(null, "Ash Bell", "Rings before eruptions.", List.of("landmark"), true));
        s.addCharacter(new Character("Mira", "Apprentice smith.", List.of("stubborn")));
        s.addCharacter(new Character("Tomas", "Her brother.", List.of()));
        s.addRelationship("Mira", new Relationship("Tomas", "sister", "protective"));
        s.addTurn(new Turn(1, "Do", "I ring the bell.", "The bell's note rolls over the roofs.", List.of("L1"),
                List.of("Contradicts pinned lore")));
        s.addProposal(new LoreProposal(null, "Ember Guild", "Smiths of Ashfall."));
        s.addProposal(new LoreProposal(null, "Rumour", "False."));
        s.rejectProposal("P2");
        s.setSummary("Mira rang the bell.", 1);
        return s;
    }

    @Test
    void roundTripKeepsTheWholeStory() {
        repo.save(fullSession("story-1"));
        StorySession loaded = repo.load("story-1");

        assertEquals("Ashfall", loaded.getScenario().getTitle());
        assertEquals("The bell rings.", loaded.getScenario().getOpeningSituation());
        assertEquals(EventStatus.REACHED, loaded.getPlot().getEvents().get(0).getStatus());
        assertEquals("E2", loaded.getPlot().nextPending().orElseThrow().getId());
        assertEquals("A rescue", loaded.getPlot().getBasis());

        LoreEntry bell = loaded.getLoreBook().find("L1").orElseThrow();
        assertTrue(bell.isPinned());
        assertEquals(List.of("landmark"), bell.getTags());

        Character mira = loaded.findCharacter("Mira").orElseThrow();
        assertEquals(List.of("stubborn"), mira.getTraits());
        assertEquals("sister", mira.relationshipTo("Tomas").orElseThrow().getType());

        Turn t = loaded.lastTurn().orElseThrow();
        assertEquals("I ring the bell.", t.getUserInput());
        assertEquals(List.of("L1"), t.getUsedLoreIds());
        assertEquals(1, t.getWarnings().size());

        assertEquals(1, loaded.getPendingProposals().size());
        assertEquals(2, loaded.getProposals().size());
        assertEquals("Mira rang the bell.", loaded.getSummary());
        assertEquals(1, loaded.getSummarizedUpTo());
    }

    @Test
    void idCountersContinueAfterLoad() {
        repo.save(fullSession("story-1"));
        StorySession loaded = repo.load("story-1");
        LoreEntry next = new LoreEntry(null, "New", "Added after load.", List.of(), false);
        loaded.getLoreBook().add(next);
        assertEquals("L2", next.getId());
        LoreProposal p = new LoreProposal(null, "Another", "Text.");
        loaded.addProposal(p);
        assertEquals("P3", p.getId());
    }

    @Test
    void observersAreNotSavedAndLoadedSessionAcceptsNewOnes() {
        StorySession s = fullSession("story-1");
        int[] calls = {0};
        StoryObserver o = session -> calls[0]++;
        s.attach(o);
        repo.save(s);
        StorySession loaded = repo.load("story-1");
        loaded.notifyObservers();
        assertEquals(0, calls[0]);
        loaded.attach(o);
        loaded.notifyObservers();
        assertEquals(1, calls[0]);
    }

    @Test
    void saveReplacesEarlierSave() {
        StorySession s = fullSession("story-1");
        repo.save(s);
        s.addCharacter(new Character("Ines", "", List.of()));
        repo.save(s);
        assertEquals(3, repo.load("story-1").getCharacters().size());
        assertEquals(List.of("story-1"), repo.listSessions());
    }

    @Test
    void missingSaveIsReported() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> repo.load("nope"));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void corruptFileIsReportedAsDamaged() throws Exception {
        Files.createDirectories(dir.resolve("saves"));
        Files.writeString(dir.resolve("saves/broken.json"), "{ \"id\": \"broken\", \"turns\": [ oops");
        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> repo.load("broken"));
        assertTrue(e.getMessage().contains("damaged"));
    }

    @Test
    void fileWhoseIdDoesNotMatchIsReportedAsDamaged() throws Exception {
        repo.save(fullSession("story-1"));
        Files.copy(dir.resolve("saves/story-1.json"), dir.resolve("saves/story-2.json"));
        assertThrows(UncheckedIOException.class, () -> repo.load("story-2"));
    }

    @Test
    void unsafeIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> repo.load("../secrets"));
        assertThrows(IllegalArgumentException.class, () -> repo.save(new StorySession("a/b")));
    }

    @Test
    void listSessionsIsNewestFirstAndEmptyWithoutDirectory() throws Exception {
        assertTrue(repo.listSessions().isEmpty());
        repo.save(fullSession("older"));
        repo.save(fullSession("newer"));
        Files.setLastModifiedTime(dir.resolve("saves/older.json"), FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(dir.resolve("saves/newer.json"), FileTime.fromMillis(2_000));
        Files.writeString(dir.resolve("saves/notes.txt"), "ignored");
        assertEquals(List.of("newer", "older"), repo.listSessions());
    }

    @Test
    void failedWriteLeavesNoTemporaryFiles() throws Exception {
        Path blocked = dir.resolve("file-not-dir");
        Files.writeString(blocked, "x");
        JsonSessionRepository bad = new JsonSessionRepository(blocked);
        assertThrows(UncheckedIOException.class, () -> bad.save(fullSession("s")));
        repo.save(fullSession("ok"));
        try (var files = Files.list(dir.resolve("saves"))) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".tmp")));
        }
    }
}
