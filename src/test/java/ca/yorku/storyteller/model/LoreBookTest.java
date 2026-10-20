package ca.yorku.storyteller.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link LoreBook} and {@link LoreEntry}. */
class LoreBookTest {

    private LoreBook book;

    @BeforeEach
    void setUp() {
        book = new LoreBook();
    }

    private static LoreEntry entry(String title, String content, String... tags) {
        return new LoreEntry(null, title, content, List.of(tags), false);
    }

    @Test
    void addAssignsSequentialIds() {
        LoreEntry a = entry("Ashfall", "A city under grey snow.");
        LoreEntry b = entry("The Ember Guild", "Smiths who never let a forge go cold.");
        book.add(a);
        book.add(b);
        assertEquals("L1", a.getId());
        assertEquals("L2", b.getId());
        assertEquals(2, book.size());
    }

    @Test
    void addRejectsDuplicateId() {
        book.add(new LoreEntry("L7", "Ashfall", "A city.", List.of(), false));
        LoreEntry clash = new LoreEntry("l7", "Other", "Other text.", List.of(), false);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> book.add(clash));
        assertTrue(e.getMessage().contains("L7") || e.getMessage().contains("l7"));
    }

    @Test
    void generatedIdsSkipIdsAlreadyTaken() {
        book.add(new LoreEntry("L1", "Taken", "Already here.", List.of(), false));
        LoreEntry next = entry("Next", "Gets a free id.");
        book.add(next);
        assertEquals("L2", next.getId());
    }

    @Test
    void addRejectsDuplicateTitleIgnoringCase() {
        book.add(entry("Ashfall", "A city."));
        assertThrows(IllegalArgumentException.class, () -> book.add(entry("ashfall", "Again.")));
    }

    @Test
    void entryRejectsEmptyTitleOrContent() {
        assertThrows(IllegalArgumentException.class, () -> entry("  ", "Content."));
        assertThrows(IllegalArgumentException.class, () -> entry("Title", ""));
        assertThrows(IllegalArgumentException.class, () -> entry(null, "Content."));
    }

    @Test
    void tagsAreNormalisedAndInvalidTagsRejected() {
        LoreEntry e = entry("Ashfall", "A city.", " City ", "north", "city");
        assertEquals(List.of("city", "north"), e.getTags());
        assertThrows(IllegalArgumentException.class, () -> entry("Bad", "Tag.", "two words"));
        assertThrows(IllegalArgumentException.class, () -> entry("Bad", "Tag.", ""));
        assertThrows(IllegalArgumentException.class, () -> entry("Bad", "Tag.", "semi;colon"));
    }

    @Test
    void updateReplacesEntryWithSameId() {
        LoreEntry e = entry("Ashfall", "A city.");
        book.add(e);
        LoreEntry edited = e.copy();
        edited.setContent("A city buried in ash.");
        edited.setPinned(true);
        book.update(edited);
        assertEquals("A city buried in ash.", book.find("L1").orElseThrow().getContent());
        assertTrue(book.find("L1").orElseThrow().isPinned());
    }

    @Test
    void updateUnknownIdFails() {
        LoreEntry ghost = new LoreEntry("L99", "Ghost", "Not stored.", List.of(), false);
        assertThrows(IllegalArgumentException.class, () -> book.update(ghost));
    }

    @Test
    void updateCannotTakeAnotherEntrysTitle() {
        book.add(entry("Ashfall", "A city."));
        LoreEntry other = entry("Emberhold", "A fort.");
        book.add(other);
        LoreEntry edited = other.copy();
        edited.setTitle("ASHFALL");
        assertThrows(IllegalArgumentException.class, () -> book.update(edited));
    }

    @Test
    void removeDeletesEntryAndRejectsUnknownId() {
        book.add(entry("Ashfall", "A city."));
        book.remove("L1");
        assertEquals(0, book.size());
        assertThrows(IllegalArgumentException.class, () -> book.remove("L1"));
    }

    @Test
    void searchMatchesTitleContentAndTagsAllWords() {
        book.add(entry("Ashfall", "A city under grey snow.", "city"));
        book.add(entry("The Ember Guild", "Smiths of Ashfall.", "faction"));
        book.add(entry("Old Road", "Leads north.", "place"));

        assertEquals(2, book.search("ashfall").size());
        assertEquals(1, book.search("FACTION").size());
        assertEquals(1, book.search("ashfall smiths").size());
        assertEquals(0, book.search("ashfall dragons").size());
        assertEquals(3, book.search("").size());
    }

    @Test
    void searchListsPinnedEntriesFirst() {
        book.add(entry("Alpha", "Shared word."));
        LoreEntry pinned = new LoreEntry(null, "Zulu", "Shared word.", List.of(), true);
        book.add(pinned);
        List<LoreEntry> results = book.search("shared");
        assertEquals("Zulu", results.get(0).getTitle());
        assertEquals("Alpha", results.get(1).getTitle());
    }

    @Test
    void getPinnedReturnsOnlyPinnedEntries() {
        book.add(entry("Loose", "Not canon."));
        book.add(new LoreEntry(null, "Canon", "Always true.", List.of(), true));
        List<LoreEntry> pinned = book.getPinned();
        assertEquals(1, pinned.size());
        assertEquals("Canon", pinned.get(0).getTitle());
    }

    @Test
    void copyIsIndependentOfStoredEntry() {
        LoreEntry e = entry("Ashfall", "A city.");
        book.add(e);
        LoreEntry copy = book.find("L1").orElseThrow().copy();
        copy.setTitle("Changed");
        assertFalse(book.search("changed").size() > 0);
    }
}
